package edu.sjsu.android.cactus;

import android.provider.BaseColumns;

public final class ChatDatabaseContract {
    // Private constructor to prevent accidental instantiation
    private ChatDatabaseContract() {}

    /* Inner class that defines the sessions table */
    public static class SessionEntry implements BaseColumns {
        public static final String TABLE_NAME = "sessions";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    }

    /* Inner class that defines the messages table */
    public static class MessageEntry implements BaseColumns {
        public static final String TABLE_NAME = "messages";
        public static final String COLUMN_NAME_SESSION_ID = "session_id";
        public static final String COLUMN_NAME_CONTENT = "content";
        public static final String COLUMN_NAME_IS_USER = "is_user";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    }
}
