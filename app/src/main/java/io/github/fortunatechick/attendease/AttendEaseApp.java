package io.github.fortunatechick.attendease;

import android.app.Application;
import android.os.StrictMode;

import io.github.fortunatechick.attendease.data.DataChanges;
import io.github.fortunatechick.attendease.data.GuestMode;
import io.github.fortunatechick.attendease.data.PdfFonts;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public class AttendEaseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        if (BuildConfig.DEBUG) {
            // Debug builds only: flag disk or database work on the UI thread and leaked
            // resources in logcat, so they're caught before a release.
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }
        // A guest session nobody has used for hours (the friend left, the phone was locked)
        // ends on its own, so the owner never opens the app to someone else's data.
        GuestMode.endIfAbandoned(this);
        ThemeMode.apply(this);
        PDFBoxResourceLoader.init(getApplicationContext());
        PdfFonts.install();
        DataChanges.watchPrefs(this);
        TwoPane.install(this);
    }
}
