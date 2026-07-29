package com.inha.noongill.map;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static com.inha.noongill.map.MapDtos.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MapController {
    private final BuildingRepository buildingRepository;
    private final RouteNodeRepository nodeRepository;
    private final RouteEdgeRepository edgeRepository;
    private final GraphService graphService;

    @GetMapping("/buildings")
    public List<BuildingResponse> buildings(@RequestParam(required = false) String keyword) {
        List<Building> values = keyword == null || keyword.isBlank()
                ? buildingRepository.findAll() : buildingRepository.findByNameContainingIgnoreCase(keyword);
        return values.stream().map(this::building).toList();
    }

    @PostMapping("/routes/search")
    public List<RouteResponse> routes(@Valid @RequestBody RouteSearchRequest request) {
        Set<Long> sources = resolve(request.start());
        Set<Long> destinations = resolve(request.destination());
        return request.routeTypes().stream().map(type -> graphService.route(sources, destinations, type)).toList();
    }

    @GetMapping({"/map/data", "/admin/map/data"})
    public MapDataResponse mapData() {
        return new MapDataResponse(
                buildingRepository.findAll().stream().map(this::building).toList(),
                nodeRepository.findAllActive().stream().map(this::node).toList(),
                edgeRepository.findAllActive().stream().map(this::edge).toList());
    }

    private Set<Long> resolve(RouteLocationRequest location) {
        return switch (location.type()) {
            case POINT -> graphService.nearest(
                    Objects.requireNonNull(location.latitude()), Objects.requireNonNull(location.longitude()), location.floor());
            case BUILDING -> graphService.buildingNodes(
                    Objects.requireNonNull(location.buildingId()), location.floor());
            case NODE -> Set.of(Objects.requireNonNull(location.nodeId()));
        };
    }
    private BuildingResponse building(Building value) {
        return new BuildingResponse(value.getId(), value.getName(), value.getDetail(),
                value.getLatitude(), value.getLongitude(), value.getFloorCount());
    }
    private NodeResponse node(RouteNode value) {
        return new NodeResponse(value.getId(), value.getName(), value.getLatitude(), value.getLongitude(), value.getFloor(),
                value.getNodeType(), value.getBuilding() == null ? null : value.getBuilding().getId(),
                value.getIndoorX(), value.getIndoorY());
    }
    private EdgeResponse edge(RouteEdge value) {
        return new EdgeResponse(value.getId(), value.getStartNode().getId(), value.getEndNode().getId(),
                value.getPathType(), value.getDistanceMeters(), value.getDurationSeconds(), value.isIndoor(),
                value.getRainExposure(), value.getStairCount(), value.isWheelchairAccessible(), value.isBidirectional());
    }
}
