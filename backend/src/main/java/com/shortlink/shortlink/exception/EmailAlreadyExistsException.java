package com.shortlink.shortlink.exception;

public class EmailAlreadyExistsException extends BaseException {

    public EmailAlreadyExistsException(String message) {
        super("EMAIL_ALREADY_EXISTS", message, 409);
    }
}
