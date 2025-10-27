package edu.sjsu.android.cactus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<ChatSession> sessions;
    private OnSessionClickListener listener;
    private long selectedSessionId = -1;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public SessionAdapter(OnSessionClickListener listener) {
        this.sessions = new ArrayList<>();
        this.listener = listener;
    }

    public void setSessions(List<ChatSession> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    public void setSelectedSessionId(long sessionId) {
        this.selectedSessionId = sessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.titleText.setText(session.getTitle());
        
        // Format timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String dateStr = dateFormat.format(new Date(session.getTimestamp()));
        
        String subtitle = dateStr;
        if (session.getMessageCount() > 0) {
            subtitle += " • " + session.getMessageCount() + " messages";
        }
        holder.subtitleText.setText(subtitle);

        // Highlight selected session
        if (session.getId() == selectedSessionId) {
            holder.itemView.setBackgroundResource(R.drawable.selected_session_background);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.session_background);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView subtitleText;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.sessionTitle);
            subtitleText = itemView.findViewById(R.id.sessionSubtitle);
        }
    }
}
