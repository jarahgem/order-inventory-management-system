package com.megharaj.orderinventory.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds and validates the HTTP Basic user accounts used for API authentication.
 *
 * <p>At least one account must be configured; a context that starts with no accounts is a
 * misconfiguration and fails fast. Passwords are expected as BCrypt hashes so that no plaintext
 * secret is ever present in configuration or logs.
 *
 * @param users configured accounts, each with a username, BCrypt password hash, and roles
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(@NotEmpty List<@Valid UserAccount> users) {

    /**
     * A single API account.
     *
     * @param username login name supplied via HTTP Basic
     * @param password BCrypt password hash (never plaintext)
     * @param roles granted roles without the {@code ROLE_} prefix (for example {@code ADMIN})
     */
    public record UserAccount(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty List<@NotBlank String> roles) {
    }
}
