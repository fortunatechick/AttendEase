package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.BunkAdvice;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.data.Status;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

// Display helpers shared by the screens. All wording comes from string resources so the app
// can be translated; the calc package keeps its own English text for its tests.
public final class Formats {

    private Formats() {
    }

    // Lecture dates are stored as midnight UTC, so they are formatted in UTC too, in the
    // phone's language and date order ("Tue, 5 Jan 2027", "Tue, Jan 5, 2027", ...).
    public static String date(long dateMillis) {
        return format("EEEdMMMyyyy", dateMillis);
    }

    public static String shortDate(long dateMillis) {
        return format("dMMM", dateMillis);
    }

    private static String format(String skeleton, long dateMillis) {
        Locale locale = Locale.getDefault();
        SimpleDateFormat f = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(dateMillis));
    }

    // "10:00:01 AM" -> "10:00 AM"
    public static String time(String sapTime) {
        if (sapTime == null) return "";
        return sapTime.replaceFirst("^(\\d{1,2}:\\d{2}):\\d{2}", "$1");
    }

    public static String component(Context context, String component) {
        return context.getString("P".equals(component) ? R.string.component_practical : R.string.component_theory);
    }

    public static String statusLabel(Context context, String effectiveStatus) {
        switch (effectiveStatus) {
            case Status.PRESENT:
                return context.getString(R.string.status_present);
            case Status.ABSENT:
                return context.getString(R.string.status_absent);
            case Status.PENDING_LEAVE:
                return context.getString(R.string.status_pending_leave);
            case Status.EXEMPT:
                return context.getString(R.string.status_leave_approved);
            default:
                return context.getString(R.string.status_not_updated);
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

    public static String standingLabel(Context context, AttendanceStats.Standing standing) {
        switch (standing) {
            case SAFE:
                return context.getString(R.string.standing_safe);
            case AT_RISK:
                return context.getString(R.string.standing_at_risk);
            case DEPENDS_ON_LEAVE:
                return context.getString(R.string.standing_depends_on_leave);
            default:
                return context.getString(R.string.standing_no_data);
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
    // Text is white or near-black, whichever reads better on the colour (at least 4.5:1).
    public static void tintChip(TextView chip, String text, int color) {
        chip.setText(text);
        ViewCompat.setBackgroundTintList(chip, ColorStateList.valueOf(color));
        boolean white = ColorUtils.calculateContrast(Color.WHITE, color | 0xFF000000) >= 4.5;
        chip.setTextColor(white ? Color.WHITE : CHIP_DARK_TEXT);
    }

    private static final int CHIP_DARK_TEXT = 0xFF1B1B1B;

    // Same wording as BunkAdvice.text(), from resources.
    public static String advice(Context context, BunkAdvice a) {
        Resources r = context.getResources();
        if (a.mustAttend < 0) return r.getString(R.string.advice_cannot_reach, a.thresholdPercent);
        if (a.mustAttend > 0) {
            return r.getQuantityString(R.plurals.advice_attend_next, a.mustAttend, a.mustAttend, a.thresholdPercent);
        }
        if (a.canMiss == 0) return r.getString(R.string.advice_cannot_miss);
        return r.getQuantityString(R.plurals.advice_can_miss, a.canMiss, a.canMiss);
    }

    // Same wording as SemesterBudget.summaryText(): "60 lectures · 12 misses allowed · 8 used".
    public static String budgetSummary(Context context, SemesterBudget b) {
        Resources r = context.getResources();
        return r.getString(R.string.budget_summary,
                r.getQuantityString(R.plurals.budget_lectures, b.total, b.total),
                r.getQuantityString(R.plurals.budget_misses_allowed, b.maxMisses, b.maxMisses),
                r.getString(R.string.budget_used, b.missed));
    }

    // Same wording as SemesterBudget.adviceText().
    public static String budgetAdvice(Context context, SemesterBudget b) {
        Resources r = context.getResources();
        int left = b.canStillMiss();
        if (left < 0) {
            return r.getString(R.string.budget_over_limit, -left, AttendanceStats.formatPercent(b.bestFinalPercent()));
        }
        String text;
        if (left == 0) {
            text = r.getString(R.string.budget_no_misses_left, b.thresholdPercent);
        } else if (b.lecturesLeft > 0 && left >= b.lecturesLeft) {
            text = r.getQuantityString(R.plurals.budget_safe_for_semester, b.lecturesLeft, b.lecturesLeft);
        } else {
            text = r.getQuantityString(R.plurals.budget_can_miss, left, left);
        }
        if (b.pending > 0) {
            int worst = b.canStillMissWorstCase();
            text += worst < 0
                    ? r.getString(R.string.budget_worst_over, -worst)
                    : r.getString(R.string.budget_worst, worst);
        }
        return text;
    }

    // Same wording as AttendanceStats.pendingText().
    public static String pending(Context context, AttendanceStats s) {
        if (s.pending == 0) return "";
        return context.getResources().getQuantityString(R.plurals.lectures_awaiting_decision, s.pending, s.pending);
    }

    public static String lectures(Context context, int count) {
        return context.getResources().getQuantityString(R.plurals.lectures, count, count);
    }

    public static String noLecturesMarked(Context context) {
        return context.getString(R.string.no_lectures_marked_yet);
    }
}
