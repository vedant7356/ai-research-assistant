package com.researchassistant.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.researchassistant.dto.LibraryItemDto;
import com.researchassistant.dto.SavePaperRequest;
import com.researchassistant.dto.UpdatePaperRequest;
import com.researchassistant.service.LibraryService;

/**
 * POST   /api/library        save a search result (201 new, 200 if it was already saved)
 * GET    /api/library        list: ?q=&source=ALL|SAVED|UPLOAD&sort=recent|title|year
 * GET    /api/library/{id}   one paper
 * PATCH  /api/library/{id}   correct title/authors/year/abstract
 * DELETE /api/library/{id}   remove (204)
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @PostMapping
    public ResponseEntity<LibraryItemDto> save(@RequestBody SavePaperRequest paper) {
        LibraryService.SaveResult result = libraryService.save(paper);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @GetMapping
    public Map<String, List<LibraryItemDto>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "recent") String sort) {
        return Map.of("items", libraryService.list(q, source, sort));
    }

    @GetMapping("/{id}")
    public LibraryItemDto get(@PathVariable Long id) {
        return libraryService.get(id);
    }

    @PatchMapping("/{id}")
    public LibraryItemDto update(@PathVariable Long id, @RequestBody UpdatePaperRequest body) {
        return libraryService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        libraryService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
