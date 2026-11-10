package id.sekolah.exambrowser;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Pemeriksaan kesiapan HP sebelum ujian. */
public class DeviceChecker {

    public static class Item {
        public final String title;
        public final String detail;
        public final boolean ok;
        public final boolean mandatory;
        public final Intent fix;

        Item(String title, String detail, boolean ok, boolean mandatory, Intent fix) {
            this.title = title;
            this.detail = detail;
            this.ok = ok;
            this.mandatory = mandatory;
            this.fix = ok ? null : fix;
        }
    }

    public static List<Item> run(Context c) {
        Resources r = c.getResources();
        List<Item> list = new ArrayList<>();
        list.add(internet(c, r.getBoolean(R.bool.wajib_internet)));
        list.add(developer(c, r.getBoolean(R.bool.wajib_opsi_pengembang_mati)));
        list.add(accessibility(c, r.getBoolean(R.bool.wajib_aksesibilitas_mati)));
        list.add(root(r.getBoolean(R.bool.wajib_tidak_root)));
        list.add(battery(c, r.getInteger(R.integer.minimal_baterai), r.getBoolean(R.bool.wajib_baterai)));
        list.add(doNotDisturb(c, r.getBoolean(R.bool.wajib_jangan_ganggu)));
        list.add(memory(c, r.getBoolean(R.bool.wajib_memori)));
        list.add(camera(c, r.getBoolean(R.bool.wajib_kamera)));
        return list;
    }

    // ---------- Internet ----------
    public static boolean isOnline(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static Item internet(Context c, boolean m) {
        boolean ok = isOnline(c);
        return new Item("Koneksi internet",
                ok ? "Terhubung" : "Tidak ada internet. Aktifkan data seluler atau WiFi.",
                ok, m, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
    }

    // ---------- Opsi pengembang ----------
    private static Item developer(Context c, boolean m) {
        ContentResolver cr = c.getContentResolver();
        boolean dev = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        boolean adb = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1;
        boolean ok = !dev && !adb;
        String detail = ok ? "Nonaktif"
                : adb ? "USB debugging menyala. Matikan Opsi Pengembang."
                : "Opsi Pengembang menyala. Matikan dulu.";
        return new Item("Opsi Pengembang", detail, ok, m,
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    }

    // ---------- Aksesibilitas aplikasi lain ----------
    private static Item accessibility(Context c, boolean m) {
        List<String> names = new ArrayList<>();
        AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null) {
            PackageManager pm = c.getPackageManager();
            List<AccessibilityServiceInfo> services =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : services) {
                ResolveInfo ri = info.getResolveInfo();
                if (ri == null || ri.serviceInfo == null) continue;
                ApplicationInfo ai = ri.serviceInfo.applicationInfo;
                boolean system = ai != null && (ai.flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                if (system) continue; // layanan bawaan HP diabaikan
                String label = String.valueOf(ri.loadLabel(pm));
                if (!names.contains(label)) names.add(label);
            }
        }
        boolean ok = names.isEmpty();
        String detail = ok ? "Tidak ada aplikasi lain yang mengakses layar"
                : "Matikan aksesibilitas: " + TextUtils.join(", ", names);
        return new Item("Aksesibilitas aplikasi lain", detail, ok, m,
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ---------- Root ----------
    private static Item root(boolean m) {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/system/app/Superuser.apk", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/data/adb/magisk"};
        boolean rooted = false;
        for (String p : paths) {
            if (new File(p).exists()) {
                rooted = true;
                break;
            }
        }
        return new Item("Keamanan sistem (root)",
                rooted ? "HP terdeteksi di-root. Gunakan HP lain." : "Aman",
                !rooted, m, null);
    }

    // ---------- Baterai ----------
    /** @return {persen, sedangDicas(1/0)} */
    public static int[] batteryState(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return new int[]{100, 1};
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = (level >= 0 && scale > 0) ? level * 100 / scale : 100;
        int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return new int[]{pct, plugged != 0 ? 1 : 0};
    }

    private static Item battery(Context c, int min, boolean m) {
        int[] s = batteryState(c);
        boolean charging = s[1] == 1;
        boolean ok = s[0] >= min || charging;
        String detail = s[0] + "%" + (charging ? " (sedang dicas)" : "")
                + (ok ? "" : ". Minimal " + min + "% atau sambungkan pengisi daya.");
        return new Item("Baterai", detail, ok, m, null);
    }

    // ---------- Jangan Ganggu ----------
    private static Item doNotDisturb(Context c, boolean m) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        int f = nm == null ? NotificationManager.INTERRUPTION_FILTER_UNKNOWN : nm.getCurrentInterruptionFilter();
        boolean ok = f >= NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        return new Item("Mode Jangan Ganggu",
                ok ? "Aktif" : "Belum aktif. Notifikasi bisa muncul saat ujian.",
                ok, m, new Intent("android.settings.ZEN_MODE_SETTINGS"));
    }

    // ---------- Memori ----------
    private static Item memory(Context c, boolean m) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long mb = mi.availMem / (1024 * 1024);
        boolean ok = am == null || (!mi.lowMemory && mb >= 300);
        String detail = ok ? "Tersedia " + mb + " MB"
                : "Memori hampir penuh (" + mb + " MB). Tutup aplikasi lain.";
        return new Item("Memori (RAM)", detail, ok, m, null);
    }

    // ---------- Kamera ----------
    private static Item camera(Context c, boolean m) {
        boolean ok = c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        return new Item("Kamera untuk scan",
                ok ? "Tersedia" : "Kamera tidak ditemukan. Gunakan link manual.",
                ok, m, null);
    }
}
