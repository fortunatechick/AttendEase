package io.github.fortunatechick.attendease.data;

import android.content.ContentResolver;
import android.net.Uri;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Pulls the text out of a picked PDF. Every failure becomes an ImportException with a Reason
// the screen turns into a message saying what went wrong and what to do about it.
//
// Built for low-end phones: the file is copied to a private temp file first (refusing anything
// over MAX_BYTES), and PdfBox reads it from there with a small in-memory buffer, spilling to
// temp files instead of holding the whole document in RAM. The SAP report is a few pages and
// well under 1 MB, so the limits only ever stop the wrong file.
public class PdfTextExtractor {

    public static final long MAX_BYTES = 25L * 1024 * 1024;
    public static final int MAX_PAGES = 300;
    private static final long MAIN_MEMORY_BYTES = 4L * 1024 * 1024;

    public enum Reason { PASSWORD, NOT_FOUND, NOT_PDF, TOO_LARGE, TOO_MANY_PAGES, OUT_OF_MEMORY, NO_TEXT, NO_ROWS }

    public static class ImportException extends Exception {
        public final Reason reason;

        public ImportException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    public static String extract(ContentResolver resolver, Uri uri, File cacheDir) throws ImportException {
        File copy = null;
        try {
            copy = File.createTempFile("upload", ".pdf", cacheDir);
            copyCapped(resolver, uri, copy);
            try (PDDocument doc = PDDocument.load(copy, MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES)
                    .setTempDir(cacheDir))) {
                if (doc.getNumberOfPages() > MAX_PAGES) throw new ImportException(Reason.TOO_MANY_PAGES);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(doc);
            }
        } catch (InvalidPasswordException e) {
            throw new ImportException(Reason.PASSWORD);
        } catch (FileNotFoundException | SecurityException e) {
            throw new ImportException(Reason.NOT_FOUND);
        } catch (IOException | RuntimeException e) {
            // PdfBox reports damaged or non-PDF files as IOExceptions, and occasionally as
            // runtime exceptions from deep inside the parser.
            throw new ImportException(Reason.NOT_PDF);
        } catch (OutOfMemoryError e) {
            throw new ImportException(Reason.OUT_OF_MEMORY);
        } finally {
            if (copy != null && !copy.delete()) copy.deleteOnExit();
        }
    }

    private static void copyCapped(ContentResolver resolver, Uri uri, File to) throws IOException, ImportException {
        try (InputStream in = resolver.openInputStream(uri); OutputStream out = new FileOutputStream(to)) {
            if (in == null) throw new FileNotFoundException();
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new ImportException(Reason.TOO_LARGE);
                out.write(buffer, 0, n);
            }
        }
    }
}
