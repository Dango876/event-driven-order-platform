package com.procurehub.auth.service;

import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.event.UserCreatedEventPublisher;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.UserRepository;
import com.procurehub.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceOAuth2LoginTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserCreatedEventPublisher userCreatedEventPublisher;

    @InjectMocks
    private AuthService authService;

    @Test
    void oauth2LoginShouldCreateVerifiedUserWhenMissing() {
        String email = "new.user@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.issueToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.oauth2Login(email);

        assertEquals("OAuth2 login successful", response.getMessage());
        assertEquals(email, response.getEmail());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(userRepository).save(any(User.class));
        verify(userRepository).findByEmail(email);
        verify(userCreatedEventPublisher).publish(any(User.class));
    }

    @Test
    void oauth2LoginShouldVerifyExistingUserIfEmailNotVerified() {
        String email = "existing.user@example.com";
        User existing = new User(email, "hash", "ROLE_USER", true, false, LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.issueToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.oauth2Login(email);

        assertEquals(email, response.getEmail());
        assertTrue(existing.isEmailVerified());
        verify(userRepository).save(eq(existing));
        verify(userCreatedEventPublisher, never()).publish(any(User.class));
    }

    @Test
    void oauth2LoginShouldNotSaveExistingAlreadyVerifiedUser() {
        String email = "verified.user@example.com";
        User existing = new User(email, "hash", "ROLE_USER", true, true, LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.issueToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.oauth2Login(email);

        assertEquals(email, response.getEmail());
        assertTrue(existing.isEmailVerified());
        assertFalse(response.getAccessToken().isBlank());
        assertNotNull(response.getRefreshToken());
        verify(userRepository, never()).save(any(User.class));
        verify(userCreatedEventPublisher, never()).publish(any(User.class));
    }
}
