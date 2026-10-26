package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class FilmImportService {

    private final FilmRepository filmRepository;

    public FilmImportService(FilmRepository filmRepository) {
        this.filmRepository = filmRepository;
    }

    public int importCsv(MultipartFile file) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        int count = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            boolean hasRating = parser.getHeaderMap().containsKey("Rating");

            for (CSVRecord row : parser) {
                String uri = row.get("Letterboxd URI");
                if (uri == null || uri.isBlank()) continue;

                // update the film if we already have it, otherwise create a new one
                Film film = filmRepository.findByLetterboxdUri(uri).orElseGet(Film::new);
                film.setLetterboxdUri(uri);
                film.setName(row.get("Name"));

                String year = row.get("Year");
                if (!year.isBlank()) film.setYear(Integer.parseInt(year));

                String date = row.get("Date");
                if (!date.isBlank() && film.getWatchedDate() == null) {
                    film.setWatchedDate(LocalDate.parse(date));
                }

                if (hasRating) {
                    String rating = row.get("Rating");
                    if (!rating.isBlank()) film.setRating(Double.parseDouble(rating));
                }

                filmRepository.save(film);
                count++;
            }
        }
        return count;
    }
}