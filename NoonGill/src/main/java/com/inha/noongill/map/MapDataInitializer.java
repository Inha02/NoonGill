package com.inha.noongill.map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MapDataInitializer implements CommandLineRunner {
    private final BuildingRepository buildings;
    private final RouteNodeRepository nodes;
    private final RouteEdgeRepository edges;
    private final GraphService graphService;

    @Override @Transactional
    public void run(String... args) {
        if (buildings.count() > 0) { graphService.reload(); return; }
        Building gate = buildings.save(new Building("정문", "제1캠퍼스 정문", 37.54478, 126.96418));
        Building myeong = buildings.save(new Building("명신관", "1층 정문", 37.5458093, 126.9636153));
        Building soon = buildings.save(new Building("순헌관", "2층 연결통로", 37.5464918, 126.9647042));
        Building library = buildings.save(new Building("중앙도서관", "1층 로비", 37.5440803, 126.9660741));
        Building student = buildings.save(new Building("학생회관", "1층 로비", 37.5455117, 126.9650966));

        RouteNode n1 = node("정문", 37.54478, 126.96418, null, RouteNode.NodeType.OUTDOOR, gate);
        RouteNode n2 = node("명신관 정문", 37.54573, 126.96374, 1, RouteNode.NodeType.ENTRANCE, myeong);
        RouteNode n3 = node("명신관 1층 로비", 37.54581, 126.96362, 1, RouteNode.NodeType.LOBBY, myeong);
        RouteNode n4 = node("명신관 2층 계단", 37.54594, 126.96386, 2, RouteNode.NodeType.STAIRS, myeong);
        RouteNode n5 = node("명신관 엘리베이터", 37.54588, 126.96391, 2, RouteNode.NodeType.ELEVATOR, myeong);
        RouteNode n6 = node("순헌관 연결통로", 37.54625, 126.96430, 2, RouteNode.NodeType.CONNECTOR, soon);
        RouteNode n7 = node("순헌관 3층 로비", 37.54649, 126.96470, 3, RouteNode.NodeType.LOBBY, soon);
        RouteNode n8 = node("중앙도서관 입구", 37.54408, 126.96607, 1, RouteNode.NodeType.ENTRANCE, library);
        RouteNode n9 = node("학생회관 입구", 37.54551, 126.96510, 1, RouteNode.NodeType.ENTRANCE, student);
        edge(n1,n2,RouteEdge.PathType.OUTDOOR,110,85,false,1,0,true);
        edge(n2,n3,RouteEdge.PathType.ENTRANCE,18,16,true,0,0,true);
        edge(n3,n4,RouteEdge.PathType.STAIRS,48,55,true,0,15,false);
        edge(n3,n5,RouteEdge.PathType.ELEVATOR,62,72,true,0,0,true);
        edge(n4,n6,RouteEdge.PathType.BUILDING_CONNECTION,65,45,true,0,0,true);
        edge(n5,n6,RouteEdge.PathType.BUILDING_CONNECTION,75,52,true,0,0,true);
        edge(n6,n7,RouteEdge.PathType.CORRIDOR,42,50,true,0,0,true);
        edge(n1,n8,RouteEdge.PathType.OUTDOOR,205,160,false,1,0,true);
        edge(n2,n9,RouteEdge.PathType.COVERED_PATH,155,125,false,.25,0,true);
        graphService.reload();
    }
    private RouteNode node(String name, double lat, double lon, Integer floor, RouteNode.NodeType type, Building building) {
        return nodes.save(new RouteNode(name, lat, lon, floor, type, building));
    }
    private void edge(RouteNode a, RouteNode b, RouteEdge.PathType type, double distance, double seconds,
                      boolean indoor, double rain, int stairs, boolean accessible) {
        edges.save(new RouteEdge(a,b,type,distance,seconds,indoor,rain,stairs,accessible));
    }
}
