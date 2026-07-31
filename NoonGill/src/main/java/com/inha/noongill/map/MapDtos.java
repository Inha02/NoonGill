package com.inha.noongill.map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class MapDtos {
    private MapDtos() {}
    public enum RouteType { FASTEST, RAIN_FREE, ACCESSIBLE }
    public enum LocationType { POINT, BUILDING, NODE }

    public record BuildingResponse(long id, String name, String detail, double latitude, double longitude,
                                   int floorCount, int basementFloorCount) {}
    public record NodeResponse(long id, String name, double latitude, double longitude, Integer floor,
                               RouteNode.NodeType nodeType, Long buildingId, Double indoorX, Double indoorY,
                               boolean virtualNode) {}
    public record EdgeResponse(long id, long startNodeId, long endNodeId, RouteEdge.PathType pathType,
                               double distanceMeters, double durationSeconds, boolean indoor,
                               double rainExposure, int stairCount, boolean wheelchairAccessible,
                               boolean bidirectional, List<Integer> connectionFloors) {}
    public record MapDataResponse(List<BuildingResponse> buildings, List<NodeResponse> nodes,
                                  List<EdgeResponse> edges) {}
    public record RouteLocationRequest(@NotNull LocationType type, Double latitude, Double longitude,
                                       Integer floor, Long buildingId, Long nodeId) {}
    public record RouteSearchRequest(@NotNull @Valid RouteLocationRequest start,
                                     @NotNull @Valid RouteLocationRequest destination,
                                     @NotEmpty List<RouteType> routeTypes) {}
    public record RoutePointResponse(long nodeId, String name, double latitude, double longitude,
                                     Integer floor, Long buildingId, RouteNode.NodeType nodeType,
                                     Double indoorX, Double indoorY) {}
    public record RouteSegmentResponse(long edgeId, long fromNodeId, long toNodeId, String instruction,
                                       double distanceMeters, double estimatedSeconds,
                                       RouteEdge.PathType pathType, boolean indoor) {}
    public record RouteResponse(RouteType routeType, double totalDistanceMeters, double estimatedSeconds,
                                double indoorRatio, List<RoutePointResponse> points,
                                List<RouteSegmentResponse> segments) {}

    public record NodeChange(Long id, @NotBlank String name, double latitude, double longitude,
                             Integer floor, @NotNull RouteNode.NodeType nodeType, Long buildingId,
                             Double indoorX, Double indoorY, boolean virtualNode) {}
    public record BuildingChange(Long id, @NotBlank String name, String detail,
                                 double latitude, double longitude, @Min(1) int floorCount,
                                 @PositiveOrZero int basementFloorCount) {}
    public record EdgeChange(Long id, @NotNull Long startNodeId, @NotNull Long endNodeId,
                             @NotNull RouteEdge.PathType pathType, @PositiveOrZero double distanceMeters,
                             @PositiveOrZero double durationSeconds, boolean indoor,
                             @DecimalMin("0.0") @DecimalMax("1.0") double rainExposure,
                             @PositiveOrZero int stairCount, boolean wheelchairAccessible,
                             boolean bidirectional, List<@NotNull Integer> connectionFloors) {}
    public record MapChangesRequest(List<@Valid BuildingChange> buildings,
                                    List<@Valid NodeChange> nodes, List<@Valid EdgeChange> edges,
                                    List<Long> deletedBuildingIds, List<Long> deletedNodeIds,
                                    List<Long> deletedEdgeIds) {}
}
