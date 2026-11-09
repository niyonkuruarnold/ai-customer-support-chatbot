import { createMockDashboard } from '../data/mockData.js';
import { getKnowledgeFromDatabase, loadDashboardFromDatabase, recordConversationTurn } from '../lib/db.js';
const state = createMockDashboard();
function tokenize(text) {
    return text
        .toLowerCase()
        .replace(/[^a-z0-9\s]/g, ' ')
        .split(/\s+/)
        .filter((token) => token.length > 2);
}
function rankKnowledge(message, knowledge) {
    const tokens = tokenize(message);
    return knowledge
        .map((item) => {
        const haystack = `${item.title} ${item.summary} ${item.content} ${item.tags.join(' ')}`.toLowerCase();
        const matches = tokens.reduce((score, token) => score + (haystack.includes(token) ? 1 : 0), 0);
        return { item, score: matches };
    })
        .sort((left, right) => right.score - left.score || right.item.trustScore - left.item.trustScore)
        .filter((entry) => entry.score > 0)
        .slice(0, 3)
        .map((entry) => entry.item);
}
function detectSentiment(message) {
    const normalized = message.toLowerCase();
    if (/(angry|cancel|lawyer|fraud|terrible|unacceptable|today|asap|now)/.test(normalized)) {
        return 'Critical';
    }
    if (/(charged|refund|delay|late|frustrated|issue|broken)/.test(normalized)) {
        return 'Frustrated';
    }
    return 'Calm';
}
function shouldEscalate(message, matchedKnowledge) {
    const normalized = message.toLowerCase();
    return (matchedKnowledge.length === 0 ||
        /(fraud|lawyer|chargeback|vip|outage|public|enterprise|angry|cancel my account)/.test(normalized));
}
function createActions(message, knowledge) {
    const normalized = message.toLowerCase();
    const actions = ['Summarize case', 'Prepare handoff notes'];
    if (/password|login|sign/.test(normalized)) {
        actions.unshift('Send recovery steps');
    }
    if (/refund|charged|billing|invoice|subscription/.test(normalized)) {
        actions.unshift('Open billing review');
    }
    if (/order|delivery|shipping|late/.test(normalized)) {
        actions.unshift('Start courier trace');
    }
    if (knowledge.some((item) => item.tags.includes('vip'))) {
        actions.unshift('Flag VIP workflow');
    }
    return Array.from(new Set(actions)).slice(0, 4);
}
function buildReplyText(message, matchedKnowledge, escalated) {
    if (matchedKnowledge.length === 0) {
        return 'I could not find a trusted knowledge-base answer for that yet, so I am packaging the conversation for a human agent with the relevant urgency and context.';
    }
    const lead = matchedKnowledge[0];
    const opening = `I found guidance in "${lead.title}".`;
    const guidance = lead.content.split('. ').slice(0, 2).join('. ').trim();
    const close = escalated
        ? 'Because this looks sensitive, I am also escalating it with a ready-to-use summary so your team can jump in fast.'
        : 'Based on that guidance, this looks solvable without waiting for a human handoff unless you want extra review.';
    return `${opening} ${guidance}. ${close}`;
}
function toAssistantMessage(content) {
    return {
        id: `msg-${Date.now()}`,
        role: 'assistant',
        author: 'Ari Copilot',
        content,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
}
function toEscalatedTicket(message, sentiment) {
    return {
        id: `T-${Math.floor(2500 + Math.random() * 400)}`,
        customer: 'Live customer',
        topic: message.slice(0, 42) + (message.length > 42 ? '...' : ''),
        category: /refund|billing|invoice|charge/.test(message.toLowerCase()) ? 'Billing' : 'General support',
        status: 'Escalated',
        priority: sentiment === 'Critical' ? 'Urgent' : 'High',
        sentiment,
        eta: sentiment === 'Critical' ? '10 min' : '35 min',
        updatedAt: 'just now',
        slaRisk: true,
    };
}
export async function getDashboard() {
    const fromDatabase = await loadDashboardFromDatabase();
    return fromDatabase ?? state;
}
export async function getKnowledge() {
    const fromDatabase = await getKnowledgeFromDatabase();
    return fromDatabase ?? state.knowledge;
}
export async function handleChatMessage(message) {
    const knowledge = await getKnowledge();
    const matchedKnowledge = rankKnowledge(message, knowledge);
    const escalated = shouldEscalate(message, matchedKnowledge);
    const sentiment = detectSentiment(message);
    const confidence = matchedKnowledge.length === 0 ? 42 : Math.min(97, 66 + matchedKnowledge.length * 11);
    const suggestedActions = createActions(message, matchedKnowledge);
    const replyText = buildReplyText(message, matchedKnowledge, escalated);
    const assistantMessage = toAssistantMessage(replyText);
    state.conversation.push({
        id: `msg-${Date.now()}-customer`,
        role: 'customer',
        author: 'Live customer',
        content: message,
        timestamp: assistantMessage.timestamp,
    });
    state.conversation.push(assistantMessage);
    let ticket;
    let escalationReason;
    if (escalated) {
        escalationReason =
            sentiment === 'Critical'
                ? 'Urgency, risk language, or VIP context triggered an immediate handoff.'
                : 'The request needs human review because the automation confidence is below the safe threshold.';
        ticket = toEscalatedTicket(message, sentiment);
        state.tickets = [ticket, ...state.tickets].slice(0, 6);
    }
    const persisted = await recordConversationTurn({
        customerMessage: message,
        assistantMessage: replyText,
        escalated,
        escalationReason,
    });
    if (ticket && persisted) {
        ticket.id = persisted.id;
    }
    return {
        message: assistantMessage,
        confidence,
        matchedKnowledge,
        suggestedActions,
        escalated,
        escalationReason,
        ticket,
    };
}
