package io.github.fortunatechick.attendease.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.ParseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class AttendanceRepository {

    public static final String DEFAULT_SEMESTER_LABEL = "My semester";

    public final AttendEaseDb helper;
    public final SemesterDao semesters;
    public final SubjectDao subjects;
    public final LectureDao lectures;
    public final ImportRunDao importRuns;
    public final LeaveDao leaves;
    private final Prefs prefs;

    public AttendanceRepository(Context context) {
        helper = AttendEaseDb.get(context);
        semesters = new SemesterDao(helper);
        subjects = new SubjectDao(helper);
        lectures = new LectureDao(helper);
        importRuns = new ImportRunDao(helper);
        leaves = new LeaveDao(helper);
        prefs = new Prefs(context);
    }

    // ---------------- Semesters ----------------

    // The semester the app is showing: the one picked in Settings, else the newest. Null if none.
    public Semester currentSemester() {
        Semester s = semesters.byId(prefs.currentSemesterId());
        return s != null ? s : semesters.latest();
    }

    // For adding subjects by hand before any PDF: makes a semester if there isn't one.
    public Semester ensureCurrentSemester() {
        Semester s = currentSemester();
        if (s == null) {
            s = semesters.insert(DEFAULT_SEMESTER_LABEL);
            prefs.setCurrentSemesterId(s.id);
        }
        return s;
    }

    public void setCurrentSemester(long semesterId) {
        prefs.setCurrentSemesterId(semesterId);
    }

    // The report's own "Academic Year & Academic Session" line, e.g. "2026-2027, Semester V".
    // Without one, the report belongs to whichever semester is current.
    private static String semesterLabelOf(ParseResult result) {
        String label = result.profile.academicSession;
        return label == null || label.trim().isEmpty() ? null : label.trim().replaceAll("\\s+", " ");
    }

    // ---------------- Import ----------------

    // Writes a parsed report in a single transaction: either all of it lands or none of it.
    public ImportSummary importReport(ParseResult result, String fileName) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ImportSummary summary = new ImportSummary();
        summary.rowsParsed = result.records.size();

        db.beginTransaction();
        try {
            String label = semesterLabelOf(result);
            Semester semester = label == null ? ensureCurrentSemester() : semesters.byLabel(label);
            if (semester == null) {
                semester = semesters.insert(label);
                summary.newSemester = true;
            }
            summary.semesterId = semester.id;
            summary.semesterLabel = semester.label;

            summary.importRunId = importRuns.insert(fileName, result.records.size(),
                    result.unmatchedLines.size(), result.profile.durationFrom, result.profile.durationTo);

            Map<String, Long> idByIncomingName = resolveSubjects(semester.id, result.subjectNames(), summary);

            Map<Long, Long> rejected = leaves.rejectedLectures();
            ImportPlanner.Plan plan = ImportPlanner.plan(lectures.allByNaturalKey(), result.records,
                    idByIncomingName, summary.importRunId, rejected);
            for (Lecture l : plan.inserts) lectures.insert(l);
            for (Lecture l : plan.updates) lectures.update(l);

            // The college has acted on these leaves: approve them.
            new LeaveRepository(helper).autoApprove(plan.resolvedByCollege);

            summary.inserted = plan.inserts.size();
            summary.updated = plan.updates.size();
            summary.unchanged = plan.unchanged;
            summary.autoApproved = plan.resolvedByCollege.size();
            summary.stillAbsent = plan.stillAbsent.size();
            summary.mergedManual = plan.mergedManual;
            for (Lecture l : plan.conflicts) {
                summary.conflicts.add(conflict(l, ImportSummary.Conflict.MANUAL, null));
            }
            for (Lecture l : plan.leaveConflicts) {
                summary.conflicts.add(conflict(l, ImportSummary.Conflict.REJECTED_LEAVE, rejected.get(l.id)));
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // Show the semester that was just imported.
        prefs.setCurrentSemesterId(summary.semesterId);
        return summary;
    }

    private ImportSummary.Conflict conflict(Lecture l, int kind, Long leaveId) {
        ImportSummary.Conflict c = new ImportSummary.Conflict();
        c.kind = kind;
        c.lectureId = l.id;
        c.leaveId = leaveId;
        Subject s = subjects.byId(l.subjectId);
        c.subjectName = s == null ? l.rawCourseName : s.displayName();
        c.component = l.component;
        c.dateMillis = l.dateMillis;
        c.startTime = l.startTime;
        c.yours = l.effectiveStatus;
        c.pdf = l.sourceStatus;
        return c;
    }

    // The student sided with the PDF on a conflict.
    public void acceptPdf(ImportSummary.Conflict c) {
        if (c.kind == ImportSummary.Conflict.REJECTED_LEAVE && c.leaveId != null) {
            new LeaveRepository(helper).decide(c.leaveId, c.lectureId, LeaveRules.APPROVED);
        } else {
            Lecture l = lectures.byId(c.lectureId);
            if (l != null) lectures.setEffectiveStatus(l.id, Status.fromSource(l.sourceStatus), false);
        }
    }

    public static class ImportPreview {
        public String semesterLabel;
        public boolean newSemester;
        public int newLectures;
        public int updatedLectures;
        public int unchangedLectures;
        public final List<String> newSubjects = new ArrayList<>();
        public final Map<String, String> renamedSubjects = new HashMap<>();
        public int resolvedByCollege;
        public int stillAbsent;
        public int mergedManual;
        public int conflicts;

        public boolean changesNothing() {
            return newLectures == 0 && updatedLectures == 0 && newSubjects.isEmpty() && renamedSubjects.isEmpty();
        }
    }

    // What importReport would do, without writing anything. Shown on the review screen.
    public ImportPreview previewImport(ParseResult result) {
        ImportPreview preview = new ImportPreview();
        String label = semesterLabelOf(result);
        Semester semester = label == null ? currentSemester() : semesters.byLabel(label);
        preview.newSemester = semester == null;
        preview.semesterLabel = semester != null ? semester.label : (label != null ? label : DEFAULT_SEMESTER_LABEL);

        List<String> incoming = result.subjectNames();
        List<Subject> existing = semester == null ? new ArrayList<Subject>() : subjects.forSemester(semester.id);
        Map<String, String> viaAlias = applyAliases(semester == null ? -1 : semester.id, existing, incoming);

        Map<String, Long> idByCanonical = new HashMap<>();
        for (Subject s : existing) idByCanonical.put(s.canonicalName, s.id);
        SubjectResolver.Result resolved = SubjectResolver.resolve(
                new ArrayList<>(idByCanonical.keySet()), new ArrayList<>(new LinkedHashSet<>(viaAlias.values())));

        for (Map.Entry<String, String> rename : resolved.renames.entrySet()) {
            idByCanonical.put(rename.getValue(), idByCanonical.get(rename.getKey()));
            preview.renamedSubjects.put(rename.getKey(), rename.getValue());
        }
        preview.renamedSubjects.putAll(resolved.folds);
        long placeholderId = -1;
        for (String name : resolved.toCreate) {
            idByCanonical.put(name, placeholderId--);
            preview.newSubjects.add(name);
        }

        Map<String, Long> idByIncomingName = new HashMap<>();
        for (String name : incoming) {
            idByIncomingName.put(name, idByCanonical.get(resolved.target.get(viaAlias.get(name))));
        }

        ImportPlanner.Plan plan = ImportPlanner.plan(lectures.allByNaturalKey(), result.records,
                idByIncomingName, null, leaves.rejectedLectures());
        preview.newLectures = plan.inserts.size();
        preview.updatedLectures = plan.updates.size();
        preview.unchangedLectures = plan.unchanged;
        preview.resolvedByCollege = plan.resolvedByCollege.size();
        preview.stillAbsent = plan.stillAbsent.size();
        preview.mergedManual = plan.mergedManual;
        preview.conflicts = plan.conflicts.size() + plan.leaveConflicts.size();
        return preview;
    }

    // Incoming name -> the name to resolve it under: the subject it was merged into by hand,
    // if the student merged that spelling before, otherwise itself.
    private Map<String, String> applyAliases(long semesterId, List<Subject> existing, List<String> incoming) {
        Map<String, Long> aliases = semesterId < 0 ? new HashMap<String, Long>() : subjects.aliases(semesterId);
        Map<Long, Subject> byId = new HashMap<>();
        for (Subject s : existing) byId.put(s.id, s);
        Map<String, String> result = new HashMap<>();
        for (String name : incoming) {
            Long id = aliases.get(AttendanceParser.nameKey(name));
            Subject target = id == null ? null : byId.get(id);
            result.put(name, target != null ? target.canonicalName : name);
        }
        return result;
    }

    // Returns a map from every incoming subject name to the id of the subject row it belongs to.
    // Only subjects of the same semester are considered, so a new semester never folds into an old one.
    private Map<String, Long> resolveSubjects(long semesterId, List<String> incomingNames, ImportSummary summary) {
        List<Subject> existingList = subjects.forSemester(semesterId);
        Map<String, Subject> existing = new HashMap<>();
        for (Subject s : existingList) existing.put(s.canonicalName, s);
        Map<String, String> viaAlias = applyAliases(semesterId, existingList, incomingNames);

        SubjectResolver.Result resolved = SubjectResolver.resolve(new ArrayList<>(existing.keySet()),
                new ArrayList<>(new LinkedHashSet<>(viaAlias.values())));

        Map<String, Long> idByCanonical = new HashMap<>();
        for (Subject s : existing.values()) idByCanonical.put(s.canonicalName, s.id);

        for (Map.Entry<String, String> rename : resolved.renames.entrySet()) {
            Subject s = existing.get(rename.getKey());
            subjects.renameCanonical(s, rename.getValue());
            idByCanonical.remove(rename.getKey());
            idByCanonical.put(rename.getValue(), s.id);
            summary.renamedSubjects.put(rename.getKey(), rename.getValue());
        }
        for (String name : resolved.toCreate) {
            idByCanonical.put(name, subjects.insert(semesterId, name));
            summary.createdSubjects.add(name);
        }
        for (Map.Entry<String, String> fold : resolved.folds.entrySet()) {
            Subject from = existing.get(fold.getKey());
            long into = idByCanonical.get(fold.getValue());
            lectures.moveToSubject(from.id, into);
            subjects.repointAliases(from.id, into);
            subjects.delete(from.id);
            idByCanonical.remove(fold.getKey());
            summary.renamedSubjects.put(fold.getKey(), fold.getValue());
        }

        Map<String, Long> result = new HashMap<>();
        for (String name : incomingNames) {
            result.put(name, idByCanonical.get(resolved.target.get(viaAlias.get(name))));
        }
        return result;
    }

    // ---------------- Merging subjects by hand ----------------

    // For name variations the automatic rules can't be sure about (e.g. "Machine Learning" and
    // "Machine Learning Lab" being one subject). Moves every lecture into the other subject and
    // remembers the old spelling, so later imports put those rows in the right place too.
    public void mergeSubjects(long fromSubjectId, long intoSubjectId) {
        if (fromSubjectId == intoSubjectId) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Subject from = subjects.byId(fromSubjectId);
            Subject into = subjects.byId(intoSubjectId);
            if (from == null || into == null || from.semesterId != into.semesterId) return;
            lectures.moveToSubject(from.id, into.id);
            subjects.repointAliases(from.id, into.id);
            subjects.addAlias(from.semesterId, from.canonicalName, into.id);
            if (!from.displayName().equals(from.canonicalName)) {
                subjects.addAlias(from.semesterId, from.displayName(), into.id);
            }
            subjects.delete(from.id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
