package com.example.agrismart.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.databinding.ItemChatBotBinding;
import com.example.agrismart.databinding.ItemChatUserBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur de la conversation : bulles de l'utilisateur (droite) et de l'assistant (gauche).
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;

    /** Une bulle de la conversation. */
    public static class Line {
        final String text;
        final boolean fromUser;

        public Line(String text, boolean fromUser) {
            this.text = text;
            this.fromUser = fromUser;
        }
    }

    private final List<Line> lines = new ArrayList<>();

    public void setLines(List<Line> newLines) {
        lines.clear();
        lines.addAll(newLines);
        notifyDataSetChanged();
    }

    public void addLine(Line line) {
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return lines.get(position).fromUser ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            ItemChatUserBinding user = ItemChatUserBinding.inflate(inflater, parent, false);
            return new ChatViewHolder(user.getRoot(), user.tvText);
        }
        ItemChatBotBinding bot = ItemChatBotBinding.inflate(inflater, parent, false);
        return new ChatViewHolder(bot.getRoot(), bot.tvText);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.text.setText(lines.get(position).text);
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        ChatViewHolder(@NonNull android.view.View root, TextView text) {
            super(root);
            this.text = text;
        }
    }
}
