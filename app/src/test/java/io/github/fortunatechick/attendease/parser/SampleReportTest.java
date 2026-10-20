package io.github.fortunatechick.attendease.parser;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.fortunatechick.attendease.parser.AttendanceParserTest.attended;
import static io.github.fortunatechick.attendease.parser.AttendanceParserTest.held;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Runs the parser over the full text PdfBox-Android extracted on the emulator from the
// real report (13.07.2026 to 11.10.2026, 16 pages). These numbers are the ground truth.
public class SampleReportTest {

    private static ParseResult result;

    @BeforeClass
    public static void parseSample() throws Exception {
        try (InputStream in = SampleReportTest.class.getClassLoader()
                .getResourceAsStream("sap_report_2026-10-11.txt")) {
            Assume.assumeNotNull(in);   // sample report is private and git-ignored; skip without it
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            result = new AttendanceParser().parse(out.toString(StandardCharsets.UTF_8.name()));
        }
    }

    @Test
    public void allRowsParsedInUnbrokenSequence() {
        assertEquals(327, result.records.size());
        assertTrue(result.missingSrNos.isEmpty());
        assertTrue(result.duplicateSrNos.isEmpty());
        assertTrue(result.unmatchedLines.isEmpty());
        assertEquals(327, result.records.get(326).srNo);
    }

    @Test
    public void statusCounts() {
        assertEquals(268, result.countByStatus("P"));
        assertEquals(59, result.countByStatus("A"));
        assertEquals(0, result.countByStatus("E"));
        assertEquals(0, result.countByStatus("L"));
        assertEquals(0, result.countByStatus("NU"));
    }

    @Test
    public void eightSubjectsWithOneMerge() {
        assertEquals(8, result.subjectNames().size());
        assertEquals(1, result.prefixMerges.size());
        assertEquals("Mobile Application Development", result.prefixMerges.get("Mobile Application Develop"));
    }

    @Test
    public void overallAttendance() {
        assertEquals(327, held(result.records));
        assertEquals(268, attended(result.records));
    }

    @Test
    public void perSubjectAttendance() {
        assertSubject("Operating Systems", 44, 50, 22, 26, 22, 24);
        assertSubject("Artificial Intelligence", 37, 50, 19, 26, 18, 24);
        assertSubject("Research Methodology", 27, 35, 27, 35, 0, 0);
        assertSubject("RAG Sys&Agen WorkflowithPine&LaGr", 15, 15, 15, 15, 0, 0);
        assertSubject("Software Engineering", 35, 45, 19, 25, 16, 20);
        assertSubject("Image and Video Processing", 45, 54, 19, 26, 26, 28);
        assertSubject("Mobile Application Development", 38, 46, 20, 24, 18, 22);
        assertSubject("Financial Der", 27, 32, 27, 32, 0, 0);
    }

    @Test
    public void headerProfile() {
        assertTrue(result.profile.name != null && !result.profile.name.isEmpty());
        assertTrue(result.profile.rollNo.matches("[A-Z]\\d+"));
        assertEquals("13.07.2026", result.profile.durationFrom);
        assertEquals("11.10.2026", result.profile.durationTo);
    }

    private static void assertSubject(String name, int attended, int held,
                                      int theoryAttended, int theoryHeld,
                                      int labAttended, int labHeld) {
        List<LectureRecord> all = result.recordsFor(name);
        assertEquals(name + " attended", attended, attended(all));
        assertEquals(name + " held", held, held(all));
        assertEquals(name + " theory attended", theoryAttended, attended(component(all, "T")));
        assertEquals(name + " theory held", theoryHeld, held(component(all, "T")));
        assertEquals(name + " lab attended", labAttended, attended(component(all, "P")));
        assertEquals(name + " lab held", labHeld, held(component(all, "P")));
    }

    private static List<LectureRecord> component(List<LectureRecord> records, String component) {
        List<LectureRecord> list = new java.util.ArrayList<>();
        for (LectureRecord r : records) if (r.component.equals(component)) list.add(r);
        return list;
    }
}
