package com.example.vehicleservice;

import com.example.vehicleservice.dev.AdminController;
import com.example.vehicleservice.dev.DevDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garantie de sûreté « prod » : sans {@code app.dev-tools.enabled=true} (valeur par défaut), les
 * beans d'administration ne sont pas créés — les endpoints {@code /api/admin/**} n'existent pas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VehicleDevToolsDisabledIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void adminBeansAbsent_whenDevToolsDisabled() {
        assertThat(context.getBeanNamesForType(AdminController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DevDataService.class)).isEmpty();
    }
}
