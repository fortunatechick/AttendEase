package io.github.fortunatechick.attendease.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

// Medical leave operations. Each one runs in a transaction so a request and its
// lectures can never get out of step.
public class LeaveRepository {

    private final AttendEaseDb helper;
    public final LeaveDao leaves;
    private final LectureDao lectures;

    public LeaveRepository(Context context) {
        this(AttendEaseDb.get(context));
    }

    LeaveRepository(AttendEaseDb helper) {
        this.helper = helper;
        this.leaves = new LeaveDao(helper);
        this.lectures = new LectureDao(helper);
    }

    // Saves the request and moves every chosen lecture to PENDING_LEAVE. The lecture's
    // PDF status stays in source_status, so it can be restored on rejection.
    public long create(LeaveRequest request, List<Long> lectureIds) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long leaveId = leaves.insert(request);
            for (long lectureId : lectureIds) {
                Lecture l = lectures.byId(lectureId);
                if (l == null || !LeaveRules.canBeAddedToLeave(l)) continue;
                leaves.link(leaveId, l.id, l.effectiveStatus, l.manualOverride);
                applyEffect(l, leaveId, LeaveRules.PENDING, l.effectiveStatus, l.manualOverride);
            }
            db.setTransactionSuccessful();
            return leaveId;
        } finally {
            db.endTransaction();
        }
    }

    public void decide(long leaveId, long lectureId, String decision) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            decideOne(leaveId, lectureId, decision);
            refreshStatus(leaveId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void decideAll(long leaveId, String decision) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (LeaveRequest.Link link : leaves.links(leaveId)) {
                decideOne(leaveId, link.lectureId, decision);
            }
            refreshStatus(leaveId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // Deletes the request and puts every lecture back the way it was before.
    public void withdraw(long leaveId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (LeaveRequest.Link link : leaves.links(leaveId)) {
                Lecture l = lectures.byId(link.lectureId);
                if (l != null) {
                    applyEffect(l, leaveId, LeaveRules.REJECTED, link.previousStatus, link.previousManual);
                }
            }
            leaves.delete(leaveId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // "I've submitted a medical certificate for this lecture", from the subject screen.
    // Joins an open request that already covers the lecture's date, otherwise starts a
    // one-day request. Returns the leave id, or -1 if the lecture can't take a leave.
    public long markOnLeave(long lectureId, String submittedTo, long submittedOn) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Lecture l = lectures.byId(lectureId);
            if (l == null || !LeaveRules.canBeAddedToLeave(l)) return -1;

            LeaveRequest open = null;
            for (LeaveRequest r : leaves.all()) {
                boolean covers = r.fromDate <= l.dateMillis && l.dateMillis <= r.toDate;
                boolean undecided = LeaveRequest.SUBMITTED.equals(r.status) || LeaveRequest.PARTIAL.equals(r.status);
                if (covers && undecided) {
                    open = r;
                    break;
                }
            }
            long leaveId;
            if (open != null) {
                leaveId = open.id;
                leaves.link(leaveId, l.id, l.effectiveStatus, l.manualOverride);
                applyEffect(l, leaveId, LeaveRules.PENDING, l.effectiveStatus, l.manualOverride);
                refreshStatus(leaveId);
            } else {
                LeaveRequest r = new LeaveRequest();
                r.fromDate = l.dateMillis;
                r.toDate = l.dateMillis;
                r.submittedTo = submittedTo;
                r.submittedOn = submittedOn;
                leaveId = leaves.insert(r);
                leaves.link(leaveId, l.id, l.effectiveStatus, l.manualOverride);
                applyEffect(l, leaveId, LeaveRules.PENDING, l.effectiveStatus, l.manualOverride);
            }
            db.setTransactionSuccessful();
            return leaveId;
        } finally {
            db.endTransaction();
        }
    }

    // The leave a lecture currently belongs to (pending or approved), or null.
    public LeaveRequest.Link linkForLecture(Lecture lecture) {
        if (lecture.leaveId == null) return null;
        return leaves.link(lecture.leaveId, lecture.id);
    }

    // Takes one lecture out of its leave request and puts it back the way it was.
    // A request left with no lectures is deleted.
    public void removeLecture(long leaveId, long lectureId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            LeaveRequest.Link link = leaves.link(leaveId, lectureId);
            Lecture l = lectures.byId(lectureId);
            if (link != null && l != null) {
                applyEffect(l, leaveId, LeaveRules.REJECTED, link.previousStatus, link.previousManual);
                leaves.unlink(leaveId, lectureId);
            }
            if (leaves.links(leaveId).isEmpty()) {
                leaves.delete(leaveId);
            } else {
                refreshStatus(leaveId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveNote(long leaveId, String note) {
        leaves.updateNote(leaveId, note);
    }

    public List<Lecture> lecturesFor(long leaveId) {
        return lectures.forLeave(leaveId);
    }

    // Called inside the import transaction when the PDF shows a pending lecture as P, E or L.
    // Returns the ids of the leave requests whose status changed.
    List<Long> autoApprove(List<Lecture> resolved) {
        List<Long> touched = new ArrayList<>();
        for (Lecture l : resolved) {
            if (l.leaveId == null) continue;
            decideOne(l.leaveId, l.id, LeaveRules.APPROVED);
            if (!touched.contains(l.leaveId)) touched.add(l.leaveId);
        }
        for (long leaveId : touched) refreshStatus(leaveId);
        return touched;
    }

    private void decideOne(long leaveId, long lectureId, String decision) {
        LeaveRequest.Link link = leaves.link(leaveId, lectureId);
        Lecture l = lectures.byId(lectureId);
        if (link == null || l == null) return;
        leaves.setDecision(leaveId, lectureId, decision);
        applyEffect(l, leaveId, decision, link.previousStatus, link.previousManual);
    }

    private void applyEffect(Lecture l, long leaveId, String decision,
                             String previousStatus, boolean previousManual) {
        LeaveRules.Effect effect = LeaveRules.effectOf(decision, l.sourceStatus, previousStatus, previousManual);
        l.effectiveStatus = effect.effectiveStatus;
        l.manualOverride = effect.manualOverride;
        // A rejected lecture is no longer held by the leave; the link row keeps the history.
        l.leaveId = LeaveRules.REJECTED.equals(decision) ? null : leaveId;
        lectures.update(l);
    }

    private void refreshStatus(long leaveId) {
        List<String> decisions = new ArrayList<>();
        for (LeaveRequest.Link link : leaves.links(leaveId)) decisions.add(link.decision);
        leaves.updateStatus(leaveId, LeaveRules.requestStatus(decisions));
    }
}
