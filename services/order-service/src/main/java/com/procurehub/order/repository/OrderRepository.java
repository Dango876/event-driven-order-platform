package com.procurehub.order.repository;

import com.procurehub.order.model.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOwnerSubject(String ownerSubject, Sort sort);

    Optional<Order> findByIdAndOwnerSubject(Long id, String ownerSubject);
}
