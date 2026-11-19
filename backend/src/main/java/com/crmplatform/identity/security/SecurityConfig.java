package com.crmplatform.identity.security;

import com.crmplatform.identity.application.IdentityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Stateless JWT security (ADR-0003/0005). Deny by default; only the auth flows are public. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/auth/invitations/accept",
            "/api/v1/admin/auth/login"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtToUserConverter converter, ProblemWriter problems,
            @Value("${springdoc.api-docs.enabled:true}") boolean docsEnabled) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // bearer tokens; cookie endpoints verified by CookieRequestGuard
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/invitations/*").permitAll();
                    auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    auth.requestMatchers("/ws/**").permitAll(); // authenticated at STOMP CONNECT
                    if (docsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            String message = ex.getMessage() == null ? "" : ex.getMessage();
                            boolean expired = message.contains("expired");
                            problems.write(req, res, HttpStatus.UNAUTHORIZED, expired ? "TOKEN_EXPIRED" : "UNAUTHENTICATED",
                                    expired ? "Your session has expired" : "Authentication is required");
                        })
                        .accessDeniedHandler((req, res, ex) -> problems.write(req, res, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "You do not have permission to perform this action")))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> problems.write(req, res,
                        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required")))
                .addFilterAfter(new TenantContextFilter(problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    SecretKey jwtSigningKey(IdentityProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, IdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location", "ETag", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
