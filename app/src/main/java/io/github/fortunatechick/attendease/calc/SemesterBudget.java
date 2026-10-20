package io.github.fortunatechick.attendease.calc;

import java.util.Locale;

// How many lectures a subject can still be missed this semester before it finishes under
// the threshold. The college counts the whole semester: a subject with 2 theory + 2 lab
// lectures a week over 15 weeks has 60 lectures, so 12 absences (20%) is the limit,
// theory and lab together.
//
//   total       = max(lectures per week x weeks, lectures already recorded)
//   maxMisses   = floor(total x (100 - threshold) / 100)
//   canStillMiss = maxMisses - absences so far
//
// Approved leave counted as attended is not a miss. Under "excluded from total" it leaves
// the total instead. Pending leave is shown as a possible miss (the leave being rejected).
public class SemesterBudget {

    public final int weeks;
    public final int perWeek;
    public final int total;            // lectures in the whole semester
    public final int effectiveTotal;   // total, less approved leave when it is excluded
    public final int maxMisses;
    public final int missed;
    public final int pending;
    public final int lecturesLeft;     // not yet held
    public final int thresholdPercent;

    public SemesterBudget(int weeks, int perWeek, AttendanceStats stats) {
        this.weeks = weeks;
        this.perWeek = perWeek;
        this.thresholdPercent = stats.thresholdPercent;
        int recorded = stats.present + stats.absent + stats.pending + stats.exempt + stats.notUpdated;
        this.total = Math.max(perWeek * weeks, recorded);
        this.lecturesLeft = total - recorded;
        int excluded = stats.convention == ApprovalConvention.EXCLUDED_FROM_TOTAL ? stats.exempt : 0;
        this.effectiveTotal = total - excluded;
        this.maxMisses = effectiveTotal * (100 - thresholdPercent) / 100;   // integer division = floor
        this.missed = stats.absent;
        this.pending = stats.pending;
    }

    public boolean isKnown() {
        return perWeek > 0;
    }

    // Negative when already over the limit.
    public int canStillMiss() {
        return maxMisses - missed;
    }

    // If every pending leave were rejected.
    public int canStillMissWorstCase() {
        return maxMisses - missed - pending;
    }

    public boolean isOverLimit() {
        return canStillMiss() < 0;
    }

    // Best possible end-of-semester figure: attend everything from here on.
    public double bestFinalPercent() {
        if (effectiveTotal == 0) return Double.NaN;
        return (effectiveTotal - missed) * 100.0 / effectiveTotal;
    }

    // "60 lectures · 12 misses allowed · 8 used"
    public String summaryText() {
        return total + " lectures · " + maxMisses + " misses allowed · " + missed + " used";
    }

    public String adviceText() {
        int left = canStillMiss();
        if (left < 0) {
            return "Over the semester limit by " + (-left) + ". Attending every remaining lecture ends at "
                    + String.format(Locale.US, "%.1f%%", bestFinalPercent()) + ".";
        }
        String text;
        if (left == 0) {
            text = "No misses left this semester: one more absence takes you under " + thresholdPercent + "%";
        } else if (lecturesLeft > 0 && left >= lecturesLeft) {
            text = "Safe for the semester: you could miss all " + lecturesLeft + " remaining lectures";
        } else {
            text = "You can miss " + left + " more this semester";
        }
        if (pending > 0) {
            int worst = canStillMissWorstCase();
            text += worst < 0
                    ? " (over the limit by " + (-worst) + " if the leave is rejected)"
                    : " (" + worst + " if the leave is rejected)";
        }
        return text;
    }
}
