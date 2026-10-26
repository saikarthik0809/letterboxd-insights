package com.karthik.letterboxd.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "films")
@Getter
@Setter
@NoArgsConstructor
public class Film {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer year;

    @Column(unique = true)
    private String letterboxdUri;

    private Double rating;
    private LocalDate watchedDate;
}