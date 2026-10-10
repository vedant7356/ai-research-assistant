package com.researchassistant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.researchassistant.dto.SummaryDto;
import com.researchassistant.service.SummaryService;

/**
 * GET  /api/library/{id}/summary            cached summary (summary is null if none yet)
 * POST /api/library/{id}/summary?refresh=   generate (or return cached unless refresh=true)
 */
@RestController
@RequestMapping("/api/library/{id}/summary")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    public SummaryDto get(@PathVariable Long id) {
        return summaryService.get(id);
    }

    @PostMapping
    public SummaryDto generate(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh) {
        return summaryService.summarize(id, refresh);
    }
}
