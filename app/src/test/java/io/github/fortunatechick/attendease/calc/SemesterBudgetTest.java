package io.github.fortunatechick.attendease.calc;

import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.Status;
import io.github.fortunatechick.attendease.data.Subject;
import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.LectureRecord;
import io.github.fortunatechick.attendease.parser.ParseResult;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SemesterBudgetTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static AttendanceStats stats(int present, int absent, int pending, int exempt,
                                         ApprovalConvention convention, int threshold) {
        return new AttendanceStats(present, absent, pending, exempt, 0, convention, threshold);
    }

    private static AttendanceStats stats(int present, int absent) {
        return stats(present, absent, 0, 0, ApprovalConvention.COUNTED_AS_ATTENDED, 80);
    }

    // ---- The college rules ----

    @Test
    public void fourLecturesAWeekIsSixtyWithTwelveMissesAllowed() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(0, 0));
        assertEquals(60, b.total);
        assertEquals(12, b.maxMisses);
        assertEquals(12, b.canStillMiss());
    }

    @Test
    public void threeLecturesAWeekIsFortyFiveWithNineMissesAllowed() {
        SemesterBudget b = new SemesterBudget(15, 3, stats(0, 0));
        assertEquals(45, b.total);
        assertEquals(9, b.maxMisses);
    }

    @Test
    public void missingExactlyTheLimitEndsAtExactlyEightyPercent() {
        for (int perWeek : new int[]{3, 4}) {
            SemesterBudget b = new SemesterBudget(15, perWeek, stats(0, 0));
            int attended = b.total - b.maxMisses;
            assertTrue(attended * 100 >= 80 * b.total);
            // One more miss and the final figure drops below 80%.
            assertTrue((attended - 1) * 100 < 80 * b.total);
        }
    }

    @Test
    public void absencesComeOutOfTheBudget() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(30, 8));
        assertEquals(4, b.canStillMiss());
        assertEquals(22, b.lecturesLeft);
        assertEquals("You can miss 4 more this semester", b.adviceText());
        assertEquals("60 lectures · 12 misses allowed · 8 used", b.summaryText());
    }

    @Test
    public void exactlyAtTheLimit() {
        SemesterBudget b = new SemesterBudget(15, 3, stats(20, 9));
        assertEquals(0, b.canStillMiss());
        assertEquals("No misses left this semester: one more absence takes you under 80%", b.adviceText());
    }

    @Test
    public void overTheLimitShowsBestPossibleFinish() {
        // AI on the real report: 13 absences against a limit of 12.
        SemesterBudget b = new SemesterBudget(15, 4, stats(37, 13));
        assertTrue(b.isOverLimit());
        assertEquals(-1, b.canStillMiss());
        assertEquals(78.3, b.bestFinalPercent(), 0.05);
        assertEquals("Over the semester limit by 1. Attending every remaining lecture ends at 78.3%.", b.adviceText());
    }

    @Test
    public void extraLecturesBeyondThePlanGrowTheTotal() {
        // 3 a week planned (45) but 48 already recorded, e.g. make-up classes.
        SemesterBudget b = new SemesterBudget(15, 3, stats(40, 8));
        assertEquals(48, b.total);
        assertEquals(9, b.maxMisses);     // floor(48 x 0.2) = 9
        assertEquals(0, b.lecturesLeft);
    }

    @Test
    public void safeWhenTheBudgetCoversEveryRemainingLecture() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(55, 0));
        assertEquals(5, b.lecturesLeft);
        assertEquals("Safe for the semester: you could miss all 5 remaining lectures", b.adviceText());
    }

    @Test
    public void pendingLeaveShowsTheWorstCase() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(30, 8, 2, 0, ApprovalConvention.COUNTED_AS_ATTENDED, 80));
        assertEquals(4, b.canStillMiss());
        assertEquals(2, b.canStillMissWorstCase());
        assertEquals("You can miss 4 more this semester (2 if the leave is rejected)", b.adviceText());
    }

    @Test
    public void approvedLeaveCountedAsAttendedIsNotAMiss() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(30, 8, 0, 2, ApprovalConvention.COUNTED_AS_ATTENDED, 80));
        assertEquals(60, b.effectiveTotal);
        assertEquals(4, b.canStillMiss());
    }

    @Test
    public void approvedLeaveExcludedFromTotalShrinksTheBudget() {
        SemesterBudget b = new SemesterBudget(15, 4, stats(30, 8, 0, 2, ApprovalConvention.EXCLUDED_FROM_TOTAL, 80));
        assertEquals(58, b.effectiveTotal);
        assertEquals(11, b.maxMisses);    // floor(58 x 0.2)
        assertEquals(3, b.canStillMiss());
    }

    @Test
    public void followsThresholdAndSemesterLength() {
        assertEquals(15, new SemesterBudget(15, 4, stats(0, 0, 0, 0, ApprovalConvention.COUNTED_AS_ATTENDED, 75)).maxMisses);
        assertEquals(11, new SemesterBudget(14, 4, stats(0, 0)).maxMisses);   // floor(56 x 0.2)
    }

    @Test
    public void unknownPatternIsReported() {
        assertFalse(new SemesterBudget(15, 0, stats(0, 0)).isKnown());
    }

    // ---- Estimating lectures per week ----

    @Test
    public void weeklyPatternUsesTheMostCommonWeek() {
        List<Lecture> l = new ArrayList<>();
        // Weeks 0-3: 2 theory each; week 4: holiday (0); week 5: make-up (4). Lab: 2 a week for 3 weeks.
        for (int week = 0; week < 6; week++) {
            int theory = week < 4 ? 2 : (week == 5 ? 4 : 0);
            for (int i = 0; i < theory; i++) l.add(lecture("T", week * 7 + i));
        }
        for (int week = 0; week < 3; week++) {
            l.add(lecture("P", week * 7 + 2));
            l.add(lecture("P", week * 7 + 2));
        }
        WeeklyPattern p = WeeklyPattern.infer(l, 0);
        assertEquals(2, p.theory);
        assertEquals(2, p.lab);
        assertEquals(4, p.total());
    }

    @Test
    public void tiesGoToTheLargerCount() {
        List<Lecture> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) l.add(lecture("T", i));        // week 0: 4
        for (int i = 0; i < 2; i++) l.add(lecture("T", 7 + i));    // week 1: 2
        assertEquals(4, WeeklyPattern.infer(l, 0).theory);
    }

    @Test
    public void studentSetPatternOverridesTheEstimate() {
        List<Lecture> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) l.add(lecture("T", i));
        Subject s = new Subject(1, "Financial Der", "Financial Der", 0);
        assertEquals(4, AttendanceCalculator.patternFor(s, l, 0).total());
        s.theoryPerWeek = 3;
        s.labPerWeek = 0;
        assertEquals(3, AttendanceCalculator.patternFor(s, l, 0).total());
    }

    // ---- The real report (skipped when the private sample file is absent) ----

    @Test
    public void realReportBudgets() throws Exception {
        String text = readResource("sap_report_2026-10-11.txt");
        Assume.assumeNotNull(text);
        ParseResult report = new AttendanceParser().parse(text);

        Map<String, List<Lecture>> bySubject = new HashMap<>();
        long start = Long.MAX_VALUE;
        for (LectureRecord r : report.records) {
            Lecture l = new Lecture();
            l.component = r.component;
            l.dateMillis = r.dateMillis;
            l.effectiveStatus = Status.fromSource(r.status);
            List<Lecture> list = bySubject.get(r.subjectName);
            if (list == null) bySubject.put(r.subjectName, list = new ArrayList<>());
            list.add(l);
            start = Math.min(start, r.dateMillis);
        }
        AttendanceCalculator calc = new AttendanceCalculator(ApprovalConvention.COUNTED_AS_ATTENDED, 80, 15);

        assertBudget(calc, bySubject, start, "Artificial Intelligence", 60, 12, -1);
        assertBudget(calc, bySubject, start, "Operating Systems", 60, 12, 6);
        assertBudget(calc, bySubject, start, "Software Engineering", 60, 12, 2);
        assertBudget(calc, bySubject, start, "Image and Video Processing", 60, 12, 3);
        assertBudget(calc, bySubject, start, "Mobile Application Development", 60, 12, 4);
        assertBudget(calc, bySubject, start, "Research Methodology", 45, 9, 1);
    }

    private static void assertBudget(AttendanceCalculator calc, Map<String, List<Lecture>> bySubject, long start,
                                     String name, int total, int maxMisses, int canStillMiss) {
        Subject s = new Subject(1, name, name, 0);
        SemesterBudget b = calc.budget(s, bySubject.get(name), start);
        assertEquals(name + " total", total, b.total);
        assertEquals(name + " max misses", maxMisses, b.maxMisses);
        assertEquals(name + " can still miss", canStillMiss, b.canStillMiss());
    }

    private static Lecture lecture(String component, int day) {
        Lecture l = new Lecture();
        l.component = component;
        l.dateMillis = day * DAY;
        l.effectiveStatus = Status.PRESENT;
        return l;
    }

    private static String readResource(String name) throws Exception {
        try (InputStream in = SemesterBudgetTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
