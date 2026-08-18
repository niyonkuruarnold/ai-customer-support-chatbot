package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    /**
     * Find documents that have never been indexed (indexedAt IS NULL) or
     * were updated after their last scan (updatedAt > indexedAt).
     * The fallback date is used to handle NULL indexedAt in the comparison.
     */
    @Query("""
        SELECT d FROM KnowledgeDocument d
        WHERE d.indexedAt IS NULL
           OR d.updatedAt > d.indexedAt
    """)
    List<KnowledgeDocument> findUnindexedOrUpdated();
}
