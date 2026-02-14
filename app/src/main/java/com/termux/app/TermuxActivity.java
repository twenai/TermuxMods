package com.termux.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.packages.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.terminal.io.extrakeys.ExtraKeysView;
import com.termux.app.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.app.utils.CrashUtils;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
import android.widget.Button;

public final class TermuxActivity extends Activity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionClient mTermuxTerminalSessionClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app shared properties manager, loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean isOnResumeAfterOnCreate = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private int mTerminalToolbarDefaultHeight;


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Logger.logDebug(LOG_TAG, "onCreate");
        isOnResumeAfterOnCreate = true;

        // Check if a crash happened on last run of the app and show a
        // notification with the crash details if it did
        CrashUtils.notifyAppCrashOnLastRun(this, LOG_TAG);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load termux shared properties
        mProperties = new TermuxAppSharedProperties(this);

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setDrawerTheme();

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        setupRightSidebar();

        registerForContextMenu(mTerminalView);

        // Start the {@link TermuxService} and make it run regardless of who is bound to it
        Intent serviceIntent = new Intent(this, TermuxService.class);
        startService(serviceIntent);

        // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
        // callback if it succeeds.
        if (!bindService(serviceIntent, this, 0))
            throw new RuntimeException("bindService() failed");

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        isOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiever();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {

        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        Bundle bundle = getIntent().getExtras();
                        boolean launchFailsafe = false;
                        if (bundle != null) {
                            launchFailsafe = bundle.getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            Intent i = getIntent();
            if (i != null && Intent.ACTION_RUN.equals(i.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = i.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionClient.setCurrentSession(mTermuxTerminalSessionClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }





    private void setActivityTheme() {
        if (mProperties.isUsingBlackUI()) {
            this.setTheme(R.style.Theme_Termux_Black);
        } else {
            this.setTheme(R.style.Theme_Termux);
        }
    }

    private void setDrawerTheme() {
        if (mProperties.isUsingBlackUI()) {
            findViewById(R.id.left_drawer).setBackgroundResource(R.drawable.sidebar_left_glass_bg);
            ((ImageButton) findViewById(R.id.settings_button)).setColorFilter(Color.WHITE);
        }
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionClient = new TermuxTerminalSessionClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = (int) Math.round(mTerminalToolbarDefaultHeight *
            (mProperties.getExtraKeysInfo() == null ? 0 : mProperties.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView =  findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionClient != null)
                mTermuxTerminalSessionClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install, (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))).setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access shared internal storage (/sdcard) we need this permission.
     */
    public boolean ensureStoragePermissionGranted() {
        if (PermissionUtils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return true;
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission not granted, requesting permission.");
            PermissionUtils.requestPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.logInfo(LOG_TAG, "Storage permission granted by user on request.");
            TermuxInstaller.setupStorageSymlinks(this);
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission denied by user on request.");
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public void setTerminalBackground(Drawable drawable) {
        if (mTerminalView == null) return;

        if (drawable == null)
            mTerminalView.setBackgroundColor(Color.BLACK);
        else
            mTerminalView.setBackground(drawable);
    }

    public DrawerLayout getDrawer() {
        return findViewById(R.id.drawer_layout);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return findViewById(R.id.terminal_toolbar_view_pager);
    }

    public boolean isTerminalViewSelected() {
        ViewPager viewPager = getTerminalToolbarViewPager();
        return viewPager != null && viewPager.getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        ViewPager viewPager = getTerminalToolbarViewPager();
        return viewPager != null && viewPager.getCurrentItem() == 1;
    }

    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null) {
            mTermuxSessionListViewController.notifyDataSetChanged();
        }
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return isOnResumeAfterOnCreate;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public static void updateTermuxActivityStyling(Context context) {
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        context.sendBroadcast(stylingIntent);
    }

    private void showInstallationDialog(String part) {
        String message;
        switch (part) {
            case "prompt":
                message = getString(R.string.termuxmods_dialog_apply_prompt);
                break;
            case "logo":
                message = getString(R.string.termuxmods_dialog_install_logo);
                break;
            case "reset":
                message = getString(R.string.termuxmods_dialog_reset);
                break;
            default:
                message = getString(R.string.termuxmods_dialog_run_script);
                break;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.termuxmods_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
    }

    private void setCustomBackground() {
        setTerminalBackground(null);
        showToast(getString(R.string.termuxmods_toast_bg_reset), false);
    }

    private void animateSidebarTap(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(80)
            .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
            .start();
    }

    private void executeScriptPart(String part) {
        TerminalSession session = getCurrentSession();
        if (session == null) return;

        showInstallationDialog(part);

        String script = "";
        switch (part) {
            case "prompt":
                script = "mv ~/.bashrc ~/.bashrc.bak.$(date +%s) 2>/dev/null; " +
                        "cat > ~/.bashrc << 'EOF'\n" +
                        "PS1='\\[\\e[1;32m\\]┌──(\\[\\e[1;34m\\]Termux㉿localhost\\[\\e[1;32m\\])-[\\[\\e[1;37;1m\\]\\w\\[\\e[1;32m\\]]\\n\\[\\e[1;32m\\]└─\\[\\e[1;34;1m\\]$ \\[\\e[37;1m\\]'\n" +
                        "command_not_found_handle() {\n" +
                        "    local PKG=\"$1\"\n" +
                        "    shift\n" +
                        "    if pkg search \"^${PKG}\\$\" 2>/dev/null | grep -q \"^${PKG}/\"; then\n" +
                        "        printf \"\\033[1;32m[+] Installing %s...\\033[0m\\n\" \"$PKG\"\n" +
                        "        if pkg install -y \"$PKG\"; then\n" +
                        "            if command -v \"$PKG\" >/dev/null 2>&1; then\n" +
                        "                printf \"\\033[1;32m[✓] Installed successfully.\\033[0m\\n\"\n" +
                        "                \"$PKG\" \"$@\"\n" +
                        "            else\n" +
                        "                printf \"\\033[1;33m[!] Installed but no executable found.\\033[0m\\n\"\n" +
                        "            fi\n" +
                        "        else\n" +
                        "            printf \"\\033[1;31m[✗] Failed to install %s.\\033[0m\\n\" \"$PKG\"\n" +
                        "        fi\n" +
                        "    else\n" +
                        "        printf \"\\033[1;31m[!] Not found in repo: %s\\033[0m\\n\" \"$PKG\"\n" +
                        "    fi\n" +
                        "}\n" +
                        "EOF\n" +
                        "source ~/.bashrc >/dev/null 2>&1; clear; echo 'Prompt updated. Restart Termux untuk melihat perubahan.'\n";
                break;
            case "logo":
                script = "pkg update -y && pkg upgrade -y && pkg install -y bash git curl make ruby neofetch lolcat && " +
                        "rm -rf ~/.config/neofetch && mkdir -p ~/.config/neofetch && " +
                        "cat > ~/.config/neofetch/vip-art.txt <<'EOF'\n" +
                        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⠀⠀⠀⠀⠀⠀⠀⠀\n" +
                        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣤⣶⡋⠁⠀⠀⠀⠀⢀⣀⣀⡀\n" +
                        "⠀⠀⠀⠀⠀⠠⠒⣶⣶⣿⣿⣷⣾⣿⣿⣿⣿⣛⣋⣉⠀⠀\n" +
                        "⠀⠀⠀⠀⢀⣤⣞⣫⣿⣿⣿⡻⢿⣿⣿⣿⣿⣿⣦⡀⠀⠀\n" +
                        "⠀⠀⣶⣾⡿⠿⠿⠿⠿⠋⠈⠀⣸⣿⣿⣿⣿⣷⡈⠙⢆⠀\n" +
                        "⠀⠀⠉⠁⠀⠤⣤⣤⣤⣤⣶⣾⣿⣿⣿⣿⠿⣿⣷⠀⠀⠀\n" +
                        "⠀⠀⣠⣴⣾⣿⣿⣿⣿⣿⣿⣿⣿⡿⠟⠁⠀⢹⣿⠀⠀⠀\n" +
                        " ⣾⣿⣿⣿⣿⠟⠋⠉⠛⠋⠉⠁⣀⠀⠀⠀⠸⠃⠀⠀⠀\n" +
                        " ⣿⣿⣿⠹⣇⠀⠀⠀⠀⢀⡀⠀⢀⡙⢷⣦⡀⠀⠀⠀\n" +
                        " ⢿⣿⣿⣷⣦⠤⠤⠀⠀⣠⣿⣶⣶⣿⣿⣿⣿⣿⣷⣄⠀\n" +
                        "  ⣿⡿⢿⣿⣿⣷⣿⣿⡿⢿⣿⣿⣁⡀⠀⠀⠉⢻⣿⣧\n" +
                        "⠀ ⡟⠀⠀⠉⠛⠙⠻⢿⣦⡀⠙⠛⠯⠤⠄⠀⠀⠈⠈⣿\n" +
                        "⠀ ⠀⠀⠀⠀⠀⠀⠀⠀⠈⠻⡆⠀⠀⠀⠀⠀⠀⠀⢀⠟\n" +
                        "EOF\n" +
                        "cat > ~/.config/neofetch/config.conf <<'CONF'\n" +
                        "print_info() {\n" +
                        "    info \"OS\" distro\n" +
                        "    info \"Host\" model\n" +
                        "    info \"Kernel\" kernel\n" +
                        "    info \"Uptime\" uptime\n" +
                        "    info \"Packages\" packages\n" +
                        "    info \"Shell\" shell\n" +
                        "    info \"Terminal\" term\n" +
                        "    info \"CPU\" cpu\n" +
                        "    info \"Memory\" memory\n" +
                        "}\n" +
                        "image_backend=\"ascii\"\n" +
                        "ascii_art() {\n" +
                        "    cat ~/.config/neofetch/vip-art.txt | lolcat -a -d 3\n" +
                        "}\n" +
                        "image_source=\"~/.config/neofetch/vip-art.txt\"\n" +
                        "image_size=\"auto\"\n" +
                        "CONF\n" +
                        "grep -qxF 'neofetch --source ~/.config/neofetch/vip-art.txt --ascii' ~/.bashrc || " +
                        "echo 'neofetch --source ~/.config/neofetch/vip-art.txt --ascii' >> ~/.bashrc && " +
                        "neofetch --source ~/.config/neofetch/vip-art.txt --ascii\n";
                break;
            case "reset":
                script = "if [ -f ~/.bashrc.bak.* ]; then cp $(ls -t ~/.bashrc.bak.* | head -n1) ~/.bashrc; else : > ~/.bashrc; fi; " +
                        "rm -rf ~/.config/neofetch; " +
                        "sed -i '/neofetch --source/d' ~/.bashrc; " +
                        "source ~/.bashrc >/dev/null 2>&1; clear; " +
                        "echo 'Reset complete. Restart Termux untuk melihat perubahan.';\n";
                setTerminalBackground(null);
                break;
        }
        session.write(script);
    }

    private void runTerminalCommand(String command) {
        TerminalSession session = getCurrentSession();
        if (session == null) return;
        session.write(command.endsWith("\n") ? command : command + "\n");
    }

    private void openFileManager() {
        Intent[] intents = new Intent[] {
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES),
            new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        };

        for (Intent intent : intents) {
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }

        showToast(getString(R.string.termuxmods_toast_no_file_manager), true);
    }

    private void showTermuxInfoDialog() {
        String versionName = "unknown";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String infoText = getString(R.string.termuxmods_info_message) + "\n\nVersion: " + versionName;
        new AlertDialog.Builder(this)
            .setTitle(R.string.termuxmods_info_title)
            .setMessage(infoText)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void openSidebarSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void setupRightSidebar() {
        int btnFileManagerId = getResources().getIdentifier("btn_file_manager", "id", getPackageName());
        View btnFileManager = btnFileManagerId != 0 ? findViewById(btnFileManagerId) : null;
        if (btnFileManager != null) {
            btnFileManager.setOnClickListener(v -> {
                animateSidebarTap(v);
                openFileManager();
            });
        }

        int btnInfoTermuxId = getResources().getIdentifier("btn_info_termux", "id", getPackageName());
        View btnInfoTermux = btnInfoTermuxId != 0 ? findViewById(btnInfoTermuxId) : null;
        if (btnInfoTermux != null) {
            btnInfoTermux.setOnClickListener(v -> {
                animateSidebarTap(v);
                showTermuxInfoDialog();
            });
        }

        int btnSidebarSettingsId = getResources().getIdentifier("btn_sidebar_settings", "id", getPackageName());
        View btnSidebarSettings = btnSidebarSettingsId != 0 ? findViewById(btnSidebarSettingsId) : null;
        if (btnSidebarSettings != null) {
            btnSidebarSettings.setOnClickListener(v -> {
                animateSidebarTap(v);
                openSidebarSettings();
            });
        }

        int btnPromptId = getResources().getIdentifier("btn_prompt", "id", getPackageName());
        Button btnPrompt = btnPromptId != 0 ? findViewById(btnPromptId) : null;
        if (btnPrompt != null) {
            btnPrompt.setOnClickListener(v -> { animateSidebarTap(v); executeScriptPart("prompt"); });
        }

        int btnLogoId = getResources().getIdentifier("btn_logo", "id", getPackageName());
        Button btnLogo = btnLogoId != 0 ? findViewById(btnLogoId) : null;
        if (btnLogo != null) {
            btnLogo.setOnClickListener(v -> { animateSidebarTap(v); executeScriptPart("logo"); });
        }

        int btnBackgroundId = getResources().getIdentifier("btn_background", "id", getPackageName());
        Button btnBackground = btnBackgroundId != 0 ? findViewById(btnBackgroundId) : null;
        if (btnBackground != null) {
            btnBackground.setOnClickListener(v -> { animateSidebarTap(v); setCustomBackground(); });
        }

        int btnResetId = getResources().getIdentifier("btn_reset", "id", getPackageName());
        Button btnReset = btnResetId != 0 ? findViewById(btnResetId) : null;
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> { animateSidebarTap(v); executeScriptPart("reset"); });
        }

        int btnUpdateId = getResources().getIdentifier("btn_update", "id", getPackageName());
        Button btnUpdate = btnUpdateId != 0 ? findViewById(btnUpdateId) : null;
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg update -y"); });
        }

        int btnUpgradeId = getResources().getIdentifier("btn_upgrade", "id", getPackageName());
        Button btnUpgrade = btnUpgradeId != 0 ? findViewById(btnUpgradeId) : null;
        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg upgrade -y"); });
        }


        int btnPkgGitId = getResources().getIdentifier("btn_pkg_git", "id", getPackageName());
        Button btnPkgGit = btnPkgGitId != 0 ? findViewById(btnPkgGitId) : null;
        if (btnPkgGit != null) {
            btnPkgGit.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install git -y"); });
        }

        int btnPkgCurlId = getResources().getIdentifier("btn_pkg_curl", "id", getPackageName());
        Button btnPkgCurl = btnPkgCurlId != 0 ? findViewById(btnPkgCurlId) : null;
        if (btnPkgCurl != null) {
            btnPkgCurl.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install curl -y"); });
        }

        int btnPkgWgetId = getResources().getIdentifier("btn_pkg_wget", "id", getPackageName());
        Button btnPkgWget = btnPkgWgetId != 0 ? findViewById(btnPkgWgetId) : null;
        if (btnPkgWget != null) {
            btnPkgWget.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install wget -y"); });
        }

        int btnPkgPythonId = getResources().getIdentifier("btn_pkg_python", "id", getPackageName());
        Button btnPkgPython = btnPkgPythonId != 0 ? findViewById(btnPkgPythonId) : null;
        if (btnPkgPython != null) {
            btnPkgPython.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install python -y"); });
        }

        int btnPkgPipId = getResources().getIdentifier("btn_pkg_pip", "id", getPackageName());
        Button btnPkgPip = btnPkgPipId != 0 ? findViewById(btnPkgPipId) : null;
        if (btnPkgPip != null) {
            btnPkgPip.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install python-pip -y"); });
        }

        int btnPkgNodejsId = getResources().getIdentifier("btn_pkg_nodejs", "id", getPackageName());
        Button btnPkgNodejs = btnPkgNodejsId != 0 ? findViewById(btnPkgNodejsId) : null;
        if (btnPkgNodejs != null) {
            btnPkgNodejs.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install nodejs -y"); });
        }

        int btnPkgOpensshId = getResources().getIdentifier("btn_pkg_openssh", "id", getPackageName());
        Button btnPkgOpenssh = btnPkgOpensshId != 0 ? findViewById(btnPkgOpensshId) : null;
        if (btnPkgOpenssh != null) {
            btnPkgOpenssh.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install openssh -y"); });
        }

        int btnPkgOpensslId = getResources().getIdentifier("btn_pkg_openssl", "id", getPackageName());
        Button btnPkgOpenssl = btnPkgOpensslId != 0 ? findViewById(btnPkgOpensslId) : null;
        if (btnPkgOpenssl != null) {
            btnPkgOpenssl.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg install openssl -y"); });
        }

        int btnPkgMoreId = getResources().getIdentifier("btn_pkg_more", "id", getPackageName());
        Button btnPkgMore = btnPkgMoreId != 0 ? findViewById(btnPkgMoreId) : null;
        if (btnPkgMore != null) {
            btnPkgMore.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("pkg search ."); });
        }
        int btnClearTerminalId = getResources().getIdentifier("btn_clear_terminal", "id", getPackageName());
        Button btnClearTerminal = btnClearTerminalId != 0 ? findViewById(btnClearTerminalId) : null;
        if (btnClearTerminal != null) {
            btnClearTerminal.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("clear && printf '\\e[3J'"); });
        }

        int btnRemoveCacheId = getResources().getIdentifier("btn_remove_cache", "id", getPackageName());
        Button btnRemoveCache = btnRemoveCacheId != 0 ? findViewById(btnRemoveCacheId) : null;
        if (btnRemoveCache != null) {
            btnRemoveCache.setOnClickListener(v -> { animateSidebarTap(v); runTerminalCommand("rm -rf ~/.cache/* && rm -rf $PREFIX/tmp/* && echo 'Cache removed'"); });
        }
    }

    private void unregisterTermuxActivityBroadcastReceiever() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceieverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceieverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        if (ensureStoragePermissionGranted())
                            TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling();
                        return;
                    default:
                }
            }
        }
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        filter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        registerReceiver(mTermuxActivityBroadcastReceiver, filter);
    }

    private void reloadActivityStyling() {
        if (mProperties!= null) {
            mProperties.loadTermuxPropertiesFromDisk();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mProperties.getExtraKeysInfo());
            }
        }

        setMargins();
        setTerminalToolbarHeight();

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onReload();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReload();

        if (mTermuxService != null)
            mTermuxService.setTerminalTranscriptRows();

        // To change the activity and drawer theme, activity needs to be recreated.
        // But this will destroy the activity, and will call the onCreate() again.
        // We need to investigate if enabling this is wise, since all stored variables and
        // views will be destroyed and bindService() will be called again. Extra keys input
        // text will we restored since that has already been implemented. Terminal sessions
        // and transcripts are also already preserved. Theme does change properly too.
        // TermuxActivity.this.recreate();
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        context.startActivity(newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
