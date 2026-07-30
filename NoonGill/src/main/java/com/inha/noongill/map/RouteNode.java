package com.inha.noongill.map;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_nodes")
@Getter @Setter @NoArgsConstructor
public class RouteNode {
    public enum NodeType { OUTDOOR, ENTRANCE, DOOR, LOBBY, CORRIDOR, STAIRS, ELEVATOR, CONNECTOR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private double latitude;
    private double longitude;
    private Integer floor;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NodeType nodeType;
    @ManyToOne(fetch = FetchType.LAZY)
    private Building building;
    private Double indoorX;
    private Double indoorY;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean virtualNode;
    private boolean active = true;

    public RouteNode(String name, double latitude, double longitude, Integer floor,
                     NodeType nodeType, Building building) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.floor = floor;
        this.nodeType = nodeType;
        this.building = building;
    }
}
