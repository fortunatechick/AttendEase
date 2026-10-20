package io.github.fortunatechick.attendease.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Fixture is 30 lines in the shape PdfBox-Android produced on the device, including the
// page break and the wrapped table header. The real report has no E, L or NU rows,
// so rows 7, 8, 26-29 have had their status changed to cover every code.
public class AttendanceParserTest {

    static final String FIXTURE =
            "SVKM'S NMIMS Mukesh Patel Schl of Tech Mgt & Engg-Mum, Mumbai\n" +
            "Student Name TEST STUDENT\n" +
            "Student Number 70000000000\n" +
            "Roll No. X000\n" +
            "Academic Year & Academic Session 2026-2027, Semester V\n" +
            "Program Name B Tech (Computer Engineering)\n" +
            "Attendance Report Duration :      From 13.07.2026 to 11.10.2026\n" +
            "Sr \n" +
            "No. Course Name Date Start Time End Time Attenda\n" +
            "nce\n" +
            "1 Operating SystemsT1 BT IT A Jul 13, 2026 12:00:01 PM 1:00:00 PM P\n" +
            "2 Artificial Intelligence T1  B Tech IT A Jul 13, 2026 2:00:01 PM 3:00:00 PM P\n" +
            "3 Research MethodologyT1-OE1-V Jul 13, 2026 4:00:01 PM 5:00:00 PM A\n" +
            "4 Research MethodologyT1-OE1-V Jul 13, 2026 5:00:01 PM 6:00:00 PM A\n" +
            "5 RAG Sys&Agen WorkflowithPine&LaGrT1BTC5A Jul 14, 2026 7:00:01 AM 8:00:00 AM P\n" +
            "6 Software EngineeringT1 BT IT A Jul 14, 2026 10:00:01 AM 11:00:00 AM P\n" +
            "7 Image and Video ProcessingT1 BTech IT A Jul 14, 2026 11:00:01 AM 12:00:00 PM E\n" +
            "8 Artificial Intelligence T1  B Tech IT A Jul 14, 2026 12:00:01 PM 1:00:00 PM L\n" +
            "9 Research MethodologyT1-OE1-V Jul 14, 2026 4:00:01 PM 5:00:00 PM A\n" +
            "10 RAG Sys&Agen WorkflowithPine&LaGrT1BTC5A Jul 15, 2026 7:00:01 AM 8:00:00 AM P\n" +
            "11 Artificial Intelligence P1 BT IT A A2 Jul 15, 2026 8:00:01 AM 9:00:00 AM P\n" +
            "12 Artificial Intelligence P1 BT IT A A2 Jul 15, 2026 9:00:01 AM 10:00:00 AM A\n" +
            "13 Mobile Application DevelopP1 BT IT A1+A2 Jul 15, 2026 10:00:01 AM 11:00:00 AM P\n" +
            "14 Mobile Application DevelopP1 BT IT A1+A2 Jul 15, 2026 11:00:01 AM 12:00:00 PM A\n" +
            "Page 1 of 16\n" +
            "Sr \n" +
            "No. Course Name Date Start Time End Time Attenda\n" +
            "nce\n" +
            "15 Operating SystemsP1 BT IT A A2 Jul 15, 2026 2:00:01 PM 3:00:00 PM P\n" +
            "16 Operating SystemsP1 BT IT A A2 Jul 15, 2026 3:00:01 PM 4:00:00 PM P\n" +
            "17 Mobile Application DevelopmentT1 BT IT A Jul 16, 2026 10:00:01 AM 11:00:00 AM P\n" +
            "18 Operating SystemsT1 BT IT A Jul 16, 2026 11:00:01 AM 12:00:00 PM A\n" +
            "19 Financial DerT1-OE 2-B Tech & MBA-DIV A Jul 16, 2026 4:00:01 PM 5:00:00 PM P\n" +
            "20 Financial DerT1-OE 2-B Tech & MBA-DIV A Jul 16, 2026 5:00:01 PM 6:00:00 PM A\n" +
            "21 Image and Video ProcessingP1 BT IT A2 Jul 17, 2026 10:00:01 AM 11:00:00 AM P\n" +
            "22 Image and Video ProcessingP1 BT IT A2 Jul 17, 2026 11:00:01 AM 12:00:00 PM P\n" +
            "23 Software EngineeringT1 BT IT A Jul 17, 2026 1:00:01 PM 2:00:00 PM P\n" +
            "24 Software EngineeringP1 B Tech IT A A2 Jul 17, 2026 2:00:01 PM 3:00:00 PM P\n" +
            "25 Software EngineeringP1 B Tech IT A A2 Jul 17, 2026 3:00:01 PM 4:00:00 PM A\n" +
            "26 Mobile Application DevelopmentT1 BT IT A Jul 20, 2026 10:00:01 AM 11:00:00 AM NU\n" +
            "27 Artificial Intelligence T1  B Tech IT A Jul 20, 2026 2:00:01 PM 3:00:00 PM NU\n" +
            "28 Operating SystemsT1 BT IT A Jul 20, 2026 3:00:01 PM 4:00:00 PM E\n" +
            "29 Research MethodologyT1-OE1-V Jul 20, 2026 4:00:01 PM 5:00:00 PM NU\n" +
            "30 Mobile Application DevelopmentT1 BT IT A Jul 21, 2026 12:00:01 PM 1:00:00 PM P\n" +
            "P - Present : A - Absent : E - Exemption : L - Late Admission : NU - Not Updated\n" +
            "Above data is viewed as per attendance data in the SAP system. Please contact college admin staff for any \n" +
            "attendance related query.\n" +
            "Page 2 of 16\n";

