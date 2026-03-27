package com.procurehub.auth.service;

import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.dto.LoginRequest;
import com.procurehub.auth.dto.RefreshTokenRequest;
import com.procurehub.auth.dto.RegisterRequest;
import com.procurehub.auth.event.UserCreatedEventPublisher;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.UserRepository;
import com.procurehub.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceCoreCoverageTest {

    @Mock
    private UserRepository userRepository;

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                jwtService,
                refreshTokenService,
                emailVerificationService,
                passwordEncoder,
                userCreatedEventPublisher
        );
    }

    @Test
    void registerShouldPersistUserCreateVerificationAndPublishEvent() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        AuthResponse response = authService.register(request);

        assertEquals("User registered. Please confirm email.", response.getMessage());
        assertEquals("new@example.com", response.getEmail());
        verify(emailVerificationService).createAndLogToken(any(User.class));
        verify(userCreatedEventPublisher).publish(any(User.class));
    }

    @Test
    void registerShouldRejectDuplicateEmailAndDataIntegrityViolations() {
        RegisterRequest request = new RegisterRequest("dup@example.com", "password123");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));

        when(userRepository.existsByEmail("dup2@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        RegisterRequest duplicateOnSave = new RegisterRequest("dup2@example.com", "password123");
        assertThrows(IllegalArgumentException.class, () -> authService.register(duplicateOnSave));
    }

    @Test
    void loginShouldHandleHappyPathAndValidationBranches() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> authService.login(new LoginRequest("missing@example.com", "password123")));

        User unverified = new User("user@example.com", "hash", "ROLE_USER", true, false, LocalDateTime.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(unverified));
        assertThrows(IllegalArgumentException.class, () -> authService.login(request));

        User disabled = new User("user@example.com", "hash", "ROLE_USER", false, true, LocalDateTime.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(disabled));
        assertThrows(IllegalArgumentException.class, () -> authService.login(request));

        User valid = new User("user@example.com", "hash", "ROLE_USER", true, true, LocalDateTime.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(valid));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(jwtService.generateToken(valid)).thenReturn("access-token");
        when(refreshTokenService.issueToken(valid)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);
        assertEquals("Login successful", response.getMessage());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }

    @Test
    void refreshChangeRoleAndConfirmEmailShouldDelegateCorrectly() {
        User user = new User("user@example.com", "hash", "ROLE_USER", true, true, LocalDateTime.now());

        when(refreshTokenService.validateAndRotate("refresh-token")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(refreshTokenService.issueToken(user)).thenReturn("new-refresh-token");

        AuthResponse refreshed = authService.refresh(new RefreshTokenRequest("refresh-token"));
        assertEquals("Token refreshed successfully", refreshed.getMessage());
        assertEquals("access-token", refreshed.getAccessToken());
        assertEquals("new-refresh-token", refreshed.getRefreshToken());

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        authService.changeUserRole("admin@example.com", "ROLE_ADMIN");
        assertEquals("ROLE_ADMIN", user.getRole());
        verify(userRepository).save(user);

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> authService.changeUserRole("missing@example.com", "ROLE_ADMIN"));

        authService.confirmEmail("token");
        verify(emailVerificationService).confirm("token");
    }
}
