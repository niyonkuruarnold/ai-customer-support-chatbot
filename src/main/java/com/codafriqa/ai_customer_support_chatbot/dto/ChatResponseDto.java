package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * DTO for chat responses sent to the frontend
 */
public class ChatResponseDto {

    private String response;

    public ChatResponseDto() {
    }

    public ChatResponseDto(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
