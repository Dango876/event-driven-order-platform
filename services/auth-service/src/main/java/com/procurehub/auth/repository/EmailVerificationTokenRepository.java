package com.procurehub.auth.repository;

import com.procurehub.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    List<EmailVerificationToken> findAllByUser_IdAndConsumedFalse(Long userId);
}
