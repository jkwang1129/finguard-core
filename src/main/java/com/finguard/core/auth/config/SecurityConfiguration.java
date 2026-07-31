package com.finguard.core.auth.config;

import com.finguard.core.auth.model.RoleCode;
import com.finguard.core.auth.security.JwtRoleConverter;
import com.finguard.core.auth.security.RestAccessDeniedHandler;
import com.finguard.core.auth.security.RestAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtRoleConverter jwtRoleConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts",
                                "/api/accounts/*",
                                "/api/transactions",
                                "/api/transactions/*",
                                "/api/import-jobs/*",
                                "/api/import-jobs/*/errors"
                        ).hasAnyRole(
                                RoleCode.ADMIN.name(),
                                RoleCode.REVIEWER.name()
                        )
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts",
                                "/api/transactions",
                                "/api/import-jobs"
                        ).hasRole(RoleCode.ADMIN.name())
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/accounts/*/name",
                                "/api/accounts/*/status"
                        ).hasRole(RoleCode.ADMIN.name())
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/transactions/*"
                        ).hasRole(RoleCode.ADMIN.name())
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/accounts/*",
                                "/api/transactions/*"
                        ).hasRole(RoleCode.ADMIN.name())
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtRoleConverter
                                )
                        )
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults());
        return http.build();
    }
}
