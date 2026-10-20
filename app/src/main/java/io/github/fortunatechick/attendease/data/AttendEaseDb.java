package io.github.fortunatechick.attendease.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AttendEaseDb extends SQLiteOpenHelper {

    public static final String NAME = "attendease.db";
    public static final int VERSION = 3;

    public static final String T_SEMESTER = "semester";
    public static final String T_SUBJECT = "subject";
    public static final String T_ALIAS = "subject_alias";
    public static final String T_LECTURE = "lecture";
    public static final String T_LEAVE = "leave_request";
    public static final String T_LEAVE_LECTURE = "leave_lecture";
    public static final String T_IMPORT_RUN = "import_run";

    private static AttendEaseDb instance;
    private static AttendEaseDb guestInstance;

    // The owner's database, or the throwaway guest one while guest mode is on.
    public static synchronized AttendEaseDb get(Context context) {
        if (GuestMode.isActive(context)) {
            if (guestInstance == null) {
                guestInstance = new AttendEaseDb(context.getApplicationContext(), GuestMode.GUEST_DB);
            }
            return guestInstance;
        }
        if (instance == null) {
            instance = new AttendEaseDb(context.getApplicationContext(), NAME);
        }
        return instance;
    }

    static synchronized void closeGuest() {
        if (guestInstance != null) {
            guestInstance.close();
            guestInstance = null;
        }
    }

    private final Context appContext;

    private AttendEaseDb(Context context, String fileName) {
        super(context, fileName, null, VERSION);
        appContext = context;
    }

    // Foreign keys are switched on after any upgrade has run (not in onConfigure), because
    // the version 3 upgrade rebuilds the subject table and, with them on, dropping the old
    // table would cascade-delete every lecture.
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSemesterTable(db);
        createSubjectTable(db, "subject");
        createAliasTable(db);

        db.execSQL("CREATE TABLE import_run ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_name TEXT,"
                + "imported_at INTEGER NOT NULL,"
                + "rows_parsed INTEGER NOT NULL,"
                + "rows_skipped INTEGER NOT NULL,"
                + "duration_from TEXT,"
                + "duration_to TEXT)");

        db.execSQL("CREATE TABLE leave_request ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "from_date INTEGER NOT NULL,"
                + "to_date INTEGER NOT NULL,"
                + "reason TEXT,"
                + "submitted_to TEXT NOT NULL CHECK (submitted_to IN ('HOD','COURSE_COORDINATOR')),"
                + "submitted_on INTEGER NOT NULL,"
                + "status TEXT NOT NULL DEFAULT 'SUBMITTED'"
                + " CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','PARTIAL')),"
                + "note TEXT)");

        db.execSQL("CREATE TABLE lecture ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "subject_id INTEGER NOT NULL REFERENCES subject(_id) ON DELETE CASCADE,"
                + "component TEXT NOT NULL CHECK (component IN ('T','P')),"
                + "date_millis INTEGER NOT NULL,"
                + "start_time TEXT NOT NULL,"
                + "start_minutes INTEGER NOT NULL,"
                + "end_time TEXT,"
                + "raw_course_name TEXT NOT NULL,"
                + "batch_label TEXT,"
                + "sr_no INTEGER NOT NULL DEFAULT 0,"
                + "source_status TEXT CHECK (source_status IN ('P','A','E','L','NU')),"
                + "effective_status TEXT NOT NULL"
                + " CHECK (effective_status IN ('PRESENT','ABSENT','PENDING_LEAVE','NOT_UPDATED','EXEMPT')),"
                + "manual_override INTEGER NOT NULL DEFAULT 0,"
                + "leave_id INTEGER REFERENCES leave_request(_id) ON DELETE SET NULL,"
                + "import_run_id INTEGER REFERENCES import_run(_id) ON DELETE SET NULL,"
                + "UNIQUE (raw_course_name, date_millis, start_time))");
        db.execSQL("CREATE INDEX idx_lecture_subject ON lecture(subject_id)");
        db.execSQL("CREATE INDEX idx_lecture_date ON lecture(date_millis, start_minutes)");

        db.execSQL("CREATE TABLE leave_lecture ("
                + "leave_id INTEGER NOT NULL REFERENCES leave_request(_id) ON DELETE CASCADE,"
                + "lecture_id INTEGER NOT NULL REFERENCES lecture(_id) ON DELETE CASCADE,"
                + "decision TEXT NOT NULL DEFAULT 'PENDING'"
                + " CHECK (decision IN ('PENDING','APPROVED','REJECTED')),"
                // What the lecture was before the leave, if the student had set it by hand,
                // so a rejection can put it back exactly.
                + "previous_status TEXT,"
                + "previous_manual INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (leave_id, lecture_id))");
        db.execSQL("CREATE INDEX idx_leave_lecture_lecture ON leave_lecture(lecture_id)");
    }

    // Migrations run one version at a time so a user several versions behind is
    // walked forward step by step without losing data. Add a case for each new VERSION.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            migrateTo(db, version);
        }
    }

    private static void createSemesterTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE semester ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "label TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                + "created_at INTEGER NOT NULL)");
    }

    // A subject name only has to be unique within its semester: "Project" can exist in two.
    private static void createSubjectTable(SQLiteDatabase db, String name) {
        db.execSQL("CREATE TABLE " + name + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "semester_id INTEGER NOT NULL REFERENCES semester(_id) ON DELETE CASCADE,"
                + "canonical_name TEXT NOT NULL,"
                + "short_name TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "theory_per_week INTEGER,"
                + "lab_per_week INTEGER,"
                + "UNIQUE (semester_id, canonical_name))");
    }

    // Other spellings of a subject that the student merged by hand; later imports use them.
    private static void createAliasTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE subject_alias ("
                + "semester_id INTEGER NOT NULL REFERENCES semester(_id) ON DELETE CASCADE,"
                + "alias_key TEXT NOT NULL,"
                + "subject_id INTEGER NOT NULL REFERENCES subject(_id) ON DELETE CASCADE,"
                + "PRIMARY KEY (semester_id, alias_key))");
    }

    private void migrateTo(SQLiteDatabase db, int version) {
        switch (version) {
            case 2:
                // Lectures per week for the semester budget; NULL means estimate from the PDF.
                db.execSQL("ALTER TABLE subject ADD COLUMN theory_per_week INTEGER");
                db.execSQL("ALTER TABLE subject ADD COLUMN lab_per_week INTEGER");
                break;
            case 3: {
                // Semesters. Everything stored so far becomes one semester, labelled from the
                // last imported PDF header. The subject table is rebuilt (SQLite can't change a
                // UNIQUE constraint in place), keeping every _id so lectures stay attached.
                createSemesterTable(db);
                String label = new Prefs(appContext).semester();
                if (label == null || label.trim().isEmpty()) label = "My semester";
                ContentValues v = new ContentValues();
                v.put("label", label.trim());
                v.put("created_at", System.currentTimeMillis());
                long semesterId = db.insertOrThrow(T_SEMESTER, null, v);

                createSubjectTable(db, "subject_new");
                db.execSQL("INSERT INTO subject_new (_id, semester_id, canonical_name, short_name, created_at,"
                        + " theory_per_week, lab_per_week)"
                        + " SELECT _id, " + semesterId + ", canonical_name, short_name, created_at,"
                        + " theory_per_week, lab_per_week FROM subject");
                db.execSQL("DROP TABLE subject");
                db.execSQL("ALTER TABLE subject_new RENAME TO subject");
                createAliasTable(db);
                new Prefs(appContext).setCurrentSemesterId(semesterId);
                new Prefs(appContext).copyProfileTo(semesterId);
                break;
            }
            default:
                throw new IllegalStateException("No migration defined for database version " + version);
        }
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_LEAVE_LECTURE, null, null);
            db.delete(T_LECTURE, null, null);
            db.delete(T_LEAVE, null, null);
            db.delete(T_ALIAS, null, null);
            db.delete(T_SUBJECT, null, null);
            db.delete(T_SEMESTER, null, null);
            db.delete(T_IMPORT_RUN, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
