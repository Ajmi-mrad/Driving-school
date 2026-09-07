import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        keycloakify({
            accountThemeImplementation: "none",
            themeName: "auto-ecole",
            keycloakVersionTargets: {
                "21-and-below": false,
                "22-to-25": false,
                "all-other-versions": "auto-ecole-keycloak-theme.jar"
            }
        })
    ]
});
