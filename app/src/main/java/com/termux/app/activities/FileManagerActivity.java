package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileManagerActivity extends Activity {

    private TextView pathView;
    private ListView listView;
    private File currentDirectory;
    private File homeDirectory;
    private final List<File> entries = new ArrayList<>();
    private File clipboardFile;
    private boolean clipboardCutMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        homeDirectory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        if (!homeDirectory.exists()) homeDirectory = new File(TermuxConstants.TERMUX_FILES_DIR_PATH);
        currentDirectory = homeDirectory;

        pathView = findViewById(R.id.file_manager_path);
        listView = findViewById(R.id.file_manager_list);

        setupButtons();
        setupList();
        refreshList();
    }

    private void setupButtons() {
        findViewById(R.id.file_manager_btn_back).setOnClickListener(v -> finish());
        Button btnUp = findViewById(R.id.file_manager_btn_up);
        Button btnHome = findViewById(R.id.file_manager_btn_home);
        Button btnNewFolder = findViewById(R.id.file_manager_btn_new_folder);
        Button btnNewFile = findViewById(R.id.file_manager_btn_new_file);
        Button btnPaste = findViewById(R.id.file_manager_btn_paste);
        Button btnRefresh = findViewById(R.id.file_manager_btn_refresh);

        btnUp.setOnClickListener(v -> navigateUp());
        btnHome.setOnClickListener(v -> {
            currentDirectory = homeDirectory;
            refreshList();
        });
        btnNewFolder.setOnClickListener(v -> showCreateDialog(true));
        btnNewFile.setOnClickListener(v -> showCreateDialog(false));
        btnPaste.setOnClickListener(v -> pasteClipboard());
        btnRefresh.setOnClickListener(v -> refreshList());
    }

    private void setupList() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            File selected = entries.get(position);
            if (selected.isDirectory()) {
                currentDirectory = selected;
                refreshList();
            } else {
                showFileOptionsDialog(selected);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            File selected = entries.get(position);
            showEntryActionDialog(selected);
            return true;
        });
    }

    private void refreshList() {
        pathView.setText(getString(R.string.file_manager_current_path, currentDirectory.getAbsolutePath()));

        File[] files = currentDirectory.listFiles();
        entries.clear();
        if (files != null) {
            entries.addAll(Arrays.asList(files));
            Collections.sort(entries, Comparator
                .comparing(File::isFile)
                .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        }

        ArrayAdapter<File> adapter = new ArrayAdapter<File>(this, R.layout.file_manager_list_item, entries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.file_manager_list_item, parent, false);
                }

                ImageView iconView = view.findViewById(R.id.file_manager_item_icon);
                TextView nameView = view.findViewById(R.id.file_manager_item_name);
                TextView metaView = view.findViewById(R.id.file_manager_item_meta);

                File file = getItem(position);
                if (file == null) return view;

                boolean isDirectory = file.isDirectory();
                iconView.setImageResource(isDirectory ? R.drawable.ic_folder_material : R.drawable.ic_file_material);
                nameView.setText(file.getName());

                String details;
                if (isDirectory) {
                    int children = file.list() == null ? 0 : file.list().length;
                    details = getString(R.string.file_manager_folder_details, children);
                } else {
                    details = getString(R.string.file_manager_file_details,
                        Formatter.formatFileSize(FileManagerActivity.this, file.length()));
                }
                metaView.setText(details);
                return view;
            }
        };

        listView.setAdapter(adapter);
    }

    private void navigateUp() {
        File parent = currentDirectory.getParentFile();
        if (parent != null && parent.exists()) {
            currentDirectory = parent;
            refreshList();
        }
    }

    private void showCreateDialog(boolean isFolder) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        int title = isFolder ? R.string.file_manager_dialog_new_folder : R.string.file_manager_dialog_new_file;

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (whichDialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;

                File target = new File(currentDirectory, name);
                boolean success;
                try {
                    success = isFolder ? target.mkdir() : target.createNewFile();
                } catch (IOException e) {
                    success = false;
                }

                toast(success ? getString(R.string.file_manager_success_created) : getString(R.string.file_manager_error_create));
                refreshList();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        showSmoothDialog(dialog);
    }

    private void showEntryActionDialog(File file) {
        String[] actions = new String[] {
            getString(R.string.file_manager_action_rename),
            getString(R.string.file_manager_action_delete),
            getString(R.string.file_manager_action_copy),
            getString(R.string.file_manager_action_move),
            getString(R.string.file_manager_action_show_info)
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(file.getName())
            .setItems(actions, (whichDialog, which) -> {
                if (which == 0) {
                    showRenameDialog(file);
                } else if (which == 1) {
                    showDeleteDialog(file);
                } else if (which == 2) {
                    clipboardFile = file;
                    clipboardCutMode = false;
                    toast(getString(R.string.file_manager_clipboard_copy_ready));
                } else if (which == 3) {
                    clipboardFile = file;
                    clipboardCutMode = true;
                    toast(getString(R.string.file_manager_clipboard_move_ready));
                } else if (which == 4) {
                    showFileInfo(file);
                }
            })
            .create();
        showSmoothDialog(dialog);
    }

    private void showRenameDialog(File file) {
        EditText input = new EditText(this);
        input.setText(file.getName());

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.file_manager_action_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (whichDialog, which) -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) return;
                File target = new File(file.getParentFile(), newName);
                boolean success = file.renameTo(target);
                toast(success ? getString(R.string.file_manager_success_rename) : getString(R.string.file_manager_error_rename));
                refreshList();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        showSmoothDialog(dialog);
    }

    private void showDeleteDialog(File file) {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.file_manager_action_delete)
            .setMessage(getString(R.string.file_manager_delete_confirm, file.getName()))
            .setPositiveButton(android.R.string.ok, (whichDialog, which) -> {
                boolean success = deleteRecursive(file);
                toast(success ? getString(R.string.file_manager_success_delete) : getString(R.string.file_manager_error_delete));
                refreshList();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        showSmoothDialog(dialog);
    }

    private void showFileOptionsDialog(File file) {
        String[] actions = new String[] {
            getString(R.string.file_manager_action_show_info),
            getString(R.string.file_manager_action_rename),
            getString(R.string.file_manager_action_delete)
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(file.getName())
            .setItems(actions, (whichDialog, which) -> {
                if (which == 0) {
                    showFileInfo(file);
                } else if (which == 1) {
                    showRenameDialog(file);
                } else if (which == 2) {
                    showDeleteDialog(file);
                }
            })
            .create();
        showSmoothDialog(dialog);
    }

    private void showFileInfo(File file) {
        String lastModified = DateFormat.getMediumDateFormat(this).format(new Date(file.lastModified())) + " " +
            DateFormat.getTimeFormat(this).format(new Date(file.lastModified()));

        String info = getString(
            R.string.file_manager_file_info,
            file.getAbsolutePath(),
            Formatter.formatFileSize(this, file.length()),
            lastModified
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.file_manager_action_show_info)
            .setMessage(info)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        showSmoothDialog(dialog);
    }

    private void pasteClipboard() {
        if (clipboardFile == null || !clipboardFile.exists()) {
            toast(getString(R.string.file_manager_clipboard_empty));
            return;
        }

        File destination = new File(currentDirectory, clipboardFile.getName());
        if (destination.equals(clipboardFile)) {
            destination = new File(currentDirectory, clipboardFile.getName() + "_copy");
        }

        boolean success;
        if (clipboardCutMode) {
            success = clipboardFile.renameTo(destination);
            if (success) clipboardFile = null;
        } else {
            success = copyRecursive(clipboardFile, destination);
        }

        toast(success ? getString(R.string.file_manager_success_paste) : getString(R.string.file_manager_error_paste));
        refreshList();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return file.delete();
    }

    private boolean copyRecursive(File source, File target) {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) return false;
            File[] children = source.listFiles();
            if (children == null) return true;
            for (File child : children) {
                if (!copyRecursive(child, new File(target, child.getName()))) return false;
            }
            return true;
        }

        return copyFile(source, target);
    }

    private boolean copyFile(File source, File target) {
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void showSmoothDialog(AlertDialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_glass_bg);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
