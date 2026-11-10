package id.sekolah.exambrowser;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** Layar depan: cek perangkat, lalu scan barcode / link ujian. */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "exambro";
    private static final String KEY_HISTORY = "riwayat_link";
    private static final int MAX_HISTORY = 5;

    private LinearLayout checkList;
    private LinearLayout historyList;
    private View historyGroup;
    private TextView textStatus;
    private EditText inputLink;

    private List<DeviceChecker.Item> lastResult = new ArrayList<>();
    private boolean checksEnabled;
    private boolean manualAllowed;

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    attemptOpen(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checksEnabled = getResources().getBoolean(R.bool.cek_perangkat);
        manualAllowed = getResources().getBoolean(R.bool.allow_manual_link);

        ((TextView) findViewById(R.id.textSchool)).setText(getString(R.string.school_name));

        checkList = findViewById(R.id.checkList);
        textStatus = findViewById(R.id.textStatus);
        historyList = findViewById(R.id.historyList);
        historyGroup = findViewById(R.id.historyGroup);
        inputLink = findViewById(R.id.inputLink);

        findViewById(R.id.checkCard).setVisibility(checksEnabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnRecheck).setOnClickListener(v -> {
            runChecks();
            Toast.makeText(this, "Pengecekan diperbarui", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnScan).setOnClickListener(v -> startScan());

        View manualGroup = findViewById(R.id.manualGroup);
        if (manualAllowed) {
            findViewById(R.id.btnOpen).setOnClickListener(v -> attemptOpen(inputLink.getText().toString()));
            findViewById(R.id.btnPaste).setOnClickListener(v -> pasteLink());
            inputLink.setOnEditorActionListener((tv, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    attemptOpen(inputLink.getText().toString());
                    return true;
                }
                return false;
            });
        } else {
            manualGroup.setVisibility(View.GONE);
        }

        if (savedInstanceState == null) handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        runChecks();
        refreshHistory();
    }

    // ================= CEK PERANGKAT =================

    private void runChecks() {
        if (!checksEnabled) {
            lastResult = new ArrayList<>();
            return;
        }
        lastResult = DeviceChecker.run(this);
        checkList.removeAllViews();
        int mustFail = 0, warnFail = 0;
        for (DeviceChecker.Item it : lastResult) {
            checkList.addView(makeRow(it));
            if (!it.ok) {
                if (it.mandatory) mustFail++;
                else warnFail++;
            }
        }
        if (mustFail > 0) {
            textStatus.setText("✗ " + mustFail + " hal wajib belum sesuai. Ujian belum bisa dimulai.");
            textStatus.setTextColor(Color.parseColor("#DC2626"));
        } else if (warnFail > 0) {
            textStatus.setText("! Perangkat siap, dengan " + warnFail + " peringatan.");
            textStatus.setTextColor(Color.parseColor("#B45309"));
        } else {
            textStatus.setText("✓ Perangkat siap untuk ujian.");
            textStatus.setTextColor(Color.parseColor("#16A34A"));
        }
    }

    private View makeRow(DeviceChecker.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        String color = it.ok ? "#16A34A" : (it.mandatory ? "#DC2626" : "#D97706");
        TextView icon = new TextView(this);
        icon.setText(it.ok ? "✓" : (it.mandatory ? "✗" : "!"));
        icon.setTextSize(18);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setTextColor(Color.parseColor(color));
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(it.title + (it.mandatory ? "" : "  (anjuran)"));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        TextView detail = new TextView(this);
        detail.setText(it.detail);
        detail.setTextSize(12);
        detail.setTextColor(Color.parseColor(it.ok ? "#64748B" : color));
        texts.addView(title);
        texts.addView(detail);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(6));
        row.addView(texts, tp);

        if (it.fix != null) {
            Button b = new Button(this);
            b.setText("Perbaiki");
            b.setTextSize(12);
            b.setAllCaps(false);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            final Intent fix = it.fix;
            b.setOnClickListener(v -> openSettings(fix));
            row.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    /** Jalankan aksi hanya jika perangkat memenuhi spesifikasi. */
    private void guard(Runnable go) {
        runChecks();
        StringBuilder must = new StringBuilder();
        StringBuilder warn = new StringBuilder();
        for (DeviceChecker.Item it : lastResult) {
            if (it.ok) continue;
            StringBuilder sb = it.mandatory ? must : warn;
            sb.append("• ").append(it.title).append(": ").append(it.detail).append("\n");
        }
        if (must.length() > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Perangkat belum siap ujian")
                    .setMessage("Perbaiki dulu hal berikut:\n\n" + must
                            + "\nGunakan tombol \"Perbaiki\" pada daftar Cek Perangkat.")
                    .setPositiveButton("Mengerti", null)
                    .show();
            return;
        }
        if (warn.length() > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Perhatian")
                    .setMessage(warn + "\nTetap lanjut ke ujian?")
                    .setPositiveButton("Lanjut", (d, w) -> go.run())
                    .setNegativeButton("Perbaiki dulu", null)
                    .show();
            return;
        }
        go.run();
    }

    // ================= LINK UJIAN =================

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Arahkan kamera ke barcode ujian");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        scanner.launch(options);
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.setAction(null);
        if (text == null) return;
        if (!manualAllowed) {
            toast("Link ujian hanya bisa dibuka lewat scan barcode");
            return;
        }
        String url = extractUrl(text);
        if (url == null) {
            toast("Tidak ditemukan link pada teks yang dibagikan");
            return;
        }
        inputLink.setText(url);
        attemptOpen(url);
    }

    private void pasteLink() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cb == null ? null : cb.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("Belum ada link yang disalin");
            return;
        }
        CharSequence t = clip.getItemAt(0).coerceToText(this);
        String text = t == null ? "" : t.toString().trim();
        String url = extractUrl(text);
        inputLink.setText(url != null ? url : text);
        inputLink.setSelection(inputLink.getText().length());
    }

    private static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = Patterns.WEB_URL.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            toast("Link ujian masih kosong");
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                toast("Barcode/link ini bukan link ujian");
                return null;
            }
        }
        String prefix = getString(R.string.allowed_link_prefix).trim();
        if (!prefix.isEmpty() && !url.startsWith(prefix)) {
            toast("Link ini bukan link ujian sekolah");
            return null;
        }
        return url;
    }

    private void attemptOpen(String raw) {
        final String url = normalize(raw);
        if (url == null) return;
        guard(() -> {
            saveToHistory(url);
            Intent intent = new Intent(this, ExamActivity.class);
            intent.putExtra(ExamActivity.EXTRA_URL, url);
            startActivity(intent);
        });
    }

    // ================= RIWAYAT =================

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private List<String> loadHistory() {
        List<String> list = new ArrayList<>();
        for (String s : prefs().getString(KEY_HISTORY, "").split("\n")) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list;
    }

    private void saveToHistory(String url) {
        List<String> list = loadHistory();
        list.remove(url);
        list.add(0, url);
        while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
        prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", list)).apply();
    }

    private void refreshHistory() {
        historyList.removeAllViews();
        List<String> list = loadHistory();
        historyGroup.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        for (String url : list) {
            TextView tv = new TextView(this);
            tv.setText("↻  " + url);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tv.setTextSize(13);
            tv.setTextColor(Color.parseColor("#2563EB"));
            tv.setPadding(0, dp(9), 0, dp(9));
            tv.setOnClickListener(v -> attemptOpen(url));
            historyList.addView(tv);
        }
        findViewById(R.id.btnClearHistory).setOnClickListener(v -> {
            prefs().edit().remove(KEY_HISTORY).apply();
            refreshHistory();
        });
    }

    // ================= BANTUAN =================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
