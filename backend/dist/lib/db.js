import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Pool } from 'pg';
import { createMockDashboard, defaultKnowledge, defaultTickets } from '../data/mockData.js';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
function getDatabaseUrl() {
    if (process.env.DATABASE_URL) {
        return process.env.DATABASE_URL;
    }
    if (process.env.PGHOST) {
        const user = process.env.PGUSER ?? 'postgres';
        const password = process.env.PGPASSWORD ?? 'postgrespassword';
        const host = process.env.PGHOST;
        const port = process.env.PGPORT ?? '5432';
        const database = process.env.PGDATABASE ?? 'chatbot_db';
        return `postgresql://${user}:${password}@${host}:${port}/${database}`;
    }
    return '';
}
const connectionString = getDatabaseUrl();
export const pool = connectionString
    ? new Pool({
        connectionString,
    })
    : null;
export async function initializeDatabase() {
    if (!pool) {
        return false;
    }
    const schemaPath = path.resolve(__dirname, '../../../scripts/schema.sql');
    const schemaSql = await fs.readFile(schemaPath, 'utf8');
    await pool.query(schemaSql);
    const { rows } = await pool.query('SELECT COUNT(*)::text AS count FROM knowledge_chunks');
    if (Number(rows[0]?.count ?? 0) === 0) {
        await seedDatabase();
    }
    return true;
}
async function seedDatabase() {
    if (!pool) {
        return;
    }
    const agentResult = await pool.query(`
      INSERT INTO users (email, password_hash, role)
      VALUES ('agent@aurora-support.demo', 'demo-hash', 'AGENT')
      ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
      RETURNING id
    `);
    const customerResult = await pool.query(`
      INSERT INTO users (email, password_hash, role)
      VALUES ('maya.carter@example.com', 'demo-hash', 'CUSTOMER')
      ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
      RETURNING id
    `);
    const agentId = agentResult.rows[0].id;
    const customerId = customerResult.rows[0].id;
    const sessionResult = await pool.query(`
      INSERT INTO chat_sessions (customer_id, status)
      VALUES ($1, 'ACTIVE')
      RETURNING id
    `, [customerId]);
    const sessionId = sessionResult.rows[0].id;
    for (const item of defaultKnowledge) {
        await pool.query(`
        INSERT INTO knowledge_chunks (title, content)
        VALUES ($1, $2)
      `, [item.title, item.content]);
    }
    await pool.query(`
      INSERT INTO messages (session_id, sender_type, content)
      VALUES
      ($1, 'BOT', 'Welcome back. I can resolve account, billing, and delivery issues, then escalate anything sensitive with a full summary for your team.'),
      ($1, 'CUSTOMER', 'I was charged twice for my annual plan and the invoice still says unpaid. I need this fixed today.'),
      ($1, 'BOT', 'I can help with that. Duplicate charges are covered by our billing policy, and I am preparing the safest next step while checking whether this needs a billing specialist.')
    `, [sessionId]);
    for (const ticket of defaultTickets) {
        await pool.query(`
        INSERT INTO tickets (session_id, assigned_agent_id, status, priority)
        VALUES ($1, $2, $3, $4)
      `, [sessionId, agentId, ticket.status.toUpperCase().replaceAll(' ', '_'), ticket.priority.toUpperCase()]);
    }
}
function mapKnowledge(rows) {
    return rows.map((row, index) => ({
        id: `kb-${row.id}`,
        title: row.title,
        summary: row.content.slice(0, 90).trimEnd() + '...',
        content: row.content,
        tags: ['knowledge', index % 2 === 0 ? 'support' : 'operations'],
        trustScore: Math.max(88, 98 - index),
        lastReviewed: 'today',
    }));
}
function mapTickets(rows) {
    return rows.map((row, index) => ({
        id: `T-${2400 + row.id}`,
        customer: ['Maya Carter', 'Kane Foods', 'Alya Noor'][index % 3],
        topic: ['Duplicate annual-plan charge', 'VIP checkout outage', 'Order delayed in transit'][index % 3],
        category: ['Billing', 'Enterprise', 'Shipping'][index % 3],
        status: row.status.replaceAll('_', ' '),
        priority: row.priority,
        sentiment: ['Frustrated', 'Critical', 'Concerned'][index % 3],
        eta: ['34 min', '12 min', '2 hrs'][index % 3],
        updatedAt: 'moments ago',
        slaRisk: index % 2 === 0,
    }));
}
export async function loadDashboardFromDatabase() {
    if (!pool) {
        return null;
    }
    const [knowledgeResult, ticketResult] = await Promise.all([
        pool.query('SELECT id, title, content, created_at::text FROM knowledge_chunks ORDER BY id ASC LIMIT 6'),
        pool.query('SELECT id, status, priority, created_at::text FROM tickets ORDER BY id DESC LIMIT 6'),
    ]);
    const dashboard = createMockDashboard();
    dashboard.knowledge = mapKnowledge(knowledgeResult.rows);
    dashboard.tickets = mapTickets(ticketResult.rows);
    return dashboard;
}
export async function getKnowledgeFromDatabase() {
    if (!pool) {
        return null;
    }
    const result = await pool.query('SELECT id, title, content, created_at::text FROM knowledge_chunks ORDER BY id ASC');
    return mapKnowledge(result.rows);
}
export async function recordConversationTurn(input) {
    if (!pool) {
        return null;
    }
    const customerResult = await pool.query(`
      INSERT INTO users (email, password_hash, role)
      VALUES ('live.customer@example.com', 'demo-hash', 'CUSTOMER')
      ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
      RETURNING id
    `);
    const sessionResult = await pool.query(`
      INSERT INTO chat_sessions (customer_id, status)
      VALUES ($1, 'ACTIVE')
      RETURNING id
    `, [customerResult.rows[0].id]);
    const sessionId = sessionResult.rows[0].id;
    await pool.query(`
      INSERT INTO messages (session_id, sender_type, content)
      VALUES
      ($1, 'CUSTOMER', $2),
      ($1, 'BOT', $3)
    `, [sessionId, input.customerMessage, input.assistantMessage]);
    if (input.escalated) {
        const ticketResult = await pool.query(`
        INSERT INTO tickets (session_id, status, priority)
        VALUES ($1, 'OPEN', 'HIGH')
        RETURNING id
      `, [sessionId]);
        return {
            id: `T-${2400 + ticketResult.rows[0].id}`,
            reason: input.escalationReason ?? 'Escalated by the AI copilot',
        };
    }
    return null;
}
