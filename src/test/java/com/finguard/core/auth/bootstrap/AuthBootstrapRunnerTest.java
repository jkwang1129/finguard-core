package com.finguard.core.auth.bootstrap;

import com.finguard.core.auth.mapper.RoleMapper;
import com.finguard.core.auth.mapper.UserMapper;
import com.finguard.core.auth.mapper.UserRoleMapper;
import com.finguard.core.auth.model.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthBootstrapRunnerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final UserRoleMapper userRoleMapper =
            mock(UserRoleMapper.class);
    private final PasswordEncoder passwordEncoder =
            mock(PasswordEncoder.class);

    @Test
    void defaultsToDisabledAndTouchesNoAuthenticationData() {
        AuthBootstrapProperties properties =
                new AuthBootstrapProperties();
        AuthBootstrapRunner runner = runner(properties);

        runner.run(null);

        assertThat(properties.isEnabled()).isFalse();
        verifyNoPersistenceInteractions();
    }

    @Test
    void missingCredentialFailsBeforeAnyPersistenceWithoutLeakingValues() {
        AuthBootstrapProperties properties =
                enabledProperties();
        properties.getAdmin().setUsername("Secret-Admin-Name");
        properties.getAdmin().setPassword(
                "Secret-Admin-Password"
        );

        assertThatThrownBy(() -> runner(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "REVIEWER bootstrap username and password "
                                + "must be configured"
                )
                .hasMessageNotContaining("Secret-Admin-Name")
                .hasMessageNotContaining("Secret-Admin-Password");
        verifyNoPersistenceInteractions();
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        AuthBootstrapProperties properties =
                completeProperties();
        properties.getAdmin().setPassword("Short-12345");

        assertThatThrownBy(() -> runner(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "ADMIN bootstrap password must contain "
                                + "at least 12 characters"
                )
                .hasMessageNotContaining("Short-12345");
        verifyNoPersistenceInteractions();
    }

    @Test
    void acceptsSeventyTwoUtf8BytesButRejectsSeventyFive() {
        AuthBootstrapProperties validProperties =
                completeProperties();
        validProperties.getAdmin().setPassword("密".repeat(24));
        when(roleMapper.findIdByRoleCode(RoleCode.ADMIN))
                .thenReturn(null);

        assertThatThrownBy(() -> runner(validProperties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Required bootstrap role is unavailable"
                );

        AuthBootstrapProperties invalidProperties =
                completeProperties();
        invalidProperties.getAdmin().setPassword("密".repeat(25));
        assertThatThrownBy(() -> runner(invalidProperties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "ADMIN bootstrap password must not exceed "
                                + "72 UTF-8 bytes"
                );
    }

    @Test
    void bootstrapExecutionDeclaresOneTransactionBoundary()
            throws NoSuchMethodException {
        Method runMethod = AuthBootstrapRunner.class.getMethod(
                "run",
                org.springframework.boot.ApplicationArguments.class
        );

        assertThat(runMethod.isAnnotationPresent(
                Transactional.class
        )).isTrue();
    }

    private AuthBootstrapRunner runner(
            AuthBootstrapProperties properties) {
        return new AuthBootstrapRunner(
                properties,
                userMapper,
                roleMapper,
                userRoleMapper,
                passwordEncoder
        );
    }

    private AuthBootstrapProperties enabledProperties() {
        AuthBootstrapProperties properties =
                new AuthBootstrapProperties();
        properties.setEnabled(true);
        return properties;
    }

    private AuthBootstrapProperties completeProperties() {
        AuthBootstrapProperties properties = enabledProperties();
        properties.getAdmin().setUsername("Bootstrap-Admin");
        properties.getAdmin().setPassword(
                "Admin-Test-Password"
        );
        properties.getReviewer().setUsername(
                "Bootstrap-Reviewer"
        );
        properties.getReviewer().setPassword(
                "Reviewer-Test-Password"
        );
        return properties;
    }

    private void verifyNoPersistenceInteractions() {
        verifyNoInteractions(
                userMapper,
                roleMapper,
                userRoleMapper,
                passwordEncoder
        );
    }
}
