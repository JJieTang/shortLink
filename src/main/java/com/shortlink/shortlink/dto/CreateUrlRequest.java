package com.shortlink.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest (

    @NotBlank(message = "originalUrl must not be blank")
    @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
    String originalUrl,

    @Size(max = 30, message = "customAlias must not exceed 30 characters")
    String customAlias,

    Instant expiresAt

    ) {}
