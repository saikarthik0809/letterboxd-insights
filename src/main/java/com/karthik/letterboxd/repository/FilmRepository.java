package com.karthik.letterboxd.repository;

import com.karthik.letterboxd.model.Film;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilmRepository extends JpaRepository<Film, Long> {

    Optional<Film> findByLetterboxdUri(String letterboxdUri);

    // films not yet looked up on TMDB, a batch at a time
    List<Film> findByEnrichedIsNull(Pageable pageable);

    long countByEnrichedIsNull();
}