package com.researchassistant.dto;

import java.util.List;

public record SearchResponse(String query, long total, int page, int limit, List<PaperDto> items) {
}
