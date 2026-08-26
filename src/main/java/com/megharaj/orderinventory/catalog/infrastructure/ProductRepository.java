package com.megharaj.orderinventory.catalog.infrastructure;

import com.megharaj.orderinventory.catalog.domain.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    List<Product> findAllByOrderBySkuAsc();
}
