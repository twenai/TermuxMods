package com.termux.app.activities;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorActivity extends Activity {

    public static final String EXTRA_FILE_PATH = "file_path";

    private EditText editorInput;
    private File targetFile;
    private boolean formatting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_editor);

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (path == null || path.isEmpty()) {
            toast(getString(R.string.code_editor_error_no_file));
            finish();
            return;
        }

        targetFile = new File(path);
        TextView fileName = findViewById(R.id.code_editor_file_name);
        fileName.setText(targetFile.getName());

        editorInput = findViewById(R.id.code_editor_input);
        findViewById(R.id.code_editor_btn_back).setOnClickListener(v -> finish());

        Button saveButton = findViewById(R.id.code_editor_btn_save);
        saveButton.setOnClickListener(v -> saveFile());

        loadFile();

        editorInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyMarkdownHighlight(s);
            }
        });

        applyMarkdownHighlight(editorInput.getText());
    }

    private void loadFile() {
        try {
            if (!targetFile.exists()) targetFile.createNewFile();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(targetFile);
            byte[] data = new byte[8192];
            int read;
            while ((read = in.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            in.close();

            editorInput.setText(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast(getString(R.string.code_editor_error_load));
        }
    }

    private void saveFile() {
        try {
            FileOutputStream out = new FileOutputStream(targetFile, false);
            out.write(editorInput.getText().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            toast(getString(R.string.code_editor_saved));
        } catch (Exception e) {
            toast(getString(R.string.code_editor_error_save));
        }
    }

    private void applyMarkdownHighlight(Editable editable) {
        if (formatting || editable == null) return;
        formatting = true;

        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) editable.removeSpan(span);

        applyPattern(editable, "(?m)^#{1,6}\\s.*$", Color.parseColor("#4FC3F7"));
        applyPattern(editable, "(?m)^```[\\s\\S]*?^```$", Color.parseColor("#81C784"));
        applyPattern(editable, "`[^`]+`", Color.parseColor("#AED581"));
        applyPattern(editable, "\\*\\*[^*]+\\*\\*", Color.parseColor("#FFCC80"));
        applyPattern(editable, "_[^_]+_", Color.parseColor("#CE93D8"));
        applyPattern(editable, "\\[[^\\]]+\\]\\([^\\)]+\\)", Color.parseColor("#90CAF9"));

        formatting = false;
    }

    private void applyPattern(Spannable text, String regex, int color) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        while (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
