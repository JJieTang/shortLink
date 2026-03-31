package com.shortlink.shortlink.repository;

import com.shortlink.shortlink.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    Optional<ClickEvent> findByEventId(UUID eventId);
}
