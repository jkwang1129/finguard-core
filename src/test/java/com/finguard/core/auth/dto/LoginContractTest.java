package com.finguard.core.auth.dto;

import com.finguard.core.auth.validation.AuthInputNormalizer;
import com.finguard.core.auth.vo.LoginResponse;
import com.finguard.core.common.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

class LoginContractTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginContractController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRequestPreservesPasswordAndNormalizesUsernameSeparately() {
        LoginRequest request = new LoginRequest(
                "  Admin  ",
                "  Case-Sensitive Secret  "
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getPassword())
                .isEqualTo("  Case-Sensitive Secret  ");
        assertThat(AuthInputNormalizer.normalizeUsername(
                request.getUsername()
        )).isEqualTo("admin");
    }

    @Test
    void usernameLengthIsCheckedAfterNormalization() {
        LoginRequest request = new LoginRequest("  ab  ", "password");

        Set<String> fields = validator
                .validate(request)
                .stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).containsExactly("username");
    }

    @Test
    void passwordLengthUsesUtf8Bytes() {
        LoginRequest validRequest = new LoginRequest(
                "admin",
                "密".repeat(24)
        );
        LoginRequest invalidRequest = new LoginRequest(
                "admin",
                "密".repeat(25)
        );

        assertThat(validator.validate(validRequest)).isEmpty();
        assertThat(validator.validate(invalidRequest))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .containsExactly("password");
    }

    @Test
    void requestToStringRedactsPassword() {
        String password = "never-log-this-password";
        LoginRequest request = new LoginRequest("admin", password);

        assertThat(request.toString())
                .contains("username='admin'")
                .contains("password='<redacted>'")
                .doesNotContain(password);
    }

    @Test
    void responseUsesBearerContractAndRedactsToken() {
        String token = "header.payload.signature";
        LoginResponse response = new LoginResponse(token, 7200);

        assertThat(response.getAccessToken()).isEqualTo(token);
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(7200);
        assertThat(response.toString())
                .contains("accessToken='<redacted>'")
                .doesNotContain(token);
    }

    @Test
    void responseRejectsInvalidConstruction() {
        org.assertj.core.api.Assertions
                .assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoginResponse(" ", 7200))
                .withMessage("accessToken must not be blank");
        org.assertj.core.api.Assertions
                .assertThatIllegalArgumentException()
                .isThrownBy(() -> new LoginResponse("token", 0))
                .withMessage("expiresInSeconds must be positive");
    }

    @Test
    void mvcContractSerializesSuccessfulResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " Admin ",
                                  "password": "Case-Sensitive Secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("test-only-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(7200));
    }

    @Test
    void mvcContractRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "password": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"))
                .andExpect(jsonPath("$.fieldErrors[1].field")
                        .value("username"));
    }

    @Test
    void mvcContractRejectsPasswordOverSeventyTwoBytes()
            throws Exception {
        String oversizedPassword = "a".repeat(73);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "%s"
                                }
                                """.formatted(oversizedPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"));
    }

    @Test
    void mvcContractRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"));
    }

    @RestController
    @RequestMapping("/api/auth")
    private static class LoginContractController {

        @PostMapping("/login")
        LoginResponse login(@Valid @RequestBody LoginRequest request) {
            return new LoginResponse("test-only-token", 7200);
        }
    }
}
