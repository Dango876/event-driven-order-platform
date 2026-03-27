package com.procurehub.notification.kafka;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.notification.model.NotificationUser;
import com.procurehub.notification.repository.NotificationUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class UserEventsListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventsListener.class);

    private final NotificationUserRepository notificationUserRepository;

    public UserEventsListener(NotificationUserRepository notificationUserRepository) {
        this.notificationUserRepository = notificationUserRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.user-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onUserCreated(UserCreatedEvent event) {
        NotificationUser user = notificationUserRepository.findById(event.getUserId())
                .orElseGet(NotificationUser::new);

        boolean isNew = user.getCreatedAt() == null;

        user.setUserId(event.getUserId());
        user.setEmail(event.getEmail());
        user.setRole(event.getRole());
        user.setEmailVerified(event.getEmailVerified());

        if (isNew) {
            user.setCreatedAt(parseTimestamp(event.getCreatedAt()));
        }

        user.setUpdatedAt(Instant.now());

        notificationUserRepository.save(user);

        log.info(
                "Upserted notification user projection for userId={}, email={}",
                event.getUserId(),
                event.getEmail()
        );
    }

    private Instant parseTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }

        log.warn("Failed to parse user.created timestamp '{}', fallback to Instant.now()", value);
        return Instant.now();
    }
}
