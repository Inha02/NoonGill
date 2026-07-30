package com.inha.noongill.map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static com.inha.noongill.map.MapDtos.*;

@Service
@RequiredArgsConstructor
public class AdminMapService {
    private final BuildingRepository buildingRepository;
    private final RouteNodeRepository nodeRepository;
    private final RouteEdgeRepository edgeRepository;
    private final GraphService graphService;

    @Transactional
    public void publish(MapChangesRequest request) {
        Map<Long, Building> resolvedBuildings = new HashMap<>();
        Set<Building> savedBuildings = new HashSet<>();
        if (request.buildings() != null) {
            for (BuildingChange change : request.buildings()) {
                Building building = change.id() != null && change.id() > 0
                        ? buildingRepository.findById(change.id()).orElseThrow() : new Building();
                building.setName(change.name());
                building.setDetail(change.detail());
                building.setLatitude(change.latitude());
                building.setLongitude(change.longitude());
                building.setFloorCount(Math.max(1, change.floorCount()));
                buildingRepository.save(building);
                savedBuildings.add(building);
                if (change.id() != null) resolvedBuildings.put(change.id(), building);
                resolvedBuildings.put(building.getId(), building);
            }
        }
        savedBuildings.forEach(this::syncVirtualFloorNodes);
        nodeRepository.flush();
        if (request.deletedEdgeIds() != null) {
            request.deletedEdgeIds().forEach(id -> edgeRepository.findById(id).ifPresent(edge -> edge.setActive(false)));
        }
        if (request.deletedNodeIds() != null) {
            request.deletedNodeIds().forEach(id -> nodeRepository.findById(id).ifPresent(node -> node.setActive(false)));
        }
        Map<Long, RouteNode> resolvedNodes = new HashMap<>();
        if (request.nodes() != null) {
            for (NodeChange change : request.nodes()) {
                if (change.virtualNode()) continue;
                RouteNode node = change.id() != null && change.id() > 0
                        ? nodeRepository.findById(change.id()).orElseThrow() : new RouteNode();
                node.setName(change.name());
                node.setLatitude(change.latitude());
                node.setLongitude(change.longitude());
                node.setFloor(change.floor());
                node.setNodeType(change.nodeType());
                node.setBuilding(change.buildingId() == null ? null
                        : resolveBuilding(change.buildingId(), resolvedBuildings));
                node.setIndoorX(change.indoorX());
                node.setIndoorY(change.indoorY());
                node.setVirtualNode(false);
                node.setActive(true);
                nodeRepository.save(node);
                if (change.id() != null) resolvedNodes.put(change.id(), node);
                resolvedNodes.put(node.getId(), node);
            }
        }
        if (request.edges() != null) {
            for (EdgeChange change : request.edges()) {
                RouteEdge edge = change.id() != null && change.id() > 0
                        ? edgeRepository.findById(change.id()).orElseThrow() : new RouteEdge();
                edge.setStartNode(resolveNode(change.startNodeId(), resolvedNodes));
                edge.setEndNode(resolveNode(change.endNodeId(), resolvedNodes));
                boolean sameBuilding = edge.getStartNode().getBuilding() != null
                        && edge.getEndNode().getBuilding() != null
                        && edge.getStartNode().getBuilding().getId()
                        .equals(edge.getEndNode().getBuilding().getId());
                edge.setPathType(change.pathType());
                edge.setDistanceMeters(change.distanceMeters());
                edge.setDurationSeconds(change.durationSeconds());
                edge.setIndoor(sameBuilding || change.indoor());
                edge.setRainExposure(sameBuilding ? 0 : change.rainExposure());
                edge.setStairCount(change.stairCount());
                edge.setWheelchairAccessible(change.wheelchairAccessible());
                edge.setBidirectional(change.bidirectional());
                boolean connectionNodes = edge.getStartNode().getNodeType() == RouteNode.NodeType.CONNECTOR
                        && edge.getEndNode().getNodeType() == RouteNode.NodeType.CONNECTOR;
                edge.setConnectionFloors(connectionNodes
                        ? serializeFloors(change.connectionFloors())
                        : "");
                if (connectionNodes && change.connectionFloors() != null
                        && !change.connectionFloors().isEmpty()) {
                    edge.setPathType(RouteEdge.PathType.BUILDING_CONNECTION);
                    edge.setIndoor(true);
                    edge.setRainExposure(0);
                }
                edge.setActive(true);
                edgeRepository.save(edge);
            }
        }
        edgeRepository.flush();
        nodeRepository.flush();
        if (request.deletedBuildingIds() != null) {
            request.deletedBuildingIds().forEach(buildingRepository::deleteById);
            buildingRepository.flush();
        }
        graphService.reload();
    }

    private String serializeFloors(List<Integer> floors) {
        if (floors == null) return "";
        return floors.stream()
                .filter(Objects::nonNull)
                .filter(floor -> floor > 0)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private RouteNode resolveNode(long id, Map<Long, RouteNode> resolved) {
        RouteNode node = resolved.get(id);
        return node != null ? node : nodeRepository.findById(id).orElseThrow();
    }

    private Building resolveBuilding(long id, Map<Long, Building> resolved) {
        Building building = resolved.get(id);
        return building != null ? building : buildingRepository.findById(id).orElseThrow();
    }

    private void syncVirtualFloorNodes(Building building) {
        List<RouteNode> virtualNodes = nodeRepository.findByBuildingId(building.getId()).stream()
                .filter(RouteNode::isVirtualNode)
                .toList();
        Map<Integer, RouteNode> byFloor = new HashMap<>();
        virtualNodes.stream()
                .filter(node -> node.getFloor() != null)
                .forEach(node -> byFloor.put(node.getFloor(), node));

        for (int floor = 1; floor <= building.getFloorCount(); floor++) {
            RouteNode node = byFloor.getOrDefault(floor, new RouteNode());
            node.setName(building.getName() + " " + floor + "층");
            node.setLatitude(building.getLatitude());
            node.setLongitude(building.getLongitude());
            node.setFloor(floor);
            node.setNodeType(RouteNode.NodeType.LOBBY);
            node.setBuilding(building);
            node.setIndoorX(null);
            node.setIndoorY(null);
            node.setVirtualNode(true);
            node.setActive(true);
            nodeRepository.save(node);
        }
        virtualNodes.stream()
                .filter(node -> node.getFloor() == null || node.getFloor() > building.getFloorCount())
                .forEach(node -> node.setActive(false));
    }

    @Transactional
    public void deleteBuilding(long buildingId) {
        Building building = buildingRepository.findById(buildingId).orElseThrow();
        List<RouteNode> linkedNodes = nodeRepository.findByBuildingId(buildingId);
        linkedNodes.forEach(node -> {
            if (node.isVirtualNode()) node.setActive(false);
            node.setBuilding(null);
        });
        nodeRepository.saveAllAndFlush(linkedNodes);
        buildingRepository.delete(building);
        buildingRepository.flush();
        graphService.reload();
    }
}
