package com.shortlink.shortlink.exception;

public class InvalidAliasException extends BaseException{
    public InvalidAliasException(String msg) {
        super("INVALID_ALIAS", msg, 400);
    }
}
