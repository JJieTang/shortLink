package com.shortlink.shortlink.exception;

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String msg) {
        super("NOT_FOUND", msg, 404);
    }
}
