package com.shortlink.shortlink.repository;

import com.shortlink.shortlink.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID>, ClickEventBatchRepository {

    boolean existsByEventId(UUID eventId);

    @Query("select ce.eventId from ClickEvent ce where ce.eventId in :eventIds")
    List<UUID> findExistingEventIdsByEventIdIn(Collection<UUID> eventIds);

    Optional<ClickEvent> findByEventId(UUID eventId);

    boolean existsByUrl_IdAndIpAddressAndClickedAtGreaterThanEqualAndClickedAtLessThan(
            UUID urlId,
            String ipAddress,
            Instant startInclusive,
            Instant endExclusive
    );
}
