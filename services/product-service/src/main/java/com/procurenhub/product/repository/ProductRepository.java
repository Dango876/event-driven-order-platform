package com.procurehub.product.repository;

import com.procurehub.product.model.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {
}
