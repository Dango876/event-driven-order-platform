package com.procurehub.user;

import com.procurehub.auth.avro.UserCreatedEvent;
import com.procurehub.user.api.HealthController;
import com.procurehub.user.api.UserController;
import com.procurehub.user.api.dto.CreateUserRequest;
import com.procurehub.user.api.dto.UpdateUserRequest;
import com.procurehub.user.config.SecurityConfig;
import com.procurehub.user.kafka.UserCreatedKafkaListener;
import com.procurehub.user.model.UserProjection;
import com.procurehub.user.repository.UserProjectionRepository;
import com.procurehub.user.service.UserSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModuleSupportCoverageTest {

    @Mock
    private UserProjectionRepository repository;

    @Test
    void dtoModelAndHealthControllerShouldWork() {
        CreateUserRequest create = new CreateUserRequest();
        create.setId(10L);
        create.setEmail("user@example.com");
        create.setRole("ROLE_USER");
        create.setEmailVerified(true);
        assertEquals(10L, create.getId());

        UpdateUserRequest update = new UpdateUserRequest();
        update.setEmail("admin@example.com");
        update.setRole("ROLE_ADMIN");
        update.setEmailVerified(false);
        assertEquals("ROLE_ADMIN", update.getRole());

        UserProjection projection = new UserProjection();
        LocalDateTime now = LocalDateTime.now();
        projection.setId(11L);
        projection.setEmail("reader@example.com");
        projection.setRole("ROLE_MANAGER");
        projection.setEmailVerified(true);
        projection.setCreatedAt(now);
        projection.setUpdatedAt(now);
        assertEquals("reader@example.com", projection.getEmail());
        assertEquals(true, projection.isEmailVerified());

        assertEquals("user-service", new HealthController().health().get("service"));
    }

    @Test
    void userSyncServiceShouldUpsertProjectionFromEvent() {
        UserSyncService service = new UserSyncService(repository);
        UserCreatedEvent event = UserCreatedEvent.newBuilder()
                .setUserId(20L)
                .setEmail("synced@example.com")
                .setRole("ROLE_USER")
                .setEmailVerified(true)
                .setCreatedAt("2026-03-27T10:15:30")
                .build();

        when(repository.findById(20L)).thenReturn(Optional.empty());
        when(repository.save(any(UserProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertFromUserCreated(event);

        ArgumentCaptor<UserProjection> captor = ArgumentCaptor.forClass(UserProjection.class);
        verify(repository).save(captor.capture());
        assertEquals("synced@example.com", captor.getValue().getEmail());
        assertEquals("ROLE_USER", captor.getValue().getRole());
    }

    @Test
    void kafkaListenerShouldDelegateToSyncService() {
        UserSyncService service = mock(UserSyncService.class);
        UserCreatedKafkaListener listener = new UserCreatedKafkaListener(service);
        UserCreatedEvent event = UserCreatedEvent.newBuilder()
                .setUserId(21L)
                .setEmail("listener@example.com")
                .setRole("ROLE_ADMIN")
                .setEmailVerified(true)
                .setCreatedAt("2026-03-27T10:15:30Z")
                .build();

        listener.onUserCreated(event);

        verify(service).upsertFromUserCreated(event);
    }

    @Test
    void controllerShouldHandleCrudAndFilteringFlows() {
        UserController controller = new UserController(repository);
        UserProjection projection = new UserProjection();
        projection.setId(30L);
        projection.setEmail("user@example.com");
        projection.setRole("ROLE_USER");
        projection.setEmailVerified(true);

        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(projection));
        when(repository.findAllByRole("ROLE_USER")).thenReturn(List.of(projection));
        when(repository.findAll()).thenReturn(List.of(projection));
        when(repository.findById(30L)).thenReturn(Optional.of(projection));
        when(repository.existsById(30L)).thenReturn(false);
        when(repository.save(any(UserProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, controller.getAll("user@example.com", null).size());
        assertEquals(1, controller.getAll(null, "ROLE_USER").size());
        assertEquals(1, controller.getAll(null, null).size());

        ResponseEntity<UserProjection> found = controller.getById(30L);
        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertEquals("user@example.com", found.getBody().getEmail());

        CreateUserRequest create = new CreateUserRequest();
        create.setId(30L);
        create.setEmail("new@example.com");
        create.setRole("ROLE_USER");
        create.setEmailVerified(true);

        ResponseEntity<UserProjection> created = controller.create(create);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("new@example.com", created.getBody().getEmail());

        UpdateUserRequest update = new UpdateUserRequest();
        update.setEmail("updated@example.com");
        update.setRole("ROLE_ADMIN");
        update.setEmailVerified(false);

        UserProjection updated = controller.update(30L, update);
        assertEquals("updated@example.com", updated.getEmail());
        assertEquals("ROLE_ADMIN", updated.getRole());

        when(repository.existsById(30L)).thenReturn(true);
        ResponseEntity<Void> deleted = controller.delete(30L);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        verify(repository).deleteById(30L);

        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.getById(999L).getStatusCode());

        when(repository.existsById(55L)).thenReturn(true);
        CreateUserRequest duplicateId = new CreateUserRequest();
        duplicateId.setId(55L);
        duplicateId.setEmail("dup@example.com");
        duplicateId.setRole("ROLE_USER");
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class, () -> controller.create(duplicateId));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        when(repository.existsById(56L)).thenReturn(false);
        when(repository.findByEmail("dup-email@example.com")).thenReturn(Optional.of(projection));
        CreateUserRequest duplicateEmail = new CreateUserRequest();
        duplicateEmail.setId(56L);
        duplicateEmail.setEmail("dup-email@example.com");
        duplicateEmail.setRole("ROLE_USER");
        ResponseStatusException emailConflict = assertThrows(ResponseStatusException.class, () -> controller.create(duplicateEmail));
        assertEquals(HttpStatus.CONFLICT, emailConflict.getStatusCode());

        when(repository.findById(31L)).thenReturn(Optional.of(projection));
        UserProjection other = new UserProjection();
        other.setId(99L);
        other.setEmail("taken@example.com");
        when(repository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));
        UpdateUserRequest conflictUpdate = new UpdateUserRequest();
        conflictUpdate.setEmail("taken@example.com");
        conflictUpdate.setRole("ROLE_ADMIN");
        ResponseStatusException updateConflict = assertThrows(ResponseStatusException.class, () -> controller.update(31L, conflictUpdate));
        assertEquals(HttpStatus.CONFLICT, updateConflict.getStatusCode());

        when(repository.existsById(777L)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.delete(777L).getStatusCode());
    }

    @Test
    void securityBeansShouldExposeJwtAuthorities() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(
                config,
                "jwtSecret",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))
        );

        assertNotNull(config.jwtDecoder());

        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();

        assertEquals(
                "ROLE_ADMIN",
                converter.convert(jwt).getAuthorities().iterator().next().getAuthority()
        );
    }
}
