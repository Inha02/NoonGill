package com.inha.noongill.map;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RouteNodeRepository extends JpaRepository<RouteNode, Long> {
    @Query("select n from RouteNode n left join fetch n.building where n.active = true")
    List<RouteNode> findAllActive();

    @Query("select n from RouteNode n where n.building.id = :buildingId and n.active = true")
    List<RouteNode> findActiveByBuildingId(@Param("buildingId") long buildingId);

    List<RouteNode> findByBuildingId(long buildingId);
}
