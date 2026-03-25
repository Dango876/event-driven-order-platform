package com.procurehub.product.api;

import com.procurehub.product.dto.CreateProductRequest;
import com.procurehub.product.dto.ProductResponse;
import com.procurehub.product.dto.UpdateProductRequest;
import com.procurehub.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable("id") String id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/publish")
    public ProductResponse setPublished(@PathVariable("id") String id, @RequestParam("published") boolean published) {
        return service.setPublished(id, published);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable("id") String id) {
        return service.getById(id);
    }

    @GetMapping
    public List<ProductResponse> search(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "published", required = false) Boolean published
    ) {
        return service.search(text, category, minPrice, maxPrice, published);
    }
}
