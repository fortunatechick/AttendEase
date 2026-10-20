package io.github.fortunatechick.attendease.data;

// Effective status of a lecture, as stored in lecture.effective_status.
public final class Status {

    public static final String PRESENT = "PRESENT";
    public static final String ABSENT = "ABSENT";
    public static final String PENDING_LEAVE = "PENDING_LEAVE";
    public static final String NOT_UPDATED = "NOT_UPDATED";
    // An approved medical leave. Counted as attended or dropped from the total,
    // depending on the approval-convention setting.
    public static final String EXEMPT = "EXEMPT";

    private Status() {
    }

    // P, E and L all count as attended; NU is not a lecture held yet.
    public static String fromSource(String sourceStatus) {
        if (sourceStatus == null) return NOT_UPDATED;
        switch (sourceStatus) {
            case "P":
            case "E":
            case "L":
                return PRESENT;
            case "A":
                return ABSENT;
            default:
                return NOT_UPDATED;
        }
    }

    // Statuses the PDF can no longer overwrite: they carry a decision the student made.
    public static boolean isStudentOwned(String effectiveStatus) {
        return PENDING_LEAVE.equals(effectiveStatus) || EXEMPT.equals(effectiveStatus);
    }
}
