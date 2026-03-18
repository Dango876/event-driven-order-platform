package com.procurehub.user.service;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.user.model.UserProjection;
import com.procurehub.user.repository.UserProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserSyncService {

    private final UserProjectionRepository repository;

    public UserSyncService(UserProjectionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsertFromUserCreated(UserCreatedEvent event) {
        UserProjection user = repository.findById(event.getUserId()).orElseGet(UserProjection::new);

        user.setId(event.getUserId());
        user.setEmail(event.getEmail().toString());
        user.setRole(event.getRole().toString());
        user.setEmailVerified(event.getEmailVerified());

        LocalDateTime eventCreatedAt = LocalDateTime.parse(event.getCreatedAt().toString());
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(eventCreatedAt);
        }
        user.setUpdatedAt(LocalDateTime.now());

        repository.save(user);
    }
}
