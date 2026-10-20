package io.github.fortunatechick.attendease.parser;

// One lecture hour as printed in the SAP attendance report.
public class LectureRecord {

    public final int srNo;
    public final String rawCourseName;
    public final String subjectName;   // canonical name, after prefix merging
    public final String component;     // "T" (theory) or "P" (practical)
    public final String batchLabel;
    public final String dateText;      // e.g. "Jul 13, 2026"
    public final long dateMillis;      // midnight UTC of that date
    public final String startTime;     // e.g. "12:00:01 PM"
    public final String endTime;
    public final String status;        // P, A, E, L or NU

    public LectureRecord(int srNo, String rawCourseName, String subjectName, String component,
                         String batchLabel, String dateText, long dateMillis,
                         String startTime, String endTime, String status) {
        this.srNo = srNo;
        this.rawCourseName = rawCourseName;
        this.subjectName = subjectName;
        this.component = component;
        this.batchLabel = batchLabel;
        this.dateText = dateText;
        this.dateMillis = dateMillis;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public boolean isNotUpdated() {
        return "NU".equals(status);
    }

    public boolean isAttended() {
        return "P".equals(status) || "E".equals(status) || "L".equals(status);
    }

    public int startMinutes() {
        return AttendanceParser.minutesOfDay(startTime);
    }

    @Override
    public String toString() {
        return srNo + " " + rawCourseName + " " + dateText + " " + startTime + " " + status;
    }
}
