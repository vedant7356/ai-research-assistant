package com.researchassistant.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.researchassistant.dto.LibraryItemDto;
import com.researchassistant.dto.SavePaperRequest;
import com.researchassistant.dto.UpdatePaperRequest;
import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;
import com.researchassistant.exception.ApiException;
import com.researchassistant.repository.PaperRepository;

/** Saving, listing and removing library papers. */
@Service
public class LibraryService {

    /** Result of a save: the item, and whether a new row was created (false = it was already saved). */
    public record SaveResult(LibraryItemDto item, boolean created) { }

    private final PaperRepository repository;

    public LibraryService(PaperRepository repository) {
        this.repository = repository;
    }

    public SaveResult save(SavePaperRequest in) {
        if (in == null || in.title() == null || in.title().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "A paper needs a title to be saved.", false);
        }
        String externalId = blankToNull(in.externalId());
        if (externalId != null) {
            var existing = repository.findByExternalId(externalId);
            if (existing.isPresent()) return new SaveResult(LibraryItemDto.from(existing.get()), false);
        }

        Paper p = new Paper();
        p.setSource(PaperSource.SAVED);
        p.setExternalId(externalId);
        p.setTitle(in.title().trim());
        p.setAuthorList(in.authors());
        p.setYear(in.year());
        p.setAbstractText(blankToNull(in.abstractText()));
        p.setUrl(blankToNull(in.url()));
        p.setPdfUrl(blankToNull(in.pdfUrl()));
        p.setOpenAccess(Boolean.TRUE.equals(in.openAccess()));
        try {
            return new SaveResult(LibraryItemDto.from(repository.saveAndFlush(p)), true);
        } catch (DataIntegrityViolationException e) {
            // Two clicks raced and the unique constraint on external_id stopped the second one: return the first.
            return repository.findByExternalId(externalId)
                    .map(x -> new SaveResult(LibraryItemDto.from(x), false))
                    .orElseThrow(() -> e);
        }
    }

    public List<LibraryItemDto> list(String q, String source, String sort) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        PaperSource wanted = "SAVED".equalsIgnoreCase(source) ? PaperSource.SAVED
                : "UPLOAD".equalsIgnoreCase(source) ? PaperSource.UPLOAD : null;

        Comparator<Paper> order;
        if ("title".equalsIgnoreCase(sort)) {
            order = Comparator.comparing(p -> p.getTitle().toLowerCase(Locale.ROOT));
        } else if ("year".equalsIgnoreCase(sort)) {
            order = Comparator.comparing(Paper::getYear, Comparator.nullsLast(Comparator.<Integer>reverseOrder()));
        } else {
            order = Comparator.comparing(Paper::getCreatedAt).reversed();
        }

        return repository.findAll().stream()
                .filter(p -> wanted == null || p.getSource() == wanted)
                .filter(p -> needle.isEmpty()
                        || p.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                        || String.join(" ", p.authorList()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(order)
                .map(LibraryItemDto::from)
                .collect(Collectors.toList());
    }

    /** Correct a paper's metadata (for example after imperfect PDF extraction). */
    public LibraryItemDto update(Long id, UpdatePaperRequest in) {
        Paper p = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_IN_LIBRARY", "That paper is not in your library.", false));
        if (in == null || in.title() == null || in.title().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The title cannot be empty.", false);
        }
        if (in.year() != null && (in.year() < 1900 || in.year() > 2100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The year must be between 1900 and 2100.", false);
        }
        p.setTitle(in.title().trim());
        p.setAuthorList(in.authors() == null ? List.of()
                : in.authors().stream().map(String::trim).filter(a -> !a.isEmpty()).toList());
        p.setYear(in.year());
        p.setAbstractText(blankToNull(in.abstractText()));
        return LibraryItemDto.from(repository.save(p));
    }

    public void remove(Long id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_IN_LIBRARY", "That paper is not in your library.", false);
        }
        repository.deleteById(id);
    }

    /** Which of these OpenAlex ids are already saved? Used to mark search results. */
    public Set<String> savedExternalIds(Collection<String> externalIds) {
        List<String> ids = externalIds.stream().filter(x -> x != null && !x.isBlank()).toList();
        if (ids.isEmpty()) return Set.of();
        return repository.findByExternalIdIn(ids).stream().map(Paper::getExternalId).collect(Collectors.toSet());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
