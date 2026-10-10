package com.researchassistant.dto;

import java.util.List;

/** Result of a PDF upload: the stored library item plus what the extractor could not find. */
public record UploadResponse(LibraryItemDto item, boolean duplicate, List<String> missingFields, int pageCount) {
}
