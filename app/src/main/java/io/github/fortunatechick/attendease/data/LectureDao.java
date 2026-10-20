package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LectureDao {

    private static final String ORDER = "date_millis, start_minutes, component";

    private final AttendEaseDb helper;

    public LectureDao(AttendEaseDb helper) {
        this.helper = helper;
    }

    public List<Lecture> all() {
        return query(null, null);
    }

    public List<Lecture> forSemester(long semesterId) {
        return query("subject_id IN (SELECT _id FROM subject WHERE semester_id = ?)",
                new String[]{String.valueOf(semesterId)});
    }

    public List<Lecture> forSubject(long subjectId) {
        return query("subject_id = ?", new String[]{String.valueOf(subjectId)});
    }

    // Both ends inclusive, dates as midnight-UTC millis.
    public List<Lecture> inDateRange(long fromMillis, long toMillis) {
        return query("date_millis BETWEEN ? AND ?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)});
    }

    public List<Lecture> forLeave(long leaveId) {
        return query("_id IN (SELECT lecture_id FROM leave_lecture WHERE leave_id = ?)",
                new String[]{String.valueOf(leaveId)});
    }

    public Lecture byId(long id) {
        List<Lecture> list = query("_id = ?", new String[]{String.valueOf(id)});
        return list.isEmpty() ? null : list.get(0);
    }

    public Map<String, Lecture> allByNaturalKey() {
        Map<String, Lecture> map = new HashMap<>();
        for (Lecture l : all()) map.put(l.naturalKey(), l);
        return map;
    }

    public long insert(Lecture l) {
        long id = helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_LECTURE, null, values(l));
        l.id = id;
        return id;
    }

    public void update(Lecture l) {
        helper.getWritableDatabase().update(AttendEaseDb.T_LECTURE, values(l),
                "_id = ?", new String[]{String.valueOf(l.id)});
    }

    public void setEffectiveStatus(long lectureId, String effectiveStatus, boolean manualOverride) {
        ContentValues v = new ContentValues();
        v.put("effective_status", effectiveStatus);
        v.put("manual_override", manualOverride ? 1 : 0);
        helper.getWritableDatabase().update(AttendEaseDb.T_LECTURE, v,
                "_id = ?", new String[]{String.valueOf(lectureId)});
    }

    public void moveToSubject(long fromSubjectId, long toSubjectId) {
        ContentValues v = new ContentValues();
        v.put("subject_id", toSubjectId);
        helper.getWritableDatabase().update(AttendEaseDb.T_LECTURE, v,
                "subject_id = ?", new String[]{String.valueOf(fromSubjectId)});
    }

    public void delete(long lectureId) {
        helper.getWritableDatabase().delete(AttendEaseDb.T_LECTURE,
                "_id = ?", new String[]{String.valueOf(lectureId)});
    }

    // Row counts per subject and effective status, e.g. "Operating Systems/T/PRESENT=22".
    // Two snapshots compare equal exactly when every total in the app is the same.
    public Map<String, Integer> statusSnapshot() {
        Map<String, Integer> snapshot = new TreeMap<>();
        String sql = "SELECT s.canonical_name, l.component, l.effective_status, COUNT(*)"
                + " FROM lecture l JOIN subject s ON s._id = l.subject_id"
                + " GROUP BY s.canonical_name, l.component, l.effective_status";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                snapshot.put(c.getString(0) + "/" + c.getString(1) + "/" + c.getString(2), c.getInt(3));
            }
        }
        return snapshot;
    }

    // Date of the semester's first lecture on record: taken as the start of the semester. -1 if none.
    public long semesterStart(long semesterId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT MIN(date_millis) FROM lecture WHERE subject_id IN (SELECT _id FROM subject WHERE semester_id = ?)",
                new String[]{String.valueOf(semesterId)})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : -1;
        }
    }

    public int count() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM lecture", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private List<Lecture> query(String where, String[] args) {
        List<Lecture> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_LECTURE, null,
                where, args, null, null, ORDER)) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    private static ContentValues values(Lecture l) {
        ContentValues v = new ContentValues();
        v.put("subject_id", l.subjectId);
        v.put("component", l.component);
        v.put("date_millis", l.dateMillis);
        v.put("start_time", l.startTime);
        v.put("start_minutes", l.startMinutes);
        v.put("end_time", l.endTime);
        v.put("raw_course_name", l.rawCourseName);
        v.put("batch_label", l.batchLabel);
        v.put("sr_no", l.srNo);
        v.put("source_status", l.sourceStatus);
        v.put("effective_status", l.effectiveStatus);
        v.put("manual_override", l.manualOverride ? 1 : 0);
        if (l.leaveId == null) v.putNull("leave_id"); else v.put("leave_id", l.leaveId);
        if (l.importRunId == null) v.putNull("import_run_id"); else v.put("import_run_id", l.importRunId);
        return v;
    }

    private static Lecture read(Cursor c) {
        Lecture l = new Lecture();
        l.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        l.subjectId = c.getLong(c.getColumnIndexOrThrow("subject_id"));
        l.component = c.getString(c.getColumnIndexOrThrow("component"));
        l.dateMillis = c.getLong(c.getColumnIndexOrThrow("date_millis"));
        l.startTime = c.getString(c.getColumnIndexOrThrow("start_time"));
        l.startMinutes = c.getInt(c.getColumnIndexOrThrow("start_minutes"));
        l.endTime = c.getString(c.getColumnIndexOrThrow("end_time"));
        l.rawCourseName = c.getString(c.getColumnIndexOrThrow("raw_course_name"));
        l.batchLabel = c.getString(c.getColumnIndexOrThrow("batch_label"));
        l.srNo = c.getInt(c.getColumnIndexOrThrow("sr_no"));
        l.sourceStatus = c.getString(c.getColumnIndexOrThrow("source_status"));
        l.effectiveStatus = c.getString(c.getColumnIndexOrThrow("effective_status"));
        l.manualOverride = c.getInt(c.getColumnIndexOrThrow("manual_override")) == 1;
        int leaveCol = c.getColumnIndexOrThrow("leave_id");
        l.leaveId = c.isNull(leaveCol) ? null : c.getLong(leaveCol);
        int runCol = c.getColumnIndexOrThrow("import_run_id");
        l.importRunId = c.isNull(runCol) ? null : c.getLong(runCol);
        return l;
    }
}
