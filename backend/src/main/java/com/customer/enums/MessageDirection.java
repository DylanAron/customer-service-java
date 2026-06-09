package com.customer.enums;

/**
 * Message direction: who sent the message.
 */
public enum MessageDirection {
    USER("user"),
    AGENT("agent");

    private final String value;

    MessageDirection(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageDirection fromValue(String value) {
        for (MessageDirection d : values()) {
            if (d.value.equals(value)) return d;
        }
        return USER;
    }
}
