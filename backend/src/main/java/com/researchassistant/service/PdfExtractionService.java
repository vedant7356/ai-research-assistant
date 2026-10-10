package com.researchassistant.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.researchassistant.exception.ApiException;

/** Reads a PDF with Apache PDFBox: full text, page count, document info, and first-page lines with font sizes. */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /** One visual line of the first page, with its average font size (used to find the title). */
    /** Separator inserted where a line has a wide horizontal gap. */
    public static final String GAP = " | ";

    /** Drops standalone 1-3 digit segments (review-copy line numbers) when a line has several segments. */
    static String dropMarginNumbers(String line) {
        if (!line.contains(GAP)) return line;
        List<String> keep = new ArrayList<>();
        for (String seg : line.split(java.util.regex.Pattern.quote(GAP))) {
            String t = seg.trim();
            if (!t.isEmpty() && !t.matches("\\d{1,3}")) keep.add(t);
        }
        return String.join(GAP, keep);
    }

    public record TextLine(String text, float fontSize) { }

    public record ExtractedPdf(String fullText, int pageCount, String infoTitle, String infoAuthor,
                               Integer infoYear, List<TextLine> firstPageLines) { }

    public ExtractedPdf extract(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            // PostgreSQL text columns cannot hold NUL characters, and PDFs sometimes contain them.
            text = text.replace("\u0000", "").replace("\r\n", "\n");

            LineCollector collector = new LineCollector();
            collector.setStartPage(1);
            collector.setEndPage(1);
            collector.getText(doc);
            collector.finishLine();

            PDDocumentInformation info = doc.getDocumentInformation();
            Integer infoYear = null;
            if (info != null && info.getCreationDate() != null) {
                infoYear = info.getCreationDate().get(Calendar.YEAR);
            }
            return new ExtractedPdf(text, doc.getNumberOfPages(),
                    info == null ? null : info.getTitle(), info == null ? null : info.getAuthor(),
                    infoYear, collector.lines);
        } catch (InvalidPasswordException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_PROTECTED",
                    "This PDF is password-protected. Remove the password and upload it again.", false);
        } catch (IOException e) {
            log.warn("PDF could not be read: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF",
                    "This file could not be read as a PDF. It may be damaged.", false);
        } catch (RuntimeException e) {
            log.warn("PDF extraction failed", e);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_EXTRACTION_FAILED",
                    "Text could not be extracted from this PDF.", false);
        }
    }

    /** PDFBox reports text word by word; this glues words back into lines and records the font size of each line. */
    private static class LineCollector extends PDFTextStripper {
        final List<TextLine> lines = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();
        private float lastEnd = -1;
        private float sizeSum;
        private int glyphs;

        LineCollector() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (text == null || text.isBlank()) return;
            if (current.length() > 0) {
                // A wide horizontal gap (columns, side-by-side author blocks, margin line numbers) becomes a separator.
                boolean wideGap = false;
                if (!positions.isEmpty() && lastEnd >= 0) {
                    TextPosition first = positions.get(0);
                    wideGap = first.getXDirAdj() - lastEnd > 1.5f * Math.max(first.getFontSizeInPt(), 6f);
                }
                current.append(wideGap ? GAP : " ");
            }
            current.append(text.trim());
            for (TextPosition tp : positions) {
                sizeSum += tp.getFontSizeInPt();
                glyphs++;
            }
            if (!positions.isEmpty()) {
                TextPosition last = positions.get(positions.size() - 1);
                lastEnd = last.getXDirAdj() + last.getWidthDirAdj();
            }
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            finishLine();
            super.writeLineSeparator();
        }

        void finishLine() {
            String t = dropMarginNumbers(current.toString().replace("\u0000", "").trim());
            lastEnd = -1;
            // Margin line numbers (review copies) can arrive as their own lines, ahead of the real text.
            if (!t.isEmpty() && !t.matches("[\\d\\s|]+")) lines.add(new TextLine(t, glyphs == 0 ? 0f : sizeSum / glyphs));
            current.setLength(0);
            sizeSum = 0;
            glyphs = 0;
        }
    }
}
