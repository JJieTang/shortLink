package com.shortlink.shortlink.security;

import com.shortlink.shortlink.exception.AuthenticationFailedException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.User;
import com.shortlink.shortlink.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID getCurrentUserId() {
        return getAuthenticatedUser().userId();
    }

    public User getCurrentUser() {
        UUID userId = getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + userId));
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new AuthenticationFailedException("Authentication is required");
        }
        return authenticatedUser;
    }
}
