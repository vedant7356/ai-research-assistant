package com.researchassistant.dto;

import java.util.List;

/**
 * A paper as shown in search results. inLibrary is filled in by the search endpoint.
 */
public record PaperDto(
        String externalId,
        String title,
        List<String> authors,
        Integer year,
        String abstractText,
        String url,
        String pdfUrl,
        boolean openAccess,
        boolean inLibrary) {

    public PaperDto withInLibrary(boolean value) {
        return new PaperDto(externalId, title, authors, year, abstractText, url, pdfUrl, openAccess, value);
    }
}
