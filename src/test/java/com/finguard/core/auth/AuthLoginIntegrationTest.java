package com.finguard.core.auth;

import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.support.AuthTestFixture;
import com.finguard.core.auth.vo.LoginResponse;
import com.finguard.core.common.exception.ApiErrorResponse;
import com.finguard.core.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AuthLoginIntegrationTest {

    private static final String CORRECT_PASSWORD =
            "Task4-Correct-Password";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SecretKey jwtSecretKey;

    private AuthTestFixture fixture;
    private String activeUsername;
    private String disabledUsername;

    @BeforeEach
    void setUp() {
        fixture = new AuthTestFixture(new JdbcTemplate(dataSource));
        fixture.clean();

        activeUsername = fixture.username("login-active");
        Long activeUserId = fixture.insertUser(
                activeUsername,
                UserStatus.ACTIVE,
                passwordEncoder.encode(CORRECT_PASSWORD)
        );
        fixture.bindRole(activeUserId, fixture.roleId("ADMIN"));

        disabledUsername = fixture.username("login-disabled");
        Long disabledUserId = fixture.insertUser(
                disabledUsername,
                UserStatus.DISABLED,
                passwordEncoder.encode(CORRECT_PASSWORD)
        );
        fixture.bindRole(
                disabledUserId,
                fixture.roleId("REVIEWER")
        );
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void realHttpAndMysqlLoginUsesNormalizedUsernameAndBcrypt() {
        ResponseEntity<LoginResponse> response =
                postLogin(
                        "  " + activeUsername.toUpperCase() + "  ",
                        CORRECT_PASSWORD,
                        LoginResponse.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTokenType())
                .isEqualTo("Bearer");
        assertThat(response.getBody().getExpiresInSeconds())
                .isEqualTo(7200);

        Jwt jwt = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build()
                .decode(response.getBody().getAccessToken());
        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo("finguard-core");
        assertThat(jwt.getSubject())
                .isEqualTo(fixture.userId(activeUsername).toString());
        assertThat(jwt.getClaimAsString("username"))
                .isEqualTo(activeUsername);
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("ADMIN");
    }

    @Test
    void wrongPasswordReturnsUnifiedUnauthorizedError() {
        assertUnifiedUnauthorized(
                activeUsername,
                "Wrong-Password"
        );
    }

    @Test
    void missingUserReturnsUnifiedUnauthorizedError() {
        assertUnifiedUnauthorized(
                fixture.username("missing"),
                CORRECT_PASSWORD
        );
    }

    @Test
    void disabledUserReturnsUnifiedUnauthorizedError() {
        assertUnifiedUnauthorized(
                disabledUsername,
                CORRECT_PASSWORD
        );
    }

    private void assertUnifiedUnauthorized(
            String username,
            String password) {
        ResponseEntity<ApiErrorResponse> response =
                postLogin(
                        username,
                        password,
                        ApiErrorResponse.class
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().code())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(response.getBody().message())
                .isEqualTo("Invalid username or password");
        assertThat(response.getBody().path())
                .isEqualTo("/api/auth/login");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    private <T> ResponseEntity<T> postLogin(
            String username,
            String password,
            Class<T> responseType) {
        return restTemplate.postForEntity(
                "/api/auth/login",
                new LoginPayload(username, password),
                responseType
        );
    }

    private record LoginPayload(String username, String password) {
    }
}
