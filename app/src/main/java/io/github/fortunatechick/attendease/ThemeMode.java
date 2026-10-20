package io.github.fortunatechick.attendease;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

// Light / dark, from the toolbar button. Follows the phone until the button is first used,
// then remembers the choice. Shared by the owner and guest mode.
public final class ThemeMode {

    private static final String FILE = "attendease_ui";
    private static final String KEY = "night_mode";
    private static final String KEY_DYNAMIC = "dynamic_color";

    private ThemeMode() {
    }

    // Called once at app start, before any screen is drawn.
    public static void apply(Application app) {
        int mode = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        // Wallpaper-based accent (Android 12+), only when switched on in Settings.
        DynamicColors.applyToActivitiesIfAvailable(app, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> isDynamic(activity))
                .build());
    }

    public static boolean isDynamic(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC, false);
    }

    public static void setDynamic(Context context, boolean on) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC, on).apply();
    }

    public static boolean isDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    // Switches to the other one; open screens are redrawn automatically.
    public static void toggle(Context context) {
        int mode = isDark(context) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
