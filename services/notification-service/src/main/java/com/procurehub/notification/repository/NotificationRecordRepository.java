package com.procurehub.notification.repository;

import com.procurehub.notification.model.NotificationRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRecordRepository extends MongoRepository<NotificationRecord, String> {

    List<NotificationRecord> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<NotificationRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<NotificationRecord> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
