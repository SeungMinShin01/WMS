package com.wms.controller.location;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.location.LocationDto;
import com.wms.service.LocationService;

@RestController
public class LocationController {
    @Autowired
    private LocationService locationService;

    @PostMapping("/wms/location")
    public boolean 위치등록(
            @RequestBody LocationDto locationDto) {
        System.out.println(locationDto);
        return locationService.위치등록(locationDto);
    }

    @GetMapping("/wms/locations")
    public List<LocationDto> 위치전체조회() {
        return locationService.위치전체조회();
    }

    @GetMapping("/wms/location/detail")
    public LocationDto 위치개별조회(
            @RequestParam(name = "locationid") int locationid) {
        return locationService.위치개별조회(locationid);
        }

    @PutMapping("wms/location")
    public boolean 위치수정(@RequestBody LocationDto locationDto) {
        return locationService.위치수정(locationDto);
    }
}
