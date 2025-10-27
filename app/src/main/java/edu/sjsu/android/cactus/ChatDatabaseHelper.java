package edu.sjsu.android.cactus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatDatabaseHelper extends SQLiteOpenHelper {
    // Database version. If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "ChatHistory.db";

    private static final String SQL_CREATE_SESSIONS =
            "CREATE TABLE " + ChatDatabaseContract.SessionEntry.TABLE_NAME + " (" +
                    ChatDatabaseContract.SessionEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    ChatDatabaseContract.SessionEntry.COLUMN_NAME_TITLE + " TEXT," +
                    ChatDatabaseContract.SessionEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)";

    private static final String SQL_CREATE_MESSAGES =
            "CREATE TABLE " + ChatDatabaseContract.MessageEntry.TABLE_NAME + " (" +
                    ChatDatabaseContract.MessageEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + " INTEGER," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT + " TEXT," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER + " INTEGER," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + " INTEGER," +
                    "FOREIGN KEY(" + ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + ") REFERENCES " +
                    ChatDatabaseContract.SessionEntry.TABLE_NAME + "(" + ChatDatabaseContract.SessionEntry._ID + "))";

    private static final String SQL_DELETE_SESSIONS =
            "DROP TABLE IF EXISTS " + ChatDatabaseContract.SessionEntry.TABLE_NAME;

    private static final String SQL_DELETE_MESSAGES =
            "DROP TABLE IF EXISTS " + ChatDatabaseContract.MessageEntry.TABLE_NAME;

    public ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_SESSIONS);
        db.execSQL(SQL_CREATE_MESSAGES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle upgrade from version 1 to version 2
        if (oldVersion < 2) {
            // Create sessions table
            db.execSQL(SQL_CREATE_SESSIONS);
            
            // Create a default session for existing messages
            ContentValues sessionValues = new ContentValues();
            sessionValues.put(ChatDatabaseContract.SessionEntry.COLUMN_NAME_TITLE, "Previous Chat");
            sessionValues.put(ChatDatabaseContract.SessionEntry.COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());
            long sessionId = db.insert(ChatDatabaseContract.SessionEntry.TABLE_NAME, null, sessionValues);
            
            // Add session_id column to messages table
            db.execSQL("CREATE TABLE messages_new (" +
                    ChatDatabaseContract.MessageEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + " INTEGER," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT + " TEXT," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER + " INTEGER," +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)");
            
            // Copy existing messages with the default session ID
            db.execSQL("INSERT INTO messages_new (" +
                    ChatDatabaseContract.MessageEntry._ID + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + ") " +
                    "SELECT " +
                    ChatDatabaseContract.MessageEntry._ID + ", " + sessionId + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER + ", " +
                    ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP +
                    " FROM " + ChatDatabaseContract.MessageEntry.TABLE_NAME);
            
            // Drop old table and rename new one
            db.execSQL("DROP TABLE " + ChatDatabaseContract.MessageEntry.TABLE_NAME);
            db.execSQL("ALTER TABLE messages_new RENAME TO " + ChatDatabaseContract.MessageEntry.TABLE_NAME);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    /**
     * Create a new chat session
     */
    public long createSession(String title) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ChatDatabaseContract.SessionEntry.COLUMN_NAME_TITLE, title);
        values.put(ChatDatabaseContract.SessionEntry.COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());

        return db.insert(ChatDatabaseContract.SessionEntry.TABLE_NAME, null, values);
    }

    /**
     * Get all chat sessions ordered by timestamp (newest first)
     */
    public List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT s." + ChatDatabaseContract.SessionEntry._ID + ", " +
                "s." + ChatDatabaseContract.SessionEntry.COLUMN_NAME_TITLE + ", " +
                "s." + ChatDatabaseContract.SessionEntry.COLUMN_NAME_TIMESTAMP + ", " +
                "COUNT(m." + ChatDatabaseContract.MessageEntry._ID + ") as message_count " +
                "FROM " + ChatDatabaseContract.SessionEntry.TABLE_NAME + " s " +
                "LEFT JOIN " + ChatDatabaseContract.MessageEntry.TABLE_NAME + " m " +
                "ON s." + ChatDatabaseContract.SessionEntry._ID + " = m." + ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + " " +
                "GROUP BY s." + ChatDatabaseContract.SessionEntry._ID + " " +
                "ORDER BY s." + ChatDatabaseContract.SessionEntry.COLUMN_NAME_TIMESTAMP + " DESC";

        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            String title = cursor.getString(1);
            long timestamp = cursor.getLong(2);
            int messageCount = cursor.getInt(3);

            sessions.add(new ChatSession(id, title, timestamp, messageCount));
        }
        cursor.close();

        return sessions;
    }

    /**
     * Update session title
     */
    public void updateSessionTitle(long sessionId, String newTitle) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ChatDatabaseContract.SessionEntry.COLUMN_NAME_TITLE, newTitle);

        db.update(ChatDatabaseContract.SessionEntry.TABLE_NAME, values,
                ChatDatabaseContract.SessionEntry._ID + " = ?",
                new String[]{String.valueOf(sessionId)});
    }

    /**
     * Delete a session and all its messages
     */
    public void deleteSession(long sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Delete all messages in this session
        db.delete(ChatDatabaseContract.MessageEntry.TABLE_NAME,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)});

        // Delete the session
        db.delete(ChatDatabaseContract.SessionEntry.TABLE_NAME,
                ChatDatabaseContract.SessionEntry._ID + " = ?",
                new String[]{String.valueOf(sessionId)});
    }

    /**
     * Insert a message into the database
     */
    public long insertMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID, message.getSessionId());
        values.put(ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT, message.getContent());
        values.put(ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER, message.isUser() ? 1 : 0);
        values.put(ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP, message.getTimestamp());

        long newRowId = db.insert(ChatDatabaseContract.MessageEntry.TABLE_NAME, null, values);
        return newRowId;
    }

    /**
     * Get all messages for a specific session ordered by timestamp
     */
    public List<Message> getMessagesForSession(long sessionId) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String[] projection = {
                ChatDatabaseContract.MessageEntry._ID,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP
        };

        String selection = ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID + " = ?";
        String[] selectionArgs = {String.valueOf(sessionId)};
        String sortOrder = ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + " ASC";

        Cursor cursor = db.query(
                ChatDatabaseContract.MessageEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );

        while (cursor.moveToNext()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry._ID));
            long sessId = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT));
            int isUserInt = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP));

            Message message = new Message(content, isUserInt == 1, timestamp);
            message.setId(id);
            message.setSessionId(sessId);
            messages.add(message);
        }
        cursor.close();

        return messages;
    }

    /**
     * Get all messages from all sessions (for backward compatibility)
     */
    public List<Message> getAllMessages() {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String[] projection = {
                ChatDatabaseContract.MessageEntry._ID,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER,
                ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP
        };

        String sortOrder = ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + " ASC";

        Cursor cursor = db.query(
                ChatDatabaseContract.MessageEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                sortOrder
        );

        while (cursor.moveToNext()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry._ID));
            long sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_SESSION_ID));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_CONTENT));
            int isUserInt = cursor.getInt(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_IS_USER));
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(ChatDatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP));

            Message message = new Message(content, isUserInt == 1, timestamp);
            message.setId(id);
            message.setSessionId(sessionId);
            messages.add(message);
        }
        cursor.close();

        return messages;
    }

    /**
     * Delete all messages from the database
     */
    public void deleteAllMessages() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(ChatDatabaseContract.MessageEntry.TABLE_NAME, null, null);
    }

    /**
     * Delete all sessions and messages
     */
    public void deleteAllSessions() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(ChatDatabaseContract.MessageEntry.TABLE_NAME, null, null);
        db.delete(ChatDatabaseContract.SessionEntry.TABLE_NAME, null, null);
    }

    /**
     * Delete a specific message by ID
     */
    public void deleteMessage(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = ChatDatabaseContract.MessageEntry._ID + " = ?";
        String[] selectionArgs = {String.valueOf(id)};
        db.delete(ChatDatabaseContract.MessageEntry.TABLE_NAME, selection, selectionArgs);
    }
}
