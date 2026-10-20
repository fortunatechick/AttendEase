package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.data.Status;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

// Display helpers shared by the screens.
public final class Formats {

    private Formats() {
    }

    // Lecture dates are stored as midnight UTC, so they are formatted in UTC too.
    public static String date(long dateMillis) {
        SimpleDateFormat f = new SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(dateMillis));
    }

    public static String shortDate(long dateMillis) {
        SimpleDateFormat f = new SimpleDateFormat("d MMM", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(dateMillis));
    }

    // "10:00:01 AM" -> "10:00 AM"
    public static String time(String sapTime) {
        if (sapTime == null) return "";
        return sapTime.replaceFirst("^(\\d{1,2}:\\d{2}):\\d{2}", "$1");
    }

    public static String component(String component) {
        return "P".equals(component) ? "Practical" : "Theory";
    }

    public static String statusLabel(String effectiveStatus) {
        switch (effectiveStatus) {
            case Status.PRESENT:
                return "Present";
            case Status.ABSENT:
                return "Absent";
            case Status.PENDING_LEAVE:
                return "Pending leave";
            case Status.EXEMPT:
                return "Leave approved";
            default:
                return "Not updated";
        }
    }

    public static int statusColor(Context context, String effectiveStatus) {
        int res;
        switch (effectiveStatus) {
            case Status.PRESENT:
                res = R.color.safe;
                break;
            case Status.ABSENT:
                res = R.color.risk;
                break;
            case Status.PENDING_LEAVE:
                res = R.color.pending;
                break;
            case Status.EXEMPT:
                res = R.color.brand;
                break;
            default:
                res = R.color.muted;
        }
        return ContextCompat.getColor(context, res);
    }

    public static String standingLabel(AttendanceStats.Standing standing) {
        switch (standing) {
            case SAFE:
                return "SAFE";
            case AT_RISK:
                return "AT RISK";
            case DEPENDS_ON_LEAVE:
                return "DEPENDS ON LEAVE";
            default:
                return "NO DATA";
        }
    }

    public static int standingColor(Context context, AttendanceStats.Standing standing) {
        switch (standing) {
            case SAFE:
                return ContextCompat.getColor(context, R.color.safe);
            case AT_RISK:
                return ContextCompat.getColor(context, R.color.risk);
            case DEPENDS_ON_LEAVE:
                return ContextCompat.getColor(context, R.color.pending);
            default:
                return ContextCompat.getColor(context, R.color.muted);
        }
    }

    // Red when over the semester limit or out of misses, amber when 2 or fewer remain.
    public static int budgetColor(Context context, SemesterBudget b) {
        int left = b.pending > 0 ? b.canStillMissWorstCase() : b.canStillMiss();
        if (left <= 0) return ContextCompat.getColor(context, R.color.risk);
        if (left <= 2) return ContextCompat.getColor(context, R.color.pending);
        return ContextCompat.getColor(context, R.color.safe);
    }

    // Small rounded label with a coloured background (uses @drawable/bg_chip).
    public static void tintChip(TextView chip, String text, int color) {
        chip.setText(text);
        ViewCompat.setBackgroundTintList(chip, ColorStateList.valueOf(color));
    }

    public static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
