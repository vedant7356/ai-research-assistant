package com.researchassistant.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;
import com.researchassistant.repository.PaperRepository;
import com.researchassistant.service.LlmClient;
import com.researchassistant.service.PaperSearchService;

/**
 * Full Spring context on the in-memory H2 test database, driven through MockMvc.
 * The two external services (LLM and OpenAlex) are always mocked. Every test is rolled back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class ApiTestBase {

    @Autowired
    WebApplicationContext context;

    @Autowired
    PaperRepository repository;

    @MockitoBean
    LlmClient llm;

    @MockitoBean
    PaperSearchService searchService;

    MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    Paper savedPaper(String title, PaperSource source, Integer year) {
        Paper p = new Paper();
        p.setSource(source);
        p.setTitle(title);
        p.setYear(year);
        return repository.saveAndFlush(p);
    }

    Paper savedPaper(String title, String abstractText, String fullText) {
        Paper p = new Paper();
        p.setSource(PaperSource.SAVED);
        p.setTitle(title);
        p.setAbstractText(abstractText);
        p.setFullText(fullText);
        return repository.saveAndFlush(p);
    }
}
