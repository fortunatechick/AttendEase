package io.github.fortunatechick.attendease.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.fortunatechick.attendease.calc.ApprovalConvention;
import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.parser.StudentProfile;

public class Prefs {

    private static final String FILE = "attendease_prefs";

    private static final String KEY_NAME = "student_name";
    private static final String KEY_ROLL = "roll_no";
    private static final String KEY_STUDENT_NUMBER = "student_number";
    private static final String KEY_PROGRAM = "program";
    private static final String KEY_SEMESTER = "semester";
    private static final String KEY_DURATION_TO = "duration_to";
    private static final String KEY_THRESHOLD = "threshold";
    private static final String KEY_CONVENTION = "approval_convention";
    private static final String KEY_SEMESTER_WEEKS = "semester_weeks";
    private static final String KEY_CURRENT_SEMESTER = "current_semester_id";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        String file = GuestMode.isActive(context) ? GuestMode.GUEST_PREFS : FILE;
        sp = context.getApplicationContext().getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    public void saveProfile(StudentProfile p) {
        SharedPreferences.Editor e = sp.edit();
        // Only overwrite fields the PDF actually had.
        if (p.name != null) e.putString(KEY_NAME, p.name);
        if (p.rollNo != null) e.putString(KEY_ROLL, p.rollNo);
        if (p.studentNumber != null) e.putString(KEY_STUDENT_NUMBER, p.studentNumber);
        if (p.programName != null) e.putString(KEY_PROGRAM, p.programName);
        if (p.academicSession != null) e.putString(KEY_SEMESTER, p.academicSession);
        if (p.durationTo != null) e.putString(KEY_DURATION_TO, p.durationTo);
        e.apply();
    }

    // The header of each semester's PDF is kept with that semester: roll numbers and
    // programme details can change between years.
    public void saveProfile(long semesterId, StudentProfile p) {
        saveProfile(p);
        SharedPreferences.Editor e = sp.edit();
        if (p.name != null) e.putString(semesterKey(semesterId, KEY_NAME), p.name);
        if (p.rollNo != null) e.putString(semesterKey(semesterId, KEY_ROLL), p.rollNo);
        if (p.programName != null) e.putString(semesterKey(semesterId, KEY_PROGRAM), p.programName);
        if (p.durationTo != null) e.putString(semesterKey(semesterId, KEY_DURATION_TO), p.durationTo);
        e.apply();
    }

    // Used once when upgrading: the profile stored so far belongs to the first semester.
    public void copyProfileTo(long semesterId) {
        SharedPreferences.Editor e = sp.edit();
        for (String key : new String[]{KEY_NAME, KEY_ROLL, KEY_PROGRAM, KEY_DURATION_TO}) {
            String value = sp.getString(key, null);
            if (value != null) e.putString(semesterKey(semesterId, key), value);
        }
        e.commit();
    }

    // Name and roll number fall back to the latest PDF (same student, e.g. for a semester
    // started by hand); programme and data date are only shown for the semester they came from.
    public String studentName(long semesterId) {
        return sp.getString(semesterKey(semesterId, KEY_NAME), studentName());
    }

    public String rollNo(long semesterId) {
        return sp.getString(semesterKey(semesterId, KEY_ROLL), rollNo());
    }

    public String program(long semesterId) {
        return sp.getString(semesterKey(semesterId, KEY_PROGRAM), null);
    }

    public String dataUpTo(long semesterId) {
        return sp.getString(semesterKey(semesterId, KEY_DURATION_TO), null);
    }

    private static String semesterKey(long semesterId, String key) {
        return "sem" + semesterId + "_" + key;
    }

    public String studentName() {
        return sp.getString(KEY_NAME, null);
    }

    public String rollNo() {
        return sp.getString(KEY_ROLL, null);
    }

    public String program() {
        return sp.getString(KEY_PROGRAM, null);
    }

    public String semester() {
        return sp.getString(KEY_SEMESTER, null);
    }

    public String dataUpTo() {
        return sp.getString(KEY_DURATION_TO, null);
    }

    public int threshold() {
        return sp.getInt(KEY_THRESHOLD, AttendanceCalculator.DEFAULT_THRESHOLD);
    }

    public void setThreshold(int percent) {
        sp.edit().putInt(KEY_THRESHOLD, percent).apply();
    }

    public ApprovalConvention convention() {
        return ApprovalConvention.fromPreference(sp.getString(KEY_CONVENTION, null));
    }

    public void setConvention(ApprovalConvention convention) {
        sp.edit().putString(KEY_CONVENTION, convention.name()).apply();
    }

    // The semester the dashboard shows. -1 means "the newest one".
    public long currentSemesterId() {
        return sp.getLong(KEY_CURRENT_SEMESTER, -1);
    }

    public void setCurrentSemesterId(long id) {
        sp.edit().putLong(KEY_CURRENT_SEMESTER, id).commit();
    }

    public int semesterWeeks() {
        return sp.getInt(KEY_SEMESTER_WEEKS, AttendanceCalculator.DEFAULT_SEMESTER_WEEKS);
    }

    public void setSemesterWeeks(int weeks) {
        sp.edit().putInt(KEY_SEMESTER_WEEKS, weeks).apply();
    }

    public AttendanceCalculator calculator() {
        return new AttendanceCalculator(convention(), threshold(), semesterWeeks());
    }

    public void clearProfile() {
        sp.edit()
                .remove(KEY_NAME).remove(KEY_ROLL).remove(KEY_STUDENT_NUMBER)
                .remove(KEY_PROGRAM).remove(KEY_SEMESTER).remove(KEY_DURATION_TO)
                .apply();
    }
}