    private final AttendanceParser parser = new AttendanceParser();

    @Test
    public void parsesEveryRowAndIgnoresHeadersAndPageBreaks() {
        ParseResult result = parser.parse(FIXTURE);
        assertEquals(30, result.records.size());
        assertFalse(result.hasSequenceProblems());
        assertTrue(result.unmatchedLines.isEmpty());
    }

    @Test
    public void countsAllFiveStatusCodes() {
        ParseResult result = parser.parse(FIXTURE);
        assertEquals(16, result.countByStatus("P"));
        assertEquals(8, result.countByStatus("A"));
        assertEquals(2, result.countByStatus("E"));
        assertEquals(1, result.countByStatus("L"));
        assertEquals(3, result.countByStatus("NU"));
    }

    @Test
    public void readsHeaderProfile() {
        StudentProfile p = parser.parse(FIXTURE).profile;
        assertEquals("TEST STUDENT", p.name);
        assertEquals("70000000000", p.studentNumber);
        assertEquals("X000", p.rollNo);
        assertEquals("2026-2027, Semester V", p.academicSession);
        assertEquals("B Tech (Computer Engineering)", p.programName);
        assertEquals("13.07.2026", p.durationFrom);
        assertEquals("11.10.2026", p.durationTo);
    }

    // Rule 1: NU rows are kept, but they are not lectures held.
    @Test
    public void notUpdatedRowsAreKeptButExcludedFromHeld() {
        ParseResult result = parser.parse(FIXTURE);
        // AI rows: P (T), L (T), P (P), A (P), NU (T)
        List<LectureRecord> ai = result.recordsFor("Artificial Intelligence");
        assertEquals(5, ai.size());
        assertEquals(4, held(ai));
        assertEquals(3, attended(ai));
    }

    // Rule 2: glued, spaced, and glued-on-both-sides markers.
    @Test
    public void splitsCourseNameOnTheoryPracticalMarker() {
        assertParts("Operating SystemsT1 BT IT A", "Operating Systems", "T", "BT IT A");
        assertParts("Artificial Intelligence T1  B Tech IT A", "Artificial Intelligence", "T", "B Tech IT A");
        assertParts("Operating SystemsP1 BT IT A A2", "Operating Systems", "P", "BT IT A A2");
        assertParts("Research MethodologyT1-OE1-V", "Research Methodology", "T", "OE1-V");
        assertParts("RAG Sys&Agen WorkflowithPine&LaGrT1BTC5A", "RAG Sys&Agen WorkflowithPine&LaGr", "T", "BTC5A");
        assertParts("Financial DerT1-OE 2-B Tech & MBA-DIV A", "Financial Der", "T", "OE 2-B Tech & MBA-DIV A");
        assertParts("Some Manual Subject", "Some Manual Subject", "T", "");
    }

