package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.researchassistant.controller.HealthController;
import com.researchassistant.dto.LibraryItemDto;
import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;

class PaperAndDtoTest {

    @Test
    void authorListRoundTripsThroughTheSingleTextColumn() {
        Paper p = new Paper();
        p.setAuthorList(List.of("Ann Lee", "Bo Chan"));
        assertThat(p.authorList()).containsExactly("Ann Lee", "Bo Chan");
    }

    @Test
    void missingOrBlankAuthorsGiveAnEmptyList() {
        Paper p = new Paper();
        assertThat(p.authorList()).isEmpty();
        p.setAuthorList(null);
        assertThat(p.authorList()).isEmpty();
        p.setAuthorList(List.of());
        assertThat(p.authorList()).isEmpty();
    }

    @Test
    void libraryItemReportsTextStatusAndSummaryFlag() {
        Paper p = new Paper();
        p.setSource(PaperSource.SAVED);
        p.setTitle("T");
        LibraryItemDto abstractOnly = LibraryItemDto.from(p);
        assertThat(abstractOnly.textStatus()).isEqualTo("ABSTRACT_ONLY");
        assertThat(abstractOnly.hasSummary()).isFalse();

        p.setFullText("some real text");
        p.setSummary("A summary");
        LibraryItemDto full = LibraryItemDto.from(p);
        assertThat(full.textStatus()).isEqualTo("FULL_TEXT");
        assertThat(full.hasSummary()).isTrue();

        p.setFullText("   ");
        p.setSummary("   ");
        LibraryItemDto blank = LibraryItemDto.from(p);
        assertThat(blank.textStatus()).isEqualTo("ABSTRACT_ONLY");
        assertThat(blank.hasSummary()).isFalse();
    }

    @Test
    void healthReportsUpWhenTheDatabaseAnswers() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.isValid(2)).thenReturn(true);
        ResponseEntity<Map<String, String>> r = new HealthController(ds).health();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("status", "UP").containsEntry("database", "UP").containsKey("time");
    }

    @Test
    void healthReportsServiceUnavailableWhenTheDatabaseIsDown() throws SQLException {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("down"));
        ResponseEntity<Map<String, String>> r = new HealthController(broken).health();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).containsEntry("status", "UP").containsEntry("database", "DOWN");

        DataSource invalid = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(invalid.getConnection()).thenReturn(c);
        when(c.isValid(2)).thenReturn(false);
        assertThat(new HealthController(invalid).health().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
