package edu.sjsu.android.cactus;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Manages secure storage and retrieval of the OpenAI API key.
 * Uses EncryptedSharedPreferences for secure storage on device.
 */
public class ApiKeyManager {

    private static final String PREFS_FILE = "secure_api_prefs";
    private static final String KEY_API_KEY = "openai_api_key";

    private final SharedPreferences sharedPreferences;

    public ApiKeyManager(Context context) {
        sharedPreferences = createEncryptedPrefs(context);
    }

    private SharedPreferences createEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to regular SharedPreferences if encryption fails
            // This should rarely happen on modern devices
            android.util.Log.e("ApiKeyManager", "Failed to create encrypted prefs, using regular prefs", e);
            return context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    /**
     * Save the API key securely.
     */
    public void saveApiKey(String apiKey) {
        sharedPreferences.edit()
                .putString(KEY_API_KEY, apiKey)
                .apply();
    }

    /**
     * Retrieve the stored API key.
     * @return The API key, or null if not set.
     */
    public String getApiKey() {
        return sharedPreferences.getString(KEY_API_KEY, null);
    }

    /**
     * Check if an API key has been set.
     */
    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }

    /**
     * Clear the stored API key.
     */
    public void clearApiKey() {
        sharedPreferences.edit()
                .remove(KEY_API_KEY)
                .apply();
    }

    /**
     * Validate that an API key looks correct (basic format check).
     */
    public static boolean isValidApiKeyFormat(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        // OpenAI keys typically start with "sk-" and are fairly long
        String trimmed = apiKey.trim();
        return trimmed.startsWith("sk-") && trimmed.length() > 20;
    }
}