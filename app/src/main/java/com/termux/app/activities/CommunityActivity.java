package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.social.SocialStore;

import java.util.ArrayList;
import java.util.List;

public class CommunityActivity extends Activity {

    private List<String> users;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        setTitle(R.string.title_community);

        ListView listView = findViewById(R.id.community_users_list);
        users = SocialStore.getCommunityUsers();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> showUserActions(users.get(position)));

        refreshUsers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsers();
    }

    private void refreshUsers() {
        List<String> rows = new ArrayList<>();
        for (String user : users) {
            boolean followed = SocialStore.isFollowing(this, user);
            boolean blocked = SocialStore.isBlocked(this, user);
            int followers = SocialStore.getFollowerCountForUser(this, user);
            rows.add(user + " • " + followers + " followers" + (followed ? " • following" : "") + (blocked ? " • blocked" : ""));
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
    }

    private void showUserActions(String username) {
        boolean blocked = SocialStore.isBlocked(this, username);
        boolean following = SocialStore.isFollowing(this, username);

        String followText = following ? getString(R.string.action_unfollow_user) : getString(R.string.action_follow_user);
        String blockText = blocked ? getString(R.string.action_unblock_user) : getString(R.string.action_block_user);

        String[] actions = new String[] {
            followText,
            getString(R.string.action_send_message),
            blockText
        };

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_user_actions, username))
            .setItems(actions, (dialog, which) -> {
                if (which == 0) {
                    SocialStore.setFollowing(this, username, !following);
                    refreshUsers();
                } else if (which == 1) {
                    if (blocked) {
                        Toast.makeText(this, R.string.error_user_blocked_message, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(this, PrivateChatActivity.class);
                    intent.putExtra(PrivateChatActivity.EXTRA_USERNAME, username);
                    startActivity(intent);
                } else if (which == 2) {
                    SocialStore.setBlocked(this, username, !blocked);
                    refreshUsers();
                }
            })
            .show();
    }
}
