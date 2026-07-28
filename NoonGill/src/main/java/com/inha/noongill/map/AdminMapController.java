package com.inha.noongill.map;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.inha.noongill.map.MapDtos.MapChangesRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/map")
public class AdminMapController {
    private final AdminMapService adminMapService;

    @PostMapping({"/changes", "/publish"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@Valid @RequestBody MapChangesRequest request) {
        adminMapService.publish(request);
    }

    @DeleteMapping("/buildings/{buildingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuilding(@PathVariable long buildingId) {
        adminMapService.deleteBuilding(buildingId);
    }
}
