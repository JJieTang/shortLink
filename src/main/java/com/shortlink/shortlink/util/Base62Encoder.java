package com.shortlink.shortlink.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 7;
    private static final int BASE = 62;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRandomCode() {
        long bound = (long) Math.pow(BASE, CODE_LENGTH);
        long number = secureRandom.nextLong(bound);
        return encode(number);
    }

    String encode(long number) {
        if(number == 0) {
            return "0".repeat(CODE_LENGTH);
        }

        StringBuilder sb = new StringBuilder();
        while(number > 0) {
            int remainder = (int)(number % BASE);
            sb.append(ALPHABET.charAt(remainder));
            number /= BASE;
        }

        sb.reverse();

        while(sb.length() < CODE_LENGTH) {
            sb.insert(0, '0');
        }

        return sb.toString();
    }
}