    @Test
    public void theoryAndPracticalShareOneSubject() {
        ParseResult result = parser.parse(FIXTURE);
        List<LectureRecord> os = result.recordsFor("Operating Systems");
        assertEquals(5, os.size());
        assertEquals(3, countComponent(os, "T"));
        assertEquals(2, countComponent(os, "P"));
    }

    // Rule 3: the truncated lab name merges into the full theory name.
    @Test
    public void truncatedNamesMergeIntoLongerName() {
        ParseResult result = parser.parse(FIXTURE);
        assertEquals(Collections.singletonMap("Mobile Application Develop", "Mobile Application Development"),
                result.prefixMerges);

        List<LectureRecord> mad = result.recordsFor("Mobile Application Development");
        assertEquals(5, mad.size());
        assertEquals(4, held(mad));
        assertEquals(3, attended(mad));
        assertTrue(result.recordsFor("Mobile Application Develop").isEmpty());
    }

    @Test
    public void truncatedNameWithoutLongerFormStaysAsIs() {
        ParseResult result = parser.parse(FIXTURE);
        assertEquals(2, result.recordsFor("Financial Der").size());
    }

    @Test
    public void findsEightSubjects() {
        assertEquals(Arrays.asList(
                        "Artificial Intelligence",
                        "Financial Der",
                        "Image and Video Processing",
                        "Mobile Application Development",
                        "Operating Systems",
                        "RAG Sys&Agen WorkflowithPine&LaGr",
                        "Research Methodology",
                        "Software Engineering"),
                parser.parse(FIXTURE).subjectNames());
    }

    @Test
    public void ambiguousPrefixIsNotMerged() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>(
                Arrays.asList("Data", "Data Science", "Data Structures"));
        assertEquals("Data", AttendanceParser.mergePrefixes(names).get("Data"));
    }

    @Test
    public void reportsGapInSerialNumbers() {
        String withGap = FIXTURE.replace(
                "12 Artificial Intelligence P1 BT IT A A2 Jul 15, 2026 9:00:01 AM 10:00:00 AM A\n", "");
        ParseResult result = parser.parse(withGap);
        assertEquals(29, result.records.size());
        assertEquals(Collections.singletonList(12), result.missingSrNos);
    }

    @Test
    public void parsesDateAndTimes() throws Exception {
        LectureRecord first = parser.parse(FIXTURE).records.get(0);
        assertEquals("Jul 13, 2026", first.dateText);
        assertEquals(AttendanceParser.parseDate("Jul 13, 2026"), first.dateMillis);
        assertEquals("12:00:01 PM", first.startTime);
        assertEquals(12 * 60, first.startMinutes());
        assertEquals(7 * 60, AttendanceParser.minutesOfDay("7:00:01 AM"));
        assertEquals(0, AttendanceParser.minutesOfDay("12:00:01 AM"));
    }

    @Test
    public void toleratesSingleSpacesAndGluedMeridiem() {
        String line = "1 Operating SystemsT1 BT IT A Jul 13,2026 12:00:01PM 1:00:00PM P";
        ParseResult result = parser.parse(line);
        assertEquals(1, result.records.size());
        assertEquals("Jul 13, 2026", result.records.get(0).dateText);
        assertEquals("12:00:01 PM", result.records.get(0).startTime);
    }

    @Test
    public void garbageInputGivesEmptyResult() {
        assertTrue(parser.parse("this is not an attendance report").isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    private static void assertParts(String raw, String subject, String component, String batch) {
        AttendanceParser.CourseParts p = AttendanceParser.splitCourseName(raw);
        assertEquals(subject, p.subject);
        assertEquals(component, p.component);
        assertEquals(batch, p.batch);
    }

    static int held(List<LectureRecord> records) {
        int n = 0;
        for (LectureRecord r : records) if (!r.isNotUpdated()) n++;
        return n;
    }

    static int attended(List<LectureRecord> records) {
        int n = 0;
        for (LectureRecord r : records) if (r.isAttended()) n++;
        return n;
    }

    private static int countComponent(List<LectureRecord> records, String component) {
        int n = 0;
        for (LectureRecord r : records) if (r.component.equals(component)) n++;
        return n;
    }
}
