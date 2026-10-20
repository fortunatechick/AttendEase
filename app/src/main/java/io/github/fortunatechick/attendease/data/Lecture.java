package io.github.fortunatechick.attendease.data;

import java.util.Objects;

// One row of the lecture table.
public class Lecture {

    public long id;
    public long subjectId;
    public String component;        // "T" or "P"
    public long dateMillis;         // midnight UTC
    public String startTime;        // "10:00:01 AM"
    public int startMinutes;        // minutes after midnight, for sorting
    public String endTime;
    public String rawCourseName;
    public String batchLabel;
    public int srNo;                // 0 for lectures entered by hand
    public String sourceStatus;     // P, A, E, L or NU as last seen in a PDF
    public String effectiveStatus;  // one of Status.*
    public boolean manualOverride;
    public Long leaveId;
    public Long importRunId;

    // Lectures typed in by hand use this in place of a SAP course name.
    public static final String MANUAL_PREFIX = "manual:";

    public static String manualCourseName(long subjectId, String component) {
        return MANUAL_PREFIX + subjectId + ":" + component;
    }

    public boolean isHandAdded() {
        return rawCourseName != null && rawCourseName.startsWith(MANUAL_PREFIX);
    }

    // Same subject, day, start time and component: how a PDF row finds a lecture typed in by hand.
    public static String slotKey(long subjectId, long dateMillis, int startMinutes, String component) {
        return subjectId + "|" + dateMillis + "|" + startMinutes + "|" + component;
    }

    public static String naturalKey(String rawCourseName, long dateMillis, String startTime) {
        return rawCourseName + "|" + dateMillis + "|" + startTime;
    }

    public String naturalKey() {
        return naturalKey(rawCourseName, dateMillis, startTime);
    }

    public boolean isTheory() {
        return "T".equals(component);
    }

    public Lecture copy() {
        Lecture c = new Lecture();
        c.id = id;
        c.subjectId = subjectId;
        c.component = component;
        c.dateMillis = dateMillis;
        c.startTime = startTime;
        c.startMinutes = startMinutes;
        c.endTime = endTime;
        c.rawCourseName = rawCourseName;
        c.batchLabel = batchLabel;
        c.srNo = srNo;
        c.sourceStatus = sourceStatus;
        c.effectiveStatus = effectiveStatus;
        c.manualOverride = manualOverride;
        c.leaveId = leaveId;
        c.importRunId = importRunId;
        return c;
    }

    // Compares the stored content, ignoring which import run last touched the row.
    public boolean sameContentAs(Lecture o) {
        return subjectId == o.subjectId
                && dateMillis == o.dateMillis
                && startMinutes == o.startMinutes
                && srNo == o.srNo
                && manualOverride == o.manualOverride
                && Objects.equals(component, o.component)
                && Objects.equals(startTime, o.startTime)
                && Objects.equals(endTime, o.endTime)
                && Objects.equals(rawCourseName, o.rawCourseName)
                && Objects.equals(batchLabel, o.batchLabel)
                && Objects.equals(sourceStatus, o.sourceStatus)
                && Objects.equals(effectiveStatus, o.effectiveStatus)
                && Objects.equals(leaveId, o.leaveId);
    }
}
