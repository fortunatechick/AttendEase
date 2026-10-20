package io.github.fortunatechick.attendease;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import io.github.fortunatechick.attendease.data.PdfTextExtractor;
import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.ParseResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Runs the real PdfBox extraction and the parser on a device, against a fixture PDF made from
// placeholder data. Run with -PtestBuildType=release to check the minified (R8) build: if a
// keep rule were missing, PdfBox would fail here to find its fonts, glyph lists or classes.
@RunWith(AndroidJUnit4.class)
public class PdfImportInstrumentedTest {

    @Test
    public void extractsAndParsesTheFixtureReport() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        PDFBoxResourceLoader.init(app);

        File pdf = new File(app.getCacheDir(), "fixture_report.pdf");
        try (InputStream in = test.getAssets().open("fixture_report.pdf");
             OutputStream out = new FileOutputStream(pdf)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }

        String text = PdfTextExtractor.extract(app.getContentResolver(), Uri.fromFile(pdf));
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
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PDFBoxResourceLoader.init(app);
        File junk = new File(app.getCacheDir(), "not_a.pdf");
        try (OutputStream out = new FileOutputStream(junk)) {
            out.write("this is not a PDF".getBytes("UTF-8"));
        }
        try {
            PdfTextExtractor.extract(app.getContentResolver(), Uri.fromFile(junk));
            throw new AssertionError("expected an ImportException");
        } catch (PdfTextExtractor.ImportException expected) {
            assertTrue(expected.getMessage().contains("isn't a readable PDF"));
        }
    }
}
