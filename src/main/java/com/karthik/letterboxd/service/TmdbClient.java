package com.karthik.letterboxd.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public class TmdbClient {

    private final RestClient restClient;

    public TmdbClient(@Value("${tmdb.token}") String token) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    // Shapes of the JSON that TMDB sends back (only the parts we need)
    public record SearchResult(Long id) {}
    public record SearchResponse(List<SearchResult> results) {}
    public record Genre(String name) {}
    public record CrewMember(String name, String job) {}
    public record Credits(List<CrewMember> crew) {}
    public record MovieDetails(
            Long id,
            String overview,
            Integer runtime,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("vote_average") Double voteAverage,
            List<Genre> genres,
            Credits credits
    ) {}

    // Find a film's TMDB id by name and year; retry without the year if nothing matches
    public Optional<Long> findMovieId(String title, Integer year) {
        SearchResponse res = search(title, year);
        if (isEmpty(res) && year != null) {
            res = search(title, null);
        }
        if (isEmpty(res)) return Optional.empty();
        return Optional.of(res.results().get(0).id());
    }

    public MovieDetails getDetails(Long tmdbId) {
        return restClient.get()
                .uri("/movie/{id}?append_to_response=credits", tmdbId)
                .retrieve()
                .body(MovieDetails.class);
    }

    private SearchResponse search(String title, Integer year) {
        return restClient.get()
                .uri(b -> {
                    b.path("/search/movie").queryParam("query", "{q}");
                    if (year != null) b.queryParam("primary_release_year", year);
                    return b.build(title);
                })
                .retrieve()
                .body(SearchResponse.class);
    }

    private boolean isEmpty(SearchResponse res) {
        return res == null || res.results() == null || res.results().isEmpty();
    }
}