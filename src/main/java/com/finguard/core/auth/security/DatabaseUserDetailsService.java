package com.finguard.core.auth.security;

import com.finguard.core.auth.mapper.UserMapper;
import com.finguard.core.auth.model.AuthUserRecord;
import com.finguard.core.auth.validation.AuthInputNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public DatabaseUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(username);
        AuthUserRecord user = userMapper
                .findAuthUserByNormalizedUsername(normalizedUsername);
        if (user == null) {
            throw new UsernameNotFoundException(
                    "Authentication user was not found"
            );
        }

        return new AuthPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getRoles() == null ? List.of() : user.getRoles()
        );
    }
}
