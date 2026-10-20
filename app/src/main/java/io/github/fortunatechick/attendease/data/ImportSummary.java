package io.github.fortunatechick.attendease.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportSummary {

    // A lecture where the student's own record and the new PDF disagree.
    public static class Conflict {
        public static final int MANUAL = 0;          // student marked it by hand
        public static final int REJECTED_LEAVE = 1;  // student recorded the leave as rejected

        public int kind;
        public long lectureId;
        public Long leaveId;
        public String subjectName;
        public String component;
        public long dateMillis;
        public String startTime;
        public String yours;     // effective status the student has
        public String pdf;       // P, A, E, L or NU from the PDF
    }

    public long importRunId;
    public long semesterId;
    public String semesterLabel;
    public boolean newSemester;
    public int rowsParsed;
    public int inserted;
    public int updated;
    public int unchanged;
    public final List<String> createdSubjects = new ArrayList<>();
    public final Map<String, String> renamedSubjects = new LinkedHashMap<>();
    public int autoApproved;      // pending lectures the PDF now shows as P/E/L
    public int stillAbsent;       // pending lectures the PDF still shows as A
    public int mergedManual;      // hand-added lectures this PDF now covers
    public final List<Conflict> conflicts = new ArrayList<>();

    public boolean changedNothing() {
        return inserted == 0 && updated == 0 && createdSubjects.isEmpty() && renamedSubjects.isEmpty();
    }

    // "3 pending lectures were resolved by this import: 2 approved, 1 still absent."
    public String reconciliationText() {
        int total = autoApproved + stillAbsent;
        if (total == 0) return null;
        return total + (total == 1 ? " pending lecture was" : " pending lectures were")
                + " checked against this import: " + autoApproved + " approved, "
                + stillAbsent + " still absent (still awaiting a decision).";
    }
}
