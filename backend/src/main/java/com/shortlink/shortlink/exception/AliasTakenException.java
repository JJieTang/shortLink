package com.shortlink.shortlink.exception;

public class AliasTakenException extends BaseException{
    public AliasTakenException(String msg) {
        super("ALIAS_TAKEN", msg, 409);
    }
}
