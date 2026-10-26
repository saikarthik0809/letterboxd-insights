package com.karthik.letterboxd.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "films")
@Getter
@Setter
@NoArgsConstructor
public class Film {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // from Letterboxd
    private String name;
    private Integer year;

    @Column(unique = true)
    private String letterboxdUri;

    private Double rating;
    private LocalDate watchedDate;

    // from TMDB
    private Long tmdbId;
    private String posterPath;

    @Column(columnDefinition = "TEXT")
    private String overview;

    private String director;
    private Integer runtime;
    private Double tmdbRating;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "film_genres", joinColumns = @JoinColumn(name = "film_id"))
    @Column(name = "genre")
    private List<String> genres = new ArrayList<>();

    // null = not looked up on TMDB yet
    private Boolean enriched;
}