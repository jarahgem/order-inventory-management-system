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

/**
 * HTTP adapter for product catalog and inventory administration use cases.
 *
 * <p>The controller is intentionally thin: it validates transport requests, delegates
 * business rules and transaction handling to {@link CatalogService}, and returns immutable
 * application views.
 */
@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

    private final CatalogService catalogService;

    /**
     * Creates the catalog HTTP adapter.
     *
     * @param catalogService application service that owns catalog and restock use cases
     */
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Lists every product together with its current available and reserved quantities.
     *
     * @return catalog entries ordered by SKU
     */
    @GetMapping
    public List<CatalogItemView> listProducts() {
        return catalogService.listCatalog();
    }

    /**
     * Retrieves one product and its current inventory snapshot.
     *
     * @param productId product identifier from the request path
     * @return product and inventory details
     */
    @GetMapping("/{productId}")
    public CatalogItemView getProduct(@PathVariable UUID productId) {
        return catalogService.getCatalogItem(productId);
    }

    /**
     * Creates a product and its initial inventory row in one transaction.
     *
     * @param request validated product attributes and initial available quantity
     * @return a {@code 201 Created} response containing the product and a
     *     {@code Location} header for the new resource
     */
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

    /**
     * Adds available units to an existing product.
     *
     * @param productId product identifier from the request path
     * @param request positive quantity to add
     * @return the updated product and inventory snapshot
     */
    @PostMapping("/{productId}/restocks")
    public CatalogItemView restock(
            @PathVariable UUID productId,
            @Valid @RequestBody RestockRequest request) {
        return catalogService.restock(productId, request.quantity());
    }
}
