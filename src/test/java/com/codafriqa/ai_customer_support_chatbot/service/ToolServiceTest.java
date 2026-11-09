package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateToolRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.UpdateToolStatusRequest;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {

    @Mock
    private ToolRepository toolRepository;

    private ToolService service;

    private static final Long TOOL_ID = 1L;
    private static final Long OWNER_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new ToolService(toolRepository);
    }

    private Tool availableTool() {
        Tool tool = new Tool("Drill", "Power drill", "Power Tools", OWNER_ID);
        tool.setId(TOOL_ID);
        tool.setStatus(ToolStatus.AVAILABLE);
        return tool;
    }

    private CreateToolRequest validRequest() {
        return new CreateToolRequest("Hammer", "Claw hammer", "Hand Tools", OWNER_ID);
    }

    // ---- createTool ----

    @Test
    void createToolSuccess() {
        when(toolRepository.save(any(Tool.class)))
                .thenAnswer(inv -> {
                    Tool t = inv.getArgument(0);
                    t.setId(1L);
                    return t;
                });

        Tool result = service.createTool(validRequest());

        assertEquals("Hammer", result.getName());
        assertEquals("Claw hammer", result.getDescription());
        assertEquals("Hand Tools", result.getCategory());
        assertEquals(OWNER_ID, result.getOwnerId());
        assertEquals(ToolStatus.AVAILABLE, result.getStatus());
        verify(toolRepository).save(any(Tool.class));
    }

    @Test
    void createToolRejectsMissingName() {
        CreateToolRequest noName = new CreateToolRequest("", "desc", "cat", OWNER_ID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTool(noName));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void createToolRejectsMissingOwnerId() {
        CreateToolRequest noOwner = new CreateToolRequest("Hammer", "desc", "cat", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTool(noOwner));
        assertTrue(ex.getMessage().contains("Owner"));
    }

    @Test
    void createToolRejectsMissingCategory() {
        CreateToolRequest noCategory = new CreateToolRequest("Hammer", "desc", "", OWNER_ID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTool(noCategory));
        assertTrue(ex.getMessage().contains("Category"));
    }

    // ---- getTool ----

    @Test
    void getToolFound() {
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(availableTool()));

        Tool result = service.getTool(TOOL_ID);
        assertEquals(TOOL_ID, result.getId());
    }

    @Test
    void getToolNotFound() {
        when(toolRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getTool(999L));
    }

    // ---- list queries ----

    @Test
    void getToolsByOwnerDelegatesToRepository() {
        when(toolRepository.findByOwnerIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(availableTool()));

        List<Tool> result = service.getToolsByOwner(OWNER_ID);
        assertEquals(1, result.size());
        verify(toolRepository).findByOwnerIdOrderByCreatedAtDesc(OWNER_ID);
    }

    @Test
    void getToolsByStatusDelegatesToRepository() {
        when(toolRepository.findByStatusOrderByCreatedAtDesc(ToolStatus.AVAILABLE))
                .thenReturn(List.of(availableTool()));

        List<Tool> result = service.getToolsByStatus(ToolStatus.AVAILABLE);
        assertEquals(1, result.size());
    }

    // ---- updateStatus ----

    @Test
    void updateStatusAvailableToMaintenance() {
        Tool tool = availableTool();
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> inv.getArgument(0));

        Tool result = service.updateStatus(TOOL_ID, new UpdateToolStatusRequest(ToolStatus.IN_MAINTENANCE));

        assertEquals(ToolStatus.IN_MAINTENANCE, result.getStatus());
    }

    @Test
    void updateStatusMaintenanceToAvailable() {
        Tool tool = availableTool();
        tool.setStatus(ToolStatus.IN_MAINTENANCE);
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> inv.getArgument(0));

        Tool result = service.updateStatus(TOOL_ID, new UpdateToolStatusRequest(ToolStatus.AVAILABLE));

        assertEquals(ToolStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void updateStatusRejectsMaintenanceToBorrowed() {
        Tool tool = availableTool();
        tool.setStatus(ToolStatus.IN_MAINTENANCE);
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(TOOL_ID, new UpdateToolStatusRequest(ToolStatus.BORROWED)));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
        verify(toolRepository, never()).save(any());
    }

    @Test
    void updateStatusRejectsBorrowedToMaintenance() {
        Tool tool = availableTool();
        tool.setStatus(ToolStatus.BORROWED);
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(TOOL_ID, new UpdateToolStatusRequest(ToolStatus.IN_MAINTENANCE)));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    void updateStatusRejectsNullStatus() {
        // No need to stub findById since the null check happens first
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(TOOL_ID, new UpdateToolStatusRequest(null)));
        assertTrue(ex.getMessage().contains("Status"));
    }

    @Test
    void updateStatusThrowsNotFoundForMissingTool() {
        when(toolRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(999L, new UpdateToolStatusRequest(ToolStatus.AVAILABLE)));
    }

    // ---- toDto ----

    @Test
    void toDtoConvertsCorrectly() {
        Tool tool = availableTool();

        var dto = service.toDto(tool);

        assertEquals(tool.getId(), dto.id());
        assertEquals(tool.getName(), dto.name());
        assertEquals(tool.getDescription(), dto.description());
        assertEquals(tool.getCategory(), dto.category());
        assertEquals(tool.getOwnerId(), dto.ownerId());
        assertEquals(tool.getStatus(), dto.status());
        assertEquals(tool.getCreatedAt(), dto.createdAt());
        assertEquals(tool.getUpdatedAt(), dto.updatedAt());
    }
}
