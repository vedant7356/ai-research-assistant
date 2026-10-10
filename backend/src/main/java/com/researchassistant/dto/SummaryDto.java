package com.researchassistant.dto;

/** basis: FULL_TEXT, FULL_TEXT_TRUNCATED or ABSTRACT_ONLY (what the summary was written from). summary is null if none yet. */
public record SummaryDto(Long paperId, String summary, String basis, boolean cached) { }
