package com.shortlink.shortlink.util;

import com.shortlink.shortlink.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlValidatorTest {

    private final UrlValidator urlValidator = new UrlValidator();

    @Test
    void shouldAcceptPublicHttpUrl() {
        assertDoesNotThrow(() -> urlValidator.validate("https://example.com/path?q=1"));
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        assertThrows(InvalidUrlException.class, () -> urlValidator.validate("ftp://example.com/file"));
    }

    @Test
    void shouldRejectPrivateAddress() {
        assertThrows(InvalidUrlException.class, () -> urlValidator.validate("http://127.0.0.1/internal"));
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThrows(InvalidUrlException.class, () -> urlValidator.validate(" "));
    }
}
