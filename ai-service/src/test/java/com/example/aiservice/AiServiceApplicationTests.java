package com.example.aiservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Contexte complet sans dépendances externes : clé LLM factice, Eureka coupé
// (MCP est paresseux : aucun serveur contacté au démarrage).
@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test",
		"eureka.client.enabled=false"
})
class AiServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
