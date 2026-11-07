package com.researchassistant.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small real PDFs in memory (ASCII text only) for upload and extraction tests. */
public final class TestPdfs {

    public record Line(String text, float size) { }

    public static final String TITLE = "A Study of Mobile Security Practices";

    private TestPdfs() { }

    public static Line line(String text, float size) {
        return new Line(text, size);
    }

    /** One page, lines written top to bottom. */
    public static byte[] pdf(Line... lines) {
        try (PDDocument doc = new PDDocument()) {
            writePage(doc, List.of(lines));
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A page with no text at all (what a scanned PDF looks like to a text extractor). */
    public static byte[] blank() {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] encrypted(Line... lines) {
        try (PDDocument doc = new PDDocument()) {
            writePage(doc, List.of(lines));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** First page empty, real text on page two (for example a cover page that is an image). */
    public static byte[] textOnSecondPageOnly() {
        List<Line> second = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            second.add(line("Body line " + i + " of a document whose first page has no text at all, only an image.", 10));
        }
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            writePage(doc, second);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A conventional two-page paper: big title, authors, affiliation, year, abstract, introduction, filler text. */
    public static byte[] typicalPaper() {
        List<Line> first = new ArrayList<>();
        first.add(line(TITLE, 22));
        first.add(line("Jane Doe, John Smith", 12));
        first.add(line("Purdue University, USA", 10));
        first.add(line("Published 2022", 10));
        first.add(line("Abstract", 12));
        first.add(line("Mobile applications handle sensitive user data, yet many developers skip basic security", 10));
        first.add(line("checks. We survey 120 open source Android applications and measure how often they use", 10));
        first.add(line("insecure storage, weak encryption and unsafe network settings. Our results show that", 10));
        first.add(line("forty percent of the applications contain at least one high severity issue, and we", 10));
        first.add(line("discuss practical ways to reduce these risks during development.", 10));
        first.add(line("1 Introduction", 14));
        for (int i = 0; i < 6; i++) {
            first.add(line("Smartphones are used for banking, health and communication every single day of the week.", 10));
        }
        List<Line> second = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            second.add(line("Section two line " + i + " describes the survey method, the tools and the results in detail.", 10));
        }
        try (PDDocument doc = new PDDocument()) {
            writePage(doc, first);
            writePage(doc, second);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One page with a single baseline holding two text runs far apart (like two author columns or a margin line number). */
    public static byte[] twoRunsOnOneLine(String left, String right, float size) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(72, 700);
                cs.showText(left);
                cs.endText();
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(400, 700);
                cs.showText(right);
                cs.endText();
            }
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writePage(PDDocument doc, List<Line> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = 730;
            for (Line l : lines) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), l.size());
                cs.newLineAtOffset(72, y);
                cs.showText(l.text());
                cs.endText();
                y -= l.size() + 8;
            }
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
