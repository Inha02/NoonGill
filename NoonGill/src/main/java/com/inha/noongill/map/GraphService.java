package com.inha.noongill.map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.inha.noongill.map.MapDtos.*;

@Service
@RequiredArgsConstructor
public class GraphService {
    private final RouteNodeRepository nodeRepository;
    private final RouteEdgeRepository edgeRepository;
    private final AtomicReference<Graph> graph = new AtomicReference<>(new Graph(Map.of(), Map.of(), Map.of()));

    public record Arc(RouteEdge edge, long from, long to) {}
    public record Graph(Map<Long, RouteNode> nodes, Map<Long, RouteEdge> edges, Map<Long, List<Arc>> adjacency) {}
    private record QueueEntry(long nodeId, double cost) {}
    private record Previous(long nodeId, long edgeId) {}

    @PostConstruct
    public void reload() {
        Map<Long, RouteNode> nodes = new HashMap<>();
        nodeRepository.findAllActive().forEach(node -> nodes.put(node.getId(), node));
        Map<Long, RouteEdge> edges = new HashMap<>();
        Map<Long, List<Arc>> adjacency = new HashMap<>();
        edgeRepository.findAllActive().forEach(edge -> {
            edges.put(edge.getId(), edge);
            adjacency.computeIfAbsent(edge.getStartNode().getId(), ignored -> new ArrayList<>())
                    .add(new Arc(edge, edge.getStartNode().getId(), edge.getEndNode().getId()));
            if (edge.isBidirectional()) {
                adjacency.computeIfAbsent(edge.getEndNode().getId(), ignored -> new ArrayList<>())
                        .add(new Arc(edge, edge.getEndNode().getId(), edge.getStartNode().getId()));
            }
        });
        graph.set(new Graph(Map.copyOf(nodes), Map.copyOf(edges),
                adjacency.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))));
    }

    public RouteResponse route(Set<Long> sources, Set<Long> destinations, RouteType type) {
        Graph snapshot = graph.get();
        Map<Long, Double> distance = new HashMap<>();
        Map<Long, Previous> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::cost));
        sources.forEach(id -> { distance.put(id, 0d); queue.add(new QueueEntry(id, 0)); });
        Long reached = null;
        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            if (current.cost() > distance.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY)) continue;
            if (destinations.contains(current.nodeId())) { reached = current.nodeId(); break; }
            for (Arc arc : snapshot.adjacency().getOrDefault(current.nodeId(), List.of())) {
                double candidate = current.cost() + weight(arc.edge(), type);
                if (candidate < distance.getOrDefault(arc.to(), Double.POSITIVE_INFINITY)) {
                    distance.put(arc.to(), candidate);
                    previous.put(arc.to(), new Previous(arc.from(), arc.edge().getId()));
                    queue.add(new QueueEntry(arc.to(), candidate));
                }
            }
        }
        if (reached == null) throw new IllegalStateException("선택한 조건으로 이동할 수 있는 경로가 없습니다.");

        LinkedList<Long> nodeIds = new LinkedList<>();
        LinkedList<Long> edgeIds = new LinkedList<>();
        for (long cursor = reached;;) {
            nodeIds.addFirst(cursor);
            Previous step = previous.get(cursor);
            if (step == null) break;
            edgeIds.addFirst(step.edgeId());
            cursor = step.nodeId();
        }
        List<RoutePointResponse> points = nodeIds.stream().map(id -> point(snapshot.nodes().get(id))).toList();
        List<RouteSegmentResponse> segments = new ArrayList<>();
        double totalDistance = 0, totalSeconds = 0, indoorDistance = 0;
        for (int i = 0; i < edgeIds.size(); i++) {
            RouteEdge edge = snapshot.edges().get(edgeIds.get(i));
            long from = nodeIds.get(i), to = nodeIds.get(i + 1);
            totalDistance += edge.getDistanceMeters();
            totalSeconds += duration(edge);
            if (edge.isIndoor()) indoorDistance += edge.getDistanceMeters();
            segments.add(new RouteSegmentResponse(edge.getId(), from, to,
                    instruction(edge, snapshot.nodes().get(from), snapshot.nodes().get(to)),
                    edge.getDistanceMeters(), duration(edge),
                    edge.getPathType(), edge.isIndoor()));
        }
        return new RouteResponse(type, totalDistance, totalSeconds,
                totalDistance == 0 ? 0 : indoorDistance / totalDistance, points, segments);
    }

    public Set<Long> nearest(double latitude, double longitude, Integer floor) {
        return graph.get().nodes().values().stream()
                .filter(node -> floor == null || node.getFloor() == null || floor.equals(node.getFloor()))
                .min(Comparator.comparingDouble(node -> haversine(latitude, longitude, node.getLatitude(), node.getLongitude())))
                .map(node -> Set.of(node.getId()))
                .orElseThrow(() -> new IllegalStateException("경로 노드가 없습니다."));
    }

    public Set<Long> buildingNodes(long buildingId, Integer floor) {
        Set<Long> ids = new HashSet<>();
        for (RouteNode node : graph.get().nodes().values()) {
            boolean sameBuilding = node.getBuilding() != null
                    && node.getBuilding().getId().equals(buildingId);
            boolean requestedFloor = floor == null
                    ? node.getNodeType() == RouteNode.NodeType.ENTRANCE
                        || node.getNodeType() == RouteNode.NodeType.DOOR
                        || node.getNodeType() == RouteNode.NodeType.CONNECTOR
                    : floor.equals(node.getFloor());
            if (sameBuilding && requestedFloor) {
                ids.add(node.getId());
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(floor == null
                    ? "건물과 연결된 경로 노드가 없습니다."
                    : "선택한 건물 층에 경로 노드가 없습니다.");
        }
        return ids;
    }

    private double weight(RouteEdge edge, RouteType type) {
        double duration = duration(edge);
        if (type == RouteType.ACCESSIBLE && (!edge.isWheelchairAccessible() || edge.getStairCount() > 0))
            return Double.POSITIVE_INFINITY;
        if (type == RouteType.RAIN_FREE) return duration + duration * edge.getRainExposure() * 4;
        return duration;
    }
    private double duration(RouteEdge edge) {
        return edge.getDurationSeconds() > 0 ? edge.getDurationSeconds() : edge.getDistanceMeters() / 1.3;
    }
    private RoutePointResponse point(RouteNode node) {
        return new RoutePointResponse(node.getId(), node.getName(), node.getLatitude(), node.getLongitude(),
                node.getFloor(), node.getBuilding() == null ? null : node.getBuilding().getId(),
                node.getNodeType(), node.getIndoorX(), node.getIndoorY());
    }
    private String instruction(RouteEdge edge, RouteNode source, RouteNode destination) {
        return switch (edge.getPathType()) {
            case STAIRS -> destination.getFloor() + "층으로 계단을 이용하세요.";
            case ELEVATOR -> destination.getFloor() + "층으로 엘리베이터를 이용하세요.";
            case BUILDING_CONNECTION -> "건물 연결통로를 이용하세요.";
            case CORRIDOR -> source.getName() + " → " + destination.getName();
            case COVERED_PATH -> "지붕이 있는 통로를 따라 이동하세요.";
            case ENTRANCE -> destination.getName() + "(으)로 들어가세요.";
            default -> destination.getName() + " 방향으로 이동하세요.";
        };
    }
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double p = Math.PI / 180, a = 0.5 - Math.cos((lat2-lat1)*p)/2
                + Math.cos(lat1*p)*Math.cos(lat2*p)*(1-Math.cos((lon2-lon1)*p))/2;
        return 12_742_000 * Math.asin(Math.sqrt(a));
    }
}
