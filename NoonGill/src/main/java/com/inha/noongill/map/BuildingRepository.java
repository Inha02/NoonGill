package com.inha.noongill.map;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByNameContainingIgnoreCase(String keyword);
}
