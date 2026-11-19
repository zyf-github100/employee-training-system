package com.zhixing.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.GlobalExceptionHandler;
import com.zhixing.training.auth.AccountSetupController;
import com.zhixing.training.auth.AccountSetupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ValidationLoggingTests {
    @Test
    void activationValidationLogsResponseTraceIdAndRulesWithoutSubmittedValues(CapturedOutput output) throws Exception {
        var service = mock(AccountSetupService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AccountSetupController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        var response = mvc.perform(post("/api/v1/auth/account-setup")
                        .queryParam("token", "example-query-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeNo":"sample-employee","token":"example-setup-secret!","newPassword":"Q7!zB"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.newPassword").isString())
                .andReturn().getResponse();

        String traceId = new ObjectMapper().readTree(response.getContentAsByteArray()).path("traceId").asText();
        assertThat(traceId).isNotBlank();
        String event = output.getAll().lines()
                .filter(line -> line.contains("API validation failed"))
                .findFirst()
                .orElseThrow();
        assertThat(event)
                .contains("traceId=" + traceId, "method=POST", "route=/api/v1/auth/account-setup",
                        "token=Pattern", "newPassword=Size")
                .doesNotContain("sample-employee", "example-query-secret", "example-setup-secret!", "Q7!zB");
        verifyNoInteractions(service);
    }
}
