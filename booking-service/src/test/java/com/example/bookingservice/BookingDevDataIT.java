package com.example.bookingservice;

import com.example.bookingservice.dev.DevDataService;
import com.example.bookingservice.dev.dto.BookingSeedRequest;
import com.example.bookingservice.dev.dto.SeedResult;
import com.example.bookingservice.domain.BookingSettings;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.repository.BookingSettingsRepository;
import com.example.bookingservice.repository.ExamRepository;
import com.example.bookingservice.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration de l'outil de dev booking (seed / reset) sur une vraie base Postgres.
 * Couvre notamment la régression « un re-seed ne doit pas écraser les paramètres modifiés ».
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BookingDevDataIT {

    @Autowired
    private DevDataService devDataService;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private BookingSettingsRepository settingsRepository;

    private static BookingSeedRequest request() {
        Map<String, String> users = new LinkedHashMap<>();
        for (String key : List.of("u-cli-1", "u-cli-2", "u-cli-3", "u-mon-1", "u-mon-2")) {
            users.put(key, UUID.randomUUID().toString());
        }
        Map<String, String> vehicles = new LinkedHashMap<>();
        for (String key : List.of("v-1", "v-2", "v-3", "v-4")) {
            vehicles.put(key, UUID.randomUUID().toString());
        }
        return new BookingSeedRequest(users, vehicles);
    }

    @BeforeEach
    void clean() {
        devDataService.reset();
    }

    @Test
    void seed_createsSessionsAndExams_resolvingActorsAndVehicles() {
        BookingSeedRequest req = request();
        SeedResult res = devDataService.seed(req);

        // 6 seed sessions + 2 seed exams.
        assertThat(res.created()).isEqualTo(8);
        assertThat(sessionRepository.count()).isEqualTo(6);
        assertThat(examRepository.count()).isEqualTo(2);
        // The CODE session has no monitor and no vehicle.
        long codeWithNulls = sessionRepository.findAll().stream()
                .filter(s -> s.getType() == SessionType.CODE)
                .filter(s -> s.getMonitorId() == null && s.getVehicleId() == null)
                .count();
        assertThat(codeWithNulls).isEqualTo(1);
        // Every client id comes from the supplied map.
        assertThat(sessionRepository.findAll())
                .allSatisfy(s -> assertThat(req.users()).containsValue(s.getClientId()));
    }

    @Test
    void reseed_preservesOwnerModifiedSettings() {
        devDataService.seed(request()); // creates sessions + default settings

        BookingSettings settings = settingsRepository.findById(BookingSettings.SINGLETON_ID).orElseThrow();
        settings.setAutoValidationEnabled(true);
        settings.setCancellationNoticeHours(48);
        settingsRepository.save(settings);

        devDataService.seed(request()); // sessions already exist -> must be a no-op

        BookingSettings after = settingsRepository.findById(BookingSettings.SINGLETON_ID).orElseThrow();
        assertThat(after.isAutoValidationEnabled()).isTrue();
        assertThat(after.getCancellationNoticeHours()).isEqualTo(48);
    }

    @Test
    void reset_truncatesSessions_andRestoresDefaultSettings() {
        devDataService.seed(request());
        BookingSettings settings = settingsRepository.findById(BookingSettings.SINGLETON_ID).orElseThrow();
        settings.setAutoValidationEnabled(true);
        settings.setCancellationNoticeHours(48);
        settingsRepository.save(settings);

        devDataService.reset();

        assertThat(sessionRepository.count()).isZero();
        assertThat(examRepository.count()).isZero();
        BookingSettings after = settingsRepository.findById(BookingSettings.SINGLETON_ID).orElseThrow();
        assertThat(after.isAutoValidationEnabled()).isFalse();
        assertThat(after.getCancellationNoticeHours()).isEqualTo(24);
    }
}
