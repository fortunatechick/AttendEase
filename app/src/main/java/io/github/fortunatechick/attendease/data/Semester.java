package io.github.fortunatechick.attendease.data;

// One semester's worth of subjects, e.g. "2026-2027, Semester V" from the PDF header.
public class Semester {

    public long id;
    public String label;
    public long createdAt;

    public Semester(long id, String label, long createdAt) {
        this.id = id;
        this.label = label;
        this.createdAt = createdAt;
    }
}
