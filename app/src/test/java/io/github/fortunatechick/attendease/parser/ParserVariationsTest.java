package io.github.fortunatechick.attendease.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Reports from other students, programmes and semesters: different electives, name styles,
// markers, date and time formats and status codes. Nothing here is from the sample report.
public class ParserVariationsTest {

    private final AttendanceParser parser = new AttendanceParser();

    private static final String OTHER_STUDENT =
            "Student Name TEST STUDENT\n" +
            "Roll No. A045\n" +
            "Academic Year & Academic Session 2027-2028, Semester III\n" +
            "Programme Name B Tech (Information Technology)\n" +
            "Attendance Report Duration : From 05-01-2027 to 30-04-2027\n" +
            "1 Data Structures and AlgorithmsT1 BT IT A Jan 5, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "2 Data Structures and AlgorithmsP1 BT IT A A1 Jan 5, 2027 10:00:01 AM 11:00:00 AM A\n" +
            "3 Engineering Mathematics - IIIT1 B Tech IT A Jan 6, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "4 Engineering Mathematics - IIITU1 B Tech IT A Jan 6, 2027 10:00:01 AM 11:00:00 AM P\n" +
            "5 Python ProgrammingP2 BT IT A2 Jan 7, 2027 2:00:01 PM 3:00:00 PM P\n" +
            "6 Machine Learning T1 BT IT A Jan 7, 2027 3:00:01 PM 4:00:00 PM P\n" +
            "7 Machine Learning ApplicationsT1-OE2 Jan 8, 2027 4:00:01 PM 5:00:00 PM A\n" +
            "8 Design ThinkingT1-OE-III Jan 8, 2027 5:00:01 PM 6:00:00 PM OD\n" +
            "9 operating systemsT1 BT IT A Jan 11, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "10 Operating SystemsP1 BT IT A A1 Jan 11, 2027 10:00:01 AM 11:00:00 AM NU\n" +
            "11 Cloud Computing and Virtualizat P1 BT IT A1 Jan 12, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "12 Cloud Computing and VirtualizationT1 BT IT A Jan 12, 2027 11:00:01 AM 12:00:00 PM L\n";

    @Test
    public void readsADifferentStudentsReport() {
        ParseResult r = parser.parse(OTHER_STUDENT);
        assertEquals(12, r.records.size());
        assertFalse(r.hasSequenceProblems());
        assertEquals("2027-2028, Semester III", r.profile.academicSession);
        assertEquals("B Tech (Information Technology)", r.profile.programName);
        assertEquals("05-01-2027", r.profile.durationFrom);
    }

    @Test
    public void subjectsComeFromTheReportNotAFixedList() {
        assertEquals(Arrays.asList(
                        "Cloud Computing and Virtualization",
                        "Data Structures and Algorithms",
                        "Design Thinking",
                        "Engineering Mathematics - III",
                        "Machine Learning",
                        "Machine Learning Applications",
                        "Operating Systems",
                        "Python Programming"),
                parser.parse(OTHER_STUDENT).subjectNames());
    }

    @Test
    public void romanNumeralsAndTutorialsSplitCorrectly() {
        AttendanceParser.CourseParts p = AttendanceParser.splitCourseName("Engineering Mathematics - IIITU1 B Tech IT A");
        assertEquals("Engineering Mathematics - III", p.subject);
        assertEquals("T", p.component);   // tutorial counts with theory
        assertEquals("B Tech IT A", p.batch);
        assertEquals("Python Programming", AttendanceParser.splitCourseName("Python ProgrammingP2 BT IT A2").subject);
        assertEquals("P", AttendanceParser.splitCourseName("Python ProgrammingP2 BT IT A2").component);
    }

    @Test
    public void caseAndSpacingVariantsAreOneSubject() {
        ParseResult r = parser.parse(OTHER_STUDENT);
        assertEquals(2, r.recordsFor("Operating Systems").size());
        assertEquals("Operating Systems", r.prefixMerges.get("operating systems"));
    }

