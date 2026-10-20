package io.github.fortunatechick.attendease.data;

import android.content.ContentResolver;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

// Pulls the text out of a picked PDF. Every failure becomes an ImportException whose
// message says what went wrong and what the student should do about it.
public class PdfTextExtractor {

    public static class ImportException extends Exception {
        public ImportException(String message) {
            super(message);
        }
    }

    public static String extract(ContentResolver resolver, Uri uri) throws ImportException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new FileNotFoundException();
            try (PDDocument doc = PDDocument.load(in)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(doc);
            }
        } catch (InvalidPasswordException e) {
            throw new ImportException("This PDF is password-protected, so its text can't be read. "
                    + "Download the attendance report again from the SAP portal and upload that copy.");
        } catch (FileNotFoundException | SecurityException e) {
            throw new ImportException("The file couldn't be opened. It may have been moved or deleted. "
                    + "Pick it again.");
        } catch (IOException e) {
            throw new ImportException("This file isn't a readable PDF. Make sure you picked the "
                    + "attendance report downloaded from the SAP portal, not a screenshot or another file.");
        } catch (OutOfMemoryError e) {
            throw new ImportException("This PDF is too large to read on this device. "
                    + "The SAP attendance report is normally well under 1 MB; check you picked the right file.");
        }
    }
}
