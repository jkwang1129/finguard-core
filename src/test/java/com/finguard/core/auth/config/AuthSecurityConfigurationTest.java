package com.finguard.core.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSecurityConfigurationTest {

    private static final String TEST_SECRET_TEXT =
            "finguard-day3-test-only-secret-key";
    private static final String TEST_SECRET_BASE64 = Base64
            .getEncoder()
            .encodeToString(TEST_SECRET_TEXT.getBytes(StandardCharsets.UTF_8));

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            PasswordConfiguration.class,
                            JwtConfiguration.class
                    )
                    .withPropertyValues(
                            "finguard.auth.jwt.secret-base64="
                                    + TEST_SECRET_BASE64,
                            "finguard.auth.jwt.issuer=finguard-core",
                            "finguard.auth.jwt.expires-in-seconds=7200"
                    );

    @Test
    void providesBcryptJwtSecretAndUtcClock() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).hasSingleBean(SecretKey.class);
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(JwtProperties.class);
            assertThat(context).hasSingleBean(JwtEncoder.class);

            PasswordEncoder passwordEncoder =
                    context.getBean(PasswordEncoder.class);
            String passwordHash = passwordEncoder.encode("local-password");
            assertThat(passwordHash).isNotEqualTo("local-password");
            assertThat(passwordEncoder.matches(
                    "local-password",
                    passwordHash
            )).isTrue();
            assertThat(passwordEncoder.matches(
                    "wrong-password",
                    passwordHash
            )).isFalse();

            SecretKey secretKey = context.getBean(SecretKey.class);
            assertThat(secretKey.getAlgorithm()).isEqualTo("HmacSHA256");
            assertThat(secretKey.getEncoded())
                    .containsExactly(TEST_SECRET_TEXT.getBytes(
                            StandardCharsets.UTF_8
                    ));

            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);

            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.getIssuer()).isEqualTo("finguard-core");
            assertThat(properties.getExpiresInSeconds()).isEqualTo(7200);
        });
    }

    @Test
    void rejectsMissingSecretWithoutLeakingSecretMaterial() {
        runInvalidSecretTest(
                "",
                "JWT_SECRET_BASE64 must be configured"
        );
    }

    @Test
    void rejectsMalformedBase64WithoutLeakingSecretMaterial() {
        runInvalidSecretTest(
                "not-valid-base64!",
                "JWT_SECRET_BASE64 must be valid Base64"
        );
    }

    @Test
    void rejectsSecretShorterThanThirtyTwoBytes() {
        String shortSecret = Base64
                .getEncoder()
                .encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        runInvalidSecretTest(
                shortSecret,
                "JWT_SECRET_BASE64 must decode to at least 32 bytes"
        );
    }

    private void runInvalidSecretTest(
            String encodedSecret,
            String expectedMessage) {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtConfiguration.class)
                .withPropertyValues(
                        "finguard.auth.jwt.secret-base64=" + encodedSecret,
                        "finguard.auth.jwt.issuer=finguard-core",
                        "finguard.auth.jwt.expires-in-seconds=7200"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(expectedMessage);
                    if (!encodedSecret.isEmpty()) {
                        assertThat(context.getStartupFailure().toString())
                                .doesNotContain(encodedSecret);
                    }
                });
    }
}
