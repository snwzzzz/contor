package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemDataSeederTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private RelationTypeRepository relationTypeRepository;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RelationTypeService relationTypeService;

    @InjectMocks
    private SystemDataSeeder seeder;

    @Test
    void seedsSystemCollectionsAndRelationTypesOnFirstStart() {
        when(collectionRepository.existsBySlug(any())).thenReturn(false);
        when(relationTypeRepository.existsBySlug(any())).thenReturn(false);
        when(collectionSchemaService.createSystemCollection(argThat(c -> c != null && c.getSlug().equals("people"))))
                .thenReturn(new CollectionDto().id(1L).slug("people"));
        when(collectionSchemaService.createSystemCollection(argThat(c -> c != null && c.getSlug().equals("organizations"))))
                .thenReturn(new CollectionDto().id(2L).slug("organizations"));
        when(collectionSchemaService.createSystemCollection(argThat(c -> c != null && c.getSlug().equals("events"))))
                .thenReturn(new CollectionDto().id(3L).slug("events"));
        when(collectionSchemaService.createSystemCollection(argThat(c -> c != null && c.getSlug().equals("tasks"))))
                .thenReturn(new CollectionDto().id(4L).slug("tasks"));

        seeder.run(null);

        var commands = ArgumentCaptor.forClass(NewCollectionCommand.class);
        verify(collectionSchemaService, times(4)).createSystemCollection(commands.capture());
        var people = commands.getAllValues().get(0);
        assertEquals("people", people.getSlug());
        assertEquals("full_name,email,phone,role_title,notes",
                String.join(",", people.getFields().stream().map(f -> f.getSlug()).toList()));
        assertEquals("name,domain,website,notes",
                String.join(",", commands.getAllValues().get(1).getFields().stream().map(f -> f.getSlug()).toList()));
        verify(relationTypeService).createSystemRelationType("works_at", "works at", "employs", 1L, 2L, true);
        verify(relationTypeService).createSystemRelationType("knows", "knows", "knows", 1L, 1L, false);
        verify(relationTypeService).createSystemRelationType("attends", "attends", "attended by", 1L, 3L, true);
    }

    @Test
    void seedsTheAssistantsOwnCalendarAndTaskList() {
        when(collectionRepository.existsBySlug(any())).thenReturn(false);
        when(relationTypeRepository.existsBySlug(any())).thenReturn(true);
        when(collectionSchemaService.createSystemCollection(any())).thenAnswer(inv -> new CollectionDto().id(9L));

        seeder.run(null);

        var commands = ArgumentCaptor.forClass(NewCollectionCommand.class);
        verify(collectionSchemaService, times(4)).createSystemCollection(commands.capture());
        var events = commands.getAllValues().get(2);
        assertEquals("events", events.getSlug());
        assertEquals("starts_at,ends_at,all_day,location,notes",
                String.join(",", events.getFields().stream().map(f -> f.getSlug()).toList()));
        assertEquals(CollectionFieldType.DATETIME, events.getFields().getFirst().getType());
        assertTrue(events.getFields().getFirst().getRequired());
        assertEquals("calendar", events.getDisplayConfig().getDefaultView());
        assertEquals("starts_at", events.getDisplayConfig().getCalendarDateField());

        var tasks = commands.getAllValues().get(3);
        assertEquals("tasks", tasks.getSlug());
        assertEquals("state,due_date,priority,notes",
                String.join(",", tasks.getFields().stream().map(f -> f.getSlug()).toList()));
        assertEquals(CollectionFieldType.STATUS, tasks.getFields().getFirst().getType());
        assertEquals(List.of("open", "in_progress", "done"), tasks.getFields().getFirst().getOptions().get("states"));
        assertEquals(List.of("low", "normal", "high"), tasks.getFields().get(2).getOptions().get("values"));
        assertEquals("state", tasks.getDisplayConfig().getKanbanStatusField());
    }

    @Test
    void skipsWhatAlreadyExists() {
        when(collectionRepository.existsBySlug(new Slug("people"))).thenReturn(true);
        when(collectionRepository.existsBySlug(new Slug("organizations"))).thenReturn(true);
        when(collectionRepository.existsBySlug(new Slug("events"))).thenReturn(true);
        when(collectionRepository.existsBySlug(new Slug("tasks"))).thenReturn(true);
        when(relationTypeRepository.existsBySlug(any())).thenReturn(true);

        seeder.run(null);

        verify(collectionSchemaService, never()).createSystemCollection(any());
        verify(relationTypeService, never()).createSystemRelationType(anyString(), anyString(), anyString(), anyLong(), anyLong(), anyBoolean());
    }
}
