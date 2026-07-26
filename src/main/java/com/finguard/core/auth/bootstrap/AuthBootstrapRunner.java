package com.finguard.core.auth.bootstrap;

import com.finguard.core.auth.entity.User;
import com.finguard.core.auth.mapper.RoleMapper;
import com.finguard.core.auth.mapper.UserMapper;
import com.finguard.core.auth.mapper.UserRoleMapper;
import com.finguard.core.auth.model.AuthUserRecord;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.validation.AuthInputNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthBootstrapRunner.class);
    private static final int MINIMUM_USERNAME_LENGTH = 3;
    private static final int MAXIMUM_USERNAME_LENGTH = 64;
    private static final int MINIMUM_PASSWORD_CHARACTERS = 12;
    private static final int MAXIMUM_PASSWORD_BYTES = 72;

    private final AuthBootstrapProperties properties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthBootstrapRunner(
            AuthBootstrapProperties properties,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!properties.isEnabled()) {
            return;
        }

        BootstrapCredential admin = validateCredential(
                "ADMIN",
                properties.getAdmin(),
                RoleCode.ADMIN
        );
        BootstrapCredential reviewer = validateCredential(
                "REVIEWER",
                properties.getReviewer(),
                RoleCode.REVIEWER
        );
        if (admin.username().equals(reviewer.username())) {
            throw new IllegalStateException(
                    "ADMIN and REVIEWER bootstrap usernames "
                            + "must be different"
            );
        }

        ensureUserAndRole(admin);
        ensureUserAndRole(reviewer);
        LOGGER.info(
                "Local authentication bootstrap completed for 2 roles"
        );
    }

    private BootstrapCredential validateCredential(
            String label,
            AuthBootstrapProperties.Credential credential,
            RoleCode role) {
        if (credential == null
                || !StringUtils.hasText(credential.getUsername())
                || !StringUtils.hasText(credential.getPassword())) {
            throw new IllegalStateException(
                    label + " bootstrap username and password "
                            + "must be configured"
            );
        }

        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(
                        credential.getUsername()
                );
        if (normalizedUsername.length() < MINIMUM_USERNAME_LENGTH
                || normalizedUsername.length()
                > MAXIMUM_USERNAME_LENGTH) {
            throw new IllegalStateException(
                    label + " bootstrap username length must be "
                            + "between 3 and 64 after normalization"
            );
        }

        String password = credential.getPassword();
        int characterCount = password.codePointCount(
                0,
                password.length()
        );
        if (characterCount < MINIMUM_PASSWORD_CHARACTERS) {
            throw new IllegalStateException(
                    label + " bootstrap password must contain "
                            + "at least 12 characters"
            );
        }
        int passwordBytes = password
                .getBytes(StandardCharsets.UTF_8)
                .length;
        if (passwordBytes > MAXIMUM_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    label + " bootstrap password must not exceed "
                            + "72 UTF-8 bytes"
            );
        }

        return new BootstrapCredential(
                normalizedUsername,
                password,
                role
        );
    }

    private void ensureUserAndRole(
            BootstrapCredential credential) {
        Long roleId = roleMapper.findIdByRoleCode(credential.role());
        if (roleId == null) {
            throw new IllegalStateException(
                    "Required bootstrap role is unavailable: "
                            + credential.role().name()
            );
        }

        AuthUserRecord existing = userMapper
                .findAuthUserByNormalizedUsername(
                        credential.username()
                );
        if (existing != null) {
            List<RoleCode> existingRoles = existing.getRoles();
            if (existingRoles == null
                    || !existingRoles.contains(credential.role())) {
                insertRoleBinding(existing.getId(), roleId);
            }
            return;
        }

        User user = new User();
        user.setUsername(credential.username());
        user.setPasswordHash(
                passwordEncoder.encode(credential.password())
        );
        user.setStatus(UserStatus.ACTIVE);
        int insertedUsers = userMapper.insert(user);
        if (insertedUsers != 1 || user.getId() == null) {
            throw new IllegalStateException(
                    "Bootstrap user could not be created"
            );
        }
        insertRoleBinding(user.getId(), roleId);
    }

    private void insertRoleBinding(Long userId, Long roleId) {
        int insertedBindings =
                userRoleMapper.insertBinding(userId, roleId);
        if (insertedBindings != 1) {
            throw new IllegalStateException(
                    "Bootstrap role binding could not be created"
            );
        }
    }

    private record BootstrapCredential(
            String username,
            String password,
            RoleCode role
    ) {
    }
}
