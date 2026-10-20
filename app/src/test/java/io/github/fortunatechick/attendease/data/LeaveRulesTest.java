package io.github.fortunatechick.attendease.data;

import io.github.fortunatechick.attendease.calc.ApprovalConvention;
import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.LectureRecord;
import io.github.fortunatechick.attendease.parser.ParseResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LeaveRulesTest {

    @Test
    public void pendingLeaveIsNeitherAttendedNorMissed() {
        LeaveRules.Effect e = LeaveRules.effectOf(LeaveRules.PENDING, "A", Status.ABSENT, false);
        assertEquals(Status.PENDING_LEAVE, e.effectiveStatus);
        assertFalse(e.manualOverride);
    }

    @Test
    public void approvedLeaveIsExempt() {
        assertEquals(Status.EXEMPT, LeaveRules.effectOf(LeaveRules.APPROVED, "A", Status.ABSENT, false).effectiveStatus);
    }

    @Test
    public void rejectedLeaveFollowsLatestPdf() {
        assertEquals(Status.ABSENT, LeaveRules.effectOf(LeaveRules.REJECTED, "A", Status.ABSENT, false).effectiveStatus);
        // The PDF moved on while the leave was pending; rejection uses the newest PDF status.
        assertEquals(Status.NOT_UPDATED, LeaveRules.effectOf(LeaveRules.REJECTED, "NU", Status.ABSENT, false).effectiveStatus);
    }

    @Test
    public void rejectedLeaveRestoresManualMark() {
        LeaveRules.Effect e = LeaveRules.effectOf(LeaveRules.REJECTED, "NU", Status.ABSENT, true);
        assertEquals(Status.ABSENT, e.effectiveStatus);
        assertTrue(e.manualOverride);
    }

    @Test
    public void requestStatusIsDerivedFromDecisions() {
        assertEquals(LeaveRequest.SUBMITTED, LeaveRules.requestStatus(Arrays.asList("PENDING", "PENDING")));
        assertEquals(LeaveRequest.APPROVED, LeaveRules.requestStatus(Arrays.asList("APPROVED", "APPROVED")));
        assertEquals(LeaveRequest.REJECTED, LeaveRules.requestStatus(Collections.singletonList("REJECTED")));
        assertEquals(LeaveRequest.PARTIAL, LeaveRules.requestStatus(Arrays.asList("APPROVED", "REJECTED")));
        assertEquals(LeaveRequest.PARTIAL, LeaveRules.requestStatus(Arrays.asList("APPROVED", "PENDING")));
        assertEquals(LeaveRequest.SUBMITTED, LeaveRules.requestStatus(Collections.<String>emptyList()));
    }

    @Test
    public void lecturesAlreadyInLeaveCannotBeAddedAgain() {
        Lecture l = new Lecture();
        l.effectiveStatus = Status.ABSENT;
        assertTrue(LeaveRules.canBeAddedToLeave(l));
        l.effectiveStatus = Status.PENDING_LEAVE;
        assertFalse(LeaveRules.canBeAddedToLeave(l));
        l.effectiveStatus = Status.EXEMPT;
        assertFalse(LeaveRules.canBeAddedToLeave(l));
    }

    // Acceptance check on the real report: a leave over 2 AI absences.
    // AI is 37/50 = 74.0%. With 2 absences pending: confirmed 37/48 = 77.1%,
    // if approved 39/50 = 78.0%, if rejected 37/50 = 74.0%.
    @Test
    public void leaveOverTwoAiAbsencesOnRealReport() throws Exception {
        ParseResult report = new AttendanceParser().parse(
                ImportPlannerTest.readResource("sap_report_2026-10-11.txt"));
        List<Lecture> ai = new ArrayList<>();
        for (LectureRecord r : report.recordsFor("Artificial Intelligence")) {
            Lecture l = new Lecture();
            l.subjectId = 1;
            l.component = r.component;
            l.sourceStatus = r.status;
            l.effectiveStatus = Status.fromSource(r.status);
            ai.add(l);
        }
        AttendanceCalculator calc = new AttendanceCalculator(ApprovalConvention.COUNTED_AS_ATTENDED, 80);
        assertEquals("74.0%", calc.stats(ai).displayText());

        List<Lecture> covered = new ArrayList<>();
        for (Lecture l : ai) {
            if (covered.size() < 2 && Status.ABSENT.equals(l.effectiveStatus)) covered.add(l);
        }
        apply(covered, LeaveRules.PENDING);
        AttendanceStats pending = calc.stats(ai);
        assertEquals(77.1, pending.confirmedPercent(), 0.05);
        assertEquals("74.0% – 78.0%", pending.displayText());
        assertEquals("2 lectures awaiting decision", pending.pendingText());

        apply(covered, LeaveRules.APPROVED);
        assertEquals("78.0%", calc.stats(ai).displayText());

        apply(covered, LeaveRules.REJECTED);
        assertEquals("74.0%", calc.stats(ai).displayText());

        AttendanceCalculator excluded = new AttendanceCalculator(ApprovalConvention.EXCLUDED_FROM_TOTAL, 80);
        apply(covered, LeaveRules.APPROVED);
        assertEquals("77.1%", excluded.stats(ai).displayText());
    }

    private static void apply(List<Lecture> lectures, String decision) {
        for (Lecture l : lectures) {
            LeaveRules.Effect e = LeaveRules.effectOf(decision, l.sourceStatus, Status.ABSENT, false);
            l.effectiveStatus = e.effectiveStatus;
            l.manualOverride = e.manualOverride;
        }
    }
}
