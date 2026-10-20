package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class SemesterDao {

    private final AttendEaseDb helper;

    public SemesterDao(AttendEaseDb helper) {
        this.helper = helper;
    }

    // Newest first.
    public List<Semester> all() {
        List<Semester> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SEMESTER, null,
                null, null, null, null, "created_at DESC, _id DESC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    public Semester byId(long id) {
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SEMESTER, null,
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    // Case-insensitive (the column is COLLATE NOCASE).
    public Semester byLabel(String label) {
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SEMESTER, null,
                "label = ?", new String[]{label.trim()}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public Semester latest() {
        List<Semester> all = all();
        return all.isEmpty() ? null : all.get(0);
    }

    public Semester insert(String label) {
        ContentValues v = new ContentValues();
        v.put("label", label.trim());
        long now = System.currentTimeMillis();
        v.put("created_at", now);
        long id = helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_SEMESTER, null, v);
        return new Semester(id, label.trim(), now);
    }

    public int subjectCount(long semesterId) {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM subject WHERE semester_id = ?", new String[]{String.valueOf(semesterId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static Semester read(Cursor c) {
        return new Semester(
                c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("label")),
                c.getLong(c.getColumnIndexOrThrow("created_at")));
    }
}
