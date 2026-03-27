package com.shortlink.shortlink.exception;

public class ExpiredShortCodeException extends BaseException{
    public ExpiredShortCodeException(String shortCode) {
        super("EXPIRED", "Short link has expired: " + shortCode, 410);
    }
}
