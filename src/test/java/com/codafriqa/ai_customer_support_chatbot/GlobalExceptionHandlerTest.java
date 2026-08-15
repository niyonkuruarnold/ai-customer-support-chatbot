package com.codafriqa.ai_customer_support_chatbot;

import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the Spring MVC slice (which wires up every @ControllerAdvice and its
 * ExceptionHandlerMethodResolver) and verifies the structured error contract.
 * Guards against the "Ambiguous @ExceptionHandler method mapped" context
 * initialization failure.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false) // this test covers exception handling, not security
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorsReturnStructuredBadRequest() throws Exception {
        mockMvc.perform(post("/probe/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.text").value("Text cannot be empty"));
    }

    @Test
    void notFoundReturns404WithMessage() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"));
    }

    @Test
    void unexpectedErrorsReturn500() throws Exception {
        mockMvc.perform(get("/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    record EchoRequest(@NotBlank(message = "Text cannot be empty") String text) {}

    @RestController
    static class ProbeController {

        @PostMapping("/probe/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.text();
        }

        @GetMapping("/probe/not-found")
        String notFound() {
            throw new ResourceNotFoundException("missing");
        }

        @GetMapping("/probe/crash")
        String crash() {
            throw new IllegalStateException("boom");
        }
    }
}
