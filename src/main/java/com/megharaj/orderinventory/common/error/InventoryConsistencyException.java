package com.megharaj.orderinventory.common.error;

public class InventoryConsistencyException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public InventoryConsistencyException(String message) {
        super(message);
    }
}
