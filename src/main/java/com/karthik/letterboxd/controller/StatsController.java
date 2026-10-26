package com.karthik.letterboxd.controller;

import com.karthik.letterboxd.service.StatsService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsService.Stats getStats() {
        return statsService.getStats();
    }
}