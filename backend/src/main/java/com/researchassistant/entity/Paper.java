package com.researchassistant.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One row per library paper (saved from search OR uploaded). Search results themselves are never stored.
 * Authors are kept as one text column, one name per line.
 */
@Entity
@Table(name = "papers")
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaperSource source;

    /** OpenAlex id (e.g. W2626778328). Unique, so saving the same paper twice cannot create two rows. Null for uploads. */
    @Column(unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String authors;

    private Integer year;

    @Column(columnDefinition = "text")
    private String abstractText;

    @Column(columnDefinition = "text")
    private String url;

    @Column(columnDefinition = "text")
    private String pdfUrl;

    private boolean openAccess;

    /** Extracted PDF text (filled by uploads, and later by full-text fetching). */
    @Column(columnDefinition = "text")
    private String fullText;

    private String originalFilename;

    /** Cached AI summary (checkpoint 5). */
    @Column(columnDefinition = "text")
    private String summary;

    /** What the cached summary was written from: FULL_TEXT, FULL_TEXT_TRUNCATED or ABSTRACT_ONLY. */
    @Column(length = 32)
    private String summaryBasis;

    /** SHA-256 of the uploaded PDF bytes; stops the same file being stored twice. Null for saved search results. */
    @Column(unique = true, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public List<String> authorList() {
        if (authors == null || authors.isBlank()) return List.of();
        return Arrays.asList(authors.split("\n"));
    }

    public void setAuthorList(List<String> list) {
        this.authors = list == null ? null : String.join("\n", list);
    }

    public Long getId() { return id; }
    public PaperSource getSource() { return source; }
    public void setSource(PaperSource source) { this.source = source; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public boolean isOpenAccess() { return openAccess; }
    public void setOpenAccess(boolean openAccess) { this.openAccess = openAccess; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSummaryBasis() { return summaryBasis; }
    public void setSummaryBasis(String summaryBasis) { this.summaryBasis = summaryBasis; }
    public Instant getCreatedAt() { return createdAt; }
}
