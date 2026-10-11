package fr.dependencyexplorer.web;

import fr.dependencyexplorer.service.DependencyTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DependencyControllerTest {
    private DependencyTreeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DependencyTreeService.class);
        mvc = standaloneSetup(new DependencyController(service)).build();
    }

    @Test
    void exposesDependencySearchResults() throws Exception {
        when(service.search("jackson")).thenReturn(List.of(Map.of("key", "g:jackson:jar::1", "label", "jackson")));

        mvc.perform(get("/api/search").param("q", "jackson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("jackson"));
    }

    @Test
    void acceptsFolderForScanning() throws Exception {
        when(service.scanFolder("C:/projects")).thenReturn(Map.<String, Object>of("found", 2, "scanned", 2, "failed", 0));

        mvc.perform(post("/api/scan").contentType("application/json")
                        .content("{\"folderPath\":\"C:/projects\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(2));
        verify(service).scanFolder("C:/projects");
    }

    @Test
    void returnsBadRequestForInvalidRequest() throws Exception {
        when(service.tree(anyString(), anyString())).thenThrow(new IllegalArgumentException("Choisissez une dépendance."));

        mvc.perform(get("/api/tree").param("key", "missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Choisissez une dépendance."));
    }
}
