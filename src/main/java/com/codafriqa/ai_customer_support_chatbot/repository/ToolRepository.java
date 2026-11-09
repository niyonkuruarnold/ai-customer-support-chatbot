package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {

    /** Get all tools owned by a specific user. */
    List<Tool> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Get all tools with a specific status. */
    List<Tool> findByStatusOrderByCreatedAtDesc(ToolStatus status);

    /** Get all tools owned by a user with a specific status. */
    List<Tool> findByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, ToolStatus status);
}
