package com.procurehub.notification;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.notification.api.HealthController;
import com.procurehub.notification.kafka.OrderEventsListener;
import com.procurehub.notification.kafka.UserEventsListener;
import com.procurehub.notification.model.NotificationRecord;
import com.procurehub.notification.model.NotificationStatus;
import com.procurehub.notification.model.NotificationType;
import com.procurehub.notification.model.NotificationUser;
import com.procurehub.notification.ratelimit.NotificationRateLimitProperties;
import com.procurehub.notification.ratelimit.NotificationRateLimiter;
import com.procurehub.notification.repository.NotificationRecordRepository;
import com.procurehub.notification.repository.NotificationUserRepository;
import com.procurehub.notification.sender.EmailNotificationSender;
import com.procurehub.notification.sender.NotificationSenderProperties;
import com.procurehub.notification.service.NotificationDispatchService;
import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationModuleSupportCoverageTest {

    @Mock
    private NotificationRateLimiter rateLimiter;

    @Mock
    private NotificationUserRepository notificationUserRepository;

    @Mock
    private NotificationRecordRepository notificationRecordRepository;

    @Mock
    private EmailNotificationSender emailNotificationSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void propertiesModelsEnumsAndHealthShouldWork() {
        NotificationSenderProperties senderProperties = new NotificationSenderProperties();
        senderProperties.setFrom("no-reply@example.com");
        assertEquals("no-reply@example.com", senderProperties.getFrom());

        NotificationRateLimitProperties rateLimitProperties = new NotificationRateLimitProperties();
        rateLimitProperties.setEnabled(false);
        rateLimitProperties.setCapacity(5);
        rateLimitProperties.setLeakPerSecond(2);
        rateLimitProperties.setBucketKeyTtlSeconds(120);
        rateLimitProperties.setOrderUserTtlSeconds(3600);
        rateLimitProperties.setUserBucketKeyPrefix("bucket:user:");
        rateLimitProperties.setOrderBucketKeyPrefix("bucket:order:");
        rateLimitProperties.setOrderUserKeyPrefix("order:user:");
        assertEquals(false, rateLimitProperties.isEnabled());
        assertEquals(5, rateLimitProperties.getCapacity());

        Instant now = Instant.now();
        NotificationUser user = new NotificationUser(11L, "user@example.com", "ROLE_USER", true, now, now);
        assertEquals(11L, user.getUserId());
        assertEquals("user@example.com", user.getEmail());

        NotificationRecord record = new NotificationRecord(
                "id-1",
                NotificationType.ORDER_CREATED,
                NotificationStatus.PENDING,
                11L,
                22L,
                "user@example.com",
                "subject",
                "body",
                "event-1",
                null,
                now,
                null
        );
        record.setStatus(NotificationStatus.SENT);
        record.setSentAt(now);
        assertEquals(NotificationStatus.SENT, record.getStatus());
        assertEquals("id-1", record.getId());

        assertEquals("ok", new HealthController().health().get("status"));
        assertNotNull(NotificationStatus.FAILED);
        assertNotNull(NotificationType.ORDER_STATUS_CHANGED);
    }

    @Test
    void emailSenderShouldPopulateMailMessage() {
        NotificationSenderProperties properties = new NotificationSenderProperties();
        properties.setFrom("robot@example.com");
        EmailNotificationSender sender = new EmailNotificationSender(javaMailSender, properties);

        sender.send("buyer@example.com", "Order update", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        assertEquals("robot@example.com", captor.getValue().getFrom());
        assertEquals("buyer@example.com", captor.getValue().getTo()[0]);
        assertEquals("Order update", captor.getValue().getSubject());
    }

    @Test
    void rateLimiterShouldHandleAllowLookupAndFailOpenPaths() {
        NotificationRateLimitProperties properties = new NotificationRateLimitProperties();
        NotificationRateLimiter limiter = new NotificationRateLimiter(redisTemplate, properties);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(1L);
        when(valueOperations.get("notification:order-user:55")).thenReturn("99");

        assertTrue(limiter.allowForUser(99L));
        assertTrue(limiter.allowForOrderFallback(55L));
        limiter.rememberOrderUser(55L, 99L);
        assertEquals(99L, limiter.findUserIdByOrder(55L));
        verify(valueOperations).set("notification:order-user:55", "99", Duration.ofSeconds(604800));

        StringRedisTemplate brokenRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> brokenOps = mock(ValueOperations.class);
        when(brokenRedisTemplate.opsForValue()).thenReturn(brokenOps);
        when(brokenRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis down"));
        when(brokenOps.get(any())).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down")).when(brokenOps).set(any(), any(), any(Duration.class));

        NotificationRateLimiter failOpenLimiter = new NotificationRateLimiter(brokenRedisTemplate, properties);
        assertTrue(failOpenLimiter.allowForUser(1L));
        assertDoesNotThrow(() -> failOpenLimiter.rememberOrderUser(1L, 2L));
        assertNull(failOpenLimiter.findUserIdByOrder(1L));
    }

    @Test
    void dispatchServiceShouldSendOrderCreatedAndPersistSentStatus() {
        NotificationDispatchService service = new NotificationDispatchService(
                rateLimiter,
                notificationUserRepository,
                notificationRecordRepository,
                emailNotificationSender
        );

        NotificationUser user = new NotificationUser(11L, "buyer@example.com", "ROLE_USER", true, Instant.now(), Instant.now());
        when(notificationUserRepository.findById(11L)).thenReturn(Optional.of(user));
        when(rateLimiter.allowForUser(11L)).thenReturn(true);
        when(notificationRecordRepository.save(any(NotificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setOrderId(7L)
                .setUserId(11L)
                .setProductId(1001L)
                .setQuantity(2)
                .setStatus("RESERVATION_PENDING")
                .setStatusMessage("created")
                .setCreatedAt("2026-03-27T10:15:30Z")
                .build();

        service.handleOrderCreated(event);

        verify(rateLimiter).rememberOrderUser(7L, 11L);
        verify(emailNotificationSender).send(eq("buyer@example.com"), eq("Order #7 created"), any(String.class));

        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        NotificationRecord lastRecord = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(NotificationStatus.SENT, lastRecord.getStatus());
        assertEquals("buyer@example.com", lastRecord.getRecipientEmail());
    }

    @Test
    void dispatchServiceShouldHandleSkippedAndFallbackStatusChangedFlows() {
        NotificationDispatchService service = new NotificationDispatchService(
                rateLimiter,
                notificationUserRepository,
                notificationRecordRepository,
                emailNotificationSender
        );

        when(notificationRecordRepository.save(any(NotificationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreatedEvent missingRecipientEvent = OrderCreatedEvent.newBuilder()
                .setOrderId(8L)
                .setUserId(12L)
                .setProductId(1002L)
                .setQuantity(1)
                .setStatus("RESERVATION_PENDING")
                .setStatusMessage("created")
                .setCreatedAt("2026-03-27T10:15:30Z")
                .build();

        when(notificationUserRepository.findById(12L)).thenReturn(Optional.empty());
        service.handleOrderCreated(missingRecipientEvent);
        verify(emailNotificationSender, never()).send(eq(null), any(), any());

        NotificationUser userWithoutEmail = new NotificationUser(21L, null, "ROLE_USER", true, Instant.now(), Instant.now());
        NotificationRecord previousRecord = new NotificationRecord(
                "id-2",
                NotificationType.ORDER_CREATED,
                NotificationStatus.SENT,
                21L,
                9L,
                "fallback@example.com",
                "subj",
                "body",
                "evt",
                null,
                Instant.now(),
                Instant.now()
        );

        when(rateLimiter.findUserIdByOrder(9L)).thenReturn(21L);
        when(notificationUserRepository.findById(21L)).thenReturn(Optional.of(userWithoutEmail));
        when(notificationRecordRepository.findFirstByOrderIdOrderByCreatedAtDesc(9L)).thenReturn(Optional.of(previousRecord));
        when(rateLimiter.allowForUser(21L)).thenReturn(true);

        OrderStatusChangedEvent statusChanged = OrderStatusChangedEvent.newBuilder()
                .setOrderId(9L)
                .setOldStatus("RESERVED")
                .setNewStatus("PAID")
                .setStatusMessage("paid")
                .setUpdatedAt("2026-03-27T10:15:30Z")
                .build();

        service.handleOrderStatusChanged(statusChanged);

        verify(rateLimiter).rememberOrderUser(9L, 21L);
        verify(emailNotificationSender).send(eq("fallback@example.com"), eq("Order #9 status changed to PAID"), any(String.class));
    }

    @Test
    void listenersShouldDelegateAndPersistUserProjection() {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        OrderEventsListener orderListener = new OrderEventsListener(dispatchService);

        OrderCreatedEvent createdEvent = OrderCreatedEvent.newBuilder()
                .setOrderId(30L)
                .setUserId(40L)
                .setProductId(50L)
                .setQuantity(1)
                .setStatus("RESERVATION_PENDING")
                .setStatusMessage("created")
                .setCreatedAt("2026-03-27T10:15:30Z")
                .build();

        OrderStatusChangedEvent statusChangedEvent = OrderStatusChangedEvent.newBuilder()
                .setOrderId(30L)
                .setOldStatus("RESERVATION_PENDING")
                .setNewStatus("RESERVED")
                .setStatusMessage("reserved")
                .setUpdatedAt("2026-03-27T10:15:30")
                .build();

        orderListener.onOrderCreated(createdEvent);
        orderListener.onOrderStatusChanged(statusChangedEvent);

        verify(dispatchService).handleOrderCreated(createdEvent);
        verify(dispatchService).handleOrderStatusChanged(statusChangedEvent);

        UserEventsListener userListener = new UserEventsListener(notificationUserRepository);
        when(notificationUserRepository.findById(41L)).thenReturn(Optional.empty());
        when(notificationUserRepository.save(any(NotificationUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreatedEvent userCreatedEvent = UserCreatedEvent.newBuilder()
                .setUserId(41L)
                .setEmail("notify@example.com")
                .setRole("ROLE_USER")
                .setEmailVerified(true)
                .setCreatedAt("2026-03-27T10:15:30")
                .build();

        userListener.onUserCreated(userCreatedEvent);

        ArgumentCaptor<NotificationUser> captor = ArgumentCaptor.forClass(NotificationUser.class);
        verify(notificationUserRepository).save(captor.capture());
        assertEquals("notify@example.com", captor.getValue().getEmail());
        assertEquals("ROLE_USER", captor.getValue().getRole());
    }
}
