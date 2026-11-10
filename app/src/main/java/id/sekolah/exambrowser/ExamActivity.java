package id.sekolah.exambrowser;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Layar ujian: layar penuh, dikunci (pin), anti-screenshot, mencatat keluar aplikasi. */
public class ExamActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "exam_url";

    private WebView web;
    private ProgressBar progress;
    private TextView textViolation;

    private boolean exiting = false;
    private boolean leftApp = false;
    private int violations = 0;

    // Pemantauan internet & baterai
    private TextView textNotice;
    private ConnectivityManager.NetworkCallback netCallback;
    private boolean netLost = false;
    private int batteryPct = 100;
    private boolean charging = true;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) batteryPct = level * 100 / scale;
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            updateNotice();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_exam);

        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        textViolation = findViewById(R.id.textViolation);
        textNotice = findViewById(R.id.textNotice);

        TextView title = findViewById(R.id.textTitle);
        title.setText(getString(R.string.school_name));

        Button btnReload = findViewById(R.id.btnReload);
        Button btnExit = findViewById(R.id.btnExit);
        btnReload.setOnClickListener(v -> web.reload());
        btnExit.setOnClickListener(v -> askExit());

        setupWebView();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            String url = getIntent().getStringExtra(EXTRA_URL);
            if (url != null) web.loadUrl(url);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(ExamActivity.this,
                        "Tombol kembali dinonaktifkan selama ujian. Gunakan tombol Keluar.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        hideSystemBars();
        startMonitoring();
    }

    private void startMonitoring() {
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }
        netLost = !DeviceChecker.isOnline(this);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    netLost = false;
                    runOnUiThread(ExamActivity.this::updateNotice);
                }

                @Override
                public void onLost(Network network) {
                    netLost = !DeviceChecker.isOnline(ExamActivity.this);
                    runOnUiThread(ExamActivity.this::updateNotice);
                }
            };
            try {
                cm.registerDefaultNetworkCallback(netCallback);
            } catch (Exception e) {
                netCallback = null;
            }
        }
        updateNotice();
    }

    private void updateNotice() {
        int limit = getResources().getInteger(R.integer.peringatan_baterai_ujian);
        if (netLost) {
            textNotice.setText("Internet terputus! Jawaban bisa tidak terkirim. Periksa data/WiFi.");
            textNotice.setBackgroundColor(Color.parseColor("#DC2626"));
            textNotice.setVisibility(View.VISIBLE);
        } else if (!charging && batteryPct <= limit) {
            textNotice.setText("Baterai tinggal " + batteryPct + "%. Segera sambungkan pengisi daya.");
            textNotice.setBackgroundColor(Color.parseColor("#D97706"));
            textNotice.setVisibility(View.VISIBLE);
        } else {
            textNotice.setVisibility(View.GONE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " ExamBrowserSekolah/1.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        // Matikan tekan lama (salin teks / buka menu)
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.setHapticFeedbackEnabled(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Hanya link web yang boleh dibuka; link ke aplikasi lain (WA, telepon, dll.) diblokir
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** Sematkan layar (screen pinning) agar siswa tidak bisa pindah aplikasi. */
    private void startPinning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (!exiting) startPinning();
        if (leftApp) {
            leftApp = false;
            showViolationWarning();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exiting || isChangingConfigurations()) return;
        // Layar mati karena tombol power tidak dihitung sebagai pelanggaran
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();
        if (screenOn) {
            leftApp = true;
            violations++;
        }
    }

    private void showViolationWarning() {
        textViolation.setText("Keluar aplikasi: " + violations + "x");
        textViolation.setVisibility(View.VISIBLE);
        new AlertDialog.Builder(this)
                .setTitle("Peringatan")
                .setMessage("Anda terdeteksi keluar dari aplikasi ujian (" + violations
                        + " kali).\n\nCatatan ini tampil di bagian atas layar dan dapat dilihat pengawas.")
                .setCancelable(false)
                .setPositiveButton("Saya mengerti", null)
                .show();
    }

    private void askExit() {
        final String password = getString(R.string.exit_password);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Keluar dari ujian?");

        if (password.isEmpty()) {
            builder.setMessage("Pastikan jawaban sudah dikirim sebelum keluar.")
                    .setPositiveButton("Keluar", (d, w) -> exitExam())
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password pengawas");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        builder.setMessage("Pastikan jawaban sudah dikirim. Masukkan password pengawas untuk keluar.")
                .setView(box)
                .setPositiveButton("Keluar", (d, w) -> {
                    if (password.equals(input.getText().toString())) {
                        exitExam();
                    } else {
                        Toast.makeText(this, "Password salah", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void exitExam() {
        exiting = true;
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        // Hapus data login agar HP aman dipakai siswa berikutnya
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        web.clearCache(true);
        web.clearHistory();
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        if (netCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
            }
        }
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
