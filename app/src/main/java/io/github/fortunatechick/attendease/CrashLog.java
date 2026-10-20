package io.github.fortunatechick.attendease;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

// If the app crashes, the stack trace is kept in a private file on this phone. Nothing is sent
// anywhere: on the next start the student is offered "Copy crash report" to paste into a GitHub
// issue themselves. The report holds the app version, Android version, device model and the
// stack trace; it never includes attendance data, names or files.
final class CrashLog {

    private static final String FILE = "last_crash.txt";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private CrashLog() {
    }

    static void install(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(report(error).getBytes(UTF8));
            } catch (IOException | RuntimeException ignored) {
                // Never let the crash log itself get in the way of the real crash.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static String report(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        return "AttendEase " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
                + trace;
    }

    // Shown once on the dashboard after a crash.
    static void offerReport(Activity activity) {
        File file = new File(activity.getFilesDir(), FILE);
        if (!file.exists()) return;
        String text = read(file);
        if (!file.delete()) file.deleteOnExit();
        if (text == null || text.isEmpty()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.crash_title)
                .setMessage(R.string.crash_message)
                .setPositiveButton(R.string.crash_copy, (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.crash_clip_label), text));
                        Toast.makeText(activity, R.string.crash_copied, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.crash_dismiss, null)
                .show();
    }

    private static String read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int total = 0, n;
            while (total < bytes.length && (n = in.read(bytes, total, bytes.length - total)) > 0) total += n;
            return new String(bytes, 0, total, UTF8);
        } catch (IOException e) {
            return null;
        }
    }
}
