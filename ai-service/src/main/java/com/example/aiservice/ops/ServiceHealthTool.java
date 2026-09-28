package com.example.aiservice.ops;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Outil local (non-MCP) : état de santé de chaque service, vu par Eureka puis par son propre
 * {@code /actuator/health}. Couvre « vérifie la santé des services » sans Kubernetes.
 */
@Component
public class ServiceHealthTool {

    /** Services attendus : un service absent d'Eureka est signalé NOT_REGISTERED au lieu d'être ignoré. */
    private static final List<String> EXPECTED = List.of(
            "api-gateway", "auth-service", "vehicle-service", "booking-service",
            "finance-service", "communication-service");

    private final DiscoveryClient discovery;
    private final RestClient http;
    // Sondes en parallèle : pendant une panne réseau, N services × timeout en série dépasseraient
    // vite le budget de l'investigation. Pool dédié (pas le TaskExecutor qui exécute déjà l'agent).
    private final ExecutorService probes = Executors.newFixedThreadPool(8);

    public ServiceHealthTool(DiscoveryClient discovery, RestClient.Builder builder) {
        this.discovery = discovery;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.http = builder.requestFactory(requestFactory).build();
    }

    @Tool(name = "service_health",
            description = "Eureka registration, /actuator/health status and running build (commit, build time) "
                    + "of every Auto-Ecole service.")
    public Map<String, Object> serviceHealth() {
        TreeSet<String> names = new TreeSet<>(EXPECTED);
        names.addAll(discovery.getServices());
        Map<String, List<CompletableFuture<String>>> pending = new TreeMap<>();
        for (String name : names) {
            List<ServiceInstance> instances = discovery.getInstances(name);
            pending.put(name, instances.stream()
                    .map(instance -> CompletableFuture.supplyAsync(() -> health(instance), probes))
                    .toList());
        }
        Map<String, Object> report = new TreeMap<>();
        // health() ne lève jamais : join() ne fait qu'attendre.
        pending.forEach((name, futures) -> report.put(name, futures.isEmpty()
                ? "NOT_REGISTERED in Eureka"
                : futures.stream().map(CompletableFuture::join).toList()));
        return report;
    }

    @PreDestroy
    void shutdown() {
        probes.shutdownNow();
    }

    private String health(ServiceInstance instance) {
        String url = instance.getUri() + "/actuator/health";
        try {
            Map<?, ?> body = http.get().uri(url).retrieve().body(Map.class);
            return url + " -> " + (body == null ? "EMPTY" : body.get("status")) + build(instance);
        } catch (RestClientResponseException e) {
            // 503 = l'actuator répond mais un composant (DB, disque…) est DOWN : le corps dit lequel.
            return url + " -> HTTP " + e.getStatusCode().value() + " " + e.getResponseBodyAsString();
        } catch (RuntimeException e) {
            return url + " -> UNREACHABLE (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** Version réellement déployée : c'est elle, pas le dernier commit GitHub, qui tourne. */
    private String build(ServiceInstance instance) {
        try {
            return describeBuild(http.get().uri(instance.getUri() + "/actuator/info").retrieve().body(Map.class));
        } catch (RuntimeException e) {
            return ", build unknown";
        }
    }

    /** {@code build.commit} vient du build-arg GIT_COMMIT de la CD ; « local » = image construite sur un poste de dev. */
    static String describeBuild(Map<?, ?> info) {
        if (info == null || !(info.get("build") instanceof Map<?, ?> build)) {
            return ", build unknown";
        }
        String commit = String.valueOf(build.get("commit"));
        return ", build commit=" + commit.substring(0, Math.min(7, commit.length())) + " built=" + build.get("time");
    }
}
