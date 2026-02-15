package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.shell.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        boolean isUsingBlackUI = mActivity.getProperties().isUsingBlackUI();

        if (isUsingBlackUI) {
            sessionTitleView.setBackground(ContextCompat.getDrawable(mActivity, R.drawable.session_background_black_selected));
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = Color.WHITE;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);
        return sessionRowView;
    }

    public void onItemSwipe(int position) {
        TermuxSession selectedSession = getItem(position);
        if (selectedSession == null || selectedSession.getTerminalSession() == null) return;

        View content = LayoutInflater.from(mActivity).inflate(R.layout.session_swipe_action_dialog, null, false);
        TextView title = content.findViewById(R.id.session_swipe_title);
        Button pinButton = content.findViewById(R.id.session_swipe_pin);
        Button deleteButton = content.findViewById(R.id.session_swipe_delete);

        title.setText(selectedSession.getTerminalSession().mSessionName);

        AlertDialog dialog = new AlertDialog.Builder(mActivity)
            .setView(content)
            .create();

        pinButton.setOnClickListener(v -> {
            togglePinSession(selectedSession.getTerminalSession());
            if (isPinned(selectedSession.getTerminalSession())) {
                remove(selectedSession);
                insert(selectedSession, 0);
            }
            notifyDataSetChanged();
            dialog.dismiss();
        });

        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmation(selectedSession);
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_light_bg);
        }
    }

    private void showDeleteConfirmation(TermuxSession selectedSession) {
        AlertDialog confirm = new AlertDialog.Builder(mActivity)
            .setTitle(R.string.session_action_delete_confirm_title)
            .setMessage(R.string.session_action_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.session_action_delete, (d, which) -> {
                selectedSession.getTerminalSession().finishIfRunning();
                mActivity.getTermuxTerminalSessionClient().removeFinishedSession(selectedSession.getTerminalSession());
                notifyDataSetChanged();
            })
            .create();
        confirm.show();
        if (confirm.getWindow() != null) {
            confirm.getWindow().setBackgroundDrawableResource(R.drawable.dialog_light_bg);
        }
    }

    private boolean isPinned(TerminalSession session) {
        return session.mSessionName != null && session.mSessionName.startsWith("[PIN] ");
    }

    private void togglePinSession(TerminalSession session) {
        String name = session.mSessionName == null ? "" : session.mSessionName;
        if (name.startsWith("[PIN] ")) {
            session.mSessionName = name.substring(6);
        } else {
            session.mSessionName = "[PIN] " + name;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }
}
