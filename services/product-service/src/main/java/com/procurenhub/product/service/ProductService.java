package com.procurehub.product.service;

import com.procurehub.product.dto.CreateProductRequest;
import com.procurehub.product.dto.ProductResponse;
import com.procurehub.product.dto.UpdateProductRequest;
import com.procurehub.product.exception.NotFoundException;
import com.procurehub.product.model.ProductDocument;
import com.procurehub.product.repository.ProductRepository;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public ProductResponse create(CreateProductRequest request) {
        ProductDocument product = new ProductDocument();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setPublished(false);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(repository.save(product));
    }

    public ProductResponse update(String id, UpdateProductRequest request) {
        ProductDocument product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(repository.save(product));
    }

    public void delete(String id) {
        ProductDocument product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        repository.delete(product);
    }

    public ProductResponse setPublished(String id, boolean published) {
        ProductDocument product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        product.setPublished(published);
        product.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(product));
    }

    public ProductResponse getById(String id) {
        ProductDocument product = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return toResponse(product);
    }

    public List<ProductResponse> search(String text, String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean published) {
        List<Criteria> criteria = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(text, "i"),
                    Criteria.where("description").regex(text, "i")
            ));
        }

        if (category != null && !category.isBlank()) {
            criteria.add(Criteria.where("category").is(category));
        }

        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (minPrice != null) {
                priceCriteria = priceCriteria.gte(new Decimal128(minPrice));
            }
            if (maxPrice != null) {
                priceCriteria = priceCriteria.lte(new Decimal128(maxPrice));
            }
            criteria.add(priceCriteria);
        }

        if (published != null) {
            criteria.add(Criteria.where("published").is(published));
        }

        Query query = new Query();
        for (Criteria c : criteria) {
            query.addCriteria(c);
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        return mongoTemplate.find(query, ProductDocument.class)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductResponse toResponse(ProductDocument product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setCategory(product.getCategory());
        response.setPrice(product.getPrice());
        response.setPublished(product.isPublished());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}
