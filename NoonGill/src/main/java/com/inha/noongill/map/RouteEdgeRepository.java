package com.inha.noongill.map;

import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface RouteEdgeRepository extends JpaRepository<RouteEdge, Long> {
    @Query("""
            select e from RouteEdge e
            join fetch e.startNode
            join fetch e.endNode
            where e.active = true
              and e.startNode.active = true
              and e.endNode.active = true
            """)
    List<RouteEdge> findAllActive();
}
