package com.shortlink.shortlink.exception;

public class InvalidRequestException extends BaseException{
    public InvalidRequestException(String msg) {
        super("INVALID_REQUEST", msg, 400);
    }
}
