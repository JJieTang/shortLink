package com.shortlink.shortlink.repository;

import com.shortlink.shortlink.model.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UrlRepository extends JpaRepository<Url, UUID> {

    Optional<Url> findByShortCodeAndIsActiveTrue(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<Url> findByUser_IdAndIsActiveTrue(UUID userId, Pageable pageable);

    @Modifying
    @Query("update Url u set u.totalClicks = u.totalClicks + :delta where u.id = :urlId")
    int incrementTotalClicks(@Param("urlId") UUID urlId, @Param("delta") long delta);
}
