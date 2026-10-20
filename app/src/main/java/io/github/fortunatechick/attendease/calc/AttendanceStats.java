package io.github.fortunatechick.attendease.calc;

import java.util.Locale;

// Attendance figures for one group of lectures: a subject, one component of it, or everything.
//
//   decidedHeld     = present + absent             (+ exempt when approved leave counts as attended)
//   decidedAttended = present                      (+ exempt when approved leave counts as attended)
//   confirmed  = decidedAttended / decidedHeld
//   ifApproved = (decidedAttended + pending) / (decidedHeld + pending)   counted as attended
//              =  decidedAttended / decidedHeld                          excluded from total
//   ifRejected =  decidedAttended / (decidedHeld + pending)
//
// Not-updated lectures are in neither number.
public class AttendanceStats {

    public enum Standing { SAFE, AT_RISK, DEPENDS_ON_LEAVE, NO_DATA }

    public final int present;
    public final int absent;
    public final int pending;
    public final int exempt;
    public final int notUpdated;
    public final ApprovalConvention convention;
    public final int thresholdPercent;

    public AttendanceStats(int present, int absent, int pending, int exempt, int notUpdated,
                           ApprovalConvention convention, int thresholdPercent) {
        this.present = present;
        this.absent = absent;
        this.pending = pending;
        this.exempt = exempt;
        this.notUpdated = notUpdated;
        this.convention = convention;
        this.thresholdPercent = thresholdPercent;
    }

    private int countedExempt() {
        return convention == ApprovalConvention.COUNTED_AS_ATTENDED ? exempt : 0;
    }

    public int decidedAttended() {
        return present + countedExempt();
    }

    public int decidedHeld() {
        return present + absent + countedExempt();
    }

    public int missed() {
        return absent;
    }

    public boolean hasPending() {
        return pending > 0;
    }

    public boolean hasData() {
        return decidedHeld() + pending > 0;
    }

    public double confirmedPercent() {
        return percent(decidedAttended(), decidedHeld());
    }

    public double ifApprovedPercent() {
        if (convention == ApprovalConvention.COUNTED_AS_ATTENDED) {
            return percent(decidedAttended() + pending, decidedHeld() + pending);
        }
        return percent(decidedAttended(), decidedHeld());
    }

    public double ifRejectedPercent() {
        return percent(decidedAttended(), decidedHeld() + pending);
    }

    // The true figure will land on one of the two ends once the leave is decided.
    public double rangeLow() {
        return Math.min(ifRejectedPercent(), ifApprovedPercent());
    }

    public double rangeHigh() {
        return Math.max(ifRejectedPercent(), ifApprovedPercent());
    }

    public Standing standing() {
        if (!hasData()) return Standing.NO_DATA;
        if (!hasPending()) {
            return confirmedPercent() >= thresholdPercent ? Standing.SAFE : Standing.AT_RISK;
        }
        if (rangeLow() >= thresholdPercent) return Standing.SAFE;
        if (rangeHigh() < thresholdPercent) return Standing.AT_RISK;
        return Standing.DEPENDS_ON_LEAVE;
    }

    // Bunk / recover advice on the decided lectures only.
    public BunkAdvice advice() {
        return BunkAdvice.of(decidedAttended(), decidedHeld(), thresholdPercent);
    }

    // Same advice if every pending leave were rejected: the safe figure to plan around.
    public BunkAdvice worstCaseAdvice() {
        return BunkAdvice.of(decidedAttended(), decidedHeld() + pending, thresholdPercent);
    }

    // "83.9%" with no pending lectures, "78.8% – 84.8%" with some.
    public String displayText() {
        if (!hasData()) return "–";
        if (!hasPending()) return formatPercent(confirmedPercent());
        return formatPercent(rangeLow()) + " – " + formatPercent(rangeHigh());
    }

    public String pendingText() {
        if (pending == 0) return "";
        return pending == 1 ? "1 lecture awaiting decision" : pending + " lectures awaiting decision";
    }

    public static String formatPercent(double value) {
        if (Double.isNaN(value)) return "–";
        return String.format(Locale.US, "%.1f%%", value);
    }

    static double percent(int part, int whole) {
        return whole == 0 ? Double.NaN : part * 100.0 / whole;
    }
}
