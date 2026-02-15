package com.termux.app.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

public class ProfileActivity extends Activity {

    private static final String PREFS_NAME = "secure_supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static final String SUPABASE_URL = "https://qahpieeevipnklizihel.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFhaHBpZWVldmlwbmtsaXppaGVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEwODIwMzQsImV4cCI6MjA4NjY1ODAzNH0._ArXv7jnn3-9oaV5WsH-vzPjqRgp9pgWVnzLEyuiCxw";

    private View root;
    private View authCard;
    private View loggedInContainer;
    private View loadingView;

    private EditText emailInput;
    private EditText passwordInput;
    private EditText usernameInput;
    private EditText avatarInput;

    private TextView authStatus;
    private TextView roleStatus;
    private TextView sessionUserId;
    private TextView sessionLastLogin;
    private TextView sessionExpiry;
    private TextView connectionStatus;

    private ShapeableImageView avatarView;
    private Chip authChip;

    private MaterialButton loginButton;
    private MaterialButton signupButton;
    private MaterialButton resetButton;
    private MaterialButton saveButton;
    private MaterialButton refreshButton;
    private MaterialButton logoutButton;

    private SharedPreferences prefs;
    private boolean isLoggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        root = findViewById(R.id.profile_root);
        authCard = findViewById(R.id.card_auth);
        loggedInContainer = findViewById(R.id.profile_logged_in_container);
        loadingView = findViewById(R.id.profile_loading);

        emailInput = findViewById(R.id.profile_email);
        passwordInput = findViewById(R.id.profile_password);
        usernameInput = findViewById(R.id.profile_username);
        avatarInput = findViewById(R.id.profile_avatar_url);

        authStatus = findViewById(R.id.profile_auth_status);
        roleStatus = findViewById(R.id.profile_role_status);
        sessionUserId = findViewById(R.id.profile_session_user_id);
        sessionLastLogin = findViewById(R.id.profile_session_last_login);
        sessionExpiry = findViewById(R.id.profile_session_expiry);
        connectionStatus = findViewById(R.id.profile_connection_status);

        avatarView = findViewById(R.id.profile_avatar);
        authChip = findViewById(R.id.profile_auth_chip);

        loginButton = findViewById(R.id.profile_btn_login);
        signupButton = findViewById(R.id.profile_btn_signup);
        resetButton = findViewById(R.id.profile_btn_reset_pass);
        saveButton = findViewById(R.id.profile_btn_save);
        refreshButton = findViewById(R.id.profile_btn_refresh);
        logoutButton = findViewById(R.id.profile_btn_logout);

        loginButton.setOnClickListener(v -> login(false));
        signupButton.setOnClickListener(v -> login(true));
        resetButton.setOnClickListener(v -> resetPassword());
        saveButton.setOnClickListener(v -> saveProfile());
        refreshButton.setOnClickListener(v -> fetchUser(false));
        logoutButton.setOnClickListener(v -> logout());

        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        updateUiState(!TextUtils.isEmpty(token));
        if (!TextUtils.isEmpty(token)) {
            fetchUser(false);
        }
    }

    private void updateUiState(boolean loggedIn) {
        isLoggedIn = loggedIn;
        authCard.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        loggedInContainer.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        signupButton.setEnabled(!loading);
        resetButton.setEnabled(!loading);
        saveButton.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
        logoutButton.setEnabled(!loading);
    }

    private void login(boolean signup) {
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showSnack(getString(R.string.profile_msg_email_password_required));
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

                if (TextUtils.isEmpty(accessToken)) {
                    String message = response.optString("msg", response.optString("message", getString(R.string.profile_msg_auth_failed)));
                    throw new IllegalStateException(message);
                }

                prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).putString(KEY_REFRESH_TOKEN, refreshToken).apply();
                if (signup) {
                    updateUserMetadata(accessToken, defaultUsernameFromEmail(email), "");
                }
                runOnUiThread(() -> showSnack(getString(R.string.profile_snackbar_logged_in)));
                fetchUser(true);
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
            showSnack(getString(R.string.profile_msg_email_required));
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                request("POST", "/auth/v1/recover", payload, null);
                runOnUiThread(() -> showSnack(getString(R.string.profile_msg_reset_sent)));
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void fetchUser(boolean switchToLoggedInState) {
        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            updateUiState(false);
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject user = request("GET", "/auth/v1/user", null, token);

                String email = user.optString("email", "-");
                String role = user.optString("role", "user");
                String userId = user.optString("id", "-");
                String lastSignInAt = user.optString("last_sign_in_at", "-");

                JSONObject metadata = user.optJSONObject("user_metadata");
                String username = metadata != null ? metadata.optString("username") : "";
                String avatar = metadata != null ? metadata.optString("avatar_url") : "";
                if (TextUtils.isEmpty(username)) username = defaultUsernameFromEmail(email);

                long expiryEpochSeconds = decodeJwtExpiry(token);
                String expiryLabel = expiryEpochSeconds > 0
                    ? DateFormat.format("yyyy-MM-dd HH:mm", new Date(expiryEpochSeconds * 1000L)).toString()
                    : "-";

                final String finalUsername = username;
                final String finalAvatar = avatar;
                final String finalEmail = email;
                final String finalRole = role;
                final String finalUserId = userId;
                final String finalLastSignInAt = lastSignInAt;
                final String finalExpiryLabel = expiryLabel;

                runOnUiThread(() -> {
                    usernameInput.setText(finalUsername);
                    avatarInput.setText(finalAvatar);
                    authStatus.setText(finalEmail);
                    roleStatus.setText(getString(R.string.profile_role_format, finalRole));
                    authChip.setText(R.string.profile_chip_authenticated);
                    connectionStatus.setText(R.string.profile_connected);
                    sessionUserId.setText(getString(R.string.profile_session_user_id_format, finalUserId));
                    sessionLastLogin.setText(getString(R.string.profile_session_last_login_format, finalLastSignInAt));
                    sessionExpiry.setText(getString(R.string.profile_session_expiry_format, finalExpiryLabel));
                    updateAvatar(finalAvatar);

                    if (switchToLoggedInState || !isLoggedIn) updateUiState(true);
                });
            } catch (Exception e) {
                prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply();
                runOnUiThread(() -> updateUiState(false));
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void saveProfile() {
        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            showSnack(getString(R.string.profile_msg_login_first));
            updateUiState(false);
            return;
        }

        final String email = authStatus.getText().toString().trim();
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
                    updateAvatar(avatar);
                    showSnack(getString(R.string.profile_snackbar_profile_updated));
                });
                fetchUser(false);
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private void logout() {
        setLoading(true);
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply();
        updateUiState(false);
        passwordInput.setText("");
        setLoading(false);
        showSnack(getString(R.string.profile_snackbar_logged_out));
    }

    private void updateAvatar(String avatarUrl) {
        avatarView.setImageResource(R.drawable.ic_profile);
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
                    if (bitmap != null) runOnUiThread(() -> avatarView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
                // Keep placeholder icon if loading fails.
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

    private long decodeJwtExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return -1;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(payload);
            return jsonObject.optLong("exp", -1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> showSnack(getString(R.string.profile_msg_request_failed) + "\n" + message));
    }

    private void showSnack(String message) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }
}
