package fr.dependencyexplorer.web;

import fr.dependencyexplorer.service.DependencyTreeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping
public class DependencyController {
    private final DependencyTreeService service;

    public DependencyController(DependencyTreeService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/api/projects")
    @ResponseBody
    public List<Map<String, Object>> projects() throws IOException {
        return service.projectSummaries();
    }

    @PostMapping("/api/scan")
    @ResponseBody
    public Map<String, Object> scan(@RequestBody Map<String, String> request) throws IOException {
        return service.scanFolder(request.get("folderPath"));
    }

    @GetMapping("/api/search")
    @ResponseBody
    public List<Map<String, String>> search(@RequestParam(defaultValue = "") String q) throws IOException {
        return service.search(q);
    }

    @GetMapping("/api/tree")
    @ResponseBody
    public Map<String, Object> tree(@RequestParam String key,
                                    @RequestParam(defaultValue = "dependencies") String direction) throws IOException {
        return service.tree(key, direction);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> serverError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", exception.getMessage() == null ? "Erreur d'analyse." : exception.getMessage()));
    }
}
