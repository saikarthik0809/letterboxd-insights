package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final FilmRepository filmRepository;

    public StatsService(FilmRepository filmRepository) {
        this.filmRepository = filmRepository;
    }

    // Shapes of the data sent to the screen
    public record Count(String label, long count) {}
    public record DirectorStat(String name, long count, double averageRating) {}
    public record HotTake(String name, Integer year, String posterPath, double yourRating, double tmdbRating) {}
    public record Stats(
            long totalFilms,
            long ratedFilms,
            double averageRating,
            long totalMinutes,
            List<Count> ratingDistribution,
            List<Count> filmsPerYear,
            List<Count> decades,
            List<Count> topGenres,
            List<DirectorStat> topDirectors,
            List<HotTake> ratedHigherThanMost,
            List<HotTake> ratedLowerThanMost
    ) {}

    public Stats getStats() {
        List<Film> films = filmRepository.findAll();
        List<Film> rated = films.stream().filter(f -> f.getRating() != null).toList();

        double average = rated.stream().mapToDouble(Film::getRating).average().orElse(0);

        long totalMinutes = films.stream()
                .filter(f -> f.getRuntime() != null)
                .mapToLong(Film::getRuntime)
                .sum();

        // How many films at each star rating, 0.5 to 5.0
        List<Count> ratingDistribution = new ArrayList<>();
        for (double r = 0.5; r <= 5.0; r += 0.5) {
            final double stars = r;
            long count = rated.stream().filter(f -> f.getRating() == stars).count();
            ratingDistribution.add(new Count(String.valueOf(stars), count));
        }

        // Films logged per year (by the date you logged them)
        Map<Integer, Long> byYear = films.stream()
                .filter(f -> f.getWatchedDate() != null)
                .collect(Collectors.groupingBy(f -> f.getWatchedDate().getYear(), TreeMap::new, Collectors.counting()));
        List<Count> filmsPerYear = byYear.entrySet().stream()
                .map(e -> new Count(String.valueOf(e.getKey()), e.getValue()))
                .toList();

        // Films by release decade
        Map<Integer, Long> byDecade = films.stream()
                .filter(f -> f.getYear() != null)
                .collect(Collectors.groupingBy(f -> (f.getYear() / 10) * 10, TreeMap::new, Collectors.counting()));
        List<Count> decades = byDecade.entrySet().stream()
                .map(e -> new Count(e.getKey() + "s", e.getValue()))
                .toList();

        // Top 10 genres
        Map<String, Long> genreCounts = films.stream()
                .flatMap(f -> f.getGenres().stream())
                .collect(Collectors.groupingBy(g -> g, Collectors.counting()));
        List<Count> topGenres = genreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new Count(e.getKey(), e.getValue()))
                .toList();

        // Top 10 directors: most films watched, then highest average rating
        Map<String, List<Film>> byDirector = films.stream()
                .filter(f -> f.getDirector() != null)
                .collect(Collectors.groupingBy(Film::getDirector));
        List<DirectorStat> topDirectors = byDirector.entrySet().stream()
                .map(e -> new DirectorStat(
                        e.getKey(),
                        e.getValue().size(),
                        round(e.getValue().stream()
                                .filter(f -> f.getRating() != null)
                                .mapToDouble(Film::getRating)
                                .average().orElse(0))
                ))
                .sorted(Comparator.comparingLong(DirectorStat::count).reversed()
                        .thenComparing(Comparator.comparingDouble(DirectorStat::averageRating).reversed()))
                .limit(10)
                .toList();

        // Hot takes: your rating vs TMDB's (TMDB is out of 10, so we double yours)
        List<Film> comparable = rated.stream()
                .filter(f -> f.getTmdbRating() != null && f.getTmdbRating() > 0)
                .toList();
        Comparator<Film> byDifference = Comparator.comparingDouble(f -> f.getRating() * 2 - f.getTmdbRating());

        List<HotTake> higher = comparable.stream()
                .sorted(byDifference.reversed())
                .limit(5)
                .map(this::toHotTake)
                .toList();
        List<HotTake> lower = comparable.stream()
                .sorted(byDifference)
                .limit(5)
                .map(this::toHotTake)
                .toList();

        return new Stats(
                films.size(),
                rated.size(),
                round(average),
                totalMinutes,
                ratingDistribution,
                filmsPerYear,
                decades,
                topGenres,
                topDirectors,
                higher,
                lower
        );
    }

    private HotTake toHotTake(Film f) {
        // TMDB's rating converted to a 5-star scale so both are comparable
        return new HotTake(f.getName(), f.getYear(), f.getPosterPath(), f.getRating(), round(f.getTmdbRating() / 2));
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}