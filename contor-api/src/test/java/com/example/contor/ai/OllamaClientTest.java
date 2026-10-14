package com.example.contor.ai;

import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OllamaClientTest {

    private static final String BASE = "http://127.0.0.1:11434";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private final AiTimings timings = new AiTimings();
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create(BASE), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        client = new OllamaClient(properties, builder.build(), jsonMapper, timings);
    }

    @Test
    void completes_json_with_the_schema_as_format_and_thinking_off() {
        JsonNode schema = jsonMapper.readTree("{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
        server.expect(requestTo(BASE + "/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("extractor"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.think").value(false))
                .andExpect(jsonPath("$.keep_alive").value("10m"))
                .andExpect(jsonPath("$.format.type").value("object"))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("be precise"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("the email"))
                .andRespond(withSuccess("{\"model\":\"extractor\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"},\"done\":true}",
                        MediaType.APPLICATION_JSON));

        String answer = client.completeJson("extractor", "be precise", "the email", schema);

        assertThat(answer).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void records_the_token_counts_and_durations_ollama_reports_for_a_chat() {
        server.expect(requestTo(BASE + "/api/chat"))
                .andRespond(withSuccess("""
                        {"model":"extractor","message":{"role":"assistant","content":"{}"},"done":true,
                         "total_duration":1300000000,"load_duration":10000000,"prompt_eval_count":2140,
                         "prompt_eval_duration":200000000,"eval_count":310,"eval_duration":900000000}""",
                        MediaType.APPLICATION_JSON));

        timings.begin(1L);
        timings.stage("extract", () -> client.completeJson("extractor", "s", "u", jsonMapper.createObjectNode()));

        assertThat(timings.finish().orElseThrow().stages().getFirst().calls()).containsExactly(new AiTimings.Call("extractor",
                2140, 310, Duration.ofMillis(10), Duration.ofMillis(200), Duration.ofMillis(900)));
    }

    @Test
    void records_an_embedding_call_without_answer_tokens() {
        server.expect(requestTo(BASE + "/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[0.5]],\"load_duration\":5000000,\"prompt_eval_count\":12,\"total_duration\":40000000}",
                        MediaType.APPLICATION_JSON));

        timings.begin(1L);
        timings.stage("embed", () -> client.embed("hello"));

        assertThat(timings.finish().orElseThrow().stages().getFirst().calls()).containsExactly(new AiTimings.Call("embedder",
                12, 0, Duration.ofMillis(5), Duration.ofMillis(35), Duration.ZERO));
    }

    @Test
    void reports_ollama_errors_as_an_ollama_exception() {
        server.expect(requestTo(BASE + "/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model 'extractor' not found\"}"));

        assertThatThrownBy(() -> client.completeJson("extractor", "s", "u", jsonMapper.createObjectNode()))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("model 'extractor' not found");
    }

    @Test
    void retries_a_chat_once_when_ollama_fails_while_loading_the_model() {
        server.expect(requestTo(BASE + "/api/chat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"llama runner process has terminated\"}"));
        server.expect(requestTo(BASE + "/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\"{}\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.completeJson("extractor", "s", "u", jsonMapper.createObjectNode())).isEqualTo("{}");
        server.verify();
    }

    @Test
    void gives_up_after_the_retry() {
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(BASE + "/api/chat"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"out of memory\"}"));
        }

        assertThatThrownBy(() -> client.completeJson("extractor", "s", "u", jsonMapper.createObjectNode()))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("out of memory");
        server.verify();
    }

    @Test
    void embeds_text_with_the_embedding_model() {
        server.expect(requestTo(BASE + "/api/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("embedder"))
                .andExpect(jsonPath("$.input").value("hello"))
                .andRespond(withSuccess("{\"embeddings\":[[0.5,-1.25,2.0]]}", MediaType.APPLICATION_JSON));

        assertThat(client.embed("hello")).containsExactly(0.5f, -1.25f, 2.0f);
    }

    @Test
    void embeds_several_texts_with_one_call() {
        server.expect(requestTo(BASE + "/api/embed"))
                .andExpect(jsonPath("$.model").value("embedder"))
                .andExpect(jsonPath("$.input[0]").value("first"))
                .andExpect(jsonPath("$.input[1]").value("second"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0],[2.0]],\"prompt_eval_count\":7}", MediaType.APPLICATION_JSON));

        timings.begin(1L);
        List<float[]> vectors = timings.stage("embed", () -> client.embedAll(List.of("first", "second")));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(1)).containsExactly(2.0f);
        assertThat(timings.finish().orElseThrow().stages().getFirst().calls()).singleElement()
                .satisfies(call -> assertThat(call.promptTokens()).isEqualTo(7));
    }

    @Test
    void an_embedding_answer_with_a_missing_vector_fails() {
        server.expect(requestTo(BASE + "/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embedAll(List.of("first", "second"))).isInstanceOf(OllamaException.class);
    }

    @Test
    void reads_the_version_and_is_empty_when_ollama_is_down() {
        server.expect(requestTo(BASE + "/api/version")).andRespond(withSuccess("{\"version\":\"0.40.1\"}", MediaType.APPLICATION_JSON));
        assertThat(client.version()).contains("0.40.1");

        server.reset();
        server.expect(requestTo(BASE + "/api/version")).andRespond(withServerError());
        assertThat(client.version()).isEmpty();
    }

    @Test
    void lists_installed_models() {
        server.expect(requestTo(BASE + "/api/tags"))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"qwen3.5:9b\",\"size\":1},{\"name\":\"nomic-embed-text:latest\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.installedModels()).containsExactlyInAnyOrder("qwen3.5:9b", "nomic-embed-text:latest");
    }

    @Test
    void pulls_a_model_and_reports_progress_from_the_stream() {
        server.expect(requestTo(BASE + "/api/pull"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"model\":\"router\",\"stream\":true}"))
                .andRespond(withSuccess("""
                        {"status":"pulling manifest"}
                        {"status":"pulling abc","total":200,"completed":50}
                        {"status":"pulling abc","total":200,"completed":200}
                        {"status":"success"}
                        """, MediaType.APPLICATION_NDJSON));
        List<Integer> progress = new ArrayList<>();

        client.pull("router", progress::add);

        assertThat(progress).containsExactly(25, 100);
    }

    @Test
    void a_pull_error_in_the_stream_fails_the_pull() {
        server.expect(requestTo(BASE + "/api/pull"))
                .andRespond(withSuccess("{\"status\":\"pulling manifest\"}\n{\"error\":\"pull model manifest: file does not exist\"}\n",
                        MediaType.APPLICATION_NDJSON));

        assertThatThrownBy(() -> client.pull("router", percent -> { }))
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("file does not exist");
    }
}
