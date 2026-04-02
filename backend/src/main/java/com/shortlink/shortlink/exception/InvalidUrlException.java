package com.shortlink.shortlink.exception;

public class InvalidUrlException extends BaseException{
    public InvalidUrlException(String msg) {
        super("INVALID_URL", msg, 400);
    }
}
