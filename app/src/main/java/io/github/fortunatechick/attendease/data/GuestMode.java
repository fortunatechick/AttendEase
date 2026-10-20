package io.github.fortunatechick.attendease.data;

import android.content.Context;
import android.content.SharedPreferences;

// A temporary, one-time attendance view for a friend using this phone.
//
// While it's on, the app uses a separate database file and a separate preferences file;
// the owner's data is never opened, so nothing a guest does can touch it. The guest
// files start blank when guest mode is entered and are erased when it is exited.
public final class GuestMode {

    static final String GUEST_DB = "attendease_guest.db";
    static final String GUEST_PREFS = "attendease_prefs_guest";
    private static final String MODE_FILE = "attendease_mode";
    private static final String KEY_ACTIVE = "guest_active";

    private static Boolean active;   // cached; read on almost every screen

    private GuestMode() {
    }

    public static synchronized boolean isActive(Context context) {
        if (active == null) {
            active = modePrefs(context).getBoolean(KEY_ACTIVE, false);
        }
        return active;
    }

    public static synchronized void enter(Context context) {
        wipe(context);
        modePrefs(context).edit().putBoolean(KEY_ACTIVE, true)
                .putLong(KEY_LAST_USED, System.currentTimeMillis()).commit();
        active = true;
    }

    public static synchronized void exit(Context context) {
        modePrefs(context).edit().putBoolean(KEY_ACTIVE, false).commit();
        active = false;
        wipe(context);
    }

    // Guest mode is meant for a few minutes. Every screen the guest opens refreshes this
    // time; on the next app start after ABANDONED_AFTER_MS without use, guest mode ends.
    private static final String KEY_LAST_USED = "guest_last_used";
    static final long ABANDONED_AFTER_MS = 6L * 60 * 60 * 1000;

    public static synchronized void touch(Context context) {
        if (isActive(context)) modePrefs(context).edit().putLong(KEY_LAST_USED, System.currentTimeMillis()).apply();
    }

    public static synchronized boolean endIfAbandoned(Context context) {
        if (!isActive(context)) return false;
        long last = modePrefs(context).getLong(KEY_LAST_USED, 0);
        long idle = System.currentTimeMillis() - last;
        if (idle >= 0 && idle < ABANDONED_AFTER_MS) return false;   // a clock set back counts as abandoned
        exit(context);
        return true;
    }

    // Erases everything the guest imported or changed.
    private static void wipe(Context context) {
        Context app = context.getApplicationContext();
        AttendEaseDb.closeGuest();
        app.deleteDatabase(GUEST_DB);
        app.getSharedPreferences(GUEST_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static SharedPreferences modePrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(MODE_FILE, Context.MODE_PRIVATE);
    }
}
