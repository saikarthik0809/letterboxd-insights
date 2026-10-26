package com.karthik.letterboxd.repository;

import com.karthik.letterboxd.model.Film;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilmRepository extends JpaRepository<Film, Long> {
    Optional<Film> findByLetterboxdUri(String letterboxdUri);
}