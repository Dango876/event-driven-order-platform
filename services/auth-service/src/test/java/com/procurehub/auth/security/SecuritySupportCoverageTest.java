package com.procurehub.auth.security;

import com.procurehub.auth.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.jsonwebtoken.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecuritySupportCoverageTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private OAuth2AuthenticationSuccessHandler successHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void securityConfigShouldExposeProviderEncoderAndAuthenticationManager() throws Exception {
        JwtFilter jwtFilter = new JwtFilter(jwtService, userDetailsService);
        SecurityConfig securityConfig = new SecurityConfig(jwtFilter, userDetailsService, successHandler);

        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();
        String encoded = passwordEncoder.encode("secret");
        assertTrue(passwordEncoder.matches("secret", encoded));

        assertNotNull(securityConfig.authenticationProvider());

        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(configuration.getAuthenticationManager()).thenReturn(authenticationManager);
        assertEquals(authenticationManager, securityConfig.authenticationManager(configuration));
    }

    @Test
    void jwtFilterShouldAuthenticateValidBearerTokens() throws Exception {
        JwtFilter filter = new JwtFilter(jwtService, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        org.springframework.security.core.userdetails.User user =
                new org.springframework.security.core.userdetails.User("user@example.com", "password", java.util.List.of());

        when(jwtService.extractUsername("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void jwtFilterShouldIgnoreMissingHeaderAndClearContextOnJwtErrors() throws Exception {
        JwtFilter filter = new JwtFilter(jwtService, userDetailsService);

        MockHttpServletRequest noHeaderRequest = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(noHeaderRequest, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale", null)
        );

        MockHttpServletRequest badTokenRequest = new MockHttpServletRequest();
        badTokenRequest.addHeader("Authorization", "Bearer broken-token");
        when(jwtService.extractUsername("broken-token")).thenThrow(new JwtException("bad token") {
        });

        filter.doFilter(badTokenRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
