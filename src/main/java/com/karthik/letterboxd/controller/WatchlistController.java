package com.karthik.letterboxd.controller;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.model.WatchlistItem;
import com.karthik.letterboxd.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    public record WatchedRequest(Double rating) {}

    @GetMapping
    public List<WatchlistItem> getAll() {
        return watchlistService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistItem add(@RequestBody WatchlistService.AddRequest request) {
        return watchlistService.add(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        watchlistService.remove(id);
    }

    @PostMapping("/{id}/watched")
    public Film markWatched(@PathVariable Long id, @RequestBody WatchedRequest request) {
        return watchlistService.markWatched(id, request.rating());
    }
}