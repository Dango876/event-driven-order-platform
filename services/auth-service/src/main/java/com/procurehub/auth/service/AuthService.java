package com.procurehub.auth.service;

import com.procurehub.auth.dto.AuthResponse;
import com.procurehub.auth.dto.LoginRequest;
import com.procurehub.auth.dto.RefreshTokenRequest;
import com.procurehub.auth.dto.RegisterRequest;
import com.procurehub.auth.event.UserCreatedEventPublisher;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.UserRepository;
import com.procurehub.auth.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final UserCreatedEventPublisher userCreatedEventPublisher;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationService emailVerificationService,
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                       UserCreatedEventPublisher userCreatedEventPublisher) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.userCreatedEventPublisher = userCreatedEventPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                "ROLE_USER",
                true,
                false,
                LocalDateTime.now()
        );

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        emailVerificationService.createAndLogToken(user);
        userCreatedEventPublisher.publish(user);

        return new AuthResponse("User registered. Please confirm email.", user.getEmail(), null, null);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is not confirmed");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issueToken(user);
        return new AuthResponse("Login successful", user.getEmail(), accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse oauth2Login(String email) {
        boolean created = false;
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            User newUser = new User(
                    email,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    "ROLE_USER",
                    true,
                    true,
                    LocalDateTime.now()
            );
            user = userRepository.save(newUser);
            created = true;
        }

        if (created) {
            userCreatedEventPublisher.publish(user);
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issueToken(user);
        return new AuthResponse("OAuth2 login successful", user.getEmail(), accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        User user = refreshTokenService.validateAndRotate(request.getRefreshToken());

        String accessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.issueToken(user);

        return new AuthResponse("Token refreshed successfully", user.getEmail(), accessToken, newRefreshToken);
    }

    @Transactional
    public void changeUserRole(String email, String role) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        user.setRole(role);
        userRepository.save(user);
    }

    @Transactional
    public void confirmEmail(String token) {
        emailVerificationService.confirm(token);
    }
}
