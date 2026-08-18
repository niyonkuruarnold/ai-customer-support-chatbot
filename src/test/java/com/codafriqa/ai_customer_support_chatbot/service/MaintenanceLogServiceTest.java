package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CreateMaintenanceLogRequest;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.MaintenanceLog;
import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.MaintenanceLogRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceLogServiceTest {

    @Mock
    private MaintenanceLogRepository maintenanceLogRepository;

    @Mock
    private ToolRepository toolRepository;

    private MaintenanceLogService service;

    private static final Long TOOL_ID = 1L;
    private static final Long OWNER_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new MaintenanceLogService(maintenanceLogRepository, toolRepository);
    }

    private Tool availableTool() {
        Tool tool = new Tool("Drill", "Power drill", "Power Tools", OWNER_ID);
        tool.setId(TOOL_ID);
        tool.setStatus(ToolStatus.AVAILABLE);
        return tool;
    }

    private Tool maintenanceTool() {
        Tool tool = new Tool("Saw", "Circular saw", "Cutting", OWNER_ID);
        tool.setId(2L);
        tool.setStatus(ToolStatus.IN_MAINTENANCE);
        return tool;
    }

    private MaintenanceLog savedLog() {
        MaintenanceLog log = new MaintenanceLog(TOOL_ID, LocalDate.now().minusDays(5), "Blade replacement");
        log.setId(1L);
        log.setCost(new BigDecimal("25.50"));
        log.setNextServiceDue(LocalDate.now().plusDays(30));
        return log;
    }

    private CreateMaintenanceLogRequest validRequest() {
        return new CreateMaintenanceLogRequest(
                TOOL_ID,
                LocalDate.now(),
                "Oil change and lubrication",
                new BigDecimal("15.00"),
                LocalDate.now().plusDays(90));
    }

    // ---- createMaintenanceLog ----

    @Test
    void createMaintenanceLogSuccess() {
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(availableTool()));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class)))
                .thenAnswer(inv -> {
                    MaintenanceLog log = inv.getArgument(0);
                    log.setId(1L);
                    return log;
                });
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceLog result = service.createMaintenanceLog(validRequest());

        assertEquals(TOOL_ID, result.getToolId());
        assertEquals("Oil change and lubrication", result.getDescription());
        assertEquals(new BigDecimal("15.00"), result.getCost());
        verify(maintenanceLogRepository).save(any(MaintenanceLog.class));
        verify(toolRepository).save(any(Tool.class));
    }

    @Test
    void createMaintenanceLogSetsToolToMaintenance() {
        Tool tool = availableTool();
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class)))
                .thenAnswer(inv -> {
                    MaintenanceLog log = inv.getArgument(0);
                    log.setId(1L);
                    return log;
                });
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createMaintenanceLog(validRequest());

        assertEquals(ToolStatus.IN_MAINTENANCE, tool.getStatus());
    }

    @Test
    void createMaintenanceLogDoesNotChangeToolIfAlreadyInMaintenance() {
        Tool tool = maintenanceTool();
        when(toolRepository.findById(2L)).thenReturn(Optional.of(tool));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class)))
                .thenAnswer(inv -> {
                    MaintenanceLog log = inv.getArgument(0);
                    log.setId(1L);
                    return log;
                });

        CreateMaintenanceLogRequest request = new CreateMaintenanceLogRequest(
                2L, LocalDate.now(), "Repair", null, null);

        service.createMaintenanceLog(request);

        assertEquals(ToolStatus.IN_MAINTENANCE, tool.getStatus());
        verify(toolRepository, never()).save(any(Tool.class));
    }

    @Test
    void createMaintenanceLogRejectsMissingToolId() {
        CreateMaintenanceLogRequest noTool = new CreateMaintenanceLogRequest(
                null, LocalDate.now(), "Service", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMaintenanceLog(noTool));
        assertTrue(ex.getMessage().contains("Tool ID"));
    }

    @Test
    void createMaintenanceLogRejectsMissingServiceDate() {
        CreateMaintenanceLogRequest noDate = new CreateMaintenanceLogRequest(
                TOOL_ID, null, "Service", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMaintenanceLog(noDate));
        assertTrue(ex.getMessage().contains("Service date"));
    }

    @Test
    void createMaintenanceLogRejectsMissingDescription() {
        CreateMaintenanceLogRequest noDesc = new CreateMaintenanceLogRequest(
                TOOL_ID, LocalDate.now(), "", null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createMaintenanceLog(noDesc));
        assertTrue(ex.getMessage().contains("Description"));
    }

    @Test
    void createMaintenanceLogThrowsNotFoundForMissingTool() {
        when(toolRepository.findById(999L)).thenReturn(Optional.empty());

        CreateMaintenanceLogRequest missing = new CreateMaintenanceLogRequest(
                999L, LocalDate.now(), "Service", null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.createMaintenanceLog(missing));
    }

    // ---- getMaintenanceLog ----

    @Test
    void getMaintenanceLogFound() {
        when(maintenanceLogRepository.findById(1L)).thenReturn(Optional.of(savedLog()));

        MaintenanceLog result = service.getMaintenanceLog(1L);
        assertEquals(1L, result.getId());
    }

    @Test
    void getMaintenanceLogNotFound() {
        when(maintenanceLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getMaintenanceLog(999L));
    }

    // ---- list queries ----

    @Test
    void getLogsByToolDelegatesToRepository() {
        when(maintenanceLogRepository.findByToolIdOrderByServiceDateDesc(TOOL_ID))
                .thenReturn(List.of(savedLog()));

        List<MaintenanceLog> result = service.getLogsByTool(TOOL_ID);
        assertEquals(1, result.size());
        verify(maintenanceLogRepository).findByToolIdOrderByServiceDateDesc(TOOL_ID);
    }

    @Test
    void getLogsByToolAndDateRangeDelegatesToRepository() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();
        when(maintenanceLogRepository.findByToolIdAndServiceDateBetweenOrderByServiceDateDesc(
                TOOL_ID, start, end))
                .thenReturn(List.of(savedLog()));

        List<MaintenanceLog> result = service.getLogsByToolAndDateRange(TOOL_ID, start, end);
        assertEquals(1, result.size());
    }

    @Test
    void getUpcomingMaintenanceDelegatesToRepository() {
        LocalDate beforeDate = LocalDate.now().plusDays(7);
        when(maintenanceLogRepository.findUpcomingMaintenance(beforeDate))
                .thenReturn(List.of(savedLog()));

        List<MaintenanceLog> result = service.getUpcomingMaintenance(beforeDate);
        assertEquals(1, result.size());
    }

    @Test
    void getLogCountForToolDelegatesToRepository() {
        when(maintenanceLogRepository.countByToolId(TOOL_ID)).thenReturn(5L);

        Long count = service.getLogCountForTool(TOOL_ID);
        assertEquals(5L, count);
    }

    // ---- completeMaintenance ----

    @Test
    void completeMaintenanceSuccess() {
        Tool tool = maintenanceTool();
        when(toolRepository.findById(2L)).thenReturn(Optional.of(tool));
        when(toolRepository.save(any(Tool.class))).thenAnswer(inv -> inv.getArgument(0));

        Tool result = service.completeMaintenance(2L);

        assertEquals(ToolStatus.AVAILABLE, result.getStatus());
        verify(toolRepository).save(any(Tool.class));
    }

    @Test
    void completeMaintenanceRejectsNonMaintenanceTool() {
        Tool tool = availableTool();
        when(toolRepository.findById(TOOL_ID)).thenReturn(Optional.of(tool));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.completeMaintenance(TOOL_ID));
        assertTrue(ex.getMessage().contains("not in maintenance"));
        verify(toolRepository, never()).save(any());
    }

    @Test
    void completeMaintenanceThrowsNotFoundForMissingTool() {
        when(toolRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.completeMaintenance(999L));
    }

    // ---- toDto ----

    @Test
    void toDtoConvertsCorrectly() {
        MaintenanceLog log = savedLog();

        var dto = service.toDto(log);

        assertEquals(log.getId(), dto.id());
        assertEquals(log.getToolId(), dto.toolId());
        assertEquals(log.getServiceDate(), dto.serviceDate());
        assertEquals(log.getDescription(), dto.description());
        assertEquals(log.getCost(), dto.cost());
        assertEquals(log.getNextServiceDue(), dto.nextServiceDue());
        assertEquals(log.getCreatedAt(), dto.createdAt());
        assertEquals(log.getUpdatedAt(), dto.updatedAt());
    }
}
