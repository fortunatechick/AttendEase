package io.github.fortunatechick.attendease.calc;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CsvExporterTest {

    private static AttendanceStats stats(int present, int absent, int pending) {
        return new AttendanceStats(present, absent, pending, 0, 0, ApprovalConvention.COUNTED_AS_ATTENDED, 80);
    }

    @Test
    public void quotesOnlyWhenNeeded() {
        assertEquals("Operating Systems", CsvExporter.quote("Operating Systems"));
        assertEquals("\"RAG Sys, Agents\"", CsvExporter.quote("RAG Sys, Agents"));
        assertEquals("\"He said \"\"hi\"\"\"", CsvExporter.quote("He said \"hi\""));
        assertEquals("RAG Sys&Agen WorkflowithPine&LaGr", CsvExporter.quote("RAG Sys&Agen WorkflowithPine&LaGr"));
    }

    @Test
    public void buildsHeaderRowsAndSummary() {
        String csv = CsvExporter.build(Arrays.asList(
                new CsvExporter.Row("Artificial Intelligence", "Combined", stats(26, 5, 2),
                        new SemesterBudget(15, 4, stats(26, 5, 2))),
                new CsvExporter.Row("Operating Systems, Lab", "Practical", stats(22, 2, 0))),
                stats(48, 7, 2));
        String[] lines = csv.split("\r\n");
        assertEquals(4, lines.length);
        assertEquals(CsvExporter.HEADER, lines[0]);
        assertEquals("Artificial Intelligence,Combined,26,31,5,2,83.9,84.8,78.8,DEPENDS ON LEAVE,60,12,7", lines[1]);
        assertEquals("\"Operating Systems, Lab\",Practical,22,24,2,0,91.7,91.7,91.7,SAFE,,,", lines[2]);
        assertEquals("Overall,All,48,55,7,2,87.3,87.7,84.2,SAFE,,,", lines[3]);
    }

    @Test
    public void emptyStatsLeavePercentagesBlank() {
        String csv = CsvExporter.build(java.util.Collections.<CsvExporter.Row>emptyList(), stats(0, 0, 0));
        assertEquals("Overall,All,0,0,0,0,,,,NO DATA,,,", csv.split("\r\n")[1]);
    }
}
