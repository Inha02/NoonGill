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
    private record BuildingEndpoint(Set<Long> nodeIds, RoutePointResponse requestedPoint,
                                    Integer requestedFloor) {}

    @PostConstruct
    public void reload() {
        Map<Long, RouteNode> nodes = new HashMap<>();
        nodeRepository.findAllActive().forEach(node -> nodes.put(node.getId(), node));
        Map<Long, RouteEdge> edges = new HashMap<>();
        Map<Long, List<Arc>> adjacency = new HashMap<>();
        edgeRepository.findAllActive().forEach(edge -> {
            addGraphEdge(edges, adjacency, edge);
        });
        addAutomaticBuildingEdges(nodes, edges, adjacency);
        graph.set(new Graph(Map.copyOf(nodes), Map.copyOf(edges),
                adjacency.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))));
    }

    private void addAutomaticBuildingEdges(
            Map<Long, RouteNode> nodes,
            Map<Long, RouteEdge> edges,
            Map<Long, List<Arc>> adjacency) {
        List<RouteEdge> configuredConnections = edges.values().stream()
                .filter(edge -> edge.getConnectionFloors() != null
                        && !edge.getConnectionFloors().isBlank())
                .toList();
        Map<Long, List<RouteNode>> nodesByBuilding = new HashMap<>();
        Map<Long, Map<Integer, RouteNode>> virtualNodesByBuilding = new HashMap<>();
        nodes.values().stream()
                .filter(node -> node.getBuilding() != null && node.getFloor() != null)
                .forEach(node -> nodesByBuilding
                        .computeIfAbsent(node.getBuilding().getId(), ignored -> new ArrayList<>())
                        .add(node));

        long syntheticEdgeId = -1;
        for (List<RouteNode> buildingNodes : nodesByBuilding.values()) {
            Map<Integer, RouteNode> virtualByFloor = new TreeMap<>();
            buildingNodes.stream()
                    .filter(RouteNode::isVirtualNode)
                    .forEach(node -> virtualByFloor.put(node.getFloor(), node));
            if (!buildingNodes.isEmpty()) {
                virtualNodesByBuilding.put(
                        buildingNodes.getFirst().getBuilding().getId(), virtualByFloor);
            }

            List<RouteNode> virtualFloors = new ArrayList<>(virtualByFloor.values());
            for (int i = 1; i < virtualFloors.size(); i++) {
                RouteNode lower = virtualFloors.get(i - 1);
                RouteNode upper = virtualFloors.get(i);
                int floorDifference = Math.max(1, upper.getFloor() - lower.getFloor());
                RouteEdge edge = automaticEdge(
                        syntheticEdgeId--, lower, upper, RouteEdge.PathType.ELEVATOR,
                        floorDifference * 4.0, floorDifference * 20.0);
                addGraphEdge(edges, adjacency, edge);
            }

            for (RouteNode node : buildingNodes) {
                if (node.isVirtualNode()) continue;
                RouteNode floorNode = virtualByFloor.get(node.getFloor());
                if (floorNode == null) continue;
                RouteEdge edge = automaticEdge(
                        syntheticEdgeId--, node, floorNode, RouteEdge.PathType.CORRIDOR, 1, 1);
                addGraphEdge(edges, adjacency, edge);
            }
        }

        for (RouteEdge configured : configuredConnections) {
            RouteNode start = configured.getStartNode();
            RouteNode end = configured.getEndNode();
            if (start.getNodeType() != RouteNode.NodeType.CONNECTOR
                    || end.getNodeType() != RouteNode.NodeType.CONNECTOR
                    || start.getBuilding() == null || end.getBuilding() == null) {
                continue;
            }
            Map<Integer, RouteNode> startFloors =
                    virtualNodesByBuilding.getOrDefault(start.getBuilding().getId(), Map.of());
            Map<Integer, RouteNode> endFloors =
                    virtualNodesByBuilding.getOrDefault(end.getBuilding().getId(), Map.of());
            for (Integer floor : parseConnectionFloors(configured.getConnectionFloors())) {
                RouteNode floorStart = startFloors.get(floor);
                RouteNode floorEnd = endFloors.get(floor);
                if (floorStart == null || floorEnd == null) continue;
                RouteEdge edge = automaticEdge(
                        syntheticEdgeId--, floorStart, floorEnd,
                        RouteEdge.PathType.BUILDING_CONNECTION,
                        configured.getDistanceMeters(), configured.getDurationSeconds());
                edge.setBidirectional(configured.isBidirectional());
                edge.setWheelchairAccessible(configured.isWheelchairAccessible());
                addGraphEdge(edges, adjacency, edge);
            }
        }
    }

    private List<Integer> parseConnectionFloors(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Integer::parseInt)
                .filter(floor -> floor > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private RouteEdge automaticEdge(
            long id, RouteNode start, RouteNode end, RouteEdge.PathType pathType,
            double distanceMeters, double durationSeconds) {
        RouteEdge edge = new RouteEdge(
                start, end, pathType, distanceMeters, durationSeconds,
                true, 0, 0, true);
        edge.setId(id);
        edge.setBidirectional(true);
        edge.setActive(true);
        return edge;
    }

    private void addGraphEdge(
            Map<Long, RouteEdge> edges,
            Map<Long, List<Arc>> adjacency,
            RouteEdge edge) {
        edges.put(edge.getId(), edge);
        adjacency.computeIfAbsent(edge.getStartNode().getId(), ignored -> new ArrayList<>())
                .add(new Arc(edge, edge.getStartNode().getId(), edge.getEndNode().getId()));
        if (edge.isBidirectional()) {
            adjacency.computeIfAbsent(edge.getEndNode().getId(), ignored -> new ArrayList<>())
                    .add(new Arc(edge, edge.getEndNode().getId(), edge.getStartNode().getId()));
        }
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
        if (reached == null) {
            return virtualFloorRoute(snapshot, sources, destinations, type)
                    .orElseThrow(() -> new IllegalStateException("선택한 조건으로 이동할 수 있는 경로가 없습니다."));
        }

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
                totalDistance == 0 ? 0 : indoorDistance / totalDistance,
                points, collapseElevatorSegments(snapshot, segments));
    }

    public RouteResponse routeBetweenBuildingFloors(
            long startBuildingId, Integer startFloor,
            long destinationBuildingId, Integer destinationFloor,
            RouteType type) {
        Graph snapshot = graph.get();
        BuildingEndpoint start = buildingEndpoint(snapshot, startBuildingId, startFloor);
        BuildingEndpoint destination = buildingEndpoint(snapshot, destinationBuildingId, destinationFloor);
        RouteResponse base = route(start.nodeIds(), destination.nodeIds(), type);

        List<RoutePointResponse> points = new ArrayList<>(base.points());
        List<RouteSegmentResponse> segments = new ArrayList<>(base.segments());
        double extraDistance = 0;
        double extraSeconds = 0;

        if (start.requestedPoint() != null && !points.isEmpty()
                && !Objects.equals(start.requestedFloor(), points.getFirst().floor())) {
            RoutePointResponse anchor = points.getFirst();
            int floorDifference = floorDifference(start.requestedFloor(), anchor.floor());
            double distance = Math.max(4, floorDifference * 4.0);
            double seconds = Math.max(20, floorDifference * 20.0);
            points.add(0, start.requestedPoint());
            segments.add(0, virtualFloorSegment(
                    start.requestedPoint(), anchor, distance, seconds, -1));
            extraDistance += distance;
            extraSeconds += seconds;
        }
        if (destination.requestedPoint() != null && !points.isEmpty()
                && !Objects.equals(destination.requestedFloor(), points.getLast().floor())) {
            RoutePointResponse anchor = points.getLast();
            int floorDifference = floorDifference(destination.requestedFloor(), anchor.floor());
            double distance = Math.max(4, floorDifference * 4.0);
            double seconds = Math.max(20, floorDifference * 20.0);
            points.add(destination.requestedPoint());
            segments.add(virtualFloorSegment(
                    anchor, destination.requestedPoint(), distance, seconds, -2));
            extraDistance += distance;
            extraSeconds += seconds;
        }

        double totalDistance = base.totalDistanceMeters() + extraDistance;
        double indoorDistance = base.totalDistanceMeters() * base.indoorRatio() + extraDistance;
        return new RouteResponse(
                type,
                totalDistance,
                base.estimatedSeconds() + extraSeconds,
                totalDistance == 0 ? 1.0 : indoorDistance / totalDistance,
                List.copyOf(points),
                collapseElevatorSegments(snapshot, segments));
    }

    private List<RouteSegmentResponse> collapseElevatorSegments(
            Graph snapshot, List<RouteSegmentResponse> segments) {
        List<RouteSegmentResponse> collapsed = new ArrayList<>();
        for (RouteSegmentResponse segment : segments) {
            RouteNode segmentSource = snapshot.nodes().get(segment.fromNodeId());
            RouteNode segmentDestination = snapshot.nodes().get(segment.toNodeId());
            boolean connectsDifferentBuildings = segmentSource != null
                    && segmentDestination != null
                    && segmentSource.getBuilding() != null
                    && segmentDestination.getBuilding() != null
                    && !segmentSource.getBuilding().getId()
                    .equals(segmentDestination.getBuilding().getId())
                    && (segmentSource.getName().contains("연결통로")
                    || segmentDestination.getName().contains("연결통로"));
            if (connectsDifferentBuildings
                    && !collapsed.isEmpty()
                    && collapsed.getLast().edgeId() < 0
                    && collapsed.getLast().pathType() == RouteEdge.PathType.CORRIDOR
                    && collapsed.getLast().toNodeId() == segment.fromNodeId()) {
                RouteSegmentResponse approach = collapsed.removeLast();
                collapsed.add(new RouteSegmentResponse(
                        segment.edgeId(),
                        approach.fromNodeId(),
                        segment.toNodeId(),
                        segmentSource.getBuilding().getName() + "에서 "
                                + segmentDestination.getBuilding().getName()
                                + " 방향 연결통로를 이용하세요.",
                        approach.distanceMeters() + segment.distanceMeters(),
                        approach.estimatedSeconds() + segment.estimatedSeconds(),
                        RouteEdge.PathType.BUILDING_CONNECTION,
                        approach.indoor() && segment.indoor()));
                continue;
            }

            boolean leavesConnectionNode = segment.edgeId() < 0
                    && segment.pathType() == RouteEdge.PathType.CORRIDOR
                    && segmentSource != null
                    && segmentSource.getName().contains("연결통로");
            if (leavesConnectionNode
                    && !collapsed.isEmpty()
                    && collapsed.getLast().pathType() == RouteEdge.PathType.BUILDING_CONNECTION
                    && collapsed.getLast().toNodeId() == segment.fromNodeId()) {
                RouteSegmentResponse connection = collapsed.removeLast();
                collapsed.add(new RouteSegmentResponse(
                        connection.edgeId(),
                        connection.fromNodeId(),
                        segment.toNodeId(),
                        connection.instruction(),
                        connection.distanceMeters() + segment.distanceMeters(),
                        connection.estimatedSeconds() + segment.estimatedSeconds(),
                        RouteEdge.PathType.BUILDING_CONNECTION,
                        connection.indoor() && segment.indoor()));
                continue;
            }

            RouteNode entranceNode = snapshot.nodes().get(segment.fromNodeId());
            RouteNode floorNode = snapshot.nodes().get(segment.toNodeId());
            boolean entersThroughEntrance = segment.edgeId() < 0
                    && segment.pathType() == RouteEdge.PathType.CORRIDOR
                    && entranceNode != null
                    && floorNode != null
                    && !entranceNode.isVirtualNode()
                    && floorNode.isVirtualNode()
                    && isEntranceName(entranceNode.getName());
            if (entersThroughEntrance
                    && !collapsed.isEmpty()
                    && collapsed.getLast().toNodeId() == segment.fromNodeId()) {
                RouteSegmentResponse approach = collapsed.removeLast();
                collapsed.add(new RouteSegmentResponse(
                        approach.edgeId(),
                        approach.fromNodeId(),
                        segment.toNodeId(),
                        entranceNode.getName() + "를 통해 들어가세요.",
                        approach.distanceMeters() + segment.distanceMeters(),
                        approach.estimatedSeconds() + segment.estimatedSeconds(),
                        approach.pathType(),
                        approach.indoor()));
                continue;
            }
            if (!collapsed.isEmpty()
                    && segment.pathType() == RouteEdge.PathType.ELEVATOR
                    && collapsed.getLast().pathType() == RouteEdge.PathType.ELEVATOR
                    && collapsed.getLast().toNodeId() == segment.fromNodeId()
                    && sameBuilding(snapshot, collapsed.getLast().fromNodeId(), segment.toNodeId())) {
                RouteSegmentResponse previous = collapsed.removeLast();
                collapsed.add(new RouteSegmentResponse(
                        previous.edgeId(),
                        previous.fromNodeId(),
                        segment.toNodeId(),
                        segment.instruction(),
                        previous.distanceMeters() + segment.distanceMeters(),
                        previous.estimatedSeconds() + segment.estimatedSeconds(),
                        RouteEdge.PathType.ELEVATOR,
                        previous.indoor() && segment.indoor()));
            } else {
                collapsed.add(segment);
            }
        }
        return List.copyOf(collapsed);
    }

    private boolean isEntranceName(String name) {
        return name != null && (name.contains("입구")
                || name.contains("정문")
                || name.contains("후문"));
    }

    private boolean sameBuilding(Graph snapshot, long firstNodeId, long secondNodeId) {
        RouteNode first = snapshot.nodes().get(firstNodeId);
        RouteNode second = snapshot.nodes().get(secondNodeId);
        if (first == null || second == null
                || first.getBuilding() == null || second.getBuilding() == null) {
            return false;
        }
        return first.getBuilding().getId().equals(second.getBuilding().getId());
    }

    private BuildingEndpoint buildingEndpoint(Graph snapshot, long buildingId, Integer floor) {
        List<RouteNode> buildingNodes = snapshot.nodes().values().stream()
                .filter(node -> node.getBuilding() != null
                        && node.getBuilding().getId().equals(buildingId))
                .toList();
        if (buildingNodes.isEmpty()) {
            throw new IllegalArgumentException("건물과 연결된 경로 노드가 없습니다.");
        }
        if (floor == null) {
            return new BuildingEndpoint(buildingNodes(buildingId, null), null, null);
        }

        List<RouteNode> exactFloorNodes = buildingNodes.stream()
                .filter(node -> floor.equals(node.getFloor()))
                .toList();
        Set<Long> connectedExactNodeIds = exactFloorNodes.stream()
                .filter(node -> hasConnectedEdge(snapshot, node.getId()))
                .map(RouteNode::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!connectedExactNodeIds.isEmpty()) {
            return new BuildingEndpoint(Set.copyOf(connectedExactNodeIds), null, floor);
        }

        Set<Long> connectedBuildingNodeIds = buildingNodes.stream()
                .filter(node -> hasConnectedEdge(snapshot, node.getId()))
                .map(RouteNode::getId)
                .collect(java.util.stream.Collectors.toSet());

        RouteNode requestedFloorNode = exactFloorNodes.stream().findFirst().orElse(null);
        if (connectedBuildingNodeIds.isEmpty() && requestedFloorNode != null) {
            connectedBuildingNodeIds.add(requestedFloorNode.getId());
        }
        if (connectedBuildingNodeIds.isEmpty()) {
            throw new IllegalArgumentException("건물과 연결된 경로 노드가 없습니다.");
        }

        RoutePointResponse requestedPoint;
        if (requestedFloorNode != null) {
            requestedPoint = point(requestedFloorNode);
        } else {
            RouteNode locationSource = buildingNodes.getFirst();
            long virtualNodeId = -9_000_000_000_000L - buildingId * 10_000L - floor;
            requestedPoint = new RoutePointResponse(
                    virtualNodeId,
                    locationSource.getBuilding().getName() + " " + floor + "층",
                    locationSource.getLatitude(),
                    locationSource.getLongitude(),
                    floor,
                    buildingId,
                    RouteNode.NodeType.LOBBY,
                    null,
                    null);
        }
        return new BuildingEndpoint(
                Set.copyOf(connectedBuildingNodeIds),
                requestedPoint,
                floor);
    }

    private int floorDifference(Integer requestedFloor, Integer anchorFloor) {
        int requested = requestedFloor == null ? 1 : requestedFloor;
        int anchor = anchorFloor == null ? 1 : anchorFloor;
        return Math.abs(requested - anchor);
    }

    private boolean hasConnectedEdge(Graph snapshot, long nodeId) {
        if (!snapshot.adjacency().getOrDefault(nodeId, List.of()).isEmpty()) return true;
        return snapshot.edges().values().stream().anyMatch(edge ->
                edge.getStartNode().getId().equals(nodeId) || edge.getEndNode().getId().equals(nodeId));
    }

    private RouteSegmentResponse virtualFloorSegment(
            RoutePointResponse source, RoutePointResponse destination,
            double distance, double seconds, long edgeSuffix) {
        long edgeId = -8_000_000_000_000L + edgeSuffix;
        return new RouteSegmentResponse(
                edgeId,
                source.nodeId(),
                destination.nodeId(),
                source.name() + " → " + destination.name(),
                distance,
                seconds,
                RouteEdge.PathType.ELEVATOR,
                true);
    }

    private Optional<RouteResponse> virtualFloorRoute(
            Graph snapshot, Set<Long> sources, Set<Long> destinations, RouteType type) {
        RouteNode bestSource = null;
        RouteNode bestDestination = null;
        double bestHorizontalDistance = Double.POSITIVE_INFINITY;

        for (Long sourceId : sources) {
            RouteNode source = snapshot.nodes().get(sourceId);
            if (source == null || source.getBuilding() == null || source.getFloor() == null) continue;
            for (Long destinationId : destinations) {
                RouteNode destination = snapshot.nodes().get(destinationId);
                if (destination == null || destination.getBuilding() == null || destination.getFloor() == null) continue;
                boolean sameBuilding = source.getBuilding().getId().equals(destination.getBuilding().getId());
                boolean differentFloor = !source.getFloor().equals(destination.getFloor());
                if (!sameBuilding || !differentFloor) continue;

                double horizontalDistance = haversine(
                        source.getLatitude(), source.getLongitude(),
                        destination.getLatitude(), destination.getLongitude());
                if (horizontalDistance < bestHorizontalDistance) {
                    bestHorizontalDistance = horizontalDistance;
                    bestSource = source;
                    bestDestination = destination;
                }
            }
        }

        if (bestSource == null || bestDestination == null) return Optional.empty();

        int floorDifference = Math.abs(bestSource.getFloor() - bestDestination.getFloor());
        double distanceMeters = bestHorizontalDistance + floorDifference * 4.0;
        double estimatedSeconds = Math.max(20, floorDifference * 20.0);
        long virtualEdgeId = -1_000_000_000L - bestSource.getId() * 100_000L - bestDestination.getId();
        String instruction = bestSource.getName() + " → " + bestDestination.getName();

        return Optional.of(new RouteResponse(
                type,
                distanceMeters,
                estimatedSeconds,
                1.0,
                List.of(point(bestSource), point(bestDestination)),
                List.of(new RouteSegmentResponse(
                        virtualEdgeId,
                        bestSource.getId(),
                        bestDestination.getId(),
                        instruction,
                        distanceMeters,
                        estimatedSeconds,
                        RouteEdge.PathType.ELEVATOR,
                        true))));
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
        if (type == RouteType.RAIN_FREE) return duration + duration * edge.getRainExposure() * 5;
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
        if (edge.getId() < 0 && edge.getPathType() == RouteEdge.PathType.CORRIDOR) {
            if (!source.isVirtualNode() && destination.isVirtualNode()) {
                return source.getName() + "(으)로 들어가세요.";
            }
            if (source.isVirtualNode() && !destination.isVirtualNode()) {
                return destination.getName() + " 방향으로 이동하세요.";
            }
        }
        return switch (edge.getPathType()) {
            case STAIRS -> destination.getFloor() + "층으로 계단을 이용하세요.";
            case ELEVATOR -> destination.getFloor() + "층으로 엘리베이터를 이용하세요.";
            case BUILDING_CONNECTION -> buildingConnectionInstruction(source, destination);
            case CORRIDOR -> source.getName() + " → " + destination.getName();
            case COVERED_PATH -> "지붕이 있는 통로를 따라 이동하세요.";
            case ENTRANCE -> destination.getName() + "(으)로 들어가세요.";
            default -> destination.getName() + " 방향으로 이동하세요.";
        };
    }
    private String buildingConnectionInstruction(RouteNode source, RouteNode destination) {
        if (source.getBuilding() == null || destination.getBuilding() == null) {
            return "건물 연결통로를 이용하세요.";
        }
        return source.getBuilding().getName() + "에서 "
                + destination.getBuilding().getName()
                + " 방향 연결통로를 이용하세요.";
    }
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double p = Math.PI / 180, a = 0.5 - Math.cos((lat2-lat1)*p)/2
                + Math.cos(lat1*p)*Math.cos(lat2*p)*(1-Math.cos((lon2-lon1)*p))/2;
        return 12_742_000 * Math.asin(Math.sqrt(a));
    }
}
