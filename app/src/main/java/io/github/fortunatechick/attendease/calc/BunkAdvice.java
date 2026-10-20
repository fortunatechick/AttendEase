package io.github.fortunatechick.attendease.calc;

// How many lectures can be skipped while staying at the threshold, or how many in a row
// must be attended to climb back to it. Integer arithmetic on the percentage avoids
// floating-point edge cases such as 0.8 * 35 = 27.999...
//
//   canMiss    = floor(attended / t - held)              = floor((100a - p*h) / p)
//   mustAttend = ceil((t * held - attended) / (1 - t))   = ceil((p*h - 100a) / (100 - p))
public class BunkAdvice {

    public final int canMiss;      // >= 0 when at or above the threshold, else 0
    public final int mustAttend;   // > 0 when below the threshold, -1 if it can never be reached
    public final int thresholdPercent;

    private BunkAdvice(int canMiss, int mustAttend, int thresholdPercent) {
        this.canMiss = canMiss;
        this.mustAttend = mustAttend;
        this.thresholdPercent = thresholdPercent;
    }

    public static BunkAdvice of(int attended, int held, int thresholdPercent) {
        int p = thresholdPercent;
        if (held == 0) return new BunkAdvice(0, 0, p);

        long gap = (long) p * held - 100L * attended;
        if (gap <= 0) {
            if (p == 0) return new BunkAdvice(Integer.MAX_VALUE, 0, p);
            return new BunkAdvice((int) (-gap / p), 0, p);
        }
        if (p >= 100) return new BunkAdvice(0, -1, p);
        int denominator = 100 - p;
        return new BunkAdvice(0, (int) ((gap + denominator - 1) / denominator), p);
    }

    public boolean isBelowThreshold() {
        return mustAttend != 0;
    }

    public String text() {
        if (mustAttend < 0) return "You can no longer reach " + thresholdPercent + "%";
        if (mustAttend > 0) {
            return "Attend the next " + mustAttend + " to reach " + thresholdPercent + "%";
        }
        if (canMiss == 0) return "You can't miss any more";
        return "You can miss " + canMiss + " more";
    }
}
