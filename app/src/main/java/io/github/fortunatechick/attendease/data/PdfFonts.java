package io.github.fortunatechick.attendease.data;

import android.os.Build;

import com.tom_roush.fontbox.FontBoxFont;
import com.tom_roush.fontbox.ttf.TTFParser;
import com.tom_roush.fontbox.ttf.TrueTypeFont;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.font.CIDFontMapping;
import com.tom_roush.pdfbox.pdmodel.font.FontMapper;
import com.tom_roush.pdfbox.pdmodel.font.FontMapping;
import com.tom_roush.pdfbox.pdmodel.font.FontMappers;
import com.tom_roush.pdfbox.pdmodel.font.PDCIDSystemInfo;
import com.tom_roush.pdfbox.pdmodel.font.PDFontDescriptor;

import java.io.IOException;
import java.io.InputStream;

// Android 5.0-7.1 work-around for PdfBox-Android.
//
// When a PDF uses a font it doesn't embed, PdfBox looks for a substitute among the phone's
// system fonts. Its font-file reader subclasses RandomAccessFile, and on these older Android
// versions that recurses forever (StackOverflowError), so every upload failed. Here the
// substitute is always the Liberation Sans font that ships inside PdfBox-Android, read from a
// stream, and system fonts are never scanned. Text extraction only needs a substitute for
// glyph metrics; the characters come from the PDF itself. Android 8.0+ keeps PdfBox's own
// font mapping unchanged.
public final class PdfFonts {

    private static final String BUNDLED_FONT = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private PdfFonts() {
    }

    public static void install() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;
        FontMappers.set(bundledFontMapper());
    }

    // Also used by a device test to check both mappings extract the same text.
    public static FontMapper bundledFontMapper() {
        return new BundledFontMapper();
    }

    private static final class BundledFontMapper implements FontMapper {

        private TrueTypeFont font;

        private synchronized TrueTypeFont font() {
            if (font == null) {
                try (InputStream in = PDFBoxResourceLoader.getStream(BUNDLED_FONT)) {
                    font = new TTFParser().parse(in);
                } catch (IOException e) {
                    throw new IllegalStateException("PdfBox-Android's bundled font is missing", e);
                }
            }
            return font;
        }

        @Override
        public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor descriptor) {
            return new FontMapping<>(font(), true);
        }

        @Override
        public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor descriptor) {
            return new FontMapping<FontBoxFont>(font(), true);
        }

        @Override
        public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor descriptor, PDCIDSystemInfo cidSystemInfo) {
            return new CIDFontMapping(null, font(), true);
        }
    }
}
