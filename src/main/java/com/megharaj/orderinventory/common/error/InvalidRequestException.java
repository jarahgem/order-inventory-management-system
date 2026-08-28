package com.megharaj.orderinventory.common.error;

public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }
}
