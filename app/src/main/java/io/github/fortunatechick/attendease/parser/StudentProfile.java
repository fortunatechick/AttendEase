package io.github.fortunatechick.attendease.parser;

// Header block from page 1 of the report. Any field may be null if it was not found.
public class StudentProfile {

    public String name;
    public String studentNumber;
    public String rollNo;
    public String academicSession;
    public String programName;
    public String durationFrom;   // as printed, e.g. "13.07.2026"
    public String durationTo;

    public boolean isEmpty() {
        return name == null && studentNumber == null && rollNo == null
                && academicSession == null && programName == null && durationFrom == null;
    }
}
