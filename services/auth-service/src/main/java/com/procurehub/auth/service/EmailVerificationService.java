package com.procurehub.auth.service;

import com.procurehub.auth.model.EmailVerificationToken;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.EmailVerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTokenRepository tokenRepository;

    @Value("${auth.email-confirmation.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${auth.base-url:http://localhost:8081}")
    private String baseUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public void createAndLogToken(User user) {
        tokenRepository.findAllByUser_IdAndConsumedFalse(user.getId()).forEach(token -> token.setConsumed(true));

        String token = generateToken();
        EmailVerificationToken entity = new EmailVerificationToken(
                token,
                user,
                LocalDateTime.now().plusMinutes(expirationMinutes),
                false,
                LocalDateTime.now()
        );
        tokenRepository.save(entity);

        log.info("Email confirmation link for {}: {}/auth/confirm?token={}", user.getEmail(), baseUrl, token);
    }

    @Transactional
    public void confirm(String token) {
        EmailVerificationToken entity = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid confirmation token"));

        if (entity.isConsumed()) {
            throw new IllegalArgumentException("Confirmation token already used");
        }

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Confirmation token expired");
        }

        entity.getUser().setEmailVerified(true);
        entity.setConsumed(true);
        tokenRepository.save(entity);
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
