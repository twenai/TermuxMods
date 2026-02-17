package com.termux.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.social.SocialStore;

public class ProfileActivity extends Activity {

    private static final int REQUEST_PICK_AVATAR = 2001;

    private ImageView avatarView;
    private TextView followersCountView;
    private TextView followingCountView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        setTitle(R.string.title_profile);

        avatarView = findViewById(R.id.profile_avatar);
        followersCountView = findViewById(R.id.profile_followers_count);
        followingCountView = findViewById(R.id.profile_following_count);

        Button editAvatarButton = findViewById(R.id.button_edit_avatar);
        Button openCommunityButton = findViewById(R.id.button_open_community);

        editAvatarButton.setOnClickListener(v -> pickAvatar());
        openCommunityButton.setOnClickListener(v -> startActivity(new Intent(this, CommunityActivity.class)));

        refreshProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfile();
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_PICK_AVATAR || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        final int grantedFlags = data.getFlags();
        if ((grantedFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 &&
            (grantedFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }

        SocialStore.setProfileAvatarUri(this, uri);
        refreshProfile();
    }

    private void refreshProfile() {
        Uri avatarUri = SocialStore.getProfileAvatarUri(this);
        if (avatarUri != null) {
            avatarView.setImageURI(avatarUri);
        } else {
            avatarView.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        followersCountView.setText(getString(R.string.label_followers_count, SocialStore.getMyFollowersCount(this)));
        followingCountView.setText(getString(R.string.label_following_count, SocialStore.getMyFollowingCount(this)));
    }
}
