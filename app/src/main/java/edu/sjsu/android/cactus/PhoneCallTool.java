package edu.sjsu.android.cactus;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tool for opening the phone dialer to make calls
 */
public class PhoneCallTool extends BaseTool {

    private final Context context;

    public PhoneCallTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "make_call";
    }

    @Override
    public String getDescription() {
        return "Open the phone dialer with a pre-filled phone number. The user can review and confirm the call.";
    }

    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("type", "object");

            JSONObject properties = new JSONObject();

            // phone_number parameter
            JSONObject phoneNumber = new JSONObject();
            phoneNumber.put("type", "string");
            phoneNumber.put("description", "The phone number to dial (e.g., '1234567890', '+1-555-123-4567'). Can include country code and formatting.");
            properties.put("phone_number", phoneNumber);

            schema.put("properties", properties);

            JSONArray required = new JSONArray();
            required.put("phone_number");
            schema.put("required", required);

            return schema;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    @Override
    public ToolResult execute(JSONObject arguments) {
        try {
            String phoneNumber = arguments.optString("phone_number", "");

            if (phoneNumber.isEmpty()) {
                return new ToolResult("", "Phone number parameter is required");
            }

            // Clean up the phone number (remove spaces, dashes, but keep + for country code)
            String cleanedNumber = phoneNumber.replaceAll("[^0-9+]", "");

            if (cleanedNumber.isEmpty()) {
                return new ToolResult("", "Invalid phone number format");
            }

            // Create intent to dial the number
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + cleanedNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Start the dialer activity
            context.startActivity(intent);

            return new ToolResult("",
                String.format("Opening dialer with phone number: %s", phoneNumber),
                true);

        } catch (Exception e) {
            e.printStackTrace();
            return new ToolResult("", "Failed to open dialer: " + e.getMessage());
        }
    }
}
