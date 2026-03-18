package com.procurehub.user.kafka;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.user.service.UserSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserCreatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedKafkaListener.class);

    private final UserSyncService userSyncService;

    public UserCreatedKafkaListener(UserSyncService userSyncService) {
        this.userSyncService = userSyncService;
    }

    @KafkaListener(topics = "${app.kafka.topics.user-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Received user.created event for userId={}", event.getUserId());
        userSyncService.upsertFromUserCreated(event);
    }
}
