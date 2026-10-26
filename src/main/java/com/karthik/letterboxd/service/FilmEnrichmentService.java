package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

@Service
public class FilmEnrichmentService {

    private final FilmRepository filmRepository;
    private final TmdbClient tmdbClient;

    public FilmEnrichmentService(FilmRepository filmRepository, TmdbClient tmdbClient) {
        this.filmRepository = filmRepository;
        this.tmdbClient = tmdbClient;
    }

    // Looks up the next batch of films on TMDB and saves their details
    public Map<String, Long> enrichBatch(int batchSize) {
        List<Film> batch = filmRepository.findByEnrichedIsNull(PageRequest.of(0, batchSize));

        for (Film film : batch) {
            try {
                tmdbClient.findMovieId(film.getName(), film.getYear()).ifPresent(tmdbId -> {
                    TmdbClient.MovieDetails details = tmdbClient.getDetails(tmdbId);

                    film.setTmdbId(tmdbId);
                    film.setPosterPath(details.posterPath());
                    film.setOverview(details.overview());
                    film.setRuntime(details.runtime());
                    film.setTmdbRating(details.voteAverage());

                    film.getGenres().clear();
                    if (details.genres() != null) {
                        details.genres().forEach(g -> film.getGenres().add(g.name()));
                    }

                    if (details.credits() != null && details.credits().crew() != null) {
                        details.credits().crew().stream()
                                .filter(c -> "Director".equals(c.job()))
                                .findFirst()
                                .ifPresent(c -> film.setDirector(c.name()));
                    }
                });
            } catch (HttpClientErrorException.Unauthorized e) {
                // wrong or missing TMDB token: stop instead of skipping every film
                throw e;
            } catch (Exception e) {
                // any other problem with one film: skip it and keep going
            }

            film.setEnriched(true);
            filmRepository.save(film);
        }

        return Map.of(
                "processed", (long) batch.size(),
                "remaining", filmRepository.countByEnrichedIsNull()
        );
    }
}