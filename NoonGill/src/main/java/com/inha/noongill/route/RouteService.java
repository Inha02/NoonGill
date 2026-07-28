package com.inha.noongill.route;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.inha.noongill.route.RouteModels.*;

@Service
public class RouteService {
    private final Map<Long, Place> places = new LinkedHashMap<>();
    private final Map<Long, List<Edge>> graph = new HashMap<>();
    private final AtomicLong reportSequence = new AtomicLong();

    public RouteService() {
        addPlace(new Place(1, "숙명여대 정문", "제1캠퍼스", "GATE", 0));
        addPlace(new Place(2, "명신관", "1층 정문", "ENTRANCE", 1));
        addPlace(new Place(3, "명신관 중앙 로비", "1층", "LOBBY", 1));
        addPlace(new Place(4, "명신관 2층 계단", "2층", "STAIRS", 2));
        addPlace(new Place(5, "순헌관 연결통로", "2층", "CONNECTOR", 2));
        addPlace(new Place(6, "순헌관", "3층 로비", "LOBBY", 3));
        addPlace(new Place(7, "중앙도서관", "1층 로비", "BUILDING", 1));
        addPlace(new Place(8, "학생회관", "1층 로비", "BUILDING", 1));

        connect(1, 2, 90, 70, "OUTDOOR", false, false, true, "명신관 방향으로 이동하세요.");
        connect(2, 3, 20, 18, "ENTRANCE", true, false, true, "명신관 정문으로 들어가세요.");
        connect(3, 4, 55, 60, "STAIRS", true, true, false, "중앙 복도를 따라 2층으로 이동하세요.");
        connect(3, 5, 100, 110, "ELEVATOR", true, false, true, "엘리베이터를 이용해 2층으로 이동하세요.");
        connect(4, 5, 65, 40, "CONNECTOR", true, false, true, "순헌관 연결통로를 이용하세요.");
        connect(5, 6, 40, 55, "CORRIDOR", true, false, true, "순헌관 3층에 도착합니다.");
        connect(5, 7, 170, 150, "OUTDOOR", false, false, true, "중앙도서관 방향으로 이동하세요.");
        connect(3, 8, 145, 130, "OUTDOOR", false, false, true, "학생회관 방향으로 이동하세요.");
    }

    private void addPlace(Place place) {
        places.put(place.id(), place);
        graph.put(place.id(), new ArrayList<>());
    }

    private void connect(long from, long to, int distance, int seconds, String pathType,
                         boolean indoor, boolean stairs, boolean accessible, String instruction) {
        graph.get(from).add(new Edge(from, to, distance, seconds, pathType, indoor, stairs, accessible, instruction));
        graph.get(to).add(new Edge(to, from, distance, seconds, pathType, indoor, stairs, accessible, instruction));
    }

    public List<Place> searchPlaces(String keyword) {
        if (keyword == null || keyword.isBlank()) return new ArrayList<>(places.values());
        String normalized = keyword.strip().toLowerCase(Locale.ROOT);
        return places.values().stream()
                .filter(place -> (place.name() + place.detail()).toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    public RouteResponse findRoute(long startId, long endId, RouteOption option) {
        if (!places.containsKey(startId) || !places.containsKey(endId)) {
            throw new IllegalArgumentException("존재하지 않는 장소입니다.");
        }

        Map<Long, Integer> cost = new HashMap<>();
        Map<Long, Edge> previous = new HashMap<>();
        PriorityQueue<NodeCost> queue = new PriorityQueue<>(Comparator.comparingInt(NodeCost::cost));
        places.keySet().forEach(id -> cost.put(id, Integer.MAX_VALUE));
        cost.put(startId, 0);
        queue.add(new NodeCost(startId, 0));

        while (!queue.isEmpty()) {
            NodeCost current = queue.poll();
            if (current.cost() != cost.get(current.nodeId())) continue;
            if (current.nodeId() == endId) break;

            for (Edge edge : graph.getOrDefault(current.nodeId(), List.of())) {
                int edgeCost = calculateCost(edge, option);
                if (edgeCost == Integer.MAX_VALUE) continue;
                int candidate = current.cost() + edgeCost;
                if (candidate < cost.get(edge.to())) {
                    cost.put(edge.to(), candidate);
                    previous.put(edge.to(), edge);
                    queue.add(new NodeCost(edge.to(), candidate));
                }
            }
        }

        if (!previous.containsKey(endId) && startId != endId) {
            throw new IllegalStateException("선택한 조건으로 이동할 수 있는 경로가 없습니다.");
        }

        LinkedList<Edge> edges = new LinkedList<>();
        for (long cursor = endId; cursor != startId;) {
            Edge edge = previous.get(cursor);
            edges.addFirst(edge);
            cursor = edge.from();
        }

        List<Place> routeNodes = new ArrayList<>();
        routeNodes.add(places.get(startId));
        edges.forEach(edge -> routeNodes.add(places.get(edge.to())));
        int distance = edges.stream().mapToInt(Edge::distance).sum();
        int seconds = edges.stream().mapToInt(Edge::seconds).sum();
        int indoorDistance = edges.stream().filter(Edge::indoor).mapToInt(Edge::distance).sum();
        List<Segment> segments = edges.stream()
                .map(edge -> new Segment(edge.from(), edge.to(), edge.instruction(), edge.distance(),
                        edge.seconds(), edge.pathType(), edge.indoor()))
                .toList();

        return new RouteResponse(distance, seconds,
                distance == 0 ? 0 : Math.round(indoorDistance * 100.0 / distance) / 100.0,
                option, routeNodes, segments);
    }

    public RouteReportResponse createReport(RouteReportRequest request) {
        return new RouteReportResponse(reportSequence.incrementAndGet(), "RECEIVED");
    }

    private int calculateCost(Edge edge, RouteOption option) {
        if (option == RouteOption.ACCESSIBLE && (!edge.wheelchairAccessible() || edge.stairs())) {
            return Integer.MAX_VALUE;
        }
        if (option == RouteOption.INDOOR && !edge.indoor()) return edge.seconds() + 180;
        return edge.seconds();
    }

    private record NodeCost(long nodeId, int cost) {}
}
