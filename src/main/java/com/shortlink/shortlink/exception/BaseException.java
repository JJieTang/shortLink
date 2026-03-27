package com.shortlink.shortlink.exception;

public abstract class BaseException extends RuntimeException{

    private final String errorCode;
    private final int status;

    protected BaseException(String errorCode, String msg, int status) {
        super(msg);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}
