package com.shortlink.shortlink.exception;

public class AuthenticationFailedException extends BaseException {

    public AuthenticationFailedException(String message) {
        super("AUTHENTICATION_FAILED", message, 401);
    }
}
