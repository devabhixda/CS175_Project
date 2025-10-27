package edu.sjsu.android.cactus;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    // OpenAI API configuration (API key is stored in local.properties)
    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/responses";

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private RecyclerView messagesRecyclerView;
    private RecyclerView sessionsRecyclerView;
    private EditText messageInput;
    private ImageButton micButton;
    private ImageButton sendButton;
    private View emptyStateView;
    private MaterialButton newChatButton;

    private MessageAdapter messageAdapter;
    private SessionAdapter sessionAdapter;
    private LinearLayoutManager layoutManager;
    private LinearLayoutManager sessionsLayoutManager;
    private ChatDatabaseHelper dbHelper;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private ExecutorService executorService;

    private boolean isListening = false;
    private long currentSessionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Draw behind system bars and apply insets manually
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.activity_main_drawer);

    // Set up drawer layout
    drawerLayout = findViewById(R.id.drawerLayout);

    // Set up toolbar
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    // Set up drawer toggle
    drawerToggle = new ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.open_drawer,
            R.string.close_drawer
    );
    drawerLayout.addDrawerListener(drawerToggle);
    drawerToggle.syncState();

    View rootView = findViewById(R.id.main);
    View inputContainer = findViewById(R.id.inputContainer);

    final ViewGroup.MarginLayoutParams toolbarLayoutParams =
        (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
    final int defaultToolbarTopMargin = toolbarLayoutParams.topMargin;

    final ViewGroup.MarginLayoutParams inputLayoutParams =
        (ViewGroup.MarginLayoutParams) inputContainer.getLayoutParams();
    final int defaultInputBottomMargin = inputLayoutParams.bottomMargin;

    WindowInsetsControllerCompat insetsController =
        WindowCompat.getInsetsController(getWindow(), rootView);
    if (insetsController != null) {
        insetsController.show(WindowInsetsCompat.Type.systemBars());
        insetsController.setAppearanceLightStatusBars(true);
    }

    ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
        Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
        Insets navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
        boolean imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
        Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

        int bottomInset = navigationBars.bottom;
        if (imeVisible) {
        bottomInset = Math.max(bottomInset, imeInsets.bottom);
        }

        toolbarLayoutParams.topMargin = defaultToolbarTopMargin + statusBars.top;
        toolbar.setLayoutParams(toolbarLayoutParams);

        inputLayoutParams.bottomMargin = defaultInputBottomMargin + bottomInset;
        inputContainer.setLayoutParams(inputLayoutParams);

        return windowInsets;
    });

        // Initialize executor service for background tasks
        executorService = Executors.newSingleThreadExecutor();

        // Initialize database
        dbHelper = new ChatDatabaseHelper(this);

        // Initialize UI components
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        micButton = findViewById(R.id.micButton);
        sendButton = findViewById(R.id.sendButton);
        emptyStateView = findViewById(R.id.emptyStateView);
        newChatButton = findViewById(R.id.newChatButton);
        sessionsRecyclerView = findViewById(R.id.sessionsRecyclerView);

        // Set up RecyclerView for messages
        messageAdapter = new MessageAdapter();
        layoutManager = new LinearLayoutManager(this);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);

        // Set up RecyclerView for sessions
        sessionAdapter = new SessionAdapter(this::onSessionClick);
        sessionsLayoutManager = new LinearLayoutManager(this);
        sessionsRecyclerView.setLayoutManager(sessionsLayoutManager);
        sessionsRecyclerView.setAdapter(sessionAdapter);

        // Load sessions and create a new session if none exist
        loadSessions();

        // Update empty state visibility
        updateEmptyState();

        // Set up new chat button
        newChatButton.setOnClickListener(v -> createNewSession());

        // Set up button listeners
        micButton.setOnClickListener(v -> handleMicButtonClick());
        sendButton.setOnClickListener(v -> handleSendButtonClick());

        // Set up text input listener
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handleSendButtonClick();
                return true;
            }
            return false;
        });

        // Check and request permissions
        checkPermissions();

        // Initialize Speech Recognizer
        initializeSpeechRecognizer();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void loadChatHistory() {
        if (currentSessionId == -1) {
            return;
        }

        executorService.execute(() -> {
            List<Message> messages = dbHelper.getMessagesForSession(currentSessionId);
            runOnUiThread(() -> {
                if (!messages.isEmpty()) {
                    messageAdapter.loadMessages(messages);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                }
                updateEmptyState();
            });
        });
    }

    private void loadSessions() {
        executorService.execute(() -> {
            // Clean up empty sessions before loading
            List<ChatSession> allSessions = dbHelper.getAllSessions();
            for (ChatSession session : allSessions) {
                if (session.getMessageCount() == 0) {
                    dbHelper.deleteSession(session.getId());
                }
            }
            
            // Reload sessions after cleanup
            List<ChatSession> sessions = dbHelper.getAllSessions();
            runOnUiThread(() -> {
                sessionAdapter.setSessions(sessions);
                // Always start with no active session (new chat)
                // Session will be created when user sends first message
            });
        });
    }

    private void createNewSession() {
        // Clear current messages without creating a session yet
        currentSessionId = -1;
        messageAdapter.clearAll();
        updateEmptyState();
        sessionAdapter.setSelectedSessionId(-1);
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void onSessionClick(ChatSession session) {
        currentSessionId = session.getId();
        sessionAdapter.setSelectedSessionId(currentSessionId);
        
        // Clear current messages
        messageAdapter.clearAll();
        
        // Load messages for selected session
        loadChatHistory();
        
        // Close drawer
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void updateSessionTitle() {
        if (currentSessionId == -1 || messageAdapter.getItemCount() == 0) {
            return;
        }

        executorService.execute(() -> {
            List<Message> messages = messageAdapter.getMessages();
            
            // Find the first user message to use as title
            String title = "New Chat";
            for (Message msg : messages) {
                if (msg.isUser() && !msg.getContent().isEmpty()) {
                    title = msg.getContent();
                    if (title.length() > 30) {
                        title = title.substring(0, 30) + "...";
                    }
                    break;
                }
            }
            
            final String finalTitle = title;
            dbHelper.updateSessionTitle(currentSessionId, finalTitle);
            
            runOnUiThread(() -> loadSessions());
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    runOnUiThread(() -> messageInput.setHint(R.string.recording));
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    micButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    micButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                    runOnUiThread(() -> {
                        messageInput.setHint(R.string.message_hint);
                        Toast.makeText(MainActivity.this, R.string.error_speech_recognition, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String transcription = matches.get(0);
                        runOnUiThread(() -> {
                            messageInput.setText(transcription);
                            messageInput.setHint(R.string.message_hint);
                        });
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        } else {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show();
        }
    }

    private void handleMicButtonClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        isListening = true;
        micButton.setImageResource(android.R.drawable.ic_media_pause);
        messageInput.setText("");
        speechRecognizer.startListening(speechRecognizerIntent);
    }

    private void stopListening() {
        isListening = false;
        micButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        speechRecognizer.stopListening();
    }

    private void updateEmptyState() {
        if (messageAdapter.getItemCount() == 0) {
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    private void handleSendButtonClick() {
        String messageText = messageInput.getText().toString().trim();

        if (messageText.isEmpty()) {
            Toast.makeText(this, R.string.no_text_to_send, Toast.LENGTH_SHORT).show();
            return;
        }

        // If no session exists, create one now
        if (currentSessionId == -1) {
            executorService.execute(() -> {
                String title = "New Chat";
                long sessionId = dbHelper.createSession(title);
                
                runOnUiThread(() -> {
                    currentSessionId = sessionId;
                    sendMessage(messageText);
                    loadSessions(); // Refresh the sessions list
                });
            });
        } else {
            sendMessage(messageText);
        }
    }

    private void sendMessage(String messageText) {
        // Add user message to chat
        Message userMessage = new Message(messageText, true);
        userMessage.setSessionId(currentSessionId);
        messageAdapter.addMessage(userMessage);
        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
        updateEmptyState();

        // Save user message to database
        final boolean isFirstMessage = messageAdapter.getItemCount() == 1;
        executorService.execute(() -> {
            long id = dbHelper.insertMessage(userMessage);
            userMessage.setId(id);
            
            // Update session title with first user message
            if (isFirstMessage) {
                runOnUiThread(() -> updateSessionTitle());
            }
        });

        // Clear input and disable send button
        messageInput.setText("");
        sendButton.setEnabled(false);

        // Add "typing" indicator
        Message typingMessage = new Message("typing", false);
        messageAdapter.addMessage(typingMessage);
        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

        final String userMessageText = messageText;

        executorService.execute(() -> {
            try {
                String response = callOpenAI(userMessageText);

                runOnUiThread(() -> {
                    // Remove typing indicator
                    messageAdapter.removeLastMessage();

                    // Add agent response
                    Message agentMessage = new Message(response, false);
                    agentMessage.setSessionId(currentSessionId);
                    messageAdapter.addMessage(agentMessage);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

                    // Save agent message to database
                    executorService.execute(() -> {
                        long id = dbHelper.insertMessage(agentMessage);
                        agentMessage.setId(id);
                    });

                    sendButton.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    // Remove typing indicator
                    messageAdapter.removeLastMessage();

                    // Add error message
                    Message errorMessage = new Message(getString(R.string.error_api_call, e.getMessage()), false);
                    errorMessage.setSessionId(currentSessionId);
                    messageAdapter.addMessage(errorMessage);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

                    // Save error message to database
                    executorService.execute(() -> {
                        long id = dbHelper.insertMessage(errorMessage);
                        errorMessage.setId(id);
                    });

                    sendButton.setEnabled(true);
                });
            }
        });
    }

    private String callOpenAI(String userMessage) throws Exception {
        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        conn.setDoOutput(true);

        // Create JSON request body for Responses API
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-5-nano-2025-08-07");
        requestBody.put("instructions", "Always respond in a friendly and helpful tone. Keep your answers concise and to the point.");
        requestBody.put("input", userMessage);

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parse JSON response from Responses API
            String responseStr = response.toString();
            JSONObject jsonResponse = new JSONObject(responseStr);
            
            // The Responses API returns output array with message objects
            if (!jsonResponse.has("output")) {
                throw new Exception("Response missing 'output' field");
            }
            
            JSONArray outputArray = jsonResponse.getJSONArray("output");
            if (outputArray.length() == 0) {
                throw new Exception("Empty output array");
            }
            
            // Find the message type in the output array (skip reasoning)
            for (int i = 0; i < outputArray.length(); i++) {
                JSONObject outputItem = outputArray.getJSONObject(i);
                String outputType = outputItem.optString("type", "");
                
                // Look for type "message"
                if ("message".equals(outputType)) {
                    // Get the content array from the message
                    if (!outputItem.has("content")) {
                        continue;
                    }
                    
                    JSONArray contentArray = outputItem.getJSONArray("content");
                    if (contentArray.length() == 0) {
                        continue;
                    }
                    
                    // Find output_text in content array
                    for (int j = 0; j < contentArray.length(); j++) {
                        JSONObject contentItem = contentArray.getJSONObject(j);
                        String contentType = contentItem.optString("type", "");
                        
                        if ("output_text".equals(contentType) && contentItem.has("text")) {
                            return contentItem.getString("text");
                        }
                    }
                }
            }
            
            throw new Exception("No message content found in response");
        } else {
            // Read error response
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String errorLine;

            while ((errorLine = errorReader.readLine()) != null) {
                errorResponse.append(errorLine);
            }
            errorReader.close();

            String errorMessage = "HTTP " + responseCode + ": " + errorResponse.toString();
            System.out.println(errorMessage);
            throw new Exception(errorMessage);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        if (dbHelper != null) {
            dbHelper.close();
        }

        executorService.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}