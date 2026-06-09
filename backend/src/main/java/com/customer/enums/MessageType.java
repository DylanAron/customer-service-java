package com.customer.enums;

/**
 * Message content types.
 */
public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    FILE("file");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromValue(String value) {
        for (MessageType t : values()) {
            if (t.value.equals(value)) return t;
        }
        return TEXT;
    }
}
