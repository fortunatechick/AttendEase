package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

// App-wide choices that aren't attendance data: shared by the owner and guest mode, kept in
// the same file as the light/dark choice.
final class UiPrefs {

    private static final String FILE = "attendease_ui";
    private static final String KEY_WELCOMED = "welcomed";
    private static final String KEY_PORTAL_URL = "portal_url";

    private UiPrefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean welcomed(Context context) {
        return sp(context).getBoolean(KEY_WELCOMED, false);
    }

    static void setWelcomed(Context context) {
        sp(context).edit().putBoolean(KEY_WELCOMED, true).apply();
    }

    // The student portal link opened from the ⋮ menu. Colleges move their portals, so it
    // can be changed in Settings; blank restores the default.
    static String portalUrl(Context context) {
        String url = sp(context).getString(KEY_PORTAL_URL, null);
        return url == null || url.isEmpty() ? SapPortal.DEFAULT_URL : url;
    }

    // Only https:// addresses with a host are accepted. Returns false if refused.
    static boolean setPortalUrl(Context context, String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            sp(context).edit().remove(KEY_PORTAL_URL).apply();
            return true;
        }
        Uri uri = Uri.parse(trimmed);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()) {
            return false;
        }
        sp(context).edit().putString(KEY_PORTAL_URL, trimmed).apply();
        return true;
    }
}
