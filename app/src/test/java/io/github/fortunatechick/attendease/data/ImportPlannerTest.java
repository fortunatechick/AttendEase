package io.github.fortunatechick.attendease.data;

import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.LectureRecord;
import io.github.fortunatechick.attendease.parser.ParseResult;

import org.junit.Before;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// Simulates the lecture table with a map keyed by natural key, so the re-import rules
// run against the real 327-row report without needing SQLite.
public class ImportPlannerTest {

    private ParseResult report;
    private Map<String, Long> subjectIds;
    private Map<String, Lecture> table;
    private long nextId = 1;

    @Before
    public void setUp() throws Exception {
        report = new AttendanceParser().parse(readResource("sap_report_2026-10-11.txt"));
        subjectIds = new HashMap<>();
        long id = 1;
        for (String name : report.subjectNames()) subjectIds.put(name, id++);
        table = new HashMap<>();
    }

    @Test
    public void firstImportInsertsEveryRow() {
        ImportPlanner.Plan plan = importReport(report.records, 1L);
        assertEquals(327, plan.inserts.size());
        assertEquals(0, plan.updates.size());
        assertEquals(327, table.size());
    }

    @Test
    public void reimportingSamePdfChangesNothing() {
        importReport(report.records, 1L);
        Map<String, Integer> before = snapshot();

        ImportPlanner.Plan second = importReport(report.records, 2L);

        assertEquals(0, second.inserts.size());
        assertEquals(0, second.updates.size());
        assertEquals(327, second.unchanged);
        assertEquals(before, snapshot());
        assertEquals(327, table.size());
    }

    @Test
    public void pendingLeaveSurvivesReimport() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        absent.effectiveStatus = Status.PENDING_LEAVE;
        absent.leaveId = 7L;

        ImportPlanner.Plan plan = importReport(report.records, 2L);

