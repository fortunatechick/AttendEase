package io.github.fortunatechick.attendease.data;

import io.github.fortunatechick.attendease.parser.LectureRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Decides, for each parsed row, whether it is a new lecture, an update to a stored one,
// or unchanged. Pure Java so the re-import rules can be tested without a database.
//
// Rules for a lecture that already exists (matched on the natural key):
//  - its PDF status, Sr No., batch and end time always follow the newest PDF;
//  - its effective status follows the PDF too, unless the student owns it:
//    a pending or approved leave, or a manual override. Those are never overwritten;
//  - a pending-leave lecture the PDF now shows as P/E/L is reported as resolved by the
//    college (the caller approves it); one still showing A stays pending;
//  - where the student's own decision and the PDF disagree, the lecture is reported as
//    a conflict and left alone for the student to choose.
public class ImportPlanner {

    public static class Plan {
        public final List<Lecture> inserts = new ArrayList<>();
        public final List<Lecture> updates = new ArrayList<>();
        public int unchanged;
        // Pending-leave lectures that the new PDF now shows as P, E or L.
        public final List<Lecture> resolvedByCollege = new ArrayList<>();
        // Pending-leave lectures the new PDF still shows as absent.
        public final List<Lecture> stillAbsent = new ArrayList<>();
        // Manually overridden lectures the new PDF disagrees with.
        public final List<Lecture> conflicts = new ArrayList<>();
        // Lectures the student recorded as a rejected leave, which the PDF now shows as attended.
        public final List<Lecture> leaveConflicts = new ArrayList<>();
        // Lectures typed in by hand that this PDF now covers; they become PDF lectures.
        public int mergedManual;
    }

    public static Plan plan(Map<String, Lecture> existingByKey, List<LectureRecord> records,
                            Map<String, Long> subjectIdByName, Long importRunId) {
        return plan(existingByKey, records, subjectIdByName, importRunId, Collections.<Long, Long>emptyMap());
    }

    // rejectedLeaveByLecture: lecture id -> leave id, for lectures whose leave was rejected.
    public static Plan plan(Map<String, Lecture> existingByKey, List<LectureRecord> records,
                            Map<String, Long> subjectIdByName, Long importRunId,
                            Map<Long, Long> rejectedLeaveByLecture) {
        Plan plan = new Plan();

        Map<String, Lecture> handAddedBySlot = new HashMap<>();
        for (Lecture l : existingByKey.values()) {
            if (l.isHandAdded()) {
                handAddedBySlot.put(Lecture.slotKey(l.subjectId, l.dateMillis, l.startMinutes, l.component), l);
            }
        }

        for (LectureRecord r : records) {
            Long subjectId = subjectIdByName.get(r.subjectName);
            if (subjectId == null) {
                throw new IllegalStateException("No subject id for " + r.subjectName);
            }

            String key = Lecture.naturalKey(r.rawCourseName, r.dateMillis, r.startTime);
            Lecture existing = existingByKey.get(key);

            if (existing == null) {
                Lecture handAdded = handAddedBySlot.remove(
                        Lecture.slotKey(subjectId, r.dateMillis, r.startMinutes(), r.component));
                if (handAdded != null) {
                    Lecture merged = mergeIntoHandAdded(handAdded, r, importRunId, plan);
                    plan.updates.add(merged);
                    plan.mergedManual++;
                    existingByKey.put(key, merged);
                    continue;
                }

                Lecture l = new Lecture();
                l.subjectId = subjectId;
                l.component = r.component;
                l.dateMillis = r.dateMillis;
                l.startTime = r.startTime;
                l.startMinutes = r.startMinutes();
                l.endTime = r.endTime;
                l.rawCourseName = r.rawCourseName;
                l.batchLabel = r.batchLabel;
                l.srNo = r.srNo;
                l.sourceStatus = r.status;
                l.effectiveStatus = Status.fromSource(r.status);
                l.importRunId = importRunId;
                plan.inserts.add(l);
                // Guard against the same key appearing twice in one PDF.
                existingByKey.put(key, l);
                continue;
            }

            Lecture updated = existing.copy();
            updated.subjectId = subjectId;
            updated.component = r.component;
            updated.endTime = r.endTime;
            updated.batchLabel = r.batchLabel;
            updated.srNo = r.srNo;
            updated.sourceStatus = r.status;

            String fromPdf = Status.fromSource(r.status);
            if (Status.PENDING_LEAVE.equals(existing.effectiveStatus)) {
                if (Status.PRESENT.equals(fromPdf)) plan.resolvedByCollege.add(updated);
                else if (Status.ABSENT.equals(fromPdf)) plan.stillAbsent.add(updated);
            } else if (existing.manualOverride) {
                if (!Status.NOT_UPDATED.equals(fromPdf) && !fromPdf.equals(existing.effectiveStatus)) {
                    plan.conflicts.add(updated);
                }
            } else if (!Status.isStudentOwned(existing.effectiveStatus)) {
                boolean rejectedByStudent = rejectedLeaveByLecture.containsKey(existing.id);
                if (rejectedByStudent && Status.PRESENT.equals(fromPdf)
                        && !Status.PRESENT.equals(existing.effectiveStatus)) {
                    plan.leaveConflicts.add(updated);
                } else {
                    updated.effectiveStatus = fromPdf;
                }
            }

            if (updated.sameContentAs(existing)) {
                plan.unchanged++;
            } else {
                updated.importRunId = importRunId;
                plan.updates.add(updated);
            }
        }
        return plan;
    }

    // The PDF now covers a lecture the student typed in. It takes on the PDF's identity so
    // later imports match it normally. If the student's mark agrees with the PDF (or the PDF
    // says NU) it is simply absorbed; if they disagree the student's mark is kept as a conflict.
    private static Lecture mergeIntoHandAdded(Lecture handAdded, LectureRecord r, Long importRunId, Plan plan) {
        Lecture merged = handAdded.copy();
        merged.rawCourseName = r.rawCourseName;
        merged.startTime = r.startTime;
        merged.startMinutes = r.startMinutes();
        merged.endTime = r.endTime;
        merged.batchLabel = r.batchLabel;
        merged.srNo = r.srNo;
        merged.sourceStatus = r.status;
        merged.importRunId = importRunId;

        String fromPdf = Status.fromSource(r.status);
        if (Status.PENDING_LEAVE.equals(handAdded.effectiveStatus)) {
            if (Status.PRESENT.equals(fromPdf)) plan.resolvedByCollege.add(merged);
            else if (Status.ABSENT.equals(fromPdf)) plan.stillAbsent.add(merged);
        } else if (Status.isStudentOwned(handAdded.effectiveStatus) || Status.NOT_UPDATED.equals(fromPdf)) {
            // Approved leave, or nothing official yet: the student's record stands.
        } else if (fromPdf.equals(handAdded.effectiveStatus)) {
            merged.manualOverride = false;
        } else {
            plan.conflicts.add(merged);
        }
        return merged;
    }
}
