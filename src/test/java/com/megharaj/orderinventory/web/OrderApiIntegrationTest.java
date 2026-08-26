package com.megharaj.orderinventory.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.catalog.application.CreateProductCommand;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogService catalogService;

    private CatalogItemView product;

    @BeforeEach
    void createProduct() {
        String sku = "API-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        product = catalogService.createProduct(new CreateProductCommand(
                sku, "API Test Product", new BigDecimal("10.00"), 2));
    }

    @Test
    void createsAndPaysForAnOrder() throws Exception {
        String createBody = """
                {
                  "customerReference": "api-customer",
                  "items": [
                    {"productId": "%s", "quantity": 1}
                  ]
                }
                """.formatted(product.productId());

        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "api-test-correlation")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "api-test-correlation"))
                .andExpect(jsonPath("$.status").value("INVENTORY_RESERVED"))
                .andReturn();

        String orderId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.orderId");

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "api-payment-12345")
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void returnsBadRequestForMalformedOrderIdentifier() throws Exception {
        mockMvc.perform(post("/api/v1/orders/not-a-uuid/cancellations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("malformed-request"));
    }

    @Test
    void returnsProblemDetailsForValidationAndInventoryConflicts() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation-failed"))
                .andExpect(jsonPath("$.correlationId").exists());

        String oversellBody = """
                {
                  "customerReference": "oversell-customer",
                  "items": [
                    {"productId": "%s", "quantity": 3}
                  ]
                }
                """.formatted(product.productId());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversellBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("conflict"));
    }
}
