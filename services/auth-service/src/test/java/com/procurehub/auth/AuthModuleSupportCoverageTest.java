package com.procurehub.auth;

import com.procurehub.auth.api.AuthController;
import com.procurehub.auth.api.ErrorResponse;
import com.procurehub.auth.api.GlobalExceptionHandler;
import com.procurehub.auth.api.HealthController;
import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.dto.LoginRequest;
import com.procurehub.auth.dto.RefreshTokenRequest;
import com.procurehub.auth.dto.RegisterRequest;
import com.procurehub.auth.dto.RoleUpdateRequest;
import com.procurehub.auth.event.UserCreatedEventPublisher;
import com.procurehub.auth.model.EmailVerificationToken;
import com.procurehub.auth.model.RefreshToken;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.EmailVerificationTokenRepository;
import com.procurehub.auth.repository.RefreshTokenRepository;
import com.procurehub.auth.repository.UserRepository;
import com.procurehub.auth.security.AppUserDetailsService;
import com.procurehub.auth.security.JwtService;
import com.procurehub.auth.service.AuthService;
import com.procurehub.auth.service.EmailVerificationService;
import com.procurehub.auth.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthModuleSupportCoverageTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private KafkaTemplate<String, com.procurehub.auth.avro.UserCreatedEvent> kafkaTemplate;

    @Test
    void dtoModelAndHealthTypesShouldWork() {
        AuthResponse authResponse = new AuthResponse("ok", "user@example.com", "access", "refresh");
        assertEquals("ok", authResponse.getMessage());

        LoginRequest loginRequest = new LoginRequest("user@example.com", "password");
        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest("refresh");
        RegisterRequest registerRequest = new RegisterRequest("new@example.com", "password123");
        RoleUpdateRequest roleUpdateRequest = new RoleUpdateRequest("admin@example.com", "ROLE_ADMIN");
        assertEquals("user@example.com", loginRequest.getEmail());
        assertEquals("refresh", refreshTokenRequest.getRefreshToken());
        assertEquals("new@example.com", registerRequest.getEmail());
        assertEquals("ROLE_ADMIN", roleUpdateRequest.getRole());

        LocalDateTime now = LocalDateTime.now();
        User user = new User("user@example.com", "hash", "ROLE_USER,ROLE_ADMIN", true, true, now);
        ReflectionTestUtils.setField(user, "id", 11L);
        assertEquals(11L, user.getId());
        assertEquals(2, user.getAuthorities().size());
        assertEquals(true, user.isEnabled());

        User disabledUser = new User("disabled@example.com", "hash", "ROLE_USER", true, false, now);
        assertFalse(disabledUser.isEnabled());

        RefreshToken refreshToken = new RefreshToken("token", user, now.plusDays(1), false, now);
        refreshToken.setRevoked(true);
        assertEquals(true, refreshToken.isRevoked());

        EmailVerificationToken verificationToken = new EmailVerificationToken("token", user, now.plusMinutes(5), false, now);
        verificationToken.setConsumed(true);
        assertEquals(true, verificationToken.isConsumed());

        ErrorResponse error = new ErrorResponse("bad", 400, now);
        assertEquals("bad", error.getMessage());
        assertEquals("ok", new HealthController().health().get("status"));
    }

    @Test
    void controllerShouldDelegateAndExposeAuthHelpers() {
        AuthController controller = new AuthController(authService);
        RegisterRequest registerRequest = new RegisterRequest("new@example.com", "password123");
        LoginRequest loginRequest = new LoginRequest("user@example.com", "password123");
        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest("refresh");
        RoleUpdateRequest roleUpdateRequest = new RoleUpdateRequest("admin@example.com", "ROLE_ADMIN");
        AuthResponse authResponse = new AuthResponse("ok", "user@example.com", "access", "refresh");

        when(authService.register(registerRequest)).thenReturn(authResponse);
        when(authService.login(loginRequest)).thenReturn(authResponse);
        when(authService.refresh(refreshTokenRequest)).thenReturn(authResponse);

        assertEquals(authResponse, controller.register(registerRequest));
        assertEquals(authResponse, controller.login(loginRequest));
        assertEquals(authResponse, controller.refresh(refreshTokenRequest));

        Map<String, Object> me = controller.me(new TestingAuthenticationToken("user@example.com", "n/a", "ROLE_USER", "ROLE_ADMIN"));
        assertEquals("user@example.com", me.get("email"));

        assertEquals("admin-ok", controller.adminPing().get("status"));
        assertEquals("role-updated", controller.changeRole(roleUpdateRequest).get("status"));
        assertEquals("email-confirmed", controller.confirm("token").get("status"));

        verify(authService).changeUserRole("admin@example.com", "ROLE_ADMIN");
        verify(authService).confirmEmail("token");
    }

    @Test
    void jwtAndUserDetailsServicesShouldWork() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(
                jwtService,
                "jwtSecret",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))
        );
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", 60000L);

        User user = new User("jwt@example.com", "hash", "ROLE_USER", true, true, LocalDateTime.now());
        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals("jwt@example.com", jwtService.extractUsername(token));
        assertEquals(true, jwtService.isTokenValid(token, user));

        AppUserDetailsService detailsService = new AppUserDetailsService(userRepository);
        when(userRepository.findByEmail("jwt@example.com")).thenReturn(Optional.of(user));
        assertEquals("jwt@example.com", detailsService.loadUserByUsername("jwt@example.com").getUsername());

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> detailsService.loadUserByUsername("missing@example.com"));
    }

    @Test
    void refreshTokenEmailVerificationAndPublisherShouldWork() {
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationDays", 7L);

        User user = new User("refresh@example.com", "hash", "ROLE_USER", true, true, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 21L);

        when(userRepository.getReferenceById(21L)).thenReturn(user);
        when(refreshTokenRepository.findAllByUser_IdAndRevokedFalse(21L)).thenReturn(List.of());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String issued = refreshTokenService.issueToken(user);
        assertNotNull(issued);

        RefreshToken storedToken = new RefreshToken(issued, user, LocalDateTime.now().plusDays(1), false, LocalDateTime.now());
        when(refreshTokenRepository.findByToken(issued)).thenReturn(Optional.of(storedToken));
        assertEquals(user, refreshTokenService.validateAndRotate(issued));

        RefreshToken expiredToken = new RefreshToken("expired", user, LocalDateTime.now().minusDays(1), false, LocalDateTime.now());
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(expiredToken));
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.validateAndRotate("expired"));

        EmailVerificationService emailVerificationService = new EmailVerificationService(emailVerificationTokenRepository);
        ReflectionTestUtils.setField(emailVerificationService, "expirationMinutes", 30L);
        ReflectionTestUtils.setField(emailVerificationService, "baseUrl", "http://localhost:8081");

        EmailVerificationToken activeToken = new EmailVerificationToken("active", user, LocalDateTime.now().plusMinutes(10), false, LocalDateTime.now());
        when(emailVerificationTokenRepository.findAllByUser_IdAndConsumedFalse(21L)).thenReturn(List.of(activeToken));
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.createAndLogToken(user);
        assertEquals(true, activeToken.isConsumed());

        EmailVerificationToken confirmableToken = new EmailVerificationToken("confirm", user, LocalDateTime.now().plusMinutes(10), false, LocalDateTime.now());
        when(emailVerificationTokenRepository.findByToken("confirm")).thenReturn(Optional.of(confirmableToken));
        emailVerificationService.confirm("confirm");
        assertEquals(true, confirmableToken.isConsumed());
        assertEquals(true, user.isEmailVerified());

        UserCreatedEventPublisher publisher = new UserCreatedEventPublisher(kafkaTemplate, "user.created");
        when(kafkaTemplate.send(eq("user.created"), eq("21"), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish(user);
        verify(kafkaTemplate).send(eq("user.created"), eq("21"), any());
    }

    @Test
    void globalExceptionHandlerShouldMapExpectedStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertEquals(401, handler.handleBadCredentialsException(new BadCredentialsException("bad")).getStatus());
        assertEquals(401, handler.handleAuthenticationException(new AuthenticationException("bad") { }).getStatus());
        assertEquals(403, handler.handleAccessDeniedException(new AccessDeniedException("forbidden")).getStatus());
        assertEquals(409, handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("dup")).getStatus());
        assertEquals(400, handler.handleIllegalArgumentException(new IllegalArgumentException("bad")).getStatus());
        assertEquals(500, handler.handleGenericException(new RuntimeException("boom")).getStatus());
    }
}
