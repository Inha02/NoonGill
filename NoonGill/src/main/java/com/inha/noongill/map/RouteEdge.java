package com.inha.noongill.map;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_edges")
@Getter @Setter @NoArgsConstructor
public class RouteEdge {
    public enum PathType { OUTDOOR, ENTRANCE, CORRIDOR, STAIRS, ELEVATOR, COVERED_PATH, BUILDING_CONNECTION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RouteNode startNode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RouteNode endNode;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PathType pathType;
    private double distanceMeters;
    private double durationSeconds;
    private boolean indoor;
    private double rainExposure;
    private int stairCount;
    private boolean wheelchairAccessible = true;
    private boolean bidirectional = true;
    private boolean active = true;
    @Column(length = 500)
    private String connectionFloors = "";

    public RouteEdge(RouteNode startNode, RouteNode endNode, PathType pathType, double distanceMeters,
                     double durationSeconds, boolean indoor, double rainExposure, int stairCount,
                     boolean wheelchairAccessible) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.pathType = pathType;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.indoor = indoor;
        this.rainExposure = rainExposure;
        this.stairCount = stairCount;
        this.wheelchairAccessible = wheelchairAccessible;
    }
}
