package com.wms.controller.location;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;   // [추가]
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

    // 로케이션 등록 — 새 로케이션 번호를 돌려준다
    // [변경] boolean → ResponseEntity<Integer>, System.out.println 삭제
    @PostMapping("/wms/location")
    public ResponseEntity<Integer> 위치등록(@RequestBody LocationDto locationDto) {
        return ResponseEntity.ok(locationService.위치등록(locationDto));
    }

    // [변경] List<LocationDto> → ResponseEntity<List<LocationDto>>
    @GetMapping("/wms/locations")
    public ResponseEntity<List<LocationDto>> 위치전체조회() {
        return ResponseEntity.ok(locationService.위치전체조회());
    }

    // [변경] LocationDto → ResponseEntity<LocationDto>, 겹쳐 있던 중괄호 정리
    @GetMapping("/wms/location/detail")
    public ResponseEntity<LocationDto> 위치개별조회(
            @RequestParam(name = "locationid") int locationid) {
        return ResponseEntity.ok(locationService.위치개별조회(locationid));
    }

    // [변경] 주소 앞에 / 추가, boolean → ResponseEntity<Boolean>
    @PutMapping("/wms/location")
    public ResponseEntity<Boolean> 위치수정(@RequestBody LocationDto locationDto) {
        return ResponseEntity.ok(locationService.위치수정(locationDto));
    }
}