package io.github.fortunatechick.attendease.calc;

import java.util.List;
import java.util.Locale;

// Builds the CSV export. Excel opens it directly; no spreadsheet library needed.
// Attended / Held is the confirmed figure (decided lectures only); pending is its own column.
public class CsvExporter {

    public static final String HEADER =
            "Subject,Component,Attended,Held,Missed,Pending,Confirmed%,IfApproved%,IfRejected%,Status,"
            + "SemesterLectures,MissesAllowed,CanStillMiss";

    public static class Row {
        public final String subject;
        public final String component;
        public final AttendanceStats stats;
        public final SemesterBudget budget;   // only on combined rows; null elsewhere

        public Row(String subject, String component, AttendanceStats stats) {
            this(subject, component, stats, null);
        }

        public Row(String subject, String component, AttendanceStats stats, SemesterBudget budget) {
            this.subject = subject;
            this.component = component;
            this.stats = stats;
            this.budget = budget;
        }
    }

    public static String build(List<Row> rows, AttendanceStats overall) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        for (Row r : rows) {
            appendRow(sb, r.subject, r.component, r.stats, r.budget);
        }
        appendRow(sb, "Overall", "All", overall, null);
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String subject, String component, AttendanceStats s,
                                  SemesterBudget b) {
        sb.append(quote(subject)).append(',')
                .append(quote(component)).append(',')
                .append(s.decidedAttended()).append(',')
                .append(s.decidedHeld()).append(',')
                .append(s.missed()).append(',')
                .append(s.pending).append(',')
                .append(number(s.confirmedPercent())).append(',')
                .append(number(s.ifApprovedPercent())).append(',')
                .append(number(s.ifRejectedPercent())).append(',')
                .append(quote(status(s))).append(',')
                .append(b == null ? "" : String.valueOf(b.total)).append(',')
                .append(b == null ? "" : String.valueOf(b.maxMisses)).append(',')
                .append(b == null ? "" : String.valueOf(b.canStillMiss())).append("\r\n");
    }

    static String status(AttendanceStats s) {
        switch (s.standing()) {
            case SAFE:
                return "SAFE";
            case AT_RISK:
                return "AT RISK";
            case DEPENDS_ON_LEAVE:
                return "DEPENDS ON LEAVE";
            default:
                return "NO DATA";
        }
    }

    static String number(double percent) {
        return Double.isNaN(percent) ? "" : String.format(Locale.US, "%.1f", percent);
    }

    // RFC 4180: quote a field containing a comma, quote or line break, doubling inner quotes.
    static String quote(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
