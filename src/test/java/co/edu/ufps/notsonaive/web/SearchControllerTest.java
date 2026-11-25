package co.edu.ufps.notsonaive.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile txt(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void homePageShowsAcademicHeader() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FRANCISCO DE PAULA SANTANDER UNIVERSITY")))
                .andExpect(content().string(containsString("SYSTEMS ENGINEERING PROGRAM")))
                .andExpect(content().string(containsString("ANALYSIS OF ALGORITHMS")))
                .andExpect(content().string(containsString("JUAN DIEGO ROJAS REY")))
                .andExpect(content().string(containsString("NOT SO NAIVE")));
    }

    @Test
    void searchReturnsCountPositionsAndStats() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("banana.txt", "BANANA")).param("pattern", "ANA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("Not So Naive"))
                .andExpect(jsonPath("$.matchCount").value(2))
                .andExpect(jsonPath("$.positions[0]").value(1))
                .andExpect(jsonPath("$.positions[1]").value(3))
                .andExpect(jsonPath("$.fileName").value("banana.txt"))
                .andExpect(jsonPath("$.k").value(1))
                .andExpect(jsonPath("$.ell").value(2))
                .andExpect(jsonPath("$.elapsedMillis").exists())
                .andExpect(jsonPath("$.trace").isArray());
    }

    @Test
    void pageContainsNoSpanishHeaderText() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        for (String spanish : new String[]{"UNIVERSIDAD", "PROGRAMA DE", "ASIGNATURA", "ALGORITMO", "ESTUDIANTE"}) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(spanish), spanish);
        }
    }

    @Test
    void emptyPatternIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "abc")).param("pattern", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("pattern")));
    }

    @Test
    void nonAsciiFileIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "año")).param("pattern", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("not ASCII")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").file(txt("a.txt", "")).param("pattern", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pdfEndpointReturnsDownloadablePdf() throws Exception {
        mvc.perform(multipart("/api/pdf").file(txt("demo.txt", "ABABABAB")).param("pattern", "ABA"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Match-Count", "3"));
    }

    @Test
    void deliveryPageAndZip() throws Exception {
        mvc.perform(get("/delivery")).andExpect(status().isOk())
                .andExpect(content().string(containsString("not-so-naive-project.zip")));
        mvc.perform(get("/entrega")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/delivery"));
        mvc.perform(get("/delivery/not-so-naive-project.zip"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void typedTextIsSearchedLikeAFile() throws Exception {
        mvc.perform(multipart("/api/search").param("text", "GCATCGCAGAGAGTAT").param("pattern", "GCAGAGAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("typed-text.txt"))
                .andExpect(jsonPath("$.matchCount").value(1))
                .andExpect(jsonPath("$.positions[0]").value(5))
                .andExpect(jsonPath("$.finalJ").value(9))
                .andExpect(jsonPath("$.trace[0].j").value(0))
                .andExpect(jsonPath("$.trace[0].stopI").value(3))
                .andExpect(jsonPath("$.trace[1].j").value(2))
                .andExpect(jsonPath("$.traceLimit").value(300));
    }

    @Test
    void typedTextPdf() throws Exception {
        mvc.perform(multipart("/api/pdf").param("text", "AAAAAA").param("pattern", "AA"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Match-Count", "5"));
    }

    @Test
    void longTextHasNoTrace() throws Exception {
        mvc.perform(multipart("/api/search").param("text", "A".repeat(301)).param("pattern", "AA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchCount").value(300))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void nonAsciiTypedTextIsRejected() throws Exception {
        mvc.perform(multipart("/api/search").param("text", "a\u00f1o").param("pattern", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("not ASCII")));
    }

    @Test
    void algorithmPageAndFooter() throws Exception {
        mvc.perform(get("/algorithm"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Why a shift of 2 is safe")))
                .andExpect(content().string(containsString("public static List&lt;Integer&gt; notSoNaive")))
                .andExpect(content().string(containsString("https://github.com/juandi12334/nat-so-naive")))
                .andExpect(content().string(containsString("Marco Antonio Adarme Jaimes")))
                .andExpect(content().string(containsString("juandiegorore@ufps.edu.co")))
                .andExpect(content().string(containsString("San Jos\u00e9 de C\u00facuta")));
    }

    @Test
    void simulatorPageHasNoRemovedSections() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        for (String gone : new String[]{"About the algorithm", "0-based character indexes", "PDF report",
                "Execution time", "Attempts (windows)", "<table class=\"trace\""}) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(gone), gone);
        }
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Or type your text"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Generate PDF"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Juan Diego Rojas Rey"));
    }
}
