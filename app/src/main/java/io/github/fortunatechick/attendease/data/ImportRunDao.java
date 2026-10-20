package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.database.Cursor;

public class ImportRunDao {

    private final AttendEaseDb helper;

    public ImportRunDao(AttendEaseDb helper) {
        this.helper = helper;
    }

    public long insert(String fileName, int rowsParsed, int rowsSkipped,
                       String durationFrom, String durationTo) {
        ContentValues v = new ContentValues();
        v.put("file_name", fileName);
        v.put("imported_at", System.currentTimeMillis());
        v.put("rows_parsed", rowsParsed);
        v.put("rows_skipped", rowsSkipped);
        v.put("duration_from", durationFrom);
        v.put("duration_to", durationTo);
        return helper.getWritableDatabase().insertOrThrow(AttendEaseDb.T_IMPORT_RUN, null, v);
    }

    // Returns {file name, imported_at, duration_to} of the latest import, or null if none.
    public String[] latest() {
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT file_name, imported_at, duration_to FROM import_run ORDER BY _id DESC LIMIT 1", null)) {
            if (!c.moveToFirst()) return null;
            return new String[]{c.getString(0), String.valueOf(c.getLong(1)), c.getString(2)};
        }
    }

    public int count() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM import_run", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
