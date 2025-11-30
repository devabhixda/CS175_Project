package edu.sjsu.android.cactus;

public class Message {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AGENT = 1;
    public static final int TYPE_TOOL = 2;

    private long id;
    private long sessionId;
    private String content;
    private boolean isUser;
    private long timestamp;
    private int messageType; // Not persisted to DB, used for UI display only

    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
        this.id = -1; // -1 indicates not yet saved to database
        this.sessionId = -1;
        this.messageType = isUser ? TYPE_USER : TYPE_AGENT;
    }

    public Message(String content, boolean isUser, long timestamp) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.id = -1;
        this.sessionId = -1;
        this.messageType = isUser ? TYPE_USER : TYPE_AGENT;
    }

    public Message(String content, int messageType) {
        this.content = content;
        this.isUser = (messageType == TYPE_USER);
        this.timestamp = System.currentTimeMillis();
        this.id = -1;
        this.sessionId = -1;
        this.messageType = messageType;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }
}