package com.researchassistant.dto;

import java.time.Instant;
import java.util.List;

import com.researchassistant.entity.Paper;

/** A library paper as sent to the frontend. Never includes the (large) full text. */
public record LibraryItemDto(
        Long id,
        String source,
        String externalId,
        String title,
        List<String> authors,
        Integer year,
        String abstractText,
        String url,
        String pdfUrl,
        boolean openAccess,
        String originalFilename,
        String textStatus,
        boolean hasSummary,
        Instant createdAt) {

    public static LibraryItemDto from(Paper p) {
        boolean hasText = p.getFullText() != null && !p.getFullText().isBlank();
        return new LibraryItemDto(p.getId(), p.getSource().name(), p.getExternalId(), p.getTitle(), p.authorList(),
                p.getYear(), p.getAbstractText(), p.getUrl(), p.getPdfUrl(), p.isOpenAccess(),
                p.getOriginalFilename(), hasText ? "FULL_TEXT" : "ABSTRACT_ONLY",
                p.getSummary() != null && !p.getSummary().isBlank(), p.getCreatedAt());
    }
}
