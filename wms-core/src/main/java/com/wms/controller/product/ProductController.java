package com.wms.controller.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                System.out.println( productDto );
        return productService.상품등록(productDto);
    }
}