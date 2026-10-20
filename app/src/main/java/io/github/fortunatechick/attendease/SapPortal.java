package io.github.fortunatechick.attendease;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

// Opens the college SAP portal so the student can log in and download the attendance PDF.
//
// It opens as a Custom Tab: the phone's browser slides in over the app with the app's
// colours. The browser does the networking, so AttendEase itself still needs no internet
// permission. A Custom Tab is just a VIEW intent with one extra, so no library is needed;
// browsers that don't support it open the page normally. After downloading, tapping
// "Open" on the PDF and choosing AttendEase lands straight on the import review screen.
public final class SapPortal {

    public static final String URL = "https://sdc-sppap1.svkm.ac.in:50001/irj/portal";

    private static final String EXTRA_SESSION = "android.support.customtabs.extra.SESSION";
    private static final String EXTRA_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR";

    private SapPortal() {
    }

    public static void showAndOpen(Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("SAP portal")
                .setMessage("1. Log in with your college ID.\n"
                        + "2. Open the student attendance report and download it as PDF.\n"
                        + "3. Tap Open on the downloaded file and choose \"Upload to AttendEase\".\n\n"
                        + "The PDF goes straight to the upload screen. If it doesn't, use Upload attendance PDF "
                        + "from this menu and pick it from Downloads.")
                .setPositiveButton("Open portal", (d, w) -> open(activity))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void open(Activity activity) {
        openInBrowser(activity, URL);
    }

    // Any page in a Custom Tab; the browser does the networking.
    public static void openInBrowser(Activity activity, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle extras = new Bundle();
        extras.putBinder(EXTRA_SESSION, null);   // asks the browser for a Custom Tab
        intent.putExtras(extras);
        intent.putExtra(EXTRA_TOOLBAR_COLOR, ContextCompat.getColor(activity, R.color.brand));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No web browser is installed to open this page.", Toast.LENGTH_LONG).show();
        }
    }
}
