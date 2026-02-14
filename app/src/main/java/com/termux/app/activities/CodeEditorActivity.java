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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) { applySyntaxHighlight(s); }
        });

        applySyntaxHighlight(editorInput.getText());
    }

    private void loadFile() {
        try {
            if (!targetFile.exists()) targetFile.createNewFile();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(targetFile);
            byte[] data = new byte[8192];
            int read;
            while ((read = in.read(data)) != -1) buffer.write(data, 0, read);
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

    private void applySyntaxHighlight(Editable editable) {
        if (formatting || editable == null) return;
        formatting = true;

        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) editable.removeSpan(span);

        String name = targetFile.getName().toLowerCase();
        if (name.endsWith(".py")) {
            highlightPython(editable);
        } else if (name.endsWith(".js") || name.endsWith(".ts")) {
            highlightJavaScript(editable);
        } else if (name.endsWith(".java") || name.endsWith(".kt")) {
            highlightJavaLike(editable);
        } else if (name.endsWith(".xml") || name.endsWith(".html")) {
            highlightXml(editable);
        } else if (name.endsWith(".sh") || name.endsWith(".bash")) {
            highlightShell(editable);
        } else {
            highlightMarkdown(editable);
        }

        formatting = false;
    }

    private void highlightPython(Editable text) {
        applyPattern(text, "(?m)#.*$", "#6A9955");
        applyPattern(text, "\"\"\"[\\s\\S]*?\"\"\"|'\'\'[\\s\\S]*?'\'\'", "#CE9178");
        applyPattern(text, "\"[^\"\\n]*\"|'[^'\\n]*'", "#CE9178");
        applyPattern(text, "\\b(def|class|import|from|as|if|elif|else|for|while|try|except|finally|with|return|yield|pass|break|continue|lambda|global|nonlocal|assert|in|is|and|or|not|None|True|False)\\b", "#C586C0");
        applyPattern(text, "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()", "#DCDCAA");
        applyPattern(text, "\\b\\d+(\\.\\d+)?\\b", "#B5CEA8");
    }

    private void highlightJavaScript(Editable text) {
        applyPattern(text, "(?m)//.*$", "#6A9955");
        applyPattern(text, "/\\*[\\s\\S]*?\\*/", "#6A9955");
        applyPattern(text, "\"[^\"\\n]*\"|'[^'\\n]*'|`[^`]*`", "#CE9178");
        applyPattern(text, "\\b(function|const|let|var|if|else|for|while|switch|case|break|continue|return|class|extends|import|export|from|async|await|try|catch|finally|new|this|true|false|null|undefined)\\b", "#C586C0");
        applyPattern(text, "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?=\\()", "#DCDCAA");
        applyPattern(text, "\\b\\d+(\\.\\d+)?\\b", "#B5CEA8");
    }

    private void highlightJavaLike(Editable text) {
        applyPattern(text, "(?m)//.*$", "#6A9955");
        applyPattern(text, "/\\*[\\s\\S]*?\\*/", "#6A9955");
        applyPattern(text, "\"[^\"\\n]*\"", "#CE9178");
        applyPattern(text, "\\b(public|private|protected|class|interface|enum|static|final|void|new|if|else|for|while|switch|case|break|continue|return|try|catch|finally|throws|import|package|extends|implements|this|super|true|false|null)\\b", "#C586C0");
        applyPattern(text, "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()", "#DCDCAA");
        applyPattern(text, "\\b\\d+(\\.\\d+)?\\b", "#B5CEA8");
    }

    private void highlightXml(Editable text) {
        applyPattern(text, "</?[A-Za-z0-9:_-]+", "#569CD6");
        applyPattern(text, "\\b[A-Za-z_:][-A-Za-z0-9_:.]*(?=\\=)", "#9CDCFE");
        applyPattern(text, "\"[^\"]*\"", "#CE9178");
        applyPattern(text, "<!--([\\s\\S]*?)-->", "#6A9955");
    }

    private void highlightShell(Editable text) {
        applyPattern(text, "(?m)#.*$", "#6A9955");
        applyPattern(text, "\"[^\"\\n]*\"|'[^'\\n]*'", "#CE9178");
        applyPattern(text, "\\$(\\{[A-Za-z0-9_]+\\}|[A-Za-z0-9_]+)", "#4EC9B0");
        applyPattern(text, "\\b(if|then|else|fi|for|in|do|done|while|case|esac|function|return|export|local)\\b", "#C586C0");
    }

    private void highlightMarkdown(Editable text) {
        applyPattern(text, "(?m)^#{1,6}\\s.*$", "#4FC3F7");
        applyPattern(text, "(?m)^```[\\s\\S]*?^```$", "#81C784");
        applyPattern(text, "`[^`]+`", "#AED581");
        applyPattern(text, "\\*\\*[^*]+\\*\\*", "#FFCC80");
        applyPattern(text, "_[^_]+_", "#CE93D8");
        applyPattern(text, "\\[[^\\]]+\\]\\([^\\)]+\\)", "#90CAF9");
    }

    private void applyPattern(Spannable text, String regex, String colorHex) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        int color = Color.parseColor(colorHex);
        while (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
