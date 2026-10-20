package io.github.fortunatechick.attendease.data;

public class Subject {

    public long id;
    public long semesterId;
    public String canonicalName;   // name used to match PDF rows
    public String shortName;       // name shown in the UI; the student can rename it
    public long createdAt;
    // Lectures per week, when the student has set them. Null means "estimate from the PDF".
    public Integer theoryPerWeek;
    public Integer labPerWeek;

    public Subject() {
    }

    public Subject(long id, String canonicalName, String shortName, long createdAt) {
        this.id = id;
        this.canonicalName = canonicalName;
        this.shortName = shortName;
        this.createdAt = createdAt;
    }

    public boolean hasCustomPattern() {
        return theoryPerWeek != null || labPerWeek != null;
    }

    public String displayName() {
        return shortName != null && !shortName.isEmpty() ? shortName : canonicalName;
    }
}
