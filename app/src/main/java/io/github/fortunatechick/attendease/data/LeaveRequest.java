package io.github.fortunatechick.attendease.data;

public class LeaveRequest {

    public static final String TO_HOD = "HOD";
    public static final String TO_COORDINATOR = "COURSE_COORDINATOR";

    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PARTIAL = "PARTIAL";

    public long id;
    public long fromDate;      // midnight UTC
    public long toDate;        // midnight UTC, inclusive
    public String reason;
    public String submittedTo;
    public long submittedOn;   // midnight UTC
    public String status = SUBMITTED;
    public String note;

    public String submittedToLabel() {
        return TO_HOD.equals(submittedTo) ? "HOD" : "Course Coordinator";
    }

    // One lecture covered by a leave request, with its decision.
    public static class Link {
        public long leaveId;
        public long lectureId;
        public String decision;          // LeaveRules.PENDING / APPROVED / REJECTED
        public String previousStatus;    // only meaningful when previousManual
        public boolean previousManual;
    }
}
