package com.example.contor.service;

import com.example.contor.ai.ImportanceSignals;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.persistence.EmailMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportanceSignalServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RecordService recordService;

    @InjectMocks
    private ImportanceSignalService service;

    @BeforeEach
    void setUp() {
        lenient().when(collectionSchemaService.findAll()).thenReturn(List.of(new CollectionDto().id(1L).slug("people").name("People")));
    }

    private void email(String from) {
        MailAccount account = MailAccount.builder().owner(AppUser.builder().id(OWNER).build()).build();
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(8L)))
                .thenReturn(Optional.of(EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).account(account).fromAddress(from).build()));
    }

    private static RecordPageDto records(long count) {
        return new RecordPageDto(new ArrayList<>(), 0, 1, count, count > 0 ? 1 : 0);
    }

    @Test
    void signalsFor_AContactYouWriteWith() {
        email("anna@acme.com");
        when(recordService.listRecords(eq(1L), eq(List.of("email:eq:anna@acme.com")), any(), any(), eq(0), eq(1))).thenReturn(records(1));
        when(emailMessageRepository.existsSentTo(OWNER, "anna@acme.com")).thenReturn(true);

        assertThat(service.signalsFor(8L)).isEqualTo(new ImportanceSignals(null, true, true, false));
    }

    @Test
    void signalsFor_ANoReplySenderIsBulk() {
        email("no-reply@shop.example");
        when(recordService.listRecords(anyLong(), any(), any(), any(), eq(0), eq(1))).thenReturn(records(0));

        assertThat(service.signalsFor(8L)).isEqualTo(new ImportanceSignals(null, false, false, true));
    }

    @Test
    void signalsFor_NoPeopleCollectionOrAFailingLookupIsNoContact() {
        email("anna@acme.com");
        when(collectionSchemaService.findAll()).thenReturn(List.of());

        assertThat(service.signalsFor(8L).knownContact()).isFalse();
    }

    @Test
    void signalsFor_UnknownEmailsOrSendersHaveNoSignals() {
        assertThat(service.signalsFor(99L)).isEqualTo(ImportanceSignals.NONE);
        email(null);
        assertThat(service.signalsFor(8L)).isEqualTo(ImportanceSignals.NONE);
    }
}
