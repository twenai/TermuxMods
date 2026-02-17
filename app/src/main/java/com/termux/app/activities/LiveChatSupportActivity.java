package com.termux.app.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.social.SocialStore;

public class LiveChatSupportActivity extends Activity {

    private static final int REQUEST_CODE_PICK_IMAGE = 1001;
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L;

    private Uri selectedImageUri;
    private ImageView imagePreview;
    private ImageView profileAvatar;
    private TextView selectedFileLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_chat_support);

        setTitle(R.string.title_live_chat_support);

        Button chooseImageButton = findViewById(R.id.button_choose_image);
        Button sendImageButton = findViewById(R.id.button_send_image);
        imagePreview = findViewById(R.id.image_preview);
        profileAvatar = findViewById(R.id.live_chat_profile_avatar);
        selectedFileLabel = findViewById(R.id.text_selected_file);

        chooseImageButton.setOnClickListener(v -> openImagePicker());
        sendImageButton.setOnClickListener(v -> shareSelectedImage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileAvatar();
    }

    private void loadProfileAvatar() {
        Uri avatarUri = SocialStore.getProfileAvatarUri(this);
        if (avatarUri != null) {
            profileAvatar.setImageURI(avatarUri);
        } else {
            profileAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CODE_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri imageUri = data.getData();
        if (imageUri == null) {
            return;
        }

        final int grantedFlags = data.getFlags();
        final int persistableReadFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if ((grantedFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 &&
            (grantedFlags & persistableReadFlag) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(imageUri, persistableReadFlag);
            } catch (SecurityException ignored) {
                // Some providers may still reject persistable grants.
            }
        }

        long imageSize = getFileSize(imageUri);
        if (imageSize < 0L) {
            selectedImageUri = null;
            imagePreview.setImageDrawable(null);
            selectedFileLabel.setText(getString(R.string.live_chat_no_file_selected));
            Toast.makeText(this, R.string.error_live_chat_image_size_unknown, Toast.LENGTH_LONG).show();
            return;
        }

        if (imageSize > MAX_IMAGE_SIZE_BYTES) {
            selectedImageUri = null;
            imagePreview.setImageDrawable(null);
            selectedFileLabel.setText(getString(R.string.live_chat_no_file_selected));
            Toast.makeText(this, R.string.error_live_chat_image_too_large, Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedImageUri != null && !selectedImageUri.equals(imageUri)) {
            try {
                getContentResolver().releasePersistableUriPermission(selectedImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Ignore if permission was not persisted.
            }
        }

        selectedImageUri = imageUri;
        imagePreview.setImageURI(imageUri);
        selectedFileLabel.setText(getString(R.string.label_live_chat_selected_file, getDisplayName(imageUri)));
    }

    private void shareSelectedImage() {
        if (selectedImageUri == null) {
            Toast.makeText(this, R.string.error_live_chat_select_image_first, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, selectedImageUri);
        shareIntent.setClipData(ClipData.newUri(getContentResolver(), "shared_image", selectedImageUri));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.title_live_chat_share_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_live_chat_no_share_target, Toast.LENGTH_LONG).show();
        }
    }

    private long getFileSize(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeColumn >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeColumn)) {
                    return cursor.getLong(sizeColumn);
                }
            } finally {
                cursor.close();
            }
        }

        try (ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            if (fileDescriptor != null) {
                return fileDescriptor.getStatSize();
            }
        } catch (Exception ignored) {
            // Fallback below.
        }

        return -1L;
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return getString(R.string.live_chat_unknown_file);
        }

        try {
            int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameColumn >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameColumn);
            }
        } finally {
            cursor.close();
        }

        return getString(R.string.live_chat_unknown_file);
    }
}
