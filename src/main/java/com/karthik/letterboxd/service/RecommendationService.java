package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final FilmRepository filmRepository;
    private final TmdbClient tmdbClient;

    public RecommendationService(FilmRepository filmRepository, TmdbClient tmdbClient) {
        this.filmRepository = filmRepository;
        this.tmdbClient = tmdbClient;
    }

    // What the screen receives for each recommended film
    public record Recommendation(
            Long tmdbId,
            String title,
            Integer year,
            String posterPath,
            String overview,
            Double tmdbRating,
            List<String> genres,
            String director,
            double score,
            List<String> because,   // your favorites that led to this pick
            List<String> reasons    // taste matches, e.g. "You rate Drama highly"
    ) {}

    // Working data for one candidate while scoring
    private static class Candidate {
        final TmdbClient.RecommendedMovie movie;
        final List<String> seeds = new ArrayList<>();
        List<String> genres = List.of();
        String director;
        double score;

        Candidate(TmdbClient.RecommendedMovie movie) {
            this.movie = movie;
        }
    }

    public List<Recommendation> recommend(int limit) {
        List<Film> films = filmRepository.findAll();
        List<Film> rated = films.stream().filter(f -> f.getRating() != null).toList();
        if (rated.isEmpty()) return List.of();

        double myAverage = rated.stream().mapToDouble(Film::getRating).average().orElse(0);

        // 1. Learn your taste
        Map<String, Double> genreTaste = taste(rated, myAverage, Film::getGenres);
        Map<String, Double> directorTaste = taste(rated, myAverage,
                f -> f.getDirector() == null ? List.of() : List.of(f.getDirector()));

        // Films you've already seen, so we never recommend them
        Set<Long> seen = films.stream()
                .map(Film::getTmdbId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. Seeds: your 20 highest-rated films (most recently watched first on ties)
        List<Film> seeds = rated.stream()
                .filter(f -> f.getTmdbId() != null)
                .sorted(Comparator.comparing(Film::getRating).reversed()
                        .thenComparing(Film::getWatchedDate,
                                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                .limit(20)
                .toList();

        // 3. Gather candidates from TMDB's "similar films" for each seed
        Map<Long, Candidate> candidates = new HashMap<>();
        for (Film seed : seeds) {
            try {
                for (TmdbClient.RecommendedMovie movie : tmdbClient.getRecommendations(seed.getTmdbId())) {
                    if (movie.id() == null || seen.contains(movie.id())) continue;
                    candidates.computeIfAbsent(movie.id(), id -> new Candidate(movie))
                            .seeds.add(seed.getName());
                }
            } catch (Exception e) {
                // skip a seed if TMDB has a problem with it
            }
        }
        if (candidates.isEmpty()) return List.of();

        // 4. First scoring pass (no extra TMDB calls)
        Map<Integer, String> genreNames = tmdbClient.getGenreNames();
        for (Candidate c : candidates.values()) {
            List<Integer> ids = c.movie.genreIds() == null ? List.of() : c.movie.genreIds();
            c.genres = ids.stream().map(genreNames::get).filter(Objects::nonNull).toList();

            double score = 0;
            score += Math.min(c.seeds.size(), 5);                       // liked by several favorites
            score += 2 * c.genres.stream()
                    .mapToDouble(g -> genreTaste.getOrDefault(g, 0.0)).sum(); // genres you like

            double vote = c.movie.voteAverage() == null ? 0 : c.movie.voteAverage();
            int votes = c.movie.voteCount() == null ? 0 : c.movie.voteCount();
            score += (vote - 6.5) * 0.5;                                // audiences like it
            if (votes < 100) score -= 1;                                // too obscure to trust

            c.score = score;
        }

        // 5. Second pass on the shortlist: look up directors and add a bonus
        List<Candidate> shortlist = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score).reversed())
                .limit(limit * 2L)
                .toList();

        for (Candidate c : shortlist) {
            try {
                TmdbClient.MovieDetails details = tmdbClient.getDetails(c.movie.id());
                if (details != null && details.credits() != null && details.credits().crew() != null) {
                    details.credits().crew().stream()
                            .filter(m -> "Director".equals(m.job()))
                            .findFirst()
                            .ifPresent(m -> c.director = m.name());
                }
                if (c.director != null) {
                    c.score += 3 * directorTaste.getOrDefault(c.director, 0.0);
                }
            } catch (Exception e) {
                // keep the candidate without director info
            }
        }

        // 6. Final ranking, with explanations
        return shortlist.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score).reversed())
                .limit(limit)
                .map(c -> toRecommendation(c, genreTaste, directorTaste))
                .toList();
    }

    /*
     * For each genre (or director): how much above or below your average
     * you rate their films. Small samples are pulled toward 0 so that one
     * film doesn't make you a "fan".
     */
    private Map<String, Double> taste(List<Film> rated, double myAverage,
                                      Function<Film, List<String>> keysOf) {
        Map<String, double[]> totals = new HashMap<>(); // [sum of differences, count]
        for (Film f : rated) {
            for (String key : keysOf.apply(f)) {
                double[] t = totals.computeIfAbsent(key, k -> new double[2]);
                t[0] += f.getRating() - myAverage;
                t[1]++;
            }
        }
        Map<String, Double> result = new HashMap<>();
        totals.forEach((key, t) -> result.put(key, t[0] / (t[1] + 3)));
        return result;
    }

    private Recommendation toRecommendation(Candidate c,
                                            Map<String, Double> genreTaste,
                                            Map<String, Double> directorTaste) {
        List<String> reasons = new ArrayList<>();

        c.genres.stream()
                .filter(g -> genreTaste.getOrDefault(g, 0.0) > 0.05)
                .max(Comparator.comparingDouble(g -> genreTaste.getOrDefault(g, 0.0)))
                .ifPresent(g -> reasons.add("You rate " + g + " highly"));

        if (c.director != null && directorTaste.getOrDefault(c.director, 0.0) > 0.05) {
            reasons.add("You've loved films by " + c.director);
        }

        String date = c.movie.releaseDate();
        Integer year = (date != null && date.length() >= 4) ? Integer.valueOf(date.substring(0, 4)) : null;

        return new Recommendation(
                c.movie.id(),
                c.movie.title(),
                year,
                c.movie.posterPath(),
                c.movie.overview(),
                c.movie.voteAverage() == null ? null : Math.round(c.movie.voteAverage() * 10) / 10.0,
                c.genres,
                c.director,
                Math.round(c.score * 100) / 100.0,
                c.seeds.stream().limit(2).toList(),
                reasons
        );
    }
}