package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ProfileActivity extends Activity {

    private static final String PREFS_NAME = "secure_supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static final String SUPABASE_URL = "https://qahpieeevipnklizihel.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFhaHBpZWVldmlwbmtsaXppaGVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEwODIwMzQsImV4cCI6MjA4NjY1ODAzNH0._ArXv7jnn3-9oaV5WsH-vzPjqRgp9pgWVnzLEyuiCxw";

    private EditText emailInput;
    private EditText passwordInput;
    private EditText usernameInput;
    private EditText avatarInput;
    private TextView authStatus;
    private TextView roleStatus;
    private TextView usernamePreview;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        emailInput = findViewById(R.id.profile_email);
        passwordInput = findViewById(R.id.profile_password);
        usernameInput = findViewById(R.id.profile_username);
        avatarInput = findViewById(R.id.profile_avatar_url);
        authStatus = findViewById(R.id.profile_auth_status);
        roleStatus = findViewById(R.id.profile_role_status);
        usernamePreview = findViewById(R.id.profile_username_preview);

        Button login = findViewById(R.id.profile_btn_login);
        Button signup = findViewById(R.id.profile_btn_signup);
        Button reset = findViewById(R.id.profile_btn_reset_pass);
        Button saveProfile = findViewById(R.id.profile_btn_save);
        Button refreshProfile = findViewById(R.id.profile_btn_refresh);
        Button logout = findViewById(R.id.profile_btn_logout);

        login.setOnClickListener(v -> login(false));
        signup.setOnClickListener(v -> login(true));
        reset.setOnClickListener(v -> resetPassword());
        saveProfile.setOnClickListener(v -> saveProfile());
        refreshProfile.setOnClickListener(v -> fetchUser());
        logout.setOnClickListener(v -> {
            prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply();
            authStatus.setText(R.string.profile_status_logged_out);
            roleStatus.setText(R.string.profile_role_default);
        });

        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (!TextUtils.isEmpty(token)) {
            fetchUser();
        }
    }

    private void login(boolean signup) {
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            toast(getString(R.string.profile_msg_email_password_required));
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                String endpoint = signup ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password";
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("password", password);

                JSONObject response = request("POST", endpoint, payload, null);
                String accessToken = response.optString("access_token");
                String refreshToken = response.optString("refresh_token");

                if (TextUtils.isEmpty(accessToken) && response.has("session")) {
                    JSONObject session = response.optJSONObject("session");
                    if (session != null) {
                        accessToken = session.optString("access_token");
                        refreshToken = session.optString("refresh_token");
                    }
                }

                if (!TextUtils.isEmpty(accessToken)) {
                    prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).putString(KEY_REFRESH_TOKEN, refreshToken).apply();
                    if (signup) {
                        updateUserMetadata(accessToken, defaultUsernameFromEmail(email), "");
                    }
                    runOnUiThread(() -> {
                        authStatus.setText(getString(R.string.profile_status_logged_in, email));
                        toast(getString(signup ? R.string.profile_msg_signup_success : R.string.profile_msg_login_success));
                    });
                    fetchUser();
                } else {
                    String message = response.optString("msg", response.optString("message", getString(R.string.profile_msg_auth_failed)));
                    showError(message);
                }
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void resetPassword() {
        final String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            toast(getString(R.string.profile_msg_email_required));
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                request("POST", "/auth/v1/recover", payload, null);
                runOnUiThread(() -> toast(getString(R.string.profile_msg_reset_sent)));
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void fetchUser() {
        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) return;

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject user = request("GET", "/auth/v1/user", null, token);

                String email = user.optString("email");
                String role = user.optString("role", "user");
                JSONObject metadata = user.optJSONObject("user_metadata");

                String username = metadata != null ? metadata.optString("username") : "";
                String avatar = metadata != null ? metadata.optString("avatar_url") : "";
                if (TextUtils.isEmpty(username)) {
                    username = defaultUsernameFromEmail(email);
                }

                final String finalUsername = username;
                final String finalAvatar = avatar;
                runOnUiThread(() -> {
                    emailInput.setText(email);
                    usernameInput.setText(finalUsername);
                    avatarInput.setText(finalAvatar);
                    usernamePreview.setText(finalUsername);
                    authStatus.setText(getString(R.string.profile_status_logged_in, email));
                    roleStatus.setText(getString(R.string.profile_role_format, role));
                });
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void saveProfile() {
        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            toast(getString(R.string.profile_msg_login_first));
            return;
        }

        final String email = emailInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        final String avatar = avatarInput.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            username = defaultUsernameFromEmail(email);
            usernameInput.setText(username);
        }
        final String finalUsername = username;

        setLoading(true);
        new Thread(() -> {
            try {
                updateUserMetadata(token, finalUsername, avatar);
                runOnUiThread(() -> {
                    usernamePreview.setText(finalUsername);
                    toast(getString(R.string.profile_msg_saved));
                });
                fetchUser();
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void updateUserMetadata(String token, String username, String avatar) throws Exception {
        JSONObject data = new JSONObject();
        data.put("username", username);
        data.put("avatar_url", avatar);

        JSONObject payload = new JSONObject();
        payload.put("data", data);
        request("PUT", "/auth/v1/user", payload, token);
    }

    private JSONObject request(String method, String endpoint, JSONObject payload, String bearerToken) throws Exception {
        URL url = new URL(SUPABASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        if (!TextUtils.isEmpty(bearerToken)) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        if (payload != null) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readStream(stream);

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + body);
        }

        if (TextUtils.isEmpty(body)) return new JSONObject();
        return new JSONObject(body);
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String defaultUsernameFromEmail(String email) {
        if (TextUtils.isEmpty(email)) return "user";
        int index = email.indexOf('@');
        if (index <= 0) return email;
        return email.substring(0, index);
    }

    private void setLoading(boolean loading) {
        View progress = findViewById(R.id.profile_loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.profile_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create();
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_light_bg);
            }
            toast(getString(R.string.profile_msg_request_failed));
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
