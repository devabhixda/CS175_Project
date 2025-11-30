package edu.sjsu.android.cactus;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tool for setting alarms using the device's alarm app
 */
public class AlarmTool extends BaseTool {

    private final Context context;

    public AlarmTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "set_alarm";
    }

    @Override
    public String getDescription() {
        return "Set an alarm on the device. Specify the time (hour and minutes) and an optional message/label for the alarm.";
    }

    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("type", "object");

            JSONObject properties = new JSONObject();

            // hour parameter
            JSONObject hour = new JSONObject();
            hour.put("type", "integer");
            hour.put("description", "The hour for the alarm (0-23, 24-hour format)");
            hour.put("minimum", 0);
            hour.put("maximum", 23);
            properties.put("hour", hour);

            // minutes parameter
            JSONObject minutes = new JSONObject();
            minutes.put("type", "integer");
            minutes.put("description", "The minutes for the alarm (0-59)");
            minutes.put("minimum", 0);
            minutes.put("maximum", 59);
            properties.put("minutes", minutes);

            // message parameter (optional)
            JSONObject message = new JSONObject();
            message.put("type", "string");
            message.put("description", "Optional message/label for the alarm (e.g., 'Wake up', 'Meeting reminder')");
            properties.put("message", message);

            schema.put("properties", properties);

            JSONArray required = new JSONArray();
            required.put("hour");
            required.put("minutes");
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
            // Get parameters
            if (!arguments.has("hour") || !arguments.has("minutes")) {
                return new ToolResult("", "Both hour and minutes parameters are required");
            }

            int hour = arguments.getInt("hour");
            int minutes = arguments.getInt("minutes");
            String message = arguments.optString("message", "Alarm");

            // Validate hour and minutes
            if (hour < 0 || hour > 23) {
                return new ToolResult("", "Hour must be between 0 and 23");
            }
            if (minutes < 0 || minutes > 59) {
                return new ToolResult("", "Minutes must be between 0 and 59");
            }

            // Create intent to set alarm
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minutes);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false); // Show UI for user confirmation
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Start the alarm activity
            context.startActivity(intent);

            // Format time for display
            String formattedTime = String.format("%02d:%02d", hour, minutes);
            String amPm = hour < 12 ? "AM" : "PM";
            int displayHour = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour);
            String formattedTime12h = String.format("%d:%02d %s", displayHour, minutes, amPm);

            return new ToolResult("",
                String.format("Alarm set for %s (%s) with message: '%s'",
                    formattedTime12h, formattedTime, message),
                true);

        } catch (Exception e) {
            e.printStackTrace();
            return new ToolResult("", "Failed to set alarm: " + e.getMessage());
        }
    }
}
