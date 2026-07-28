package com.inha.noongill.route;

import java.util.List;

public final class RouteModels {
    private RouteModels() {}

    public enum RouteOption { FASTEST, INDOOR, ACCESSIBLE }

    public record Place(long id, String name, String detail, String type, int floor) {}

    public record Edge(
            long from, long to, int distance, int seconds, String pathType,
            boolean indoor, boolean stairs, boolean wheelchairAccessible, String instruction
    ) {}

    public record Segment(
            long fromNodeId, long toNodeId, String instruction, int distance,
            int estimatedSeconds, String pathType, boolean indoor
    ) {}

    public record RouteResponse(
            int totalDistance, int estimatedSeconds, double indoorRatio,
            RouteOption option, List<Place> nodes, List<Segment> segments
    ) {}

    public record RouteReportRequest(long edgeId, String reportType, String description) {}
    public record RouteReportResponse(long id, String status) {}
}
