package io.github.fortunatechick.attendease.calc;

import io.github.fortunatechick.attendease.data.Lecture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Estimates how many theory and lab lectures a subject has in a normal week, from the
// lectures recorded so far. Uses the most common weekly count, so holiday weeks (fewer)
// and make-up weeks (more) don't skew it. Ties go to the larger count.
public final class WeeklyPattern {

    private static final long WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000;

    public final int theory;
    public final int lab;

    public WeeklyPattern(int theory, int lab) {
        this.theory = theory;
        this.lab = lab;
    }

    public int total() {
        return theory + lab;
    }

    public static WeeklyPattern infer(List<Lecture> lectures, long semesterStartMillis) {
        return new WeeklyPattern(mode(lectures, "T", semesterStartMillis), mode(lectures, "P", semesterStartMillis));
    }

    private static int mode(List<Lecture> lectures, String component, long start) {
        Map<Long, Integer> perWeek = new HashMap<>();
        for (Lecture l : lectures) {
            if (!component.equals(l.component)) continue;
            long week = Math.max(0, (l.dateMillis - start) / WEEK_MILLIS);
            Integer n = perWeek.get(week);
            perWeek.put(week, n == null ? 1 : n + 1);
        }
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int count : perWeek.values()) {
            Integer f = frequency.get(count);
            frequency.put(count, f == null ? 1 : f + 1);
        }
        int best = 0, bestFrequency = 0;
        for (Map.Entry<Integer, Integer> e : frequency.entrySet()) {
            int count = e.getKey(), f = e.getValue();
            if (f > bestFrequency || (f == bestFrequency && count > best)) {
                best = count;
                bestFrequency = f;
            }
        }
        return best;
    }
}
