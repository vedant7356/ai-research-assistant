package com.researchassistant.dto;

import java.util.List;

/** basis: FULL_TEXT or ABSTRACT_ONLY (what the answer could draw on). sources: the excerpts the answer cites. */
public record AnswerDto(String answer, String basis, List<SourceDto> sources) {

    public record SourceDto(int n, String excerpt) { }
}
