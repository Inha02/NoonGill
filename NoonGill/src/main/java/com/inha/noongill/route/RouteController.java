package com.inha.noongill.route;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.inha.noongill.route.RouteModels.*;

@RestController
@RequestMapping("/api")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping("/places")
    public List<Place> places(@RequestParam(required = false) String keyword) {
        return routeService.searchPlaces(keyword);
    }

    @GetMapping("/routes")
    public RouteResponse route(
            @RequestParam long startPlaceId,
            @RequestParam long endPlaceId,
            @RequestParam(defaultValue = "FASTEST") RouteOption option
    ) {
        return routeService.findRoute(startPlaceId, endPlaceId, option);
    }

    @PostMapping("/route-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteReportResponse report(@Valid @RequestBody RouteReportRequest request) {
        return routeService.createReport(request);
    }
}
