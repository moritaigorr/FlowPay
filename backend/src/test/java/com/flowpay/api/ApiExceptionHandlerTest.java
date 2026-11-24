package com.flowpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowpay.observability.RequestCorrelationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestCorrelationFilter())
                .build();
    }

    @Test
    void mapsDomainStatusToProblemDetailsWithTheServerRequestId() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("A operação conflita com o estado atual."))
                .andExpect(jsonPath("$.instance").value("/api/test/conflict"))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestCorrelationFilter.RESPONSE_HEADER))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(result.getResponse().getContentAsString())
                .contains("\"requestId\":\"" + result.getResponse().getHeader(RequestCorrelationFilter.RESPONSE_HEADER) + "\"");
    }

    @Test
    void returnsFieldErrorsWithoutEchoingRejectedValues() throws Exception {
        MvcResult result = mvc.perform(post("/api/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"private input\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Revise os campos informados."))
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private input");
    }

    @Test
    void malformedJsonUsesTheSamePublicErrorShape() throws Exception {
        mvc.perform(post("/api/test/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void unsupportedMethodKeepsItsHttpStatusAndUsesThePublicErrorShape() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/test/conflict"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.detail").value("O método não é permitido para este recurso."));
    }

    @Test
    void unexpectedFailureDoesNotExposeExceptionDetails() throws Exception {
        mvc.perform(get("/api/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro interno. Tente novamente."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("sensitive database detail"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/api/test/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A operação conflita com o estado atual.");
        }

        @PostMapping("/api/test/validate")
        void validate(@Valid @RequestBody Payload payload) {}

        @GetMapping("/api/test/failure")
        void failure() {
            throw new IllegalStateException("sensitive database detail");
        }
    }

    record Payload(@NotBlank @Size(max = 3) String description) {}
}
