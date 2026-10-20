package io.github.fortunatechick.attendease.parser;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertNotNull;

// Whatever text a PDF yields (damaged files, other reports, random bytes), the parser must
// return a result, never throw. This only checks robustness; what it extracts from valid
// reports is covered by the other parser tests and stays exactly as it is.
public class ParserFuzzTest {

    private static final String REPORT =
            "Student Name TEST STUDENT\n" +
            "Roll No. A045\n" +
            "Academic Year & Academic Session 2027-2028, Semester III\n" +
            "Programme Name B Tech (Information Technology)\n" +
            "Attendance Report Duration : From 05-01-2027 to 30-04-2027\n" +
            "1 Data Structures and AlgorithmsT1 BT IT A Jan 5, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "2 Data Structures and AlgorithmsP1 BT IT A A1 Jan 5, 2027 10:00:01 AM 11:00:00 AM A\n" +
            "3 Engineering Mathematics - IIIT1 B Tech IT A Jan 6, 2027 9:00:01 AM 10:00:00 AM P\n" +
            "4 Python ProgrammingP2 BT IT A2 Jan 7, 2027 2:00:01 PM 3:00:00 PM NU\n" +
            "5 Design ThinkingT1-OE-III Jan 8, 2027 5:00:01 PM 6:00:00 PM OD\n";

    private static final String ALPHABET = "0123456789 :,.-()&APMNUELODT\n\tJanFebMarSemesterRollNo. –\u0000";

    private final AttendanceParser parser = new AttendanceParser();

    @Test
    public void emptyAndBlankInput() {
        assertNotNull(parser.parse(""));
        assertNotNull(parser.parse("\n\n\n"));
        assertNotNull(parser.parse("   \t  "));
    }

    @Test
    public void randomText() {
        Random random = new Random(7);
        for (int round = 0; round < 2000; round++) {
            StringBuilder sb = new StringBuilder();
            int length = random.nextInt(400);
            for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            assertNotNull(parser.parse(sb.toString()));
        }
    }

    @Test
    public void mangledReports() {
        Random random = new Random(11);
        for (int round = 0; round < 2000; round++) {
            StringBuilder sb = new StringBuilder(REPORT);
            int edits = 1 + random.nextInt(12);
            for (int e = 0; e < edits && sb.length() > 0; e++) {
                int at = random.nextInt(sb.length());
                switch (random.nextInt(3)) {
                    case 0:
                        sb.deleteCharAt(at);
                        break;
                    case 1:
                        sb.insert(at, ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                        break;
                    default:
                        sb.setCharAt(at, ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                }
            }
            assertNotNull(parser.parse(sb.toString()));
        }
    }

    @Test
    public void truncatedReports() {
        for (int cut = 0; cut <= REPORT.length(); cut++) {
            assertNotNull(parser.parse(REPORT.substring(0, cut)));
        }
    }

    @Test
    public void veryLongInput() {
        StringBuilder sb = new StringBuilder(REPORT);
        for (int i = 6; i < 3000; i++) {
            sb.append(i).append(" Data Structures and AlgorithmsT1 BT IT A Jan 5, 2027 9:00:01 AM 10:00:00 AM P\n");
        }
        assertNotNull(parser.parse(sb.toString()));
    }
}
