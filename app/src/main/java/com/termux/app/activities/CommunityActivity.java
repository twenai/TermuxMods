package com.termux.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            toast(getString(R.string.community_login_required));
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
            return;
        }

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
                ImageView avatar = view.findViewById(R.id.community_item_avatar);
                TextView user = view.findViewById(R.id.community_item_user);
                TextView body = view.findViewById(R.id.community_item_body);
                TextView meta = view.findViewById(R.id.community_item_meta);
                if (item != null) {
                    user.setText(item.username);
                    body.setText(item.message);
                    meta.setText(getString(R.string.community_meta_format, item.termuxId, item.createdAt));
                    loadAvatarInto(avatar, item.avatarUrl);
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
                String token = prefs.getString(KEY_ACCESS_TOKEN, null);
                JSONArray array;
                try {
                    array = requestArray("GET", "/rest/v1/community_messages?select=id,username,message,termux_id,avatar_url,created_at&order=created_at.desc&limit=100", null, token);
                } catch (Exception primaryError) {
                    array = requestArray("GET", "/rest/v1/community_messages?select=id,username,message,termux_id,created_at&order=created_at.desc&limit=100", null, token);
                }
                List<ChatMessage> fresh = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj == null) continue;
                    ChatMessage m = new ChatMessage();
                    m.id = obj.optString("id");
                    m.username = obj.optString("username", "user");
                    m.message = obj.optString("message", "");
                    m.termuxId = obj.optString("termux_id", "TMX-LOCAL");
                    m.avatarUrl = obj.optString("avatar_url", "");
                    m.createdAt = formatCommunityDate(obj.optString("created_at", "-"));
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

        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            toast(getString(R.string.community_login_required));
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
            return;
        }

        input.setEnabled(false);
        findViewById(R.id.community_btn_send).setEnabled(false);

        new Thread(() -> {
            try {
                JSONObject user = requestObject("GET", "/auth/v1/user", null, token);
                String username = "user";
                String termuxId = "TMX-LOCAL";
                String avatarUrl = "";

                String email = user.optString("email", "user");
                JSONObject metadata = user.optJSONObject("user_metadata");
                if (metadata != null) {
                    username = metadata.optString("username", "");
                    termuxId = metadata.optString("termux_id", "");
                    avatarUrl = metadata.optString("avatar_url", "");
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

                String userId = user.optString("id", "");
                if (TextUtils.isEmpty(userId)) {
                    throw new IllegalStateException("Missing user id");
                }

                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("username", username);
                payload.put("message", text);
                payload.put("termux_id", termuxId);
                payload.put("avatar_url", avatarUrl);
                requestArray("POST", "/rest/v1/community_messages", payload, token);
                pruneMessagesOlderThan30Minutes(token);

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

    private void pruneMessagesOlderThan30Minutes(String token) {
        try {
            long cutoff = System.currentTimeMillis() - (30L * 60L * 1000L);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String cutoffString = iso.format(new Date(cutoff));
            String encoded = URLEncoder.encode(cutoffString, "UTF-8");
            requestArray("DELETE", "/rest/v1/community_messages?created_at=lt." + encoded, null, token);
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


    private String formatCommunityDate(String raw) {
        if (TextUtils.isEmpty(raw) || "-".equals(raw)) return "-";

        String[] patterns = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                Date parsed = parser.parse(raw);
                if (parsed != null) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(parsed);
                }
            } catch (ParseException ignored) {
            }
        }

        return raw;
    }

    private void loadAvatarInto(ImageView avatarView, String avatarUrl) {
        avatarView.setImageResource(R.drawable.ic_profile);
        avatarView.setTag(avatarUrl);
        if (TextUtils.isEmpty(avatarUrl)) return;

        new Thread(() -> {
            try {
                URL url = new URL(avatarUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            Object currentTag = avatarView.getTag();
                            if (currentTag != null && currentTag.equals(avatarUrl)) {
                                avatarView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class ChatMessage {
        String id;
        String username;
        String message;
        String termuxId;
        String avatarUrl;
        String createdAt;
    }
}
