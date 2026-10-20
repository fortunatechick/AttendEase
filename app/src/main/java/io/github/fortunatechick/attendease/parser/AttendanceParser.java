package io.github.fortunatechick.attendease.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Turns the text PdfBox extracts from the SAP attendance report into lecture records.
// Plain Java with no Android dependencies so it can be unit-tested on the JVM.
//
// Nothing here knows about particular subjects, electives, programmes or semesters: the
// subject names come from the report itself. The row format is matched loosely so other
// SAP report variants (other date or time styles, extra status codes) still parse.
public class AttendanceParser {

    public static final Set<String> KNOWN_STATUSES = new HashSet<>(java.util.Arrays.asList("P", "A", "E", "L", "NU"));

    // "Jul 13, 2026", "13.07.2026", "13/07/2026", "13-07-2026", "13-Jul-2026", "13 Jul 2026"
    private static final String DATE =
            "(?:[A-Z][a-z]{2}\\s+\\d{1,2},\\s*\\d{4}"
            + "|\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}"
            + "|\\d{1,2}[ -][A-Z][a-z]{2}[ -]\\d{4})";
    // "12:00:01 PM", "12:00 PM", "12:00:01PM", "12.00 p.m." style meridiems, or 24-hour "14:00"
    private static final String TIME = "\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[AaPp]\\.?[Mm]\\.?)?";

    // Anchored on the date, both times and a short upper-case status at the end of the line,
    // so the lazy course-name group can never swallow the date. Any status code is accepted
    // (not just P/A/E/L/NU) so an unfamiliar one is reported instead of the row vanishing.
    private static final Pattern ROW = Pattern.compile(
            "^\\s*(\\d{1,4})\\s+(.+?)\\s+(" + DATE + ")\\s+(" + TIME + ")\\s+(?:-\\s*)?(" + TIME + ")\\s+"
            + "(?!AM\\b|PM\\b)([A-Z]{1,3})\\s*$");

    // Component marker: T1 theory, P1 practical, TU1 tutorial (counted with theory).
    // No trailing \b: "...LaGrT1BTC5C" has the batch glued straight on.
    private static final Pattern MARKER = Pattern.compile("\\s*(TU|T|P)(\\d+)");

    private static final Pattern STUDENT_NAME = Pattern.compile("^\\s*Student Name\\s+(.+?)\\s*$");
    private static final Pattern STUDENT_NUMBER = Pattern.compile("^\\s*Student Number\\s+(\\S+)");
    private static final Pattern ROLL_NO = Pattern.compile("^\\s*Roll No\\.?\\s+(\\S+)");
    private static final Pattern SESSION = Pattern.compile("^\\s*Academic Year\\s*&\\s*Academic Session\\s+(.+?)\\s*$");
    private static final Pattern PROGRAM = Pattern.compile("^\\s*Program(?:me)? Name\\s+(.+?)\\s*$");
    private static final Pattern DURATION = Pattern.compile(
            "Duration\\s*:?\\s*From\\s+(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})\\s+to\\s+(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})");

    private static final Pattern STARTS_WITH_NUMBER = Pattern.compile("^\\s*\\d{1,4}\\s+\\S.*");

    private static final String[] DATE_FORMATS = {
            "MMM d, yyyy", "d.M.yyyy", "d/M/yyyy", "d-M-yyyy", "d-MMM-yyyy", "d MMM yyyy"};

    // Subject name, component and batch split out of a raw SAP course name.
    public static class CourseParts {
        public final String subject;
        public final String component;
        public final String batch;

        CourseParts(String subject, String component, String batch) {
            this.subject = subject;
            this.component = component;
            this.batch = batch;
        }
    }

    public static CourseParts splitCourseName(String rawCourseName) {
        String name = collapseSpaces(rawCourseName);
        Matcher m = MARKER.matcher(name);
        // The first marker that is not at the very start; a subject name never begins with it.
        while (m.find()) {
            if (m.start() > 0) {
                String subject = name.substring(0, m.start()).trim();
                String batch = name.substring(m.end()).replaceFirst("^[\\s\\-]+", "").trim();
                String component = "P".equals(m.group(1)) ? "P" : "T";
                return new CourseParts(subject, component, batch);
            }
        }
        // No marker at all (e.g. a manually typed name): treat it as theory.
        return new CourseParts(name, "T", "");
    }

