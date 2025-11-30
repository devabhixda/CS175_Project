package edu.sjsu.android.cactus;

import org.json.JSONObject;

/**
 * Represents a function/tool call request from the LLM
 */
public class ToolCall {
    private String id;
    private String name;
    private JSONObject arguments;

    public ToolCall(String id, String name, JSONObject arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JSONObject getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ToolCall{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
