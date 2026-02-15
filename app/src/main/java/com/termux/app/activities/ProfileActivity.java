package com.termux.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.termux.BuildConfig;
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

public class ProfileActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "secure_supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final int NETWORK_TIMEOUT_MS = 15000;

    private View root;
    private View authCard;
    private View loggedInContainer;
    private View loadingView;
    private View loginLoading;

    private EditText emailInput;
    private EditText passwordInput;
    private EditText usernameInput;

    private TextView authStatus;
    private TextView roleStatus;
    private TextView sessionUserId;
    private TextView sessionTermuxId;
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
    private MaterialButton changeAvatarButton;

    private static final int REQUEST_PICK_AVATAR = 2201;

    private SharedPreferences prefs;
    private boolean isLoggedIn = false;
    private String currentUserEmail = "";
    private String currentUserId = "";
    private String currentAvatarUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        root = findViewById(R.id.profile_root);
        authCard = findViewById(R.id.card_auth);
        loggedInContainer = findViewById(R.id.profile_logged_in_container);
        loadingView = findViewById(R.id.profile_loading);
        loginLoading = findViewById(R.id.profile_login_loading);

        MaterialToolbar toolbar = findViewById(R.id.profile_top_app_bar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        emailInput = findViewById(R.id.profile_email);
        passwordInput = findViewById(R.id.profile_password);
        usernameInput = findViewById(R.id.profile_username);

        authStatus = findViewById(R.id.profile_auth_status);
        roleStatus = findViewById(R.id.profile_role_status);
        sessionUserId = findViewById(R.id.profile_session_user_id);
        sessionTermuxId = findViewById(R.id.profile_session_termux_id);
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
        changeAvatarButton = findViewById(R.id.profile_btn_change_avatar);

        loginButton.setOnClickListener(v -> login(false));
        signupButton.setOnClickListener(v -> login(true));
        resetButton.setOnClickListener(v -> resetPassword());
        saveButton.setOnClickListener(v -> saveProfile());
        refreshButton.setOnClickListener(v -> fetchUser(false));
        logoutButton.setOnClickListener(v -> logout());
        changeAvatarButton.setOnClickListener(v -> pickAvatarImage());

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
        loginLoading.setVisibility((loading && !isLoggedIn) ? View.VISIBLE : View.GONE);

        loginButton.setEnabled(!loading);
        signupButton.setEnabled(!loading);
        resetButton.setEnabled(!loading);
        saveButton.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
        logoutButton.setEnabled(!loading);
        changeAvatarButton.setEnabled(!loading);
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

                if (TextUtils.isEmpty(accessToken) && signup) {
                    runOnUiThread(() -> showSnack(getString(R.string.profile_msg_signup_success)));
                    return;
                }

                if (TextUtils.isEmpty(accessToken)) {
                    String message = response.optString("msg", response.optString("message", getString(R.string.profile_msg_auth_failed)));
                    throw new IllegalStateException(message);
                }

                prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).putString(KEY_REFRESH_TOKEN, refreshToken).apply();
                if (signup) {
                    updateUserMetadata(accessToken, defaultUsernameFromEmail(email), "", "");
                }
                runOnUiThread(() -> {
                    passwordInput.setText("");
                    showSnack(getString(R.string.profile_snackbar_logged_in));
                });
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
                String termuxId = metadata != null ? metadata.optString("termux_id") : "";
                if (TextUtils.isEmpty(username)) username = defaultUsernameFromEmail(email);
                if (TextUtils.isEmpty(termuxId)) {
                    termuxId = defaultTermuxId(userId);
                    updateUserMetadata(token, username, avatar, termuxId);
                }

                long expiryEpochSeconds = decodeJwtExpiry(token);
                String expiryLabel = expiryEpochSeconds > 0
                    ? DateFormat.format("yyyy-MM-dd HH:mm", new Date(expiryEpochSeconds * 1000L)).toString()
                    : "-";

                final String finalUsername = username;
                final String finalAvatar = avatar;
                final String finalEmail = email;
                final String finalRole = role;
                final String finalTermuxId = termuxId;
                final String finalUserId = userId;
                final String finalLastSignInAt = lastSignInAt;
                final String finalExpiryLabel = expiryLabel;

                runOnUiThread(() -> {
                    usernameInput.setText(finalUsername);
                    authStatus.setText(finalUsername);
                    currentUserEmail = finalEmail;
                    currentUserId = finalUserId;
                    currentAvatarUrl = finalAvatar;
                    roleStatus.setText(finalEmail);
                    authChip.setText(R.string.profile_chip_authenticated);
                    connectionStatus.setText(R.string.profile_connected);

                    sessionUserId.setText(finalUserId);
                    sessionTermuxId.setText(finalTermuxId);
                    sessionLastLogin.setText(finalLastSignInAt);
                    sessionExpiry.setText(finalExpiryLabel);

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

        final String email = TextUtils.isEmpty(currentUserEmail) ? roleStatus.getText().toString().trim() : currentUserEmail;
        String username = usernameInput.getText().toString().trim();
        final String avatar = currentAvatarUrl;

        if (TextUtils.isEmpty(username)) {
            username = defaultUsernameFromEmail(email);
            usernameInput.setText(username);
        }
        final String finalUsername = username;

        setLoading(true);
        new Thread(() -> {
            try {
                String termuxId = sessionTermuxId.getText().toString().trim();
                if (TextUtils.isEmpty(termuxId) || "-".equals(termuxId)) {
                    termuxId = defaultTermuxId(sessionUserId.getText().toString().trim());
                }
                updateUserMetadata(token, finalUsername, avatar, termuxId);
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
        currentUserEmail = "";
        currentUserId = "";
        currentAvatarUrl = "";
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

    private void pickAvatarImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.profile_action_change_avatar)), REQUEST_PICK_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_AVATAR || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        final String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            showSnack(getString(R.string.profile_msg_login_first));
            return;
        }

        final String userId = TextUtils.isEmpty(currentUserId) ? sessionUserId.getText().toString().trim() : currentUserId;
        final String username = usernameInput.getText().toString().trim();
        final String termuxId = TextUtils.isEmpty(sessionTermuxId.getText().toString().trim())
            ? defaultTermuxId(userId) : sessionTermuxId.getText().toString().trim();

        setLoading(true);
        new Thread(() -> {
            try {
                String uploadedUrl = uploadAvatarToBucket(token, userId, uri);
                String finalUsername = TextUtils.isEmpty(username) ? defaultUsernameFromEmail(currentUserEmail) : username;
                updateUserMetadata(token, finalUsername, uploadedUrl, termuxId);
                currentAvatarUrl = uploadedUrl;
                runOnUiThread(() -> {
                    updateAvatar(uploadedUrl);
                    showSnack(getString(R.string.profile_snackbar_avatar_updated));
                });
                fetchUser(false);
            } catch (Exception e) {
                showError(e.getMessage());
            } finally {
                runOnUiThread(() -> setLoading(false));
            }
        }).start();
    }

    private String uploadAvatarToBucket(String token, String userId, Uri uri) throws Exception {
        String safeUserId = TextUtils.isEmpty(userId) ? "local" : userId.replaceAll("[^A-Za-z0-9_-]", "");
        String objectPath = safeUserId + "_" + System.currentTimeMillis() + ".jpg";

        byte[] bytes;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IllegalStateException("Unable to read selected image");
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            bytes = output.toByteArray();
        }

        URL uploadUrl = new URL(SUPABASE_URL + "/storage/v1/object/avatars/" + objectPath);
        HttpURLConnection connection = (HttpURLConnection) uploadUrl.openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "image/jpeg");
        connection.setRequestProperty("x-upsert", "true");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }

        try {
            int code = connection.getResponseCode();
            InputStream responseStream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readStream(responseStream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Avatar upload failed: HTTP " + code + " " + body);
            }
        } finally {
            connection.disconnect();
        }

        return SUPABASE_URL + "/storage/v1/object/public/avatars/" + objectPath;
    }

    private void updateUserMetadata(String token, String username, String avatar, String termuxId) throws Exception {
        JSONObject data = new JSONObject();
        data.put("username", username);
        data.put("avatar_url", avatar);
        data.put("termux_id", termuxId);

        JSONObject payload = new JSONObject();
        payload.put("data", data);
        request("PUT", "/auth/v1/user", payload, token);
    }

    private JSONObject request(String method, String endpoint, JSONObject payload, String bearerToken) throws Exception {
        if (TextUtils.isEmpty(SUPABASE_URL) || TextUtils.isEmpty(SUPABASE_ANON_KEY)) {
            throw new IllegalStateException(getString(R.string.profile_msg_missing_config));
        }
        if (TextUtils.isEmpty(endpoint)) {
            throw new IllegalStateException(getString(R.string.profile_msg_missing_endpoint));
        }

        if (!SUPABASE_URL.startsWith("http://") && !SUPABASE_URL.startsWith("https://")) {
            throw new IllegalStateException(getString(R.string.profile_msg_invalid_url));
        }
        URL url = new URL(SUPABASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        connection.setRequestProperty("Accept", "application/json");
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

        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readStream(stream);

            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + ": " + body);
            }

            if (TextUtils.isEmpty(body)) return new JSONObject();
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
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

    private String defaultTermuxId(String userId) {
        if (TextUtils.isEmpty(userId) || "-".equals(userId)) {
            return "TMX-LOCAL";
        }
        String compact = userId.replaceAll("[^A-Za-z0-9]", "");
        if (compact.length() > 10) compact = compact.substring(0, 10);
        return "TMX-" + compact.toUpperCase();
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
