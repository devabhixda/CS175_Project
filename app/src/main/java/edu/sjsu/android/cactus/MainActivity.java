package edu.sjsu.android.cactus;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    // OpenAI API configuration (API key is stored in local.properties)
    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private ImageButton micButton;
    private ImageButton sendButton;
    private View emptyStateView;

    private MessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private ExecutorService executorService;

    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Draw behind system bars and apply insets manually
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.activity_main);

    // Set up toolbar
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

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

        // Initialize UI components
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        micButton = findViewById(R.id.micButton);
        sendButton = findViewById(R.id.sendButton);
        emptyStateView = findViewById(R.id.emptyStateView);

        // Set up RecyclerView
        messageAdapter = new MessageAdapter();
        layoutManager = new LinearLayoutManager(this);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);

        // Update empty state visibility
        updateEmptyState();

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
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_all) {
            messageAdapter.clearAll();
            updateEmptyState();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleSendButtonClick() {
        String messageText = messageInput.getText().toString().trim();

        if (messageText.isEmpty()) {
            Toast.makeText(this, R.string.no_text_to_send, Toast.LENGTH_SHORT).show();
            return;
        }

        // Add user message to chat
        Message userMessage = new Message(messageText, true);
        messageAdapter.addMessage(userMessage);
        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
        updateEmptyState();

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
                    messageAdapter.addMessage(agentMessage);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

                    sendButton.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    // Remove typing indicator
                    messageAdapter.removeLastMessage();

                    // Add error message
                    Message errorMessage = new Message(getString(R.string.error_api_call, e.getMessage()), false);
                    messageAdapter.addMessage(errorMessage);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

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

        // Create JSON request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-5-nano-2025-08-07");

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", userMessage);
        messages.put(message);

        requestBody.put("messages", messages);
        requestBody.put("max_completion_tokens", 500);

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

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject messageObj = firstChoice.getJSONObject("message");
            return messageObj.getString("content");
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

        executorService.shutdown();
    }
}