package com.codafriqa.ai_customer_support_chatbot.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChatService {

    private final ChatModel chatModel;

    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generateResponse(String userMessage) {
        String systemInstruction = """
            You are a helpful, polite, and efficient AI Customer Support Agent for Code of Africa. 
            Your primary goal is to answer user inquiries accurately, clearly, and concisely.
            Always maintain a professional, empathetic tone.
            If you do not know the answer to a specific question, politely let the user know and offer to connect them with a human support representative. 
            Do not make up information or make promises regarding pricing or policies unless explicitly stated in your context.
            """;

        Message systemMessage = new SystemPromptTemplate(systemInstruction).createMessage();
        Message userMsg = new UserMessage(userMessage);

        Prompt prompt = new Prompt(List.of(systemMessage, userMsg));

        return chatModel.call(prompt).getResult().getOutput().getContent();
    }
}