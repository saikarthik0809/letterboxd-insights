package com.karthik.letterboxd.repository;

import com.karthik.letterboxd.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    boolean existsByTmdbId(Long tmdbId);

    // newest additions first
    List<WatchlistItem> findAllByOrderByAddedDateDescIdDesc();
}