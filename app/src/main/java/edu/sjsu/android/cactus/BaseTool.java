package edu.sjsu.android.cactus;

import org.json.JSONObject;

/**
 * Abstract base class for all tools
 */
public abstract class BaseTool {

    /**
     * Get the name of this tool
     */
    public abstract String getName();

    /**
     * Get the description of what this tool does
     */
    public abstract String getDescription();

    /**
     * Get the JSON schema for this tool's parameters
     */
    public abstract JSONObject getParametersSchema();

    /**
     * Execute the tool with the given arguments
     * @param arguments JSON object containing the tool arguments
     * @return The result of executing the tool
     */
    public abstract ToolResult execute(JSONObject arguments);

    /**
     * Get the complete function definition for OpenAI API
     */
    public JSONObject getFunctionDefinition() {
        try {
            JSONObject function = new JSONObject();
            function.put("type", "function");

            JSONObject functionDetails = new JSONObject();
            functionDetails.put("name", getName());
            functionDetails.put("description", getDescription());
            functionDetails.put("parameters", getParametersSchema());

            function.put("function", functionDetails);

            return function;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }
}
