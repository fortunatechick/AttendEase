package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import io.github.fortunatechick.attendease.parser.AttendanceParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubjectDao {

    private final AttendEaseDb helper;

    public SubjectDao(AttendEaseDb helper) {
        this.helper = helper;
    }

    public List<Subject> forSemester(long semesterId) {
        List<Subject> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SUBJECT, null,
                "semester_id = ?", new String[]{String.valueOf(semesterId)}, null, null, "short_name COLLATE NOCASE")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    // Every subject in every semester; used to show names for lectures by id.
    public List<Subject> all() {
        List<Subject> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SUBJECT, null,
                null, null, null, null, "short_name COLLATE NOCASE")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    public Subject byId(long id) {
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_SUBJECT, null,
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    // Same subject regardless of case, spacing or trailing punctuation.
    public Subject findByName(long semesterId, String name) {
        String key = AttendanceParser.nameKey(name);
        for (Subject s : forSemester(semesterId)) {
            if (AttendanceParser.nameKey(s.canonicalName).equals(key)
                    || AttendanceParser.nameKey(s.displayName()).equals(key)) {
                return s;
            }
        }
        return null;
    }

    public long insert(long semesterId, String canonicalName) {
        ContentValues v = new ContentValues();
        v.put("semester_id", semesterId);
        v.put("canonical_name", canonicalName);
        v.put("short_name", canonicalName);
        v.put("created_at", System.currentTimeMillis());
        return helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_SUBJECT, null, v);
    }

    // A later PDF revealed the full name of a truncated subject. Keep a custom
    // display name if the student set one; otherwise show the new full name.
    public void renameCanonical(Subject subject, String newCanonicalName) {
        ContentValues v = new ContentValues();
        v.put("canonical_name", newCanonicalName);
        if (subject.shortName == null || subject.shortName.equals(subject.canonicalName)) {
            v.put("short_name", newCanonicalName);
        }
        helper.getWritableDatabase().update(AttendEaseDb.T_SUBJECT, v,
                "_id = ?", new String[]{String.valueOf(subject.id)});
    }

    public void setDisplayName(long subjectId, String displayName) {
        ContentValues v = new ContentValues();
        v.put("short_name", displayName);
        helper.getWritableDatabase().update(AttendEaseDb.T_SUBJECT, v,
                "_id = ?", new String[]{String.valueOf(subjectId)});
    }

    // Pass nulls to go back to estimating from the PDF.
    public void setWeeklyPattern(long subjectId, Integer theoryPerWeek, Integer labPerWeek) {
        ContentValues v = new ContentValues();
        if (theoryPerWeek == null) v.putNull("theory_per_week"); else v.put("theory_per_week", theoryPerWeek);
        if (labPerWeek == null) v.putNull("lab_per_week"); else v.put("lab_per_week", labPerWeek);
        helper.getWritableDatabase().update(AttendEaseDb.T_SUBJECT, v,
                "_id = ?", new String[]{String.valueOf(subjectId)});
    }

    // alias key (AttendanceParser.nameKey) -> subject id, for one semester
    public Map<String, Long> aliases(long semesterId) {
        Map<String, Long> map = new HashMap<>();
        try (Cursor c = helper.getReadableDatabase().query(AttendEaseDb.T_ALIAS, null,
                "semester_id = ?", new String[]{String.valueOf(semesterId)}, null, null, null)) {
            while (c.moveToNext()) {
                map.put(c.getString(c.getColumnIndexOrThrow("alias_key")),
                        c.getLong(c.getColumnIndexOrThrow("subject_id")));
            }
        }
        return map;
    }

    public void addAlias(long semesterId, String name, long subjectId) {
        ContentValues v = new ContentValues();
        v.put("semester_id", semesterId);
        v.put("alias_key", AttendanceParser.nameKey(name));
        v.put("subject_id", subjectId);
        helper.getWritableDatabase().insertWithOnConflict(AttendEaseDb.T_ALIAS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void repointAliases(long fromSubjectId, long toSubjectId) {
        ContentValues v = new ContentValues();
        v.put("subject_id", toSubjectId);
        helper.getWritableDatabase().update(AttendEaseDb.T_ALIAS, v,
                "subject_id = ?", new String[]{String.valueOf(fromSubjectId)});
    }

    public void delete(long subjectId) {
        helper.getWritableDatabase().delete(AttendEaseDb.T_SUBJECT,
                "_id = ?", new String[]{String.valueOf(subjectId)});
    }

    private static Subject read(Cursor c) {
        Subject s = new Subject(
                c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("canonical_name")),
                c.getString(c.getColumnIndexOrThrow("short_name")),
                c.getLong(c.getColumnIndexOrThrow("created_at")));
        s.semesterId = c.getLong(c.getColumnIndexOrThrow("semester_id"));
        int theory = c.getColumnIndexOrThrow("theory_per_week");
        int lab = c.getColumnIndexOrThrow("lab_per_week");
        s.theoryPerWeek = c.isNull(theory) ? null : c.getInt(theory);
        s.labPerWeek = c.isNull(lab) ? null : c.getInt(lab);
        return s;
    }
}
