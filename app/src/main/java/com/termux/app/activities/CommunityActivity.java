package com.termux.app.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.BuildConfig;
import com.termux.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommunityActivity extends Activity {

    private static final String PREFS_NAME = "secure_supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final int POLL_INTERVAL_MS = 4000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ChatMessage> messages = new ArrayList<>();
    private ArrayAdapter<ChatMessage> adapter;
    private SharedPreferences prefs;

    private EditText input;
    private ListView listView;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        findViewById(R.id.community_btn_back).setOnClickListener(v -> finish());
        input = findViewById(R.id.community_input);
        listView = findViewById(R.id.community_list);
        findViewById(R.id.community_btn_send).setOnClickListener(v -> sendMessage());

        adapter = new ArrayAdapter<ChatMessage>(this, R.layout.community_list_item, messages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.community_list_item, parent, false);
                }
                ChatMessage item = getItem(position);
                TextView user = view.findViewById(R.id.community_item_user);
                TextView body = view.findViewById(R.id.community_item_body);
                TextView meta = view.findViewById(R.id.community_item_meta);
                if (item != null) {
                    user.setText(item.username);
                    body.setText(item.message);
                    meta.setText(item.termuxId);
                }
                return view;
            }
        };

        listView.setAdapter(adapter);
        fetchMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        destroyed = false;
        scheduleRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleRefresh() {
        handler.postDelayed(() -> {
            if (!destroyed) {
                fetchMessages();
                scheduleRefresh();
            }
        }, POLL_INTERVAL_MS);
    }

    private void fetchMessages() {
        new Thread(() -> {
            try {
                JSONArray array = requestArray("GET", "/rest/v1/community_messages?select=id,username,message,termux_id,created_at&order=created_at.desc&limit=80", null, null);
                List<ChatMessage> fresh = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj == null) continue;
                    ChatMessage m = new ChatMessage();
                    m.id = obj.optString("id");
                    m.username = obj.optString("username", "user");
                    m.message = obj.optString("message", "");
                    m.termuxId = obj.optString("termux_id", "TMX-LOCAL");
                    fresh.add(m);
                }
                Collections.reverse(fresh);
                runOnUiThread(() -> {
                    messages.clear();
                    messages.addAll(fresh);
                    adapter.notifyDataSetChanged();
                    if (!messages.isEmpty()) listView.setSelection(messages.size() - 1);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(getString(R.string.community_error_load)));
            }
        }).start();
    }

    private void sendMessage() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) return;

        input.setEnabled(false);
        findViewById(R.id.community_btn_send).setEnabled(false);

        new Thread(() -> {
            try {
                String token = prefs.getString(KEY_ACCESS_TOKEN, null);
                JSONObject user = null;
                String username = "user";
                String termuxId = "TMX-LOCAL";
                if (!TextUtils.isEmpty(token)) {
                    user = requestObject("GET", "/auth/v1/user", null, token);
                }
                if (user != null) {
                    String email = user.optString("email", "user");
                    JSONObject metadata = user.optJSONObject("user_metadata");
                    if (metadata != null) {
                        username = metadata.optString("username", "");
                        termuxId = metadata.optString("termux_id", "");
                    }
                    if (TextUtils.isEmpty(username)) {
                        int idx = email.indexOf('@');
                        username = idx > 0 ? email.substring(0, idx) : email;
                    }
                    if (TextUtils.isEmpty(termuxId)) {
                        String compact = user.optString("id", "LOCAL").replaceAll("[^A-Za-z0-9]", "");
                        if (compact.length() > 6) compact = compact.substring(0, 6);
                        if (compact.isEmpty()) compact = "LOCAL";
                        termuxId = "TMX-" + compact.toUpperCase();
                    }
                }

                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("message", text);
                payload.put("termux_id", termuxId);
                requestArray("POST", "/rest/v1/community_messages", payload, token);
                pruneOldMessages();

                runOnUiThread(() -> {
                    input.setText("");
                    fetchMessages();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(getString(R.string.community_error_send)));
            } finally {
                runOnUiThread(() -> {
                    input.setEnabled(true);
                    findViewById(R.id.community_btn_send).setEnabled(true);
                });
            }
        }).start();
    }

    private void pruneOldMessages() {
        try {
            JSONArray old = requestArray("GET", "/rest/v1/community_messages?select=id&order=created_at.desc&offset=100", null, null);
            for (int i = 0; i < old.length(); i++) {
                JSONObject obj = old.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("id", "");
                if (!TextUtils.isEmpty(id)) {
                    requestArray("DELETE", "/rest/v1/community_messages?id=eq." + id, null, null);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private JSONObject requestObject(String method, String endpoint, JSONObject payload, String bearerToken) throws Exception {
        String body = request(method, endpoint, payload, bearerToken);
        if (TextUtils.isEmpty(body)) return new JSONObject();
        return new JSONObject(body);
    }

    private JSONArray requestArray(String method, String endpoint, JSONObject payload, String bearerToken) throws Exception {
        String body = request(method, endpoint, payload, bearerToken);
        if (TextUtils.isEmpty(body)) return new JSONArray();
        if (body.startsWith("{")) {
            JSONArray array = new JSONArray();
            array.put(new JSONObject(body));
            return array;
        }
        return new JSONArray(body);
    }

    private String request(String method, String endpoint, JSONObject payload, String bearerToken) throws Exception {
        if (TextUtils.isEmpty(BuildConfig.SUPABASE_URL) || TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY)) {
            throw new IllegalStateException("Missing Supabase config");
        }
        URL url = new URL(BuildConfig.SUPABASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
        String tokenHeader = TextUtils.isEmpty(bearerToken) ? BuildConfig.SUPABASE_ANON_KEY : bearerToken;
        connection.setRequestProperty("Authorization", "Bearer " + tokenHeader);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoInput(true);
        if (payload != null) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        try {
            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " " + response);
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class ChatMessage {
        String id;
        String username;
        String message;
        String termuxId;
    }
}
