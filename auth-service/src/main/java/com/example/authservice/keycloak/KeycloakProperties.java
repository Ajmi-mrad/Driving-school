package com.example.authservice.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de connexion au client confidentiel Keycloak {@code auth-service}
 * (préfixe {@code keycloak.*} dans application.yml).
 */
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    /** URL de base de Keycloak, ex. http://localhost:8080 */
    private String serverUrl;

    /** Realm cible, ex. auto-ecole */
    private String realm;

    /** Client confidentiel utilisé pour l'API Admin */
    private String clientId;

    /** Secret du client confidentiel */
    private String clientSecret;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}