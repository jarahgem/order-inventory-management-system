package com.megharaj.orderinventory.common.error;

public class InventoryConsistencyException extends IllegalStateException {

    public InventoryConsistencyException(String message) {
        super(message);
    }
}
