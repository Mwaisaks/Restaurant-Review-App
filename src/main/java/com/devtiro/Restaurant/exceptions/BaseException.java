package com.devtiro.Restaurant.exceptions;

public class BaseException extends RuntimeException{
    public BaseException(String message) {
        super(message);
    }

    public BaseException() {
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public BaseException(Throwable cause) {
        super(cause);
    }
}

//checked vs unchecked exception
//extending runtimeException vs extending exception
//why checked over unchecked?