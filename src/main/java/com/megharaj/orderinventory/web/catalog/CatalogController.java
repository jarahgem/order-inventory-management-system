package com.megharaj.orderinventory.web.catalog;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<CatalogItemView> listProducts() {
        return catalogService.listCatalog();
    }

    @GetMapping("/{productId}")
    public CatalogItemView getProduct(@PathVariable UUID productId) {
        return catalogService.getCatalogItem(productId);
    }

    @PostMapping
    public ResponseEntity<CatalogItemView> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        CatalogItemView created = catalogService.createProduct(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{productId}")
                .buildAndExpand(created.productId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{productId}/restocks")
    public CatalogItemView restock(
            @PathVariable UUID productId,
            @Valid @RequestBody RestockRequest request) {
        return catalogService.restock(productId, request.quantity());
    }
}
