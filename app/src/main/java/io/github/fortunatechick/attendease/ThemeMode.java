package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

// Light / dark, from the toolbar button. Follows the phone until the button is first used,
// then remembers the choice. Shared by the owner and guest mode.
public final class ThemeMode {

    private static final String FILE = "attendease_ui";
    private static final String KEY = "night_mode";

    private ThemeMode() {
    }

    // Called once at app start, before any screen is drawn.
    public static void apply(Context context) {
        int mode = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
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