        assertEquals(0, plan.updates.size());
        assertEquals(Status.PENDING_LEAVE, table.get(absent.naturalKey()).effectiveStatus);
        assertEquals(Long.valueOf(7L), table.get(absent.naturalKey()).leaveId);
        assertTrue(plan.resolvedByCollege.isEmpty());
    }

    @Test
    public void pendingLeaveShownPresentByNewPdfIsReportedNotOverwritten() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        absent.effectiveStatus = Status.PENDING_LEAVE;
        absent.leaveId = 7L;

        ImportPlanner.Plan plan = importReport(withStatus(report.records, absent, "E"), 2L);

        assertEquals(1, plan.resolvedByCollege.size());
        Lecture stored = table.get(absent.naturalKey());
        assertEquals("E", stored.sourceStatus);
        assertEquals(Status.PENDING_LEAVE, stored.effectiveStatus);
    }

    @Test
    public void pendingLeaveStillAbsentIsCounted() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        absent.effectiveStatus = Status.PENDING_LEAVE;
        absent.leaveId = 7L;

        ImportPlanner.Plan plan = importReport(report.records, 2L);
        assertEquals(1, plan.stillAbsent.size());
        assertTrue(plan.resolvedByCollege.isEmpty());
    }

    @Test
    public void rejectedLeaveThatPdfShowsAttendedIsAConflict() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");   // student recorded the leave as rejected: still ABSENT
        Map<Long, Long> rejected = new HashMap<>();
        rejected.put(absent.id, 7L);

        Map<String, Lecture> existing = new HashMap<>();
        for (Lecture l : table.values()) existing.put(l.naturalKey(), l.copy());
        ImportPlanner.Plan plan = ImportPlanner.plan(existing,
                withStatus(report.records, absent, "E"), subjectIds, 2L, rejected);

        assertEquals(1, plan.leaveConflicts.size());
        Lecture kept = plan.leaveConflicts.get(0);
        assertEquals(Status.ABSENT, kept.effectiveStatus);
        assertEquals("E", kept.sourceStatus);
    }

    @Test
    public void rejectedLeaveStillAbsentInPdfIsNotAConflict() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        Map<Long, Long> rejected = new HashMap<>();
        rejected.put(absent.id, 7L);

        Map<String, Lecture> existing = new HashMap<>();
        for (Lecture l : table.values()) existing.put(l.naturalKey(), l.copy());
        ImportPlanner.Plan plan = ImportPlanner.plan(existing, report.records, subjectIds, 2L, rejected);
        assertTrue(plan.leaveConflicts.isEmpty());
        assertEquals(0, plan.updates.size());
    }

    // A lecture typed in by hand before the PDF caught up, at the same slot as PDF row 1
    // (Operating Systems theory, 13 Jul, 12:00 PM, present).
    private Lecture handAddedLikeFirstRow(String effectiveStatus) {
        LectureRecord r = report.records.get(0);
        Lecture l = new Lecture();
        l.id = nextId++;
        l.subjectId = subjectIds.get(r.subjectName);
        l.component = r.component;
        l.dateMillis = r.dateMillis;
        l.startTime = "12:00:00 PM";
        l.startMinutes = 12 * 60;
        l.endTime = "1:00:00 PM";
        l.rawCourseName = Lecture.manualCourseName(l.subjectId, l.component);
        l.effectiveStatus = effectiveStatus;
        l.manualOverride = true;
        table.put(l.naturalKey(), l);
        return l;
    }

    @Test
    public void pdfRowAbsorbsMatchingHandAddedLecture() {
        Lecture manual = handAddedLikeFirstRow(Status.PRESENT);

        ImportPlanner.Plan plan = importReport(report.records, 1L);

        assertEquals(1, plan.mergedManual);
        assertEquals(326, plan.inserts.size());
        assertTrue(plan.conflicts.isEmpty());
        // The old manual key is replaced by the PDF row; no double counting.
        table.remove(manual.naturalKey());
        assertEquals(327, table.size());
        Lecture merged = table.get(Lecture.naturalKey(report.records.get(0).rawCourseName,
                report.records.get(0).dateMillis, report.records.get(0).startTime));
        assertEquals(manual.id, merged.id);
        assertFalse(merged.manualOverride);
        assertEquals(1, merged.srNo);
    }

    @Test
    public void handAddedLectureThatDisagreesWithPdfIsAConflict() {
        handAddedLikeFirstRow(Status.ABSENT);

        ImportPlanner.Plan plan = importReport(report.records, 1L);

        assertEquals(1, plan.mergedManual);
        assertEquals(1, plan.conflicts.size());
        assertEquals(Status.ABSENT, plan.conflicts.get(0).effectiveStatus);
        assertTrue(plan.conflicts.get(0).manualOverride);
    }

    @Test
    public void mergedHandAddedLectureIsStableOnReimport() {
        Lecture manual = handAddedLikeFirstRow(Status.PRESENT);
        importReport(report.records, 1L);
        table.remove(manual.naturalKey());

        ImportPlanner.Plan second = importReport(report.records, 2L);
        assertEquals(0, second.inserts.size());
        assertEquals(0, second.updates.size());
        assertEquals(0, second.mergedManual);
        assertEquals(327, second.unchanged);
    }

    @Test
    public void handAddedLectureAtAnotherTimeIsLeftAlone() {
        Lecture manual = handAddedLikeFirstRow(Status.PRESENT);
        manual.startMinutes = 7 * 60;
        ImportPlanner.Plan plan = importReport(report.records, 1L);
        assertEquals(0, plan.mergedManual);
        assertEquals(327, plan.inserts.size());
    }

    @Test
    public void manualOverrideIsKeptAndConflictFlagged() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        absent.effectiveStatus = Status.PRESENT;
        absent.manualOverride = true;

        ImportPlanner.Plan plan = importReport(report.records, 2L);

        assertEquals(Status.PRESENT, table.get(absent.naturalKey()).effectiveStatus);
        assertEquals(1, plan.conflicts.size());
        assertEquals(0, plan.updates.size());
    }

    @Test
    public void approvedLeaveIsNotOverwritten() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");
        absent.effectiveStatus = Status.EXEMPT;

        importReport(report.records, 2L);
        assertEquals(Status.EXEMPT, table.get(absent.naturalKey()).effectiveStatus);
    }

    @Test
    public void ordinaryLectureFollowsNewPdf() {
        importReport(report.records, 1L);
        Lecture absent = firstWithSource("A");

        ImportPlanner.Plan plan = importReport(withStatus(report.records, absent, "P"), 2L);

        assertEquals(1, plan.updates.size());
        assertEquals(Status.PRESENT, table.get(absent.naturalKey()).effectiveStatus);
        assertEquals(Long.valueOf(2L), table.get(absent.naturalKey()).importRunId);
    }

    @Test
    public void notUpdatedRowsAreStoredAsNotUpdated() {
        LectureRecord r = report.records.get(0);
        LectureRecord nu = new LectureRecord(r.srNo, r.rawCourseName, r.subjectName, r.component,
                r.batchLabel, r.dateText, r.dateMillis, r.startTime, r.endTime, "NU");
        importReport(java.util.Collections.singletonList(nu), 1L);
        assertEquals(Status.NOT_UPDATED, table.values().iterator().next().effectiveStatus);
    }

    // Applies a plan to the in-memory table, the way AttendanceRepository applies it to SQLite.
    private ImportPlanner.Plan importReport(List<LectureRecord> records, Long runId) {
        Map<String, Lecture> existing = new HashMap<>();
        for (Lecture l : table.values()) existing.put(l.naturalKey(), l.copy());

        ImportPlanner.Plan plan = ImportPlanner.plan(existing, records, subjectIds, runId);
        for (Lecture l : plan.inserts) {
            l.id = nextId++;
            table.put(l.naturalKey(), l);
        }
        for (Lecture l : plan.updates) table.put(l.naturalKey(), l);
        return plan;
    }

    private Map<String, Integer> snapshot() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Lecture l : table.values()) {
            String key = l.subjectId + "/" + l.component + "/" + l.effectiveStatus;
            Integer c = counts.get(key);
            counts.put(key, c == null ? 1 : c + 1);
        }
        return counts;
    }

    private Lecture firstWithSource(String status) {
        for (LectureRecord r : report.records) {
            if (r.status.equals(status)) {
                Lecture l = table.get(Lecture.naturalKey(r.rawCourseName, r.dateMillis, r.startTime));
                assertNotNull(l);
                return l;
            }
        }
        throw new AssertionError("no row with status " + status);
    }

    private static List<LectureRecord> withStatus(List<LectureRecord> records, Lecture target, String status) {
        List<LectureRecord> copy = new java.util.ArrayList<>();
        boolean changed = false;
        for (LectureRecord r : records) {
            if (Lecture.naturalKey(r.rawCourseName, r.dateMillis, r.startTime).equals(target.naturalKey())) {
                r = new LectureRecord(r.srNo, r.rawCourseName, r.subjectName, r.component, r.batchLabel,
                        r.dateText, r.dateMillis, r.startTime, r.endTime, status);
                changed = true;
            }
            copy.add(r);
        }
        assertTrue(changed);
        return copy;
    }

    static String readResource(String name) throws Exception {
        try (InputStream in = ImportPlannerTest.class.getClassLoader().getResourceAsStream(name)) {
            Assume.assumeNotNull(in);   // sample report is private and git-ignored; skip without it
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Test
    public void lectureCopyHasSameContent() {
        importReport(report.records, 1L);
        Lecture l = table.values().iterator().next();
        assertTrue(l.copy().sameContentAs(l));
        Lecture changed = l.copy();
        changed.sourceStatus = "NU";
        assertFalse(changed.sameContentAs(l));
    }
}
