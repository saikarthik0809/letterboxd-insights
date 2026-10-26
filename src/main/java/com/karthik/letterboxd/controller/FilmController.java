package com.karthik.letterboxd.controller;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import com.karthik.letterboxd.service.FilmEnrichmentService;
import com.karthik.letterboxd.service.FilmImportService;
import com.karthik.letterboxd.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/films")
public class FilmController {

    private final FilmRepository filmRepository;
    private final FilmImportService filmImportService;
    private final FilmEnrichmentService filmEnrichmentService;

    public FilmController(FilmRepository filmRepository,
                          FilmImportService filmImportService,
                          FilmEnrichmentService filmEnrichmentService) {
        this.filmRepository = filmRepository;
        this.filmImportService = filmImportService;
        this.filmEnrichmentService = filmEnrichmentService;
    }

    public record RatingRequest(Double rating) {}

    @GetMapping
    public List<Film> getAllFilms() {
        return filmRepository.findAll();
    }

    @PostMapping("/upload")
    public Map<String, Integer> upload(@RequestParam("file") MultipartFile file) throws IOException {
        int count = filmImportService.importFile(file);
        return Map.of("imported", count);
    }

    @PostMapping("/enrich")
    public Map<String, Long> enrich() {
        return filmEnrichmentService.enrichBatch(20);
    }

    // Update one film's rating
    @PatchMapping("/{id}")
    public Film updateRating(@PathVariable Long id, @RequestBody RatingRequest request) {
        Film film = filmRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Film not found"));
        WatchlistService.validateRating(request.rating());
        film.setRating(request.rating());
        return filmRepository.save(film);
    }

    // Delete one film
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFilm(@PathVariable Long id) {
        if (!filmRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Film not found");
        }
        filmRepository.deleteById(id);
    }

    // Delete all films
    @DeleteMapping
    public void deleteAll() {
        filmRepository.deleteAll();
    }
}