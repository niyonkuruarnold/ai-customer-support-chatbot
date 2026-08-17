package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding raw FAQ/support text to the knowledge base
 * (POST /api/admin/documents/text).
 */
public class KnowledgeTextRequestDto {

    @NotBlank(message = "Title cannot be empty")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Content cannot be empty")
    @Size(max = 100000, message = "Content must be at most 100000 characters")
    private String content;

    public KnowledgeTextRequestDto() {}

    public KnowledgeTextRequestDto(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
