package com.karthik.letterboxd.service;

import com.karthik.letterboxd.model.Film;
import com.karthik.letterboxd.repository.FilmRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FilmImportService {

    private final FilmRepository filmRepository;

    public FilmImportService(FilmRepository filmRepository) {
        this.filmRepository = filmRepository;
    }

    // Accepts either the full Letterboxd zip or a single CSV
    public int importFile(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".zip")) {
            return importZip(file.getInputStream());
        }
        return importCsv(file.getInputStream());
    }

    private int importZip(InputStream in) throws IOException {
        byte[] watched = null;
        byte[] ratings = null;

        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.equals("watched.csv")) watched = zip.readAllBytes();
                else if (entryName.equals("ratings.csv")) ratings = zip.readAllBytes();
            }
        }

        int count = 0;
        // watched first (films + watch dates), then ratings on top
        if (watched != null) count = importCsv(new ByteArrayInputStream(watched));
        if (ratings != null) importCsv(new ByteArrayInputStream(ratings));
        return count;
    }

    private int importCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        int count = 0;

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            boolean hasRating = parser.getHeaderMap().containsKey("Rating");

            for (CSVRecord row : parser) {
                String uri = row.get("Letterboxd URI");
                if (uri == null || uri.isBlank()) continue;

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