package com.inha.noongill.map;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "buildings")
@Getter @Setter @NoArgsConstructor
public class Building {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    private String detail;
    private double latitude;
    private double longitude;
    @Column(nullable = false, columnDefinition = "integer default 1")
    private int floorCount = 1;

    public Building(String name, String detail, double latitude, double longitude) {
        this.name = name;
        this.detail = detail;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
