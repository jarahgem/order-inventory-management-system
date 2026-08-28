package com.megharaj.orderinventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AppSecurityPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void rejectsEmptyUserList() {
        AppSecurityProperties properties = new AppSecurityProperties(List.of());

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void rejectsAccountWithBlankPasswordOrNoRoles() {
        AppSecurityProperties blankPassword = new AppSecurityProperties(List.of(
                new AppSecurityProperties.UserAccount("admin", " ", List.of("ADMIN"))));
        AppSecurityProperties noRoles = new AppSecurityProperties(List.of(
                new AppSecurityProperties.UserAccount("admin", "hash", List.of())));

        assertThat(validator.validate(blankPassword)).isNotEmpty();
        assertThat(validator.validate(noRoles)).isNotEmpty();
    }

    @Test
    void acceptsAValidAccount() {
        AppSecurityProperties properties = new AppSecurityProperties(List.of(
                new AppSecurityProperties.UserAccount("admin", "bcrypt-hash", List.of("ADMIN"))));

        assertThat(validator.validate(properties)).isEmpty();
    }
}
