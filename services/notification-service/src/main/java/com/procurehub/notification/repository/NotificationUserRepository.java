package com.procurehub.notification.repository;

import com.procurehub.notification.model.NotificationUser;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationUserRepository extends MongoRepository<NotificationUser, Long> {
}
