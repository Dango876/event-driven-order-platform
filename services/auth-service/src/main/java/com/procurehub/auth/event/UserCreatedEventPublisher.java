package com.procurehub.auth.event;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.auth.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserCreatedEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserCreatedEventPublisher.class);

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;
    private final String topic;

    public UserCreatedEventPublisher(
            KafkaTemplate<String, UserCreatedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.user-created}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(User user) {
        UserCreatedEvent event = UserCreatedEvent.newBuilder()
                .setUserId(user.getId())
                .setEmail(user.getEmail())
                .setRole(user.getRole())
                .setEmailVerified(user.isEmailVerified())
                .setCreatedAt(user.getCreatedAt().toString())
                .build();

        try {
            kafkaTemplate.send(topic, String.valueOf(user.getId()), event).get(5, TimeUnit.SECONDS);
            log.info("Published user.created event for userId={}", user.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish user.created event", e);
        }
    }
}
