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
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    // Tools
    private final List<BaseTool> availableTools = new ArrayList<>();

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

    // Pending tool calls storage
    private JSONArray pendingMessages;
    private JSONObject pendingAssistantMessage;
    private JSONArray pendingToolCalls;
    private StringBuilder toolResults;

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

        // Initialize tools
        initializeTools();

        // Set up back press handling
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

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

        // Set up tool confirmation listener
        messageAdapter.setToolConfirmationListener(new MessageAdapter.ToolConfirmationListener() {
            @Override
            public void onToolConfirmed(Message message, int position) {
                handleToolConfirmation(message, position, true);
            }

            @Override
            public void onToolRejected(Message message, int position) {
                handleToolConfirmation(message, position, false);
            }
        });

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
                    android.util.Log.d("CACTUS_API", "sendMessage callback - response: '" + response + "'");
                    // If response is empty, tool confirmation is already showing
                    // The typing indicator was already removed in handleToolCalls
                    // Don't remove anything else or we'll delete the confirmation message!
                    if (response == null || response.trim().isEmpty()) {
                        android.util.Log.d("CACTUS_API", "Response is empty - tool confirmation should be showing, not removing anything");
                        return;
                    }

                    android.util.Log.d("CACTUS_API", "Response has content, adding agent message");
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

    /**
     * Initialize available tools
     */
    private void initializeTools() {
        availableTools.add(new AlarmTool(this));
        availableTools.add(new PhoneCallTool(this));
        android.util.Log.d("CACTUS_API", "Initialized " + availableTools.size() + " tools");
    }

    /**
     * Build conversation history for the API
     */
    private JSONArray buildConversationHistory() throws Exception {
        JSONArray messages = new JSONArray();

        // Add system message
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a helpful assistant. Always respond in a friendly and helpful tone. Keep your answers concise and to the point. You have access to tools for setting alarms and making phone calls on the device. After successfully using a tool, provide a brief confirmation of what action was completed.");
        messages.put(systemMessage);

        // Add conversation history from the current session
        List<Message> history = messageAdapter.getMessages();
        for (Message msg : history) {
            // Skip typing indicators and tool messages
            if (msg.getContent().equals("typing") || msg.getMessageType() == Message.TYPE_TOOL) {
                continue;
            }

            // Skip messages with null or empty content
            String content = msg.getContent();
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            JSONObject messageObj = new JSONObject();
            messageObj.put("role", msg.isUser() ? "user" : "assistant");
            messageObj.put("content", content);
            messages.put(messageObj);
        }

        return messages;
    }

    /**
     * Call OpenAI Chat Completions API with function calling
     */
    private String callOpenAI(String userMessage) throws Exception {
        JSONArray messages = buildConversationHistory();

        // Make initial API call
        JSONObject response = callChatCompletionsAPI(messages);

        // Check if response contains tool calls
        JSONArray choices = response.getJSONArray("choices");
        if (choices.length() == 0) {
            throw new Exception("No choices in response");
        }

        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");

        // Check for tool calls
        if (message.has("tool_calls")) {
            JSONArray toolCalls = message.getJSONArray("tool_calls");
            android.util.Log.d("CACTUS_API", "Tool calls detected: " + toolCalls.toString());
            return handleToolCalls(messages, message, toolCalls);
        }

        // No tool calls, return the response directly
        String content = message.optString("content", "");
        android.util.Log.d("CACTUS_API", "No tool calls. Response content: " + content);
        return content;
    }

    /**
     * Handle tool calls and get final response
     */
    private String handleToolCalls(JSONArray messages, JSONObject assistantMessage, JSONArray toolCalls) throws Exception {
        android.util.Log.d("CACTUS_API", "handleToolCalls called with " + toolCalls.length() + " tool calls");

        // Store pending tool calls for confirmation
        pendingMessages = messages;
        pendingAssistantMessage = assistantMessage;
        pendingToolCalls = toolCalls;

        // Show confirmation UI for the first tool call
        JSONObject toolCallObj = toolCalls.getJSONObject(0);
        JSONObject function = toolCallObj.getJSONObject("function");
        String functionName = function.getString("name");
        JSONObject arguments = new JSONObject(function.getString("arguments"));

        android.util.Log.d("CACTUS_API", "Tool call: " + functionName + " with args: " + arguments.toString());

        // Format the confirmation message
        final String confirmationText = formatToolConfirmation(functionName, arguments);
        android.util.Log.d("CACTUS_API", "Confirmation text: " + confirmationText);

        runOnUiThread(() -> {
            android.util.Log.d("CACTUS_API", "Removing typing indicator and showing confirmation UI");
            // Remove typing indicator first
            messageAdapter.removeLastMessage();

            // Add tool confirmation message
            Message confirmMessage = new Message(confirmationText, Message.TYPE_TOOL_CONFIRMATION);
            confirmMessage.setSessionId(currentSessionId);
            confirmMessage.setToolCallData(toolCallObj);
            messageAdapter.addMessage(confirmMessage);
            messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
            android.util.Log.d("CACTUS_API", "Tool confirmation UI added to adapter");
        });

        // Return empty string to indicate we're waiting for confirmation
        // The actual response will be handled in handleToolConfirmation
        android.util.Log.d("CACTUS_API", "Returning empty string from handleToolCalls");
        return "";
    }

    /**
     * Format tool call information for confirmation message
     */
    private String formatToolConfirmation(String functionName, JSONObject arguments) {
        try {
            switch (functionName) {
                case "set_alarm":
                    int hour = arguments.getInt("hour");
                    int minutes = arguments.getInt("minutes");
                    String message = arguments.optString("message", "");

                    // Convert 24h to 12h format
                    String amPm = hour < 12 ? "AM" : "PM";
                    int displayHour = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour);
                    String timeStr = String.format("%d:%02d %s", displayHour, minutes, amPm);

                    if (!message.isEmpty() && !message.equals("Alarm")) {
                        return String.format("Set an alarm for %s\nLabel: %s", timeStr, message);
                    } else {
                        return String.format("Set an alarm for %s", timeStr);
                    }

                case "make_call":
                    String phoneNumber = arguments.getString("phone_number");
                    return String.format("Call %s", phoneNumber);

                default:
                    // Fallback for unknown tools
                    return String.format("Use %s tool", functionName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Execute action";
        }
    }

    /**
     * Handle tool confirmation (Yes/No button click)
     */
    private void handleToolConfirmation(Message message, int position, boolean confirmed) {
        android.util.Log.d("CACTUS_API", "handleToolConfirmation called - confirmed: " + confirmed);

        // Remove the confirmation message
        messageAdapter.removeLastMessage();

        if (!confirmed) {
            android.util.Log.d("CACTUS_API", "User rejected tool call");
            // User rejected the tool call
            Message rejectionMessage = new Message("Tool call cancelled.", false);
            rejectionMessage.setSessionId(currentSessionId);
            messageAdapter.addMessage(rejectionMessage);
            messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

            // Save rejection message to database
            executorService.execute(() -> {
                long id = dbHelper.insertMessage(rejectionMessage);
                rejectionMessage.setId(id);
            });

            sendButton.setEnabled(true);
            return;
        }

        android.util.Log.d("CACTUS_API", "User confirmed tool call - starting execution");

        // User confirmed - execute the tool calls
        executorService.execute(() -> {
            try {
                // Initialize tool results storage
                toolResults = new StringBuilder();

                // Add assistant message with tool calls to history
                pendingMessages.put(pendingAssistantMessage);

                // Execute each tool call
                for (int i = 0; i < pendingToolCalls.length(); i++) {
                    JSONObject toolCallObj = pendingToolCalls.getJSONObject(i);
                    String toolCallId = toolCallObj.getString("id");
                    JSONObject function = toolCallObj.getJSONObject("function");
                    String functionName = function.getString("name");
                    JSONObject arguments = new JSONObject(function.getString("arguments"));

                    // Show tool execution in UI
                    final String toolName = functionName;
                    runOnUiThread(() -> {
                        Message toolMessage = new Message("🔧 Using " + toolName + "...", Message.TYPE_TOOL);
                        toolMessage.setSessionId(currentSessionId);
                        messageAdapter.addMessage(toolMessage);
                        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                    });

                    // Execute the tool
                    ToolResult toolResult = executeTool(functionName, arguments);

                    // Get the content (result or error message)
                    String toolContent = toolResult.isSuccess()
                        ? toolResult.getResult()
                        : toolResult.getError();

                    // Ensure content is never null
                    if (toolContent == null || toolContent.trim().isEmpty()) {
                        toolContent = "Tool execution completed with no output.";
                    }

                    // Store tool result for fallback display
                    if (toolResults.length() > 0) {
                        toolResults.append("\n");
                    }
                    toolResults.append(toolContent);

                    // Add tool result to messages
                    JSONObject toolResultMessage = new JSONObject();
                    toolResultMessage.put("role", "tool");
                    toolResultMessage.put("tool_call_id", toolCallId);
                    toolResultMessage.put("content", toolContent);
                    pendingMessages.put(toolResultMessage);

                    // Remove tool execution message from UI
                    runOnUiThread(() -> {
                        messageAdapter.removeLastMessage();
                    });
                }

                // Make another API call with tool results
                JSONObject finalResponse = callChatCompletionsAPI(pendingMessages);
                JSONArray choices = finalResponse.getJSONArray("choices");
                if (choices.length() == 0) {
                    throw new Exception("No choices in final response");
                }

                JSONObject choice = choices.getJSONObject(0);
                JSONObject responseMessage = choice.getJSONObject("message");
                String responseText = responseMessage.optString("content", "");

                // If OpenAI returns empty, use the tool results directly
                if (responseText == null || responseText.trim().isEmpty()) {
                    responseText = toolResults.length() > 0 ? toolResults.toString() : "I completed the action.";
                }

                String finalResponseText = responseText;
                runOnUiThread(() -> {
                    // Add agent response
                    Message agentMessage = new Message(finalResponseText, false);
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

    /**
     * Execute a tool by name
     */
    private ToolResult executeTool(String toolName, JSONObject arguments) {
        for (BaseTool tool : availableTools) {
            if (tool.getName().equals(toolName)) {
                return tool.execute(arguments);
            }
        }
        return new ToolResult("", "Unknown tool: " + toolName);
    }

    /**
     * Call OpenAI Chat Completions API
     */
    private JSONObject callChatCompletionsAPI(JSONArray messages) throws Exception {
        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        // Create JSON request body for Chat Completions API
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("messages", messages);

        // Add function definitions
        JSONArray tools = new JSONArray();
        for (BaseTool tool : availableTools) {
            tools.put(tool.getFunctionDefinition());
        }
        requestBody.put("tools", tools);
        requestBody.put("tool_choice", "auto");

        // Log request for debugging
        android.util.Log.d("CACTUS_API", "Request: " + requestBody.toString());

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

            // Log response for debugging
            android.util.Log.d("CACTUS_API", "Response: " + response.toString());

            return new JSONObject(response.toString());
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

}