# Seeded-fault results

One deliberate bug at a time in a throw-away copy of the backend; *caught* = a test that passed before now fails.

| id | file | seeded fault | initial suite | final suite |
|---|---|---|---|---|
| M01 | `QaService.java` | question length limit off by one | caught | caught |
| M02 | `QaService.java` | whole-paper threshold off by one | missed | caught |
| M03 | `QaService.java` | shows the uncited excerpts instead of the cited ones | caught | caught |
| M04 | `QaService.java` | source excerpt cut at the wrong length | missed | caught |
| M05 | `LibraryService.java` | library search ignores authors | missed | caught |
| M06 | `LibraryService.java` | year sort oldest first instead of newest first | caught | caught |
| M07 | `LibraryService.java` | saving a duplicate reported as newly created | caught | caught |
| M08 | `LibraryService.java` | blank authors kept on edit | caught | caught |
| M09 | `UploadService.java` | scanned PDFs with a little text accepted | missed | caught |
| M10 | `UploadService.java` | empty-PDF limit off by one | missed | caught |
| M11 | `SummaryService.java` | summary truncation limit off by one | caught | caught |
| M12 | `SummaryService.java` | summary cache used only when refreshing | caught | caught |
| M13 | `PaperSearchService.java` | page size cap too high | caught | caught |
| M14 | `PaperSearchService.java` | page 0 allowed | caught | caught |
| M15 | `PdfMetadataExtractor.java` | year picks the latest year on the page, not the earliest | missed | caught |
| M16 | `PdfMetadataExtractor.java` | implausible PDF-info year accepted | missed | caught |
| M17 | `PdfMetadataExtractor.java` | title length cap removed | missed | caught |
| M18 | `GlobalExceptionHandler.java` | oversize upload reported as 400 | missed | caught |
| M19 | `PaperContentService.java` | PDF download size cap far too small | caught | caught |
| M20 | `TextChunker.java` | excerpt maximum doubled | missed | caught |
| M21 | `TextChunker.java` | excerpt target doubled | missed | caught |

**Initial suite:** 10 of 21 caught. **Final suite:** 21 of 21 caught.