    @Test
    public void truncatedNameMergesButWordBoundaryPrefixDoesNot() {
        ParseResult r = parser.parse(OTHER_STUDENT);
        assertEquals("Cloud Computing and Virtualization", r.prefixMerges.get("Cloud Computing and Virtualizat"));
        // "Machine Learning" and "Machine Learning Applications" are different subjects.
        assertEquals(1, r.recordsFor("Machine Learning").size());
        assertEquals(1, r.recordsFor("Machine Learning Applications").size());
    }

    @Test
    public void unknownStatusCodesAreReportedNotDropped() {
        ParseResult r = parser.parse(OTHER_STUDENT);
        assertEquals(Collections.singletonMap("OD", 1), r.unknownStatuses);
        // Kept as "not updated" so it isn't counted until the student sets it.
        assertEquals("NU", r.recordsFor("Design Thinking").get(0).status);
    }

    @Test
    public void otherDateAndTimeStyles() throws Exception {
        String text =
                "1 Compiler DesignT1 BT CE A 13.07.2027 14:00 15:00 P\n" +
                "2 Compiler DesignT1 BT CE A 14/07/2027 9:00 AM 10:00 AM A\n" +
                "3 Compiler DesignP1 BT CE A1 15-Jul-2027 2:00PM 3:00PM P\n" +
                "4 Compiler DesignP1 BT CE A1 16 Jul 2027 11:00:01 am 12:00:00 pm P\n";
        ParseResult r = parser.parse(text);
        assertEquals(4, r.records.size());
        assertEquals("Jul 13, 2027", r.records.get(0).dateText);
        assertEquals("2:00:00 PM", r.records.get(0).startTime);
        assertEquals(14 * 60, r.records.get(0).startMinutes());
        assertEquals(AttendanceParser.parseDate("Jul 14, 2027"), r.records.get(1).dateMillis);
        assertEquals("9:00:00 AM", r.records.get(1).startTime);
        assertEquals("2:00:00 PM", r.records.get(2).startTime);
        assertEquals("11:00:01 AM", r.records.get(3).startTime);
        assertEquals("12:00:00 PM", r.records.get(3).endTime);
    }

    @Test
    public void meridiemIsNeverTakenAsTheStatus() {
        // Status column missing: must not read "PM" as a status code.
        ParseResult r = parser.parse("1 Compiler DesignT1 BT CE A Jul 13, 2027 2:00:01 PM 3:00:00 PM\n");
        assertTrue(r.isEmpty());
        assertEquals(1, r.unmatchedLines.size());
    }

    @Test
    public void sampleReportTimesAreUnchanged() {
        assertEquals("12:00:01 PM", AttendanceParser.normaliseTime("12:00:01 PM"));
        assertEquals("1:00:00 PM", AttendanceParser.normaliseTime("1:00:00PM"));
        assertEquals("12:00:00 AM", AttendanceParser.normaliseTime("00:00"));
        assertNull(AttendanceParser.normaliseTime("25:00"));
        assertNull(AttendanceParser.normaliseTime("13:00 PM"));
    }

    @Test
    public void truncationRule() {
        assertTrue(AttendanceParser.isTruncation("mobile application develop", "mobile application development"));
        assertTrue(AttendanceParser.isTruncation("financial der", "financial derivatives"));
        assertFalse(AttendanceParser.isTruncation("machine learning", "machine learning applications"));
        assertFalse(AttendanceParser.isTruncation("data", "database systems"));   // too short to trust
    }

    @Test
    public void mergeKeepsTheLongestSpelling() {
        Map<String, String> m = AttendanceParser.mergePrefixes(new LinkedHashSet<>(Arrays.asList(
                "Operating Systems", "operating  systems", "Operating Systems -")));
        assertEquals("Operating Systems -", m.get("Operating Systems"));
        assertEquals("Operating Systems -", m.get("operating  systems"));
    }
}
