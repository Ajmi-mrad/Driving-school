package com.example.aiservice.ops;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpeechControllerTest {

    private static final String URL = "https://murf.test/v1/speech/stream";

    @Test
    void sendsTextToMurfFalconAndReturnsMp3() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer murf = MockRestServiceServer.bindTo(builder).build();
        murf.expect(requestTo(URL))
                .andExpect(header("api-key", "secret"))
                .andExpect(jsonPath("$.model").value("FALCON"))
                .andExpect(jsonPath("$.voiceId").value("en-US-natalie"))
                .andExpect(jsonPath("$.locale").value("fr-FR"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.parseMediaType("audio/mpeg")));

        var response = new SpeechController(builder.build(), "secret", URL, "en-US-natalie")
                .speak(new SpeechController.SpeakRequest("Bonjour", "fr"));

        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().getContentType()).hasToString("audio/mpeg");
        murf.verify();
    }

    @Test
    void unknownLanguageFallsBackToTheVoiceDefaultLocale() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer murf = MockRestServiceServer.bindTo(builder).build();
        murf.expect(requestTo(URL))
                .andExpect(jsonPath("$.locale").doesNotExist())
                .andRespond(withSuccess(new byte[] {1}, MediaType.parseMediaType("audio/mpeg")));

        new SpeechController(builder.build(), "secret", URL, "v").speak(new SpeechController.SpeakRequest("x", "xx"));
        murf.verify();
    }

    @Test
    void blankKeyMeansDisabled() {
        SpeechController controller = new SpeechController(RestClient.create(), "", URL, "v");
        assertThatThrownBy(() -> controller.speak(new SpeechController.SpeakRequest("x", "fr")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void murfFailureIsABadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo(URL)).andRespond(withServerError());
        SpeechController controller = new SpeechController(builder.build(), "secret", URL, "v");
        assertThatThrownBy(() -> controller.speak(new SpeechController.SpeakRequest("x", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
