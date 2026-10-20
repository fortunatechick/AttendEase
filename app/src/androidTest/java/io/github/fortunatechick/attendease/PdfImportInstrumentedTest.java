package io.github.fortunatechick.attendease;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import io.github.fortunatechick.attendease.data.PdfTextExtractor;
import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.ParseResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Runs the real PdfBox extraction and the parser on a device, against a fixture PDF made from
// placeholder data. Run with -PtestBuildType=release to check the minified (R8) build: if a
// keep rule were missing, PdfBox would fail here to find its fonts, glyph lists or classes.
// The damaged-file cases make sure a bad upload always ends in a clear error, never a crash.
@RunWith(AndroidJUnit4.class)
public class PdfImportInstrumentedTest {

    private Context app;
    private byte[] fixture;

    @Before
    public void setUp() throws Exception {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        PDFBoxResourceLoader.init(app);
        try (InputStream in = test.getAssets().open("fixture_report.pdf")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            fixture = bytes.toByteArray();
        }
    }

    private Uri file(String name, byte[] content) throws Exception {
        File f = new File(app.getCacheDir(), name);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content);
        }
        return Uri.fromFile(f);
    }

    private PdfTextExtractor.Reason failureFor(Uri uri) {
        try {
            PdfTextExtractor.extract(app.getContentResolver(), uri, app.getCacheDir());
            return null;
        } catch (PdfTextExtractor.ImportException e) {
            return e.reason;
        }
    }

    @Test
    public void extractsAndParsesTheFixtureReport() throws Exception {
        String text = PdfTextExtractor.extract(app.getContentResolver(), file("fixture_report.pdf", fixture),
                app.getCacheDir());
        ParseResult result = new AttendanceParser().parse(text);

        assertEquals(12, result.records.size());
        assertTrue(result.missingSrNos.isEmpty());
        assertEquals("2027-2028, Semester III", result.profile.academicSession);
        assertEquals(8, result.subjectNames().size());
        assertEquals(Collections.singletonMap("OD", 1), result.unknownStatuses);
        assertEquals("Cloud Computing and Virtualization",
                result.prefixMerges.get("Cloud Computing and Virtualizat"));
    }

    @Test
    public void garbageBytesGiveAClearError() throws Exception {
        assertEquals(PdfTextExtractor.Reason.NOT_PDF, failureFor(file("not_a.pdf", "this is not a PDF".getBytes("UTF-8"))));
        assertEquals(PdfTextExtractor.Reason.NOT_PDF, failureFor(file("empty.pdf", new byte[0])));
    }

    @Test
    public void missingFileGivesAClearError() {
        assertEquals(PdfTextExtractor.Reason.NOT_FOUND,
                failureFor(Uri.fromFile(new File(app.getCacheDir(), "does_not_exist.pdf"))));
    }

    @Test
    public void oversizedFileIsRefusedBeforeParsing() throws Exception {
        byte[] big = new byte[(int) PdfTextExtractor.MAX_BYTES + 1];
        System.arraycopy(fixture, 0, big, 0, fixture.length);
        assertEquals(PdfTextExtractor.Reason.TOO_LARGE, failureFor(file("big.pdf", big)));
    }

    // Truncated and randomly corrupted copies of the real report: each must either still
    // extract or fail with a reason. Any other exception fails the test.
    @Test
    public void damagedCopiesNeverCrash() throws Exception {
        Random random = new Random(42);
        for (int cut = 16; cut < fixture.length; cut += fixture.length / 12) {
            failureFor(file("cut.pdf", Arrays.copyOf(fixture, cut)));
        }
        for (int round = 0; round < 40; round++) {
            byte[] damaged = fixture.clone();
            for (int i = 0; i < 20; i++) damaged[random.nextInt(damaged.length)] = (byte) random.nextInt(256);
            failureFor(file("damaged.pdf", damaged));
        }
    }
}
