package com.wms.controller.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // [추가] 응답을 ResponseEntity로 감싸려고
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wms.model.dto.product.ProductDto;
import com.wms.service.ProductService;

@RestController
public class ProductController {
    @Autowired
    private ProductService productService;


    // 품목 등록 — 새 품목 번호를 돌려준다
    // [변경] 반환 boolean → ResponseEntity<Integer> (새 PK 번호), System.out.println 삭제
    @PostMapping("/wms/product")
    public ResponseEntity<Integer> 상품등록(@RequestBody ProductDto productDto) {
        return ResponseEntity.ok(productService.상품등록(productDto));
    }

    // [변경] 반환 List<ProductDto> → ResponseEntity<List<ProductDto>>
    @GetMapping("/wms/products")
    public ResponseEntity<List<ProductDto>> 상품전체조회(
        @RequestParam(name = "tenantId", required = false) Integer tenantId) {   // [변경]
    return ResponseEntity.ok(productService.상품전체조회(tenantId));
}

    // 확인할 때 http://localhost:8080/wms/product/detail?productid=1
    // 탤런트 작성할 때 http://localhost:8080/wms/product/detail?productid=1
    @GetMapping("/wms/product/detail")
    public ResponseEntity<ProductDto> 상품개별조회(
            @RequestParam(name = "productid") int productid) {
        return ResponseEntity.ok(productService.상품개별조회(productid));
    }

    // [변경] 반환 boolean → ResponseEntity<Boolean>
    @PutMapping("/wms/product")
    public ResponseEntity<Boolean> 상품수정(@RequestBody ProductDto productDto) {
        return ResponseEntity.ok(productService.상품수정(productDto));
    }
}