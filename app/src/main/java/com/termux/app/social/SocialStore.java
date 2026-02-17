package com.termux.app.social;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SocialStore {

    private static final String PREFS_NAME = "termux_social_store";
    private static final String KEY_FOLLOWING = "following";
    private static final String KEY_BLOCKED = "blocked";
    private static final String KEY_PROFILE_AVATAR_URI = "profile_avatar_uri";

    private static final List<String> COMMUNITY_USERS = Arrays.asList(
        "alex", "sam", "maria", "reza", "nina", "leo", "rani", "dimas"
    );

    private SocialStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<String> getCommunityUsers() {
        return new ArrayList<>(COMMUNITY_USERS);
    }

    public static Set<String> getFollowing(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_FOLLOWING, new HashSet<>()));
    }

    public static boolean isFollowing(Context context, String username) {
        return getFollowing(context).contains(username);
    }

    public static void setFollowing(Context context, String username, boolean follow) {
        Set<String> following = getFollowing(context);
        if (follow) following.add(username);
        else following.remove(username);
        prefs(context).edit().putStringSet(KEY_FOLLOWING, following).apply();
    }

    public static Set<String> getBlocked(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_BLOCKED, new HashSet<>()));
    }

    public static boolean isBlocked(Context context, String username) {
        return getBlocked(context).contains(username);
    }

    public static void setBlocked(Context context, String username, boolean blocked) {
        Set<String> blockedUsers = getBlocked(context);
        if (blocked) blockedUsers.add(username);
        else blockedUsers.remove(username);
        prefs(context).edit().putStringSet(KEY_BLOCKED, blockedUsers).apply();
    }

    public static int getFollowerCountForUser(Context context, String username) {
        int base = 120 + Math.abs(username.hashCode() % 600);
        return isFollowing(context, username) ? base + 1 : base;
    }

    public static int getMyFollowersCount(Context context) {
        return 256;
    }

    public static int getMyFollowingCount(Context context) {
        return getFollowing(context).size();
    }

    public static void setProfileAvatarUri(Context context, Uri uri) {
        prefs(context).edit().putString(KEY_PROFILE_AVATAR_URI, uri == null ? null : uri.toString()).apply();
    }

    public static Uri getProfileAvatarUri(Context context) {
        String uri = prefs(context).getString(KEY_PROFILE_AVATAR_URI, null);
        return uri == null ? null : Uri.parse(uri);
    }

    public static String getConversation(Context context, String username) {
        return prefs(context).getString("chat_" + username, "");
    }

    public static void appendMessage(Context context, String username, String message) {
        String key = "chat_" + username;
        String current = prefs(context).getString(key, "");
        String next = current.isEmpty() ? message : current + "\n" + message;
        prefs(context).edit().putString(key, next).apply();
    }
}
