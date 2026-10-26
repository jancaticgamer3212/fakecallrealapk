package com.example.fakecallinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Installs / removes the bundled FakeCall.apk.
 *  - with root: as a privileged system app (real call screen), using the bundled installer.sh
 *  - without root: as a normal app through the system package installer (simulated call screen)
 */
public class MainActivity extends Activity {

    private static final String PKG = "com.example.fakecall";
    private static final String SYSTEM_COPY = "/system/priv-app/FakeCall/FakeCall.apk";
    private static final long ROOT_TIMEOUT_MS = 120000;

    private ScrollView page;
    private TextView statusView, logView;
    private Button rootInstall, rootUninstall, normalInstall, normalUninstall;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        page.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Fake Call Installer", 24, true);
        root.addView(title);

        statusView = text("", 16, false);
        statusView.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusView);

        root.addView(header("With root: real call screen"));
        root.addView(text("Installs FakeCall as a system app so it can use the phone's own call "
                + "screen. Your root manager may ask for permission. The phone has to reboot "
                + "afterwards; you choose when.", 14, false));
        rootInstall = button("Install as system app");
        rootUninstall = button("Uninstall system app");
        root.addView(rootInstall);
        root.addView(rootUninstall);

        root.addView(header("Without root: simulated call screen only"));
        root.addView(text("Installs FakeCall as a normal app. The \"real call screen\" option does "
                + "not work this way, only the app's own simulated call screen. Android may ask "
                + "you to allow \"Unknown sources\" first.", 14, false));
        normalInstall = button("Install normally");
        normalUninstall = button("Uninstall");
        root.addView(normalInstall);
        root.addView(normalUninstall);

        root.addView(header("Log"));
        logView = text("", 12, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        setContentView(page);

        rootInstall.setOnClickListener(v -> runRoot(true));
        rootUninstall.setOnClickListener(v -> runRoot(false));
        normalInstall.setOnClickListener(v -> installNormally());
        normalUninstall.setOnClickListener(v -> uninstallNormally());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    // ------------------------------------------------------------------ status

    private PackageInfo installedInfo() {
        try {
            return getPackageManager().getPackageInfo(PKG, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean isSystem(PackageInfo pi) {
        return (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        PackageInfo pi = installedInfo();
        if (pi == null) {
            sb.append("FakeCall is not installed.");
        } else {
            sb.append("FakeCall is installed (version ").append(pi.versionName).append(", ")
                    .append(isSystem(pi) ? "system app" : "normal app").append(").");
        }
        boolean copy = new File(SYSTEM_COPY).exists();
        if (copy) {
            sb.append("\nSystem copy present in /system/priv-app.");
            if (pi == null || !isSystem(pi)) sb.append(" Reboot the phone to activate it.");
        }
        statusView.setText(sb.toString());
    }

    // ------------------------------------------------------------------ root install / uninstall

    private void runRoot(final boolean install) {
        if (busy) return;
        setBusy(true);
        logView.setText("");

        new Thread(() -> {
            int code = -1;
            try {
                File script = extract("installer.sh", "installer.sh");
                String cmd;
                if (install) {
                    File apk = extract("FakeCall.apk", "FakeCall.apk");
                    cmd = "sh " + quote(script.getPath()) + " install " + quote(apk.getPath());
                } else {
                    cmd = "sh " + quote(script.getPath()) + " uninstall";
                }
                log("$ su -c " + cmd);

                final Process proc = new ProcessBuilder("su", "-c", cmd)
                        .redirectErrorStream(true).start();
                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(ROOT_TIMEOUT_MS);
                        proc.destroy();
                    } catch (InterruptedException ignored) {
                    }
                });
                watchdog.setDaemon(true);
                watchdog.start();

                BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) log(line);
                code = proc.waitFor();
                watchdog.interrupt();
                proc.destroy();
            } catch (IOException e) {
                log("Could not run su: " + e.getMessage());
                log("This phone does not look rooted (or the root manager blocked the app).");
            } catch (Exception e) {
                log("Error: " + e);
            }
            final int exit = code;
            runOnUiThread(() -> rootDone(install, exit));
        }).start();
    }

    private void rootDone(boolean install, int exit) {
        setBusy(false);
        refreshStatus();
        if (exit == 0) {
            log("Finished OK.");
            new AlertDialog.Builder(this)
                    .setTitle("Reboot needed")
                    .setMessage(install
                            ? "FakeCall is copied to the system. Reboot the phone to start using it."
                            : "FakeCall is removed from the system. Reboot the phone to finish.")
                    .setPositiveButton("Reboot now", (d, w) -> reboot())
                    .setNegativeButton("Later", null)
                    .show();
        } else {
            log("Failed (exit code " + exit + "). If root was denied, allow Fake Call Installer in "
                    + "your root manager and try again.");
        }
    }

    private void reboot() {
        new Thread(() -> {
            try {
                new ProcessBuilder("su", "-c", "reboot").start();
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not reboot: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ------------------------------------------------------------------ normal install / uninstall

    private void installNormally() {
        PackageInfo pi = installedInfo();
        if (pi != null && isSystem(pi)) {
            Toast.makeText(this, "FakeCall is installed as a system app. Use \"Uninstall system "
                    + "app\" first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (busy) return;
        setBusy(true);
        new Thread(() -> {
            String err = null;
            File apk = null;
            try {
                File dir = getExternalCacheDir();
                if (dir == null) dir = getExternalFilesDir(null);
                if (dir == null) throw new IOException("no storage available to hand the APK to the installer");
                apk = new File(dir, "FakeCall.apk");
                copyAsset("FakeCall.apk", apk);
                apk.setReadable(true, false);
            } catch (IOException e) {
                err = e.getMessage();
            }
            final String error = err;
            final File file = apk;
            runOnUiThread(() -> {
                setBusy(false);
                if (error != null) {
                    Toast.makeText(this, "Could not prepare the APK: " + error, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "No package installer found on this phone", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void uninstallNormally() {
        PackageInfo pi = installedInfo();
        if (pi == null) {
            Toast.makeText(this, "FakeCall is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean updatedSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (isSystem(pi) && !updatedSystem) {
            Toast.makeText(this, "FakeCall is a system app. Use \"Uninstall system app\" (needs root).",
                    Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + PKG)));
    }

    // ------------------------------------------------------------------ helpers

    private void setBusy(boolean b) {
        busy = b;
        rootInstall.setEnabled(!b);
        rootUninstall.setEnabled(!b);
        normalInstall.setEnabled(!b);
        normalUninstall.setEnabled(!b);
    }

    private void log(final String line) {
        runOnUiThread(() -> {
            logView.append(line + "\n");
            page.post(() -> page.fullScroll(View.FOCUS_DOWN));
        });
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private File extract(String asset, String name) throws IOException {
        File out = new File(getFilesDir(), name);
        copyAsset(asset, out);
        return out;
    }

    private void copyAsset(String asset, File out) throws IOException {
        InputStream in = getAssets().open(asset);
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            os.close();
            in.close();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        return b;
    }
}
