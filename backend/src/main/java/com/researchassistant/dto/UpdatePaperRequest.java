package com.researchassistant.dto;

import java.util.List;

/** Body of PATCH /api/library/{id}: corrected metadata (used to fix wrong PDF extraction). */
public record UpdatePaperRequest(String title, List<String> authors, Integer year, String abstractText) {
}
