package com.procurehub.user.api;

import com.procurehub.user.model.UserProjection;
import com.procurehub.user.repository.UserProjectionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProjectionRepository repository;

    public UserController(UserProjectionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<UserProjection> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProjection> getById(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
