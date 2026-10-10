package com.researchassistant.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.researchassistant.dto.LibraryItemDto;
import com.researchassistant.dto.UploadResponse;
import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;
import com.researchassistant.exception.ApiException;
import com.researchassistant.repository.PaperRepository;
import com.researchassistant.service.PdfExtractionService.ExtractedPdf;

/** Validates an uploaded PDF, extracts text + metadata, and stores it in the library. */
@Service
public class UploadService {

    /** Below this many characters we treat the PDF as having no text (for example a scan). */
    private static final int MIN_TEXT_CHARS = 100;

    public record UploadResult(UploadResponse response, boolean created) { }

    private final PdfExtractionService extraction;
    private final PaperRepository repository;

    public UploadService(PdfExtractionService extraction, PaperRepository repository) {
        this.extraction = extraction;
        this.repository = repository;
    }

    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The file is empty.", false);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "The file could not be read. Try uploading it again.", true);
        }
        // Real PDFs start with "%PDF-" near the beginning. This rejects renamed text files and images.
        String head = new String(bytes, 0, Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1);
        if (!head.contains("%PDF-")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "That file is not a PDF.", false);
        }

        String hash = sha256(bytes);
        var existing = repository.findByContentHash(hash);
        if (existing.isPresent()) {
            return new UploadResult(new UploadResponse(LibraryItemDto.from(existing.get()), true, List.of(), 0), false);
        }

        ExtractedPdf pdf = extraction.extract(bytes);
        if (pdf.fullText() == null || pdf.fullText().trim().length() < MIN_TEXT_CHARS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_PDF",
                    "No readable text was found in this PDF. Scanned documents need OCR, which this app does not do.", false);
        }
        PdfMetadataExtractor.Metadata meta = PdfMetadataExtractor.extract(pdf);

        String filename = cleanFilename(file.getOriginalFilename());
        Paper p = new Paper();
        p.setSource(PaperSource.UPLOAD);
        p.setTitle(truncate(meta.title() != null ? meta.title() : stripExtension(filename), 1000));
        p.setAuthorList(meta.authors());
        p.setYear(meta.year());
        p.setAbstractText(meta.abstractText());
        p.setFullText(pdf.fullText());
        p.setOriginalFilename(filename);
        p.setContentHash(hash);
        try {
            Paper saved = repository.saveAndFlush(p);
            return new UploadResult(new UploadResponse(LibraryItemDto.from(saved), false, meta.missing(), pdf.pageCount()), true);
        } catch (DataIntegrityViolationException e) {
            // Same file uploaded twice at the same moment: return the row the first request created.
            return repository.findByContentHash(hash)
                    .map(x -> new UploadResult(new UploadResponse(LibraryItemDto.from(x), true, List.of(), 0), false))
                    .orElseThrow(() -> e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 is guaranteed to exist on every JVM
        }
    }

    private static String cleanFilename(String name) {
        if (name == null || name.isBlank()) return "upload.pdf";
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        return truncate(n.isEmpty() ? "upload.pdf" : n, 255);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
