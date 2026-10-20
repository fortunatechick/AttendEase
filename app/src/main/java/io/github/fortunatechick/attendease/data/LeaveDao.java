package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaveDao {

    private final AttendEaseDb helper;

    public LeaveDao(AttendEaseDb helper) {
        this.helper = helper;
    }

    public long insert(LeaveRequest r) {
        ContentValues v = new ContentValues();
        v.put("from_date", r.fromDate);
        v.put("to_date", r.toDate);
        v.put("reason", r.reason);
        v.put("submitted_to", r.submittedTo);
        v.put("submitted_on", r.submittedOn);
        v.put("status", r.status);
        v.put("note", r.note);
        r.id = helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_LEAVE, null, v);
        return r.id;
    }

    public void updateStatus(long leaveId, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        helper.getWritableDatabase().update(AttendEaseDb.T_LEAVE, v, "_id = ?", args(leaveId));
    }

    public void updateNote(long leaveId, String note) {
        ContentValues v = new ContentValues();
        v.put("note", note);
        helper.getWritableDatabase().update(AttendEaseDb.T_LEAVE, v, "_id = ?", args(leaveId));
    }

    public void delete(long leaveId) {
        helper.getWritableDatabase().delete(AttendEaseDb.T_LEAVE, "_id = ?", args(leaveId));
    }

    public LeaveRequest byId(long id) {
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_LEAVE, null,
                "_id = ?", args(id), null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public List<LeaveRequest> all() {
        List<LeaveRequest> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_LEAVE, null,
                null, null, null, null, "from_date DESC, _id DESC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    public void link(long leaveId, long lectureId, String previousStatus, boolean previousManual) {
        ContentValues v = new ContentValues();
        v.put("leave_id", leaveId);
        v.put("lecture_id", lectureId);
        v.put("decision", LeaveRules.PENDING);
        v.put("previous_status", previousStatus);
        v.put("previous_manual", previousManual ? 1 : 0);
        helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_LEAVE_LECTURE, null, v);
    }

    public void setDecision(long leaveId, long lectureId, String decision) {
        ContentValues v = new ContentValues();
        v.put("decision", decision);
        helper.getWritableDatabase().update(AttendEaseDb.T_LEAVE_LECTURE, v,
                "leave_id = ? AND lecture_id = ?", new String[]{String.valueOf(leaveId), String.valueOf(lectureId)});
    }

    public void unlink(long leaveId, long lectureId) {
        helper.getWritableDatabase().delete(AttendEaseDb.T_LEAVE_LECTURE,
                "leave_id = ? AND lecture_id = ?", new String[]{String.valueOf(leaveId), String.valueOf(lectureId)});
    }

    public List<LeaveRequest.Link> links(long leaveId) {
        return queryLinks("leave_id = ?", args(leaveId));
    }

    public LeaveRequest.Link link(long leaveId, long lectureId) {
        List<LeaveRequest.Link> list = queryLinks("leave_id = ? AND lecture_id = ?",
                new String[]{String.valueOf(leaveId), String.valueOf(lectureId)});
        return list.isEmpty() ? null : list.get(0);
    }

    // lecture id -> leave id, for every lecture whose leave was rejected.
    public Map<Long, Long> rejectedLectures() {
        Map<Long, Long> map = new HashMap<>();
        for (LeaveRequest.Link l : queryLinks("decision = ?", new String[]{LeaveRules.REJECTED})) {
            map.put(l.lectureId, l.leaveId);
        }
        return map;
    }

    // Distinct subject display names covered by a leave.
    public List<String> subjectNames(long leaveId) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT DISTINCT s.short_name FROM leave_lecture ll"
                + " JOIN lecture l ON l._id = ll.lecture_id"
                + " JOIN subject s ON s._id = l.subject_id"
                + " WHERE ll.leave_id = ? ORDER BY s.short_name COLLATE NOCASE";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, args(leaveId))) {
            while (c.moveToNext()) names.add(c.getString(0));
        }
        return names;
    }

    private List<LeaveRequest.Link> queryLinks(String where, String[] whereArgs) {
        List<LeaveRequest.Link> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_LEAVE_LECTURE, null,
                where, whereArgs, null, null, null)) {
            while (c.moveToNext()) {
                LeaveRequest.Link l = new LeaveRequest.Link();
                l.leaveId = c.getLong(c.getColumnIndexOrThrow("leave_id"));
                l.lectureId = c.getLong(c.getColumnIndexOrThrow("lecture_id"));
                l.decision = c.getString(c.getColumnIndexOrThrow("decision"));
                l.previousStatus = c.getString(c.getColumnIndexOrThrow("previous_status"));
                l.previousManual = c.getInt(c.getColumnIndexOrThrow("previous_manual")) == 1;
                list.add(l);
            }
        }
        return list;
    }

    private static LeaveRequest read(Cursor c) {
        LeaveRequest r = new LeaveRequest();
        r.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        r.fromDate = c.getLong(c.getColumnIndexOrThrow("from_date"));
        r.toDate = c.getLong(c.getColumnIndexOrThrow("to_date"));
        r.reason = c.getString(c.getColumnIndexOrThrow("reason"));
        r.submittedTo = c.getString(c.getColumnIndexOrThrow("submitted_to"));
        r.submittedOn = c.getLong(c.getColumnIndexOrThrow("submitted_on"));
        r.status = c.getString(c.getColumnIndexOrThrow("status"));
        r.note = c.getString(c.getColumnIndexOrThrow("note"));
        return r;
    }

    private static String[] args(long id) {
        return new String[]{String.valueOf(id)};
    }
}