    public ParseResult parse(String text) {
        StudentProfile profile = new StudentProfile();
        List<String[]> rows = new ArrayList<>();          // {srNo, course, date, start, end, status}
        List<String> unmatched = new ArrayList<>();
        Map<String, Integer> unknownStatuses = new TreeMap<>();

        String[] lines = text == null ? new String[0] : text.split("\\r?\\n");
        for (String line : lines) {
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                rows.add(new String[]{row.group(1), row.group(2), row.group(3),
                        row.group(4), row.group(5), row.group(6)});
                continue;
            }
            readHeaderLine(line, profile);
            if (STARTS_WITH_NUMBER.matcher(line).matches()) {
                unmatched.add(line.trim());
            }
        }

        // First pass: split every course name so we know the full set of subject names.
        List<CourseParts> parts = new ArrayList<>();
        Set<String> subjectNames = new LinkedHashSet<>();
        for (String[] r : rows) {
            CourseParts p = splitCourseName(r[1]);
            parts.add(p);
            subjectNames.add(p.subject);
        }
        Map<String, String> canonical = mergePrefixes(subjectNames);

        List<LectureRecord> records = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            CourseParts p = parts.get(i);
            long dateMillis;
            try {
                dateMillis = parseDate(r[2]);
            } catch (ParseException e) {
                unmatched.add(r[0] + " " + r[1] + " " + r[2]);
                continue;
            }
            String start = normaliseTime(r[3]);
            String end = normaliseTime(r[4]);
            if (start == null || end == null) {
                unmatched.add(r[0] + " " + r[1] + " " + r[3] + " " + r[4]);
                continue;
            }
            // An unfamiliar code is kept as "not updated" (not counted) and reported, so the
            // student can set those lectures by hand rather than lose them.
            String status = r[5];
            if (!KNOWN_STATUSES.contains(status)) {
                Integer n = unknownStatuses.get(status);
                unknownStatuses.put(status, n == null ? 1 : n + 1);
                status = "NU";
            }
            records.add(new LectureRecord(
                    Integer.parseInt(r[0]),
                    collapseSpaces(r[1]),
                    canonical.get(p.subject),
                    p.component,
                    p.batch,
                    formatDate(dateMillis),
                    dateMillis,
                    start,
                    end,
                    status));
        }

        Map<String, String> merges = new TreeMap<>();
        for (Map.Entry<String, String> e : canonical.entrySet()) {
            if (!e.getKey().equals(e.getValue())) merges.put(e.getKey(), e.getValue());
        }

        List<Integer> missing = new ArrayList<>();
        List<Integer> duplicates = new ArrayList<>();
        checkSequence(records, missing, duplicates);

        return new ParseResult(profile, records, merges, missing, duplicates, unmatched, unknownStatuses);
    }

    // Comparison key for subject names: case, repeated spaces and trailing punctuation
    // don't make two names different subjects.
    public static String nameKey(String name) {
        return collapseSpaces(name).toLowerCase(Locale.ROOT).replaceAll("[\\s\\-.,:;]+$", "");
    }

    // SAP truncates long course names, differently for theory and lab rows
    // ("Mobile Application Develop" vs "Mobile Application Development"). A name is mapped
    // to a longer one when it looks like a truncation of it: same text up to its length,
    // cut in the middle of a word, and at least 8 characters. A cut at a word boundary
    // ("Machine Learning" vs "Machine Learning Applications") could be two real subjects,
    // so those are left alone; the student can merge them by hand. Names that differ only
    // in case or spacing are the same subject. If the longer candidates disagree with each
    // other the match is ambiguous and the name is left alone rather than guessed.
    public static Map<String, String> mergePrefixes(Set<String> names) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : names) {
            String key = nameKey(name);
            String best = name;
            List<String> truncationKeys = new ArrayList<>();
            for (String other : names) {
                String otherKey = nameKey(other);
                boolean truncation = isTruncation(key, otherKey);
                if (truncation) truncationKeys.add(otherKey);
                if ((truncation || otherKey.equals(key)) && preferred(other, best)) best = other;
            }
            String bestKey = nameKey(best);
            boolean ambiguous = false;
            for (String c : truncationKeys) {
                if (!bestKey.startsWith(c)) ambiguous = true;
            }
            if (ambiguous) {
                // Still fold together spellings of this exact name, but don't guess between longer ones.
                best = name;
                for (String other : names) {
                    if (nameKey(other).equals(key) && preferred(other, best)) best = other;
                }
            }
            result.put(name, best);
        }
        return result;
    }

    // Which spelling to keep: the fullest name, then the longest text, then the one with
    // the most capitals ("Operating Systems" over "operating systems"), then alphabetical.
    private static boolean preferred(String a, String b) {
        int byKey = Integer.compare(nameKey(a).length(), nameKey(b).length());
        if (byKey != 0) return byKey > 0;
        if (a.length() != b.length()) return a.length() > b.length();
        int capsA = 0, capsB = 0;
        for (char c : a.toCharArray()) if (Character.isUpperCase(c)) capsA++;
        for (char c : b.toCharArray()) if (Character.isUpperCase(c)) capsB++;
        if (capsA != capsB) return capsA > capsB;
        return a.compareTo(b) < 0;
    }

    // True when shortKey looks like SAP's truncation of longKey.
    public static boolean isTruncation(String shortKey, String longKey) {
        if (shortKey.length() < 8 || longKey.length() <= shortKey.length()) return false;
        if (!longKey.startsWith(shortKey)) return false;
        return Character.isLetterOrDigit(shortKey.charAt(shortKey.length() - 1))
                && Character.isLetterOrDigit(longKey.charAt(shortKey.length()));
    }

    private static void checkSequence(List<LectureRecord> records, List<Integer> missing,
                                      List<Integer> duplicates) {
        Set<Integer> seen = new HashSet<>();
        int max = 0;
        for (LectureRecord r : records) {
            if (!seen.add(r.srNo)) duplicates.add(r.srNo);
            max = Math.max(max, r.srNo);
        }
        for (int i = 1; i <= max; i++) {
            if (!seen.contains(i)) missing.add(i);
        }
    }

    private static void readHeaderLine(String line, StudentProfile profile) {
        Matcher m;
        if (profile.name == null && (m = STUDENT_NAME.matcher(line)).find()) {
            profile.name = m.group(1);
        } else if (profile.studentNumber == null && (m = STUDENT_NUMBER.matcher(line)).find()) {
            profile.studentNumber = m.group(1);
        } else if (profile.rollNo == null && (m = ROLL_NO.matcher(line)).find()) {
            profile.rollNo = m.group(1);
        } else if (profile.academicSession == null && (m = SESSION.matcher(line)).find()) {
            profile.academicSession = m.group(1);
        } else if (profile.programName == null && (m = PROGRAM.matcher(line)).find()) {
            profile.programName = m.group(1);
        } else if (profile.durationFrom == null && (m = DURATION.matcher(line)).find()) {
            profile.durationFrom = m.group(1);
            profile.durationTo = m.group(2);
        }
    }

    // Accepts every date style in DATE. Day-first for the numeric forms, as SAP India prints them.
    public static long parseDate(String dateText) throws ParseException {
        String text = collapseSpaces(dateText).replaceAll(",(\\S)", ", $1");
        for (String pattern : DATE_FORMATS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            format.setLenient(false);
            try {
                java.text.ParsePosition pos = new java.text.ParsePosition(0);
                java.util.Date d = format.parse(text, pos);
                if (d != null && pos.getIndex() == text.length()) return d.getTime();
            } catch (IllegalArgumentException ignored) {
                // try the next pattern
            }
        }
        throw new ParseException("Unrecognised date: " + dateText, 0);
    }

    // The one display form used everywhere: "Jul 13, 2026".
    static String formatDate(long dateMillis) {
        SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date(dateMillis));
    }

    // Any accepted time style -> "h:mm:ss AM". Returns null if it isn't a real time.
    // "10:00:01AM", "10:00 am", "10.00 a.m." style and 24-hour "14:00" are all understood.
    static String normaliseTime(String time) {
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(?:([AaPp])\\.?[Mm]\\.?)?").matcher(time.trim());
        if (!m.matches()) return null;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        int second = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        if (minute > 59 || second > 59) return null;
        String meridiem;
        if (m.group(4) != null) {
            if (hour < 1 || hour > 12) return null;
            meridiem = m.group(4).equalsIgnoreCase("A") ? "AM" : "PM";
        } else {
            if (hour > 23) return null;
            meridiem = hour < 12 ? "AM" : "PM";
            hour = hour % 12 == 0 ? 12 : hour % 12;
        }
        return String.format(Locale.US, "%d:%02d:%02d %s", hour, minute, second, meridiem);
    }

    public static int minutesOfDay(String time) {
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*([AP]M)").matcher(time);
        if (!m.find()) return 0;
        int hour = Integer.parseInt(m.group(1)) % 12;
        if ("PM".equals(m.group(3))) hour += 12;
        return hour * 60 + Integer.parseInt(m.group(2));
    }

    private static String collapseSpaces(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }
}
