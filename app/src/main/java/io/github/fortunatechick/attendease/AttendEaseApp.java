package io.github.fortunatechick.attendease;

import android.app.Application;

import io.github.fortunatechick.attendease.data.DataChanges;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public class AttendEaseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeMode.apply(this);
        PDFBoxResourceLoader.init(getApplicationContext());
        DataChanges.watchPrefs(this);
        TwoPane.install(this);
    }
}
