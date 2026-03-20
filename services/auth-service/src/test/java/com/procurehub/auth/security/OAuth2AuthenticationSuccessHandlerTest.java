package com.procurehub.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private AuthService authService;

    private OAuth2AuthenticationSuccessHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new OAuth2AuthenticationSuccessHandler(authService, objectMapper);
    }

    @Test
    void shouldAuthenticateGoogleUserByEmail() throws Exception {
        String email = "google.user@example.com";
        OAuth2AuthenticationToken authentication = oauthToken(
                "google",
                Map.of("email", email, "sub", "google-subject-id", "id", "google-user-id")
        );

        when(authService.oauth2Login(email))
                .thenReturn(new AuthResponse("OAuth2 login successful", email, "access-token", "refresh-token"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertEquals(200, response.getStatus());
        Map<?, ?> payload = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(email, payload.get("email"));
        assertEquals("access-token", payload.get("accessToken"));
        verify(authService).oauth2Login(email);
    }

    @Test
    void shouldFallbackToGithubNoreplyEmailWhenEmailMissing() throws Exception {
        String login = "octocat";
        String resolvedEmail = "octocat@users.noreply.github.com";
        OAuth2AuthenticationToken authentication = oauthToken(
                "github",
                Map.of("login", login, "id", 42)
        );

        when(authService.oauth2Login(resolvedEmail))
                .thenReturn(new AuthResponse("OAuth2 login successful", resolvedEmail, "access-token", "refresh-token"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertEquals(200, response.getStatus());
        Map<?, ?> payload = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(resolvedEmail, payload.get("email"));
        verify(authService).oauth2Login(resolvedEmail);
    }

    @Test
    void shouldReturnBadRequestWhenProviderEmailCannotBeResolved() throws Exception {
        OAuth2AuthenticationToken authentication = oauthToken(
                "github",
                Map.of("id", 1000)
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertEquals(400, response.getStatus());
        Map<?, ?> payload = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertTrue(((String) payload.get("message")).contains("Email is not available"));
        verify(authService, never()).oauth2Login(org.mockito.ArgumentMatchers.anyString());
    }

    private OAuth2AuthenticationToken oauthToken(String registrationId, Map<String, Object> attributes) {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "id"
        );
        return new OAuth2AuthenticationToken(oauth2User, oauth2User.getAuthorities(), registrationId);
    }
}
