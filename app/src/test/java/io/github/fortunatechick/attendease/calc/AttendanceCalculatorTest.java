package io.github.fortunatechick.attendease.calc;

import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.Status;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AttendanceCalculatorTest {

    private static final double EPS = 0.05;

    private final AttendanceCalculator counted =
            new AttendanceCalculator(ApprovalConvention.COUNTED_AS_ATTENDED, 80);
    private final AttendanceCalculator excluded =
            new AttendanceCalculator(ApprovalConvention.EXCLUDED_FROM_TOTAL, 80);

    // ---- The worked example from the brief: 26 attended, 7 absent, leave over 2 absences ----

    @Test
    public void workedExampleBeforeLeave() {
        AttendanceStats s = counted.stats(lectures(26, 7, 0, 0, 0));
        assertEquals(78.8, s.confirmedPercent(), EPS);
        assertEquals("78.8%", s.displayText());
        assertFalse(s.hasPending());
    }

    @Test
    public void workedExampleWithPendingLeaveCountedConvention() {
        AttendanceStats s = counted.stats(lectures(26, 5, 2, 0, 0));
        assertEquals(26, s.decidedAttended());
        assertEquals(31, s.decidedHeld());
        assertEquals(83.9, s.confirmedPercent(), EPS);
        assertEquals(84.8, s.ifApprovedPercent(), EPS);
        assertEquals(78.8, s.ifRejectedPercent(), EPS);
        assertEquals("78.8% – 84.8%", s.displayText());
        assertEquals("2 lectures awaiting decision", s.pendingText());
    }

    @Test
    public void workedExampleWithPendingLeaveExcludedConvention() {
        AttendanceStats s = excluded.stats(lectures(26, 5, 2, 0, 0));
        assertEquals(83.9, s.confirmedPercent(), EPS);
        assertEquals(83.9, s.ifApprovedPercent(), EPS);
        assertEquals(78.8, s.ifRejectedPercent(), EPS);
        assertEquals("78.8% – 83.9%", s.displayText());
    }

    @Test
    public void approvedLeaveCountedAsAttended() {
        AttendanceStats s = counted.stats(lectures(26, 5, 0, 2, 0));
        assertEquals(28, s.decidedAttended());
        assertEquals(33, s.decidedHeld());
        assertEquals("84.8%", s.displayText());
    }

    @Test
    public void approvedLeaveExcludedFromTotal() {
        AttendanceStats s = excluded.stats(lectures(26, 5, 0, 2, 0));
        assertEquals(26, s.decidedAttended());
        assertEquals(31, s.decidedHeld());
        assertEquals("83.9%", s.displayText());
    }

    @Test
    public void rejectedLeaveIsBackToAbsent() {
        // Rejection restores the original A, so the lectures are plain absences again.
        assertEquals("78.8%", counted.stats(lectures(26, 7, 0, 0, 0)).displayText());
    }

    // ---- Rule 1: not-updated lectures are in neither number ----

    @Test
    public void notUpdatedIsExcluded() {
        AttendanceStats s = counted.stats(lectures(26, 7, 0, 0, 6));
        assertEquals(33, s.decidedHeld());
        assertEquals(6, s.notUpdated);
        assertEquals(78.8, s.confirmedPercent(), EPS);
    }

    // ---- Standing ----

    @Test
    public void standingAgainstThreshold() {
        assertEquals(AttendanceStats.Standing.SAFE, counted.stats(lectures(40, 10, 0, 0, 0)).standing());
        assertEquals(AttendanceStats.Standing.AT_RISK, counted.stats(lectures(26, 7, 0, 0, 0)).standing());
        assertEquals(AttendanceStats.Standing.DEPENDS_ON_LEAVE, counted.stats(lectures(26, 5, 2, 0, 0)).standing());
        assertEquals(AttendanceStats.Standing.SAFE, counted.stats(lectures(40, 2, 1, 0, 0)).standing());
        assertEquals(AttendanceStats.Standing.NO_DATA, counted.stats(lectures(0, 0, 0, 0, 3)).standing());
        assertEquals("–", counted.stats(lectures(0, 0, 0, 0, 0)).displayText());
    }

    // ---- Bunk / recover ----

    @Test
    public void mustAttendWhenBelow() {
        BunkAdvice a = BunkAdvice.of(26, 33, 80);
        assertEquals(2, a.mustAttend);          // 28/35 = 80%
        assertEquals("Attend the next 2 to reach 80%", a.text());
    }

    @Test
    public void canMissWhenAbove() {
        BunkAdvice a = BunkAdvice.of(44, 50, 80);
        assertEquals(5, a.canMiss);             // 44/55 = 80%
        assertEquals("You can miss 5 more", a.text());
    }

    @Test
    public void exactlyAtThreshold() {
        BunkAdvice a = BunkAdvice.of(40, 50, 80);
        assertEquals(0, a.canMiss);
        assertEquals(0, a.mustAttend);
        assertFalse(a.isBelowThreshold());
        assertEquals("You can't miss any more", a.text());
    }

    @Test
    public void adviceMatchesBruteForce() {
        for (int p : new int[]{50, 75, 80, 85, 90}) {
            for (int held = 1; held <= 60; held++) {
                for (int att = 0; att <= held; att++) {
                    BunkAdvice a = BunkAdvice.of(att, held, p);
                    if (100 * att >= p * held) {
                        int miss = 0;
                        while (100 * att >= p * (held + miss + 1)) miss++;
                        assertEquals(att + "/" + held + "@" + p, miss, a.canMiss);
                    } else {
                        int need = 0;
                        while (100 * (att + need) < p * (held + need)) need++;
                        assertEquals(att + "/" + held + "@" + p, need, a.mustAttend);
                    }
                }
            }
        }
    }

    @Test
    public void hundredPercentThresholdCanBeUnreachable() {
        assertEquals(-1, BunkAdvice.of(9, 10, 100).mustAttend);
        assertEquals(0, BunkAdvice.of(10, 10, 100).canMiss);
    }

    @Test
    public void worstCaseAdviceTreatsPendingAsAbsent() {
        AttendanceStats s = counted.stats(lectures(26, 5, 2, 0, 0));
        assertEquals(0, s.advice().mustAttend);          // 26/31 is above 80%
        assertEquals(1, s.advice().canMiss);
        assertEquals(2, s.worstCaseAdvice().mustAttend); // 26/33 is not
    }

    @Test
    public void customThreshold() {
        AttendanceCalculator c75 = new AttendanceCalculator(ApprovalConvention.COUNTED_AS_ATTENDED, 75);
        AttendanceStats s = c75.stats(lectures(26, 7, 0, 0, 0));
        assertEquals(AttendanceStats.Standing.SAFE, s.standing());
        assertEquals(1, s.advice().canMiss);   // 26/34 = 76.5%, 26/35 = 74.3%
    }

    @Test
    public void conventionPreferenceParsing() {
        assertEquals(ApprovalConvention.COUNTED_AS_ATTENDED, ApprovalConvention.fromPreference(null));
        assertEquals(ApprovalConvention.EXCLUDED_FROM_TOTAL,
                ApprovalConvention.fromPreference("EXCLUDED_FROM_TOTAL"));
        assertEquals(ApprovalConvention.COUNTED_AS_ATTENDED, ApprovalConvention.fromPreference("junk"));
    }

    // ---- The real report ----

    @Test
    public void realReportFigures() throws Exception {
        ParseResult report = new AttendanceParser().parse(readResource("sap_report_2026-10-11.txt"));
        Map<String, Long> ids = new HashMap<>();
        List<Lecture> all = new ArrayList<>();
        for (LectureRecord r : report.records) {
            Long id = ids.get(r.subjectName);
            if (id == null) {
                id = (long) ids.size() + 1;
                ids.put(r.subjectName, id);
            }
            Lecture l = new Lecture();
            l.subjectId = id;
            l.component = r.component;
            l.effectiveStatus = Status.fromSource(r.status);
            all.add(l);
        }

        AttendanceStats overall = counted.stats(all);
        assertEquals(268, overall.decidedAttended());
        assertEquals(327, overall.decidedHeld());
        assertEquals("82.0%", overall.displayText());
        assertEquals(59, overall.missed());

        Map<Long, AttendanceCalculator.SubjectSummary> bySubject = counted.summariseBySubject(all);
        assertEquals(8, bySubject.size());

        AttendanceCalculator.SubjectSummary ai = bySubject.get(ids.get("Artificial Intelligence"));
        assertFigures(ai.combined, 37, 50, "74.0%");
        assertFigures(ai.theory, 19, 26, "73.1%");
        assertFigures(ai.practical, 18, 24, "75.0%");
        assertEquals(15, ai.combined.advice().mustAttend);

        assertSubject(bySubject, ids, "Operating Systems", 44, 50, "88.0%", "You can miss 5 more");
        assertSubject(bySubject, ids, "Research Methodology", 27, 35, "77.1%", "Attend the next 5 to reach 80%");
        assertSubject(bySubject, ids, "RAG Sys&Agen WorkflowithPine&LaGr", 15, 15, "100.0%", "You can miss 3 more");
        assertSubject(bySubject, ids, "Software Engineering", 35, 45, "77.8%", "Attend the next 5 to reach 80%");
        assertSubject(bySubject, ids, "Image and Video Processing", 45, 54, "83.3%", "You can miss 2 more");
        assertSubject(bySubject, ids, "Mobile Application Development", 38, 46, "82.6%", "You can miss 1 more");
        assertSubject(bySubject, ids, "Financial Der", 27, 32, "84.4%", "You can miss 1 more");
    }

    private static void assertSubject(Map<Long, AttendanceCalculator.SubjectSummary> bySubject,
                                      Map<String, Long> ids, String name, int attended, int held,
                                      String display, String advice) {
        AttendanceCalculator.SubjectSummary s = bySubject.get(ids.get(name));
        assertNotNull(name, s);
        assertFigures(s.combined, attended, held, display);
        assertEquals(name, advice, s.combined.advice().text());
    }

    private static void assertFigures(AttendanceStats s, int attended, int held, String display) {
        assertEquals(attended, s.decidedAttended());
        assertEquals(held, s.decidedHeld());
        assertEquals(display, s.displayText());
        assertTrue(s.hasData());
    }

    private static List<Lecture> lectures(int present, int absent, int pending, int exempt, int notUpdated) {
        List<Lecture> list = new ArrayList<>();
        add(list, Status.PRESENT, present);
        add(list, Status.ABSENT, absent);
        add(list, Status.PENDING_LEAVE, pending);
        add(list, Status.EXEMPT, exempt);
        add(list, Status.NOT_UPDATED, notUpdated);
        return list;
    }

    private static void add(List<Lecture> list, String status, int count) {
        for (int i = 0; i < count; i++) {
            Lecture l = new Lecture();
            l.subjectId = 1;
            l.component = "T";
            l.effectiveStatus = status;
            list.add(l);
        }
    }

    private static String readResource(String name) throws Exception {
        try (InputStream in = AttendanceCalculatorTest.class.getClassLoader().getResourceAsStream(name)) {
            Assume.assumeNotNull(in);   // sample report is private and git-ignored; skip without it
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
