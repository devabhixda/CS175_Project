package edu.sjsu.android.cactus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AGENT = 2;
    private static final int VIEW_TYPE_TYPING = 3;

    private List<Message> messages;

    public MessageAdapter() {
        this.messages = new ArrayList<>();
    }

    public void loadMessages(List<Message> messages) {
        this.messages.clear();
        this.messages.addAll(messages);
        notifyDataSetChanged();
    }

    public List<Message> getMessages() {
        return messages;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if (message.isUser()) {
            return VIEW_TYPE_USER;
        } else if (message.getContent().equals("typing")) {
            return VIEW_TYPE_TYPING;
        } else {
            return VIEW_TYPE_AGENT;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_USER) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_user, parent, false);
        } else if (viewType == VIEW_TYPE_TYPING) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_typing, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_agent, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder.messageText != null && !message.getContent().equals("typing")) {
            holder.messageText.setText(message.getContent());
        }

        // Start animation for typing indicator
        if (message.getContent().equals("typing") && holder.itemView != null) {
            View dot1 = holder.itemView.findViewById(R.id.dot1);
            View dot2 = holder.itemView.findViewById(R.id.dot2);
            View dot3 = holder.itemView.findViewById(R.id.dot3);

            if (dot1 != null && dot2 != null && dot3 != null) {
                animateTypingDot(dot1, 0);
                animateTypingDot(dot2, 200);
                animateTypingDot(dot3, 400);
            }
        }
    }

    private void animateTypingDot(View dot, long delay) {
        dot.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(600)
            .setStartDelay(delay)
            .withEndAction(() -> {
                dot.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(600)
                    .withEndAction(() -> animateTypingDot(dot, 0))
                    .start();
            })
            .start();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            int lastPosition = messages.size() - 1;
            messages.remove(lastPosition);
            notifyItemRemoved(lastPosition);
        }
    }

    public void clearAll() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }
}