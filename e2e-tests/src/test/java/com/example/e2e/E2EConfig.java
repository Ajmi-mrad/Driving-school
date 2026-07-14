package com.example.e2e;

/**
 * E2E configuration resolved from environment variables / system properties, with dev defaults that
 * match the local docker-compose stack. The owner password has no default and must be supplied
 * (e.g. {@code E2E_OWNER_PASSWORD=... mvn -Pe2e -pl e2e-tests verify}); when absent the suite is
 * skipped rather than failed.
 */
final class E2EConfig {

    static final String GATEWAY_URL = get("E2E_GATEWAY_URL", "http://localhost:8222");
    static final String KEYCLOAK_URL = get("E2E_KEYCLOAK_URL", "http://localhost:8080");
    static final String REALM = get("E2E_REALM", "auto-ecole");
    static final String CLIENT_ID = get("E2E_CLIENT_ID", "auto-ecole-frontend");
    static final String OWNER_USER = get("E2E_OWNER_USER", "owner");
    static final String OWNER_PASSWORD = get("E2E_OWNER_PASSWORD", null);

    private E2EConfig() {
    }

    /** System property wins over environment variable (uppercased with dots for the property form). */
    private static String get(String envName, String defaultValue) {
        String prop = System.getProperty(envName.toLowerCase().replace('_', '.'));
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return defaultValue;
    }
}
