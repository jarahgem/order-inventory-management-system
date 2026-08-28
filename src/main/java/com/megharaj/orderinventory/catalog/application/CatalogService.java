package com.megharaj.orderinventory.catalog.application;

import com.megharaj.orderinventory.catalog.domain.Inventory;
import com.megharaj.orderinventory.catalog.domain.Product;
import com.megharaj.orderinventory.catalog.infrastructure.InventoryRepository;
import com.megharaj.orderinventory.catalog.infrastructure.ProductRepository;
import com.megharaj.orderinventory.common.error.ConflictException;
import com.megharaj.orderinventory.common.error.InvalidRequestException;
import com.megharaj.orderinventory.common.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for product catalog queries and inventory administration.
 *
 * <p>The service owns product normalization, uniqueness checks, product/inventory consistency,
 * and transactional boundaries. Controllers should delegate these decisions rather than
 * reimplementing them.
 */
@Service
public class CatalogService {

    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("99999999999999999.99");

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    /**
     * Creates the catalog application service.
     *
     * @param productRepository product persistence gateway
     * @param inventoryRepository inventory persistence gateway
     * @param clock source of auditable timestamps
     */
    public CatalogService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            Clock clock) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    /**
     * Creates a normalized product and its initial inventory row atomically.
     *
     * @param command SKU, display name, unit price, and initial available quantity
     * @return the newly created catalog item
     * @throws InvalidRequestException when a field is missing or outside supported limits
     * @throws ConflictException when the normalized SKU already exists
     */
    @Transactional
    public CatalogItemView createProduct(CreateProductCommand command) {
        String sku = normalizeSku(command.sku());
        String name = normalizeName(command.name());
        BigDecimal unitPrice = normalizePrice(command.unitPrice());
        if (command.initialQuantity() < 0) {
            throw new InvalidRequestException("initialQuantity must not be negative");
        }
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("A product with SKU '" + sku + "' already exists");
        }

        Instant now = clock.instant();
        Product product = Product.create(UUID.randomUUID(), sku, name, unitPrice, now);
        Inventory inventory = Inventory.create(product.getId(), command.initialQuantity(), now);
        productRepository.saveAndFlush(product);
        inventoryRepository.save(inventory);
        return toView(product, inventory);
    }

    /**
     * Returns the complete catalog ordered by SKU with a consistent inventory row per product.
     *
     * @return immutable list of catalog views
     * @throws IllegalStateException when persisted product and inventory data are inconsistent
     */
    @Transactional(readOnly = true)
    public List<CatalogItemView> listCatalog() {
        List<Product> products = productRepository.findAllByOrderBySkuAsc();
        Map<UUID, Inventory> inventoryByProduct = new HashMap<>();
        inventoryRepository.findAll().forEach(item -> inventoryByProduct.put(item.getProductId(), item));

        List<CatalogItemView> result = new ArrayList<>(products.size());
        for (Product product : products) {
            Inventory inventory = inventoryByProduct.get(product.getId());
            if (inventory == null) {
                throw new IllegalStateException("Missing inventory row for product " + product.getId());
            }
            result.add(toView(product, inventory));
        }
        return List.copyOf(result);
    }

    /**
     * Retrieves one product and its current inventory snapshot.
     *
     * @param productId product identifier
     * @return catalog item view
     * @throws ResourceNotFoundException when the product does not exist
     * @throws IllegalStateException when the corresponding inventory row is missing
     */
    @Transactional(readOnly = true)
    public CatalogItemView getCatalogItem(UUID productId) {
        Product product = findProduct(productId);
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Missing inventory row for product " + productId));
        return toView(product, inventory);
    }

    /**
     * Adds units to available inventory using a database-side atomic increment.
     *
     * @param productId product to restock
     * @param quantity positive number of units to add
     * @return updated catalog item view
     * @throws InvalidRequestException when {@code quantity} is not positive
     * @throws ResourceNotFoundException when the product does not exist
     * @throws IllegalStateException when the corresponding inventory row is missing
     */
    @Transactional
    public CatalogItemView restock(UUID productId, long quantity) {
        if (quantity <= 0) {
            throw new InvalidRequestException("Restock quantity must be greater than zero");
        }
        Product product = findProduct(productId);
        if (inventoryRepository.restock(productId, quantity) != 1) {
            throw new IllegalStateException("Missing inventory row for product " + productId);
        }
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Missing inventory row for product " + productId));
        return toView(product, inventory);
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " was not found"));
    }

    private static String normalizeSku(String rawSku) {
        if (rawSku == null || rawSku.isBlank()) {
            throw new InvalidRequestException("sku must not be blank");
        }
        String sku = rawSku.trim().toUpperCase(Locale.ROOT);
        if (!SKU_PATTERN.matcher(sku).matches()) {
            throw new InvalidRequestException(
                    "sku must contain 2-64 uppercase letters, digits, underscores, or hyphens");
        }
        return sku;
    }

    private static String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new InvalidRequestException("name must not be blank");
        }
        String name = rawName.trim();
        if (name.length() > 255) {
            throw new InvalidRequestException("name must contain at most 255 characters");
        }
        return name;
    }

    private static BigDecimal normalizePrice(BigDecimal rawPrice) {
        if (rawPrice == null || rawPrice.signum() < 0) {
            throw new InvalidRequestException("unitPrice must not be negative");
        }
        try {
            BigDecimal normalized = rawPrice.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.compareTo(MAX_UNIT_PRICE) > 0) {
                throw new InvalidRequestException(
                        "unitPrice exceeds the supported maximum of " + MAX_UNIT_PRICE);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new InvalidRequestException("unitPrice must contain at most two decimal places");
        }
    }

    private static CatalogItemView toView(Product product, Inventory inventory) {
        return new CatalogItemView(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnitPrice(),
                product.isActive(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getUpdatedAt());
    }
}
