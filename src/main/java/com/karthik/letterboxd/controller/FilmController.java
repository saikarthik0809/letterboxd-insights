package com.karthik.letterboxd.controller;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import com.karthik.letterboxd.service.FilmImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/films")
public class FilmController {

    private final FilmRepository filmRepository;
    private final FilmImportService filmImportService;

    public FilmController(FilmRepository filmRepository, FilmImportService filmImportService) {
        this.filmRepository = filmRepository;
        this.filmImportService = filmImportService;
    }

    @GetMapping
    public List<Film> getAllFilms() {
        return filmRepository.findAll();
    }

    @PostMapping("/upload")
    public Map<String, Integer> upload(@RequestParam("file") MultipartFile file) throws IOException {
        int count = filmImportService.importFile(file);
        return Map.of("imported", count);
    }

    @DeleteMapping
    public void deleteAll() {
        filmRepository.deleteAll();
    }
}