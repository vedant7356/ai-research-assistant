package com.researchassistant.dto;

import java.util.List;

/**
 * Body of POST /api/library. Only fields a saved paper needs; every field except title is optional,
 * and openAccess is a Boolean so a missing value is accepted instead of rejected.
 */
public record SavePaperRequest(
        String externalId,
        String title,
        List<String> authors,
        Integer year,
        String abstractText,
        String url,
        String pdfUrl,
        Boolean openAccess) {
}
