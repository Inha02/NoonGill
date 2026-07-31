package com.inha.noongill.map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                ALTER TABLE buildings
                ADD COLUMN IF NOT EXISTS floor_count INTEGER NOT NULL DEFAULT 1
                """);
        jdbcTemplate.execute("""
                ALTER TABLE buildings
                ADD COLUMN IF NOT EXISTS basement_floor_count INTEGER NOT NULL DEFAULT 0
                """);
        jdbcTemplate.execute("""
                ALTER TABLE route_nodes
                ADD COLUMN IF NOT EXISTS virtual_node BOOLEAN NOT NULL DEFAULT FALSE
                """);
        jdbcTemplate.execute("""
                ALTER TABLE route_edges
                ADD COLUMN IF NOT EXISTS connection_floors VARCHAR(500) NOT NULL DEFAULT ''
                """);
        jdbcTemplate.execute("""
                ALTER TABLE route_nodes
                DROP CONSTRAINT IF EXISTS route_nodes_node_type_check
                """);
        jdbcTemplate.execute("""
                ALTER TABLE route_nodes
                ADD CONSTRAINT route_nodes_node_type_check
                CHECK (node_type IN (
                    'OUTDOOR', 'ENTRANCE', 'DOOR', 'LOBBY',
                    'CORRIDOR', 'STAIRS', 'ELEVATOR', 'CONNECTOR'
                ))
                """);
    }
}
