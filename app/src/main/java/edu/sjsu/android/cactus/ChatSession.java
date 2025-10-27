package edu.sjsu.android.cactus;

public class ChatSession {
    private long id;
    private String title;
    private long timestamp;
    private int messageCount;

    public ChatSession(long id, String title, long timestamp, int messageCount) {
        this.id = id;
        this.title = title;
        this.timestamp = timestamp;
        this.messageCount = messageCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
