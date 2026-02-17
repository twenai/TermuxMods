package com.termux.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.social.SocialStore;

public class PrivateChatActivity extends Activity {

    public static final String EXTRA_USERNAME = "username";

    private String username;
    private TextView chatHistoryView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username == null || username.isEmpty()) username = "unknown";

        setTitle(getString(R.string.title_private_chat, username));

        chatHistoryView = findViewById(R.id.private_chat_history);
        EditText input = findViewById(R.id.private_chat_input);
        Button sendButton = findViewById(R.id.private_chat_send_button);

        sendButton.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.error_empty_message, Toast.LENGTH_SHORT).show();
                return;
            }

            SocialStore.appendMessage(this, username, "You: " + text);
            SocialStore.appendMessage(this, username, username + ": " + getString(R.string.message_sent_ack));
            input.setText("");
            refreshHistory();
        });

        refreshHistory();
    }

    private void refreshHistory() {
        String history = SocialStore.getConversation(this, username);
        if (history.isEmpty()) history = getString(R.string.label_no_messages);
        chatHistoryView.setText(history);
    }
}
