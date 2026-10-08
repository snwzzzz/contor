package com.example.contor.service;

import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.ai.LlmClient;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxDiscoveryServiceTest {

    private static final String MAP_ANSWER = """
            {"types": [{"name": "Job applications", "description": "Applications and interview invitations", "emails": 4,
                        "fields": [{"name": "Company", "type": "TEXT", "description": "Employer"}]}]}
            """;

    private static final String REDUCE_ANSWER = """
            {"collections": [
              {"slug": "job_applications", "name": "Job applications", "description": "Jobs you applied for",
               "icon": "briefcase", "emailCount": 6, "reason": "Several application confirmations", "matchKey": "company",
               "fields": [
                 {"slug": "company", "name": "Company", "type": "TEXT", "description": "Employer", "values": []},
                 {"slug": "stage", "name": "Stage", "type": "STATUS", "description": "Where it stands", "values": ["applied", "interview", "offer", "rejected"]},
                 {"slug": "salary", "name": "Salary", "type": "MONEY", "description": "Offered salary", "values": []},
                 {"slug": "applied_on", "name": "Applied on", "type": "DATE", "description": "Date sent", "values": []},
                 {"slug": "contact", "name": "Contact", "type": "RELATION", "description": "Recruiter", "values": []},
                 {"slug": "title", "name": "Title", "type": "TEXT", "description": "Clashes with a system column", "values": []},
                 {"slug": "kind", "name": "Kind", "type": "ENUM", "description": "No values given", "values": []}
               ]},
              {"slug": "people", "name": "People", "description": "exists already", "icon": "user", "emailCount": 50,
               "reason": "", "matchKey": "", "fields": []},
              {"slug": "Bad Slug!", "name": "Broken", "description": "", "icon": "", "emailCount": 3, "reason": "",
               "matchKey": "", "fields": []}
            ]}
            """;

    @Mock
    private LlmClient llmClient;

    private InboxDiscoveryService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new InboxDiscoveryService(llmClient, properties, JsonMapper.builder().build());
    }

    private static List<DiscoveryEmail> emails(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new DiscoveryEmail("Sender " + i, "s" + i + "@example.com", "Subject " + i, "Snippet " + i,
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i)))
                .toList();
    }

    private static List<CollectionDto> existing() {
        return List.of(new CollectionDto().id(1L).slug("people").name("People"));
    }

    @Test
    void discover_SummarizesBatchesThenMerges() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any()))
                .thenReturn(MAP_ANSWER, MAP_ANSWER, REDUCE_ANSWER);

        service.discover(emails(45), existing());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(3)).completeJson(eq("router"), anyString(), prompts.capture(), any());
        assertThat(prompts.getAllValues().get(0)).contains("Subject 0").contains("s0@example.com").doesNotContain("Subject 44");
        assertThat(prompts.getAllValues().get(1)).contains("Subject 44");
        assertThat(prompts.getAllValues().get(2)).contains("Job applications").contains("people");
    }

    @Test
    void discover_SanitizesSuggestions() {
        when(llmClient.completeJson(anyString(), anyString(), anyString(), any())).thenReturn(MAP_ANSWER, REDUCE_ANSWER);

        List<InboxDiscoveryService.Suggestion> suggestions = service.discover(emails(3), existing());

        assertThat(suggestions).hasSize(1);
        InboxDiscoveryService.Suggestion jobs = suggestions.getFirst();
        assertThat(jobs.emailCount()).isEqualTo(6);
        assertThat(jobs.reason()).isEqualTo("Several application confirmations");
        NewCollectionCommand command = jobs.command();
        assertThat(command.getSlug()).isEqualTo("job_applications");
        assertThat(command.getIcon()).isEqualTo("briefcase");
        assertThat(command.getMatchKeys()).containsExactly("company");
        assertThat(command.getFields()).extracting(NewCollectionFieldCommand::getSlug)
                .containsExactly("company", "stage", "salary", "applied_on", "kind");
        assertThat(command.getFields().get(1).getOptions()).containsEntry("states", List.of("applied", "interview", "offer", "rejected"));
        assertThat(command.getFields().get(4).getType()).isEqualTo(CollectionFieldType.TEXT);
        assertThat(command.getDisplayConfig().getKanbanStatusField()).isEqualTo("stage");
        assertThat(command.getDisplayConfig().getCalendarDateField()).isEqualTo("applied_on");
    }

    @Test
    void discover_NoEmailsNoCall() {
        assertThat(service.discover(List.of(), existing())).isEmpty();
        verifyNoInteractions(llmClient);
    }

    @Test
    void discover_MalformedAnswersIgnored() {
        when(llmClient.completeJson(anyString(), anyString(), anyString(), any())).thenReturn("not json");

        assertThat(service.discover(emails(3), existing())).isEmpty();
        verify(llmClient, times(1)).completeJson(anyString(), anyString(), anyString(), any());
    }

    @Test
    void discover_LimitedToEightCollections() {
        List<String> collections = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            collections.add("""
                    {"slug": "type_%d", "name": "Type %d", "description": "", "icon": "", "emailCount": %d, "reason": "",
                     "matchKey": "", "fields": [{"slug": "note", "name": "Note", "type": "TEXT", "description": "", "values": []}]}
                    """.formatted(i, i, 20 - i));
        }
        when(llmClient.completeJson(anyString(), anyString(), anyString(), any()))
                .thenReturn(MAP_ANSWER, "{\"collections\": [" + String.join(",", collections) + "]}");

        List<InboxDiscoveryService.Suggestion> suggestions = service.discover(emails(3), existing());

        assertThat(suggestions).hasSize(8);
        assertThat(suggestions.getFirst().command().getSlug()).isEqualTo("type_0");
    }
}
