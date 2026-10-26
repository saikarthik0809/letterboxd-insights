package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.model.WatchlistItem;
import com.karthik.letterboxd.repository.FilmRepository;
import com.karthik.letterboxd.repository.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final FilmRepository filmRepository;
    private final FilmEnrichmentService filmEnrichmentService;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            FilmRepository filmRepository,
                            FilmEnrichmentService filmEnrichmentService) {
        this.watchlistRepository = watchlistRepository;
        this.filmRepository = filmRepository;
        this.filmEnrichmentService = filmEnrichmentService;
    }

    // What the screen sends when adding a film
    public record AddRequest(Long tmdbId, String title, Integer year, String posterPath) {}

    public List<WatchlistItem> getAll() {
        return watchlistRepository.findAllByOrderByAddedDateDescIdDesc();
    }

    public WatchlistItem add(AddRequest request) {
        if (request.tmdbId() == null || request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tmdbId and title are required");
        }
        if (watchlistRepository.existsByTmdbId(request.tmdbId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already on your watchlist");
        }

        WatchlistItem item = new WatchlistItem();
        item.setTmdbId(request.tmdbId());
        item.setTitle(request.title());
        item.setYear(request.year());
        item.setPosterPath(request.posterPath());
        item.setAddedDate(LocalDate.now());
        return watchlistRepository.save(item);
    }

    public void remove(Long id) {
        if (!watchlistRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found");
        }
        watchlistRepository.deleteById(id);
    }

    // Moves a watchlist film into your films, with your rating
    public Film markWatched(Long id, Double rating) {
        WatchlistItem item = watchlistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found"));
        validateRating(rating);

        Film film = new Film();
        film.setTmdbId(item.getTmdbId());
        film.setName(item.getTitle());
        film.setYear(item.getYear());
        film.setPosterPath(item.getPosterPath());
        film.setRating(rating);
        film.setWatchedDate(LocalDate.now());

        filmEnrichmentService.enrichFilm(film); // genres, director, runtime
        Film saved = filmRepository.save(film);

        watchlistRepository.delete(item);
        return saved;
    }

    // Ratings must be null (no rating) or 0.5 to 5 in half-star steps
    public static void validateRating(Double rating) {
        if (rating == null) return;
        boolean valid = rating >= 0.5 && rating <= 5 && (rating * 2) % 1 == 0;
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be 0.5 to 5 in half steps");
        }
    }
}