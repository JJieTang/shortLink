package com.shortlink.shortlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62EncoderTest {

    private final Base62Encoder base62Encoder = new Base62Encoder();

    @Test
    void generateRandomCodeShouldReturnSevenCharacterBase62String() {
        String code = base62Encoder.generateRandomCode();

        assertEquals(7, code.length());
        assertTrue(code.matches("^[0-9a-zA-Z]{7}$"));
    }

    @Test
    void encodeShouldPadWithLeadingZeros() {
        assertEquals("0000000", base62Encoder.encode(0));
        assertEquals("000000Z", base62Encoder.encode(61));
    }
}
