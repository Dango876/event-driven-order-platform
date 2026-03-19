package com.procurehub.user.repository;

import com.procurehub.user.model.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProjectionRepository extends JpaRepository<UserProjection, Long> {
    Optional<UserProjection> findByEmail(String email);
    List<UserProjection> findAllByRole(String role);
}
