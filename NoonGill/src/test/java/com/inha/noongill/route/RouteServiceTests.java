package com.inha.noongill.route;

import org.junit.jupiter.api.Test;

import static com.inha.noongill.route.RouteModels.RouteOption;
import static org.assertj.core.api.Assertions.assertThat;

class RouteServiceTests {
    private final RouteService service = new RouteService();

    @Test
    void findsIndoorRouteAcrossConnectedBuildings() {
        var route = service.findRoute(2, 6, RouteOption.INDOOR);
        assertThat(route.nodes()).extracting("name")
                .containsExactly("명신관", "명신관 중앙 로비", "명신관 2층 계단", "순헌관 연결통로", "순헌관");
        assertThat(route.indoorRatio()).isEqualTo(1.0);
    }

    @Test
    void accessibleRouteAvoidsStairs() {
        var route = service.findRoute(2, 6, RouteOption.ACCESSIBLE);
        assertThat(route.segments()).extracting("pathType").contains("ELEVATOR").doesNotContain("STAIRS");
    }
}
