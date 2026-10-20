package io.github.fortunatechick.attendease.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

// Tells open screens that saved data changed, so two panes side by side stay in step (marking a
// lecture on the right updates the percentages on the left). Every database write goes through
// AttendEaseDb.getWritableDatabase(), which calls changed(). Listeners run on the main thread,
// a moment later and once per burst of writes; their reloads queue on the single Background
// thread behind the write that triggered them, so they always read the committed data.
public final class DataChanges {

    public interface Listener {
        void onDataChanged();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Runnable DISPATCH = () -> {
        for (Listener l : new ArrayList<>(LISTENERS)) l.onDataChanged();
    };
    // Held here because SharedPreferences only keeps a weak reference to its listeners.
    private static SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private DataChanges() {
    }

    public static void changed() {
        MAIN.removeCallbacks(DISPATCH);
        MAIN.postDelayed(DISPATCH, 150);
    }

    public static void add(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    // Settings such as the attendance target live in preferences, not the database.
    public static void watchPrefs(Context context) {
        prefsListener = (sp, key) -> changed();
        Context app = context.getApplicationContext();
        app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener);
        app.getSharedPreferences(GuestMode.GUEST_PREFS, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener);
    }
}
