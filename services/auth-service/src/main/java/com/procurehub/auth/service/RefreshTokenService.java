package com.procurehub.auth.service;

import com.procurehub.auth.model.RefreshToken;
import com.procurehub.auth.model.User;
import com.procurehub.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String issueToken(User user) {
        revokeActiveTokens(user.getId());

        RefreshToken refreshToken = new RefreshToken(
                generateToken(),
                user,
                LocalDateTime.now().plusDays(refreshTokenExpirationDays),
                false,
                LocalDateTime.now()
        );

        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    @Transactional
    public User validateAndRotate(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser();
    }

    private void revokeActiveTokens(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUser_IdAndRevokedFalse(userId);
        if (!activeTokens.isEmpty()) {
            activeTokens.forEach(token -> token.setRevoked(true));
            refreshTokenRepository.saveAll(activeTokens);
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
