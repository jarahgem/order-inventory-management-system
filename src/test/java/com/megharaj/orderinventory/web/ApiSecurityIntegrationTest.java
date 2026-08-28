package com.megharaj.orderinventory.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedRequestsWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsClientRoleFromCatalogWritesWithForbidden() throws Exception {
        String body = """
                {"sku":"SEC-1","name":"Secured","unitPrice":1.00,"initialQuantity":1}
                """;
        mockMvc.perform(post("/api/v1/products")
                        .with(user("client").roles("ORDER_CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsClientRoleFromActuatorMetricsWithForbidden() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .with(user("client").roles("ORDER_CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsHealthProbeWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
