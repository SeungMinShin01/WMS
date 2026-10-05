package com.wms.controller.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/wms/product")
    public boolean 상품등록(
            @RequestBody ProductDto productDto) {
        System.out.println(productDto);
        return productService.상품등록(productDto);
    }

    @GetMapping("/wms/products")
    public List<ProductDto> 상품전체조회() {
        return productService.상품전체조회();
    }

    // 탤런트 작성할 때 http://localhost:8080/wms/product/detail?productid=1
    @GetMapping("/wms/product/detail")
    public ProductDto 상품개별조회(
            @RequestParam(name = "productid") int productid) {
        return productService.상품개별조회(productid);
    }

    @PutMapping("/wms/product")
    public boolean 상품수정(@RequestBody ProductDto productDto) {
        return productService.상품수정(productDto);
    }
}