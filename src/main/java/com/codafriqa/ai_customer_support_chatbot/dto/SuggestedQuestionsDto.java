package com.codafriqa.ai_customer_support_chatbot.dto;

import java.util.List;

/**
 * DTO for the suggested quick-question chips returned to the customer frontend.
 *
 * <p>Questions are extracted dynamically from the currently indexed content
 * in the vector store (knowledge base documents + system data) so they
 * always reflect the latest indexed material.  When the vector store is
 * empty or unreachable, a hardcoded fallback list is returned.
 */
public class SuggestedQuestionsDto {

    private List<String> questions;
    private boolean fromKnowledgeBase;

    public SuggestedQuestionsDto() {
    }

    public SuggestedQuestionsDto(List<String> questions, boolean fromKnowledgeBase) {
        this.questions = questions;
        this.fromKnowledgeBase = fromKnowledgeBase;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = questions;
    }

    public boolean isFromKnowledgeBase() {
        return fromKnowledgeBase;
    }

    public void setFromKnowledgeBase(boolean fromKnowledgeBase) {
        this.fromKnowledgeBase = fromKnowledgeBase;
    }
}
