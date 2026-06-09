package com.customer.constant;

/**
 * WebSocket message type constants exchanged between client and server.
 */
public class WsMsgType {

    public static final String USER_MESSAGE = "user_message";
    public static final String AGENT_MESSAGE = "agent_message";
    public static final String NEW_USER = "new_user";
    public static final String USER_OFFLINE = "user_offline";
    public static final String SYSTEM = "system";
    public static final String WELCOME_MESSAGE = "welcome_message";
    public static final String PING = "ping";
    public static final String PONG = "pong";

    private WsMsgType() {}
}
