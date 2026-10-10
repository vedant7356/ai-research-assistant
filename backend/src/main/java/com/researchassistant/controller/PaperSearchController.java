package com.researchassistant.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.researchassistant.dto.PaperDto;
import com.researchassistant.dto.SearchResponse;
import com.researchassistant.service.LibraryService;
import com.researchassistant.service.PaperSearchService;

/** GET /api/papers/search?q=...&yearFrom=2020&sort=relevance|newest&openAccessOnly=true&page=1&limit=10 */
@RestController
@RequestMapping("/api/papers")
public class PaperSearchController {

    private final PaperSearchService searchService;
    private final LibraryService libraryService;

    public PaperSearchController(PaperSearchService searchService, LibraryService libraryService) {
        this.searchService = searchService;
        this.libraryService = libraryService;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "false") boolean openAccessOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        SearchResponse r = searchService.search(q, yearFrom, "newest".equalsIgnoreCase(sort), openAccessOnly, page, limit);
        // Mark results that are already in the library (one query for the whole page).
        Set<String> saved = libraryService.savedExternalIds(r.items().stream().map(PaperDto::externalId).toList());
        List<PaperDto> marked = r.items().stream().map(p -> p.withInLibrary(saved.contains(p.externalId()))).toList();
        return new SearchResponse(r.query(), r.total(), r.page(), r.limit(), marked);
    }
}
