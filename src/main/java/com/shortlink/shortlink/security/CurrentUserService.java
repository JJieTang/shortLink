package com.shortlink.shortlink.security;

import com.shortlink.shortlink.exception.UnauthorizedException;
import com.shortlink.shortlink.model.User;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final EntityManager entityManager;

    public CurrentUserService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public UUID getCurrentUserId() {
        return getAuthenticatedUser().userId();
    }

    public User getCurrentUserReference() {
        return entityManager.getReference(User.class, getCurrentUserId());
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new UnauthorizedException("Authentication is required");
        }
        return authenticatedUser;
    }
}
