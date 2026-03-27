package com.shortlink.shortlink.util;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ReservedWords {

    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "health", "login", "register",
            "logout", "static", "assets", "favicon.ico"
    );

    public boolean isReserved(String alias) {
        return RESERVED.contains(alias.toLowerCase());
    }
}
