package com.finguard.core.auth.mapper;

import com.finguard.core.auth.entity.User;
import com.finguard.core.auth.model.AuthUserRecord;
import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.model.UserStatus;
import com.finguard.core.auth.support.AuthTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthMapperIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    private AuthTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AuthTestFixture(new JdbcTemplate(dataSource));
        fixture.clean();
    }

    @AfterEach
    void tearDown() {
        fixture.clean();
    }

    @Test
    void shouldInsertUserAndLoadAuthenticationRecordWithAllRoles() {
        User user = new User();
        user.setUsername(fixture.username("mapper-admin"));
        user.setPasswordHash(AuthTestFixture.TEST_PASSWORD_HASH);
        user.setStatus(UserStatus.ACTIVE);

        assertThat(userMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        Long adminRoleId = fixture.roleId(RoleCode.ADMIN.name());
        Long reviewerRoleId = fixture.roleId(RoleCode.REVIEWER.name());
        assertThat(userRoleMapper.insertBinding(user.getId(), adminRoleId)).isEqualTo(1);
        assertThat(userRoleMapper.insertBinding(user.getId(), reviewerRoleId)).isEqualTo(1);

        AuthUserRecord record =
                userMapper.findAuthUserByNormalizedUsername(user.getUsername());

        assertThat(record).isNotNull();
        assertThat(record.getId()).isEqualTo(user.getId());
        assertThat(record.getUsername()).isEqualTo(user.getUsername());
        assertThat(record.getPasswordHash()).isEqualTo(AuthTestFixture.TEST_PASSWORD_HASH);
        assertThat(record.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(record.getRoles()).containsExactly(RoleCode.ADMIN, RoleCode.REVIEWER);
    }

    @Test
    void shouldLoadDisabledUserWithNoRoles() {
        Long userId = fixture.insertUser(
                fixture.username("mapper-disabled"),
                UserStatus.DISABLED
        );

        AuthUserRecord record = userMapper.findAuthUserByNormalizedUsername(
                fixture.username("mapper-disabled")
        );

        assertThat(record).isNotNull();
        assertThat(record.getId()).isEqualTo(userId);
        assertThat(record.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(record.getRoles()).isEmpty();
        assertThat(roleMapper.findRoleCodesByUserId(userId)).isEmpty();
    }

    @Test
    void shouldReturnNullWhenNormalizedUsernameDoesNotExist() {
        AuthUserRecord record = userMapper.findAuthUserByNormalizedUsername(
                fixture.username("missing")
        );

        assertThat(record).isNull();
    }
}
