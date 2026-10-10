package com.researchassistant.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.researchassistant.dto.AnswerDto;
import com.researchassistant.dto.AskRequest;
import com.researchassistant.service.QaService;

/** POST /api/library/{id}/ask  body {"question": "..."}  ->  {answer, basis, sources[]} */
@RestController
@RequestMapping("/api/library/{id}/ask")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping
    public AnswerDto ask(@PathVariable Long id, @RequestBody AskRequest body) {
        return qaService.ask(id, body == null ? null : body.question());
    }
}
