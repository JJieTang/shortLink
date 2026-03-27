package com.shortlink.shortlink.exception;

public class ShortCodeGenerationException extends BaseException{
    public ShortCodeGenerationException(String msg) {
        super("SHORT_CODE_GENERATION_FAILED", msg, 500);
    }
}
