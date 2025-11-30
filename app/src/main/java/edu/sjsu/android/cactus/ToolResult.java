package edu.sjsu.android.cactus;

/**
 * Represents the result of executing a tool
 */
public class ToolResult {
    private String toolCallId;
    private String result;
    private boolean success;
    private String error;

    public ToolResult(String toolCallId, String result, boolean success) {
        this.toolCallId = toolCallId;
        this.result = result;
        this.success = success;
        this.error = null;
    }

    public ToolResult(String toolCallId, String error) {
        this.toolCallId = toolCallId;
        this.result = null;
        this.success = false;
        this.error = error;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ToolResult{" +
                "toolCallId='" + toolCallId + '\'' +
                ", result='" + result + '\'' +
                ", success=" + success +
                ", error='" + error + '\'' +
                '}';
    }
}
