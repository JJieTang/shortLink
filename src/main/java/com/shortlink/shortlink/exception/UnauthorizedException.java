package com.shortlink.shortlink.exception;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
