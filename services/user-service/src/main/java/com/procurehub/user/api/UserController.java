package com.procurehub.user.api;

import com.procurehub.user.api.dto.CreateUserRequest;
import com.procurehub.user.api.dto.UpdateUserRequest;
import com.procurehub.user.model.UserProjection;
import com.procurehub.user.repository.UserProjectionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProjectionRepository repository;

    public UserController(UserProjectionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<UserProjection> getAll(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "role", required = false) String role
    ) {
        if (email != null && !email.isBlank()) {
            return repository.findByEmail(email).map(List::of).orElseGet(List::of);
        }
        if (role != null && !role.isBlank()) {
            return repository.findAllByRole(role);
        }
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProjection> getById(@PathVariable("id") Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserProjection> create(@Valid @RequestBody CreateUserRequest request) {
        if (repository.existsById(request.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with id already exists: " + request.getId());
        }
        repository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User with email already exists: " + request.getEmail()
            );
        });

        LocalDateTime now = LocalDateTime.now();
        UserProjection user = new UserProjection();
        user.setId(request.getId());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setEmailVerified(request.isEmailVerified());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(user));
    }

    @PutMapping("/{id}")
    public UserProjection update(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRequest request) {
        UserProjection existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));

        repository.findByEmail(request.getEmail()).ifPresent(userByEmail -> {
            if (!Objects.equals(userByEmail.getId(), id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "User with email already exists: " + request.getEmail()
                );
            }
        });

        existing.setEmail(request.getEmail());
        existing.setRole(request.getRole());
        existing.setEmailVerified(request.isEmailVerified());
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
