package com.example.contor.service;

import com.example.contor.domain.AuditAction;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.persistence.ProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyLifecycleServiceTest {

    private static final MailAccount.MailAccountId ACCOUNT = new MailAccount.MailAccountId(1L);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private AuditService auditService;

    private ReplyLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ReplyLifecycleService(proposalRepository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(proposalRepository.saveAndFlush(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Proposal pending(long id) {
        return Proposal.builder().id(new Proposal.ProposalId(id)).kind(ProposalKind.DRAFT_REPLY).status(ProposalStatus.PENDING)
                .payload(Map.of("body", "Hi")).build();
    }

    @Test
    void retireOvertaken_SentReplyDismissesPendingDraft() {
        Proposal answered = pending(5L);
        when(proposalRepository.findWithNewerSent(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, ACCOUNT)).thenReturn(List.of(answered));
        when(proposalRepository.findWithNewerReceived(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, ACCOUNT)).thenReturn(List.of());

        assertThat(service.retireOvertaken(ACCOUNT)).isEqualTo(1);

        assertThat(answered.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(answered.getResult()).containsEntry("reason", ReplyLifecycleService.ANSWERED);
        assertThat(answered.getDecidedAt()).isEqualTo(NOW);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.PROPOSAL_SUPERSEDED);
        assertThat(event.getValue().entityId()).isEqualTo(5L);
        assertThat(event.getValue().after()).containsEntry("reason", ReplyLifecycleService.ANSWERED);
    }

    @Test
    void retireOvertaken_NewInboundSupersedesAndAnsweredWins() {
        Proposal both = pending(5L);
        Proposal followedUp = pending(6L);
        when(proposalRepository.findWithNewerSent(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, ACCOUNT)).thenReturn(List.of(both));
        when(proposalRepository.findWithNewerReceived(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, ACCOUNT))
                .thenReturn(List.of(both, followedUp));

        assertThat(service.retireOvertaken(ACCOUNT)).isEqualTo(2);

        assertThat(both.getResult()).containsEntry("reason", ReplyLifecycleService.ANSWERED);
        assertThat(followedUp.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(followedUp.getResult()).containsEntry("reason", ReplyLifecycleService.NEWER_EMAIL);
        verify(auditService, times(2)).record(any());
    }

    @Test
    void supersedeConversation_RejectsThePendingDraftsOfTheConversation() {
        Proposal old = pending(5L);
        when(proposalRepository.findForConversation(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, ACCOUNT, "conv")).thenReturn(List.of(old));

        service.supersedeConversation(ACCOUNT, "conv", ReplyLifecycleService.NEWER_EMAIL);

        assertThat(old.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    }

    @Test
    void supersedeConversation_WithoutConversationDoesNothing() {
        service.supersedeConversation(ACCOUNT, null, ReplyLifecycleService.NEWER_EMAIL);

        verify(proposalRepository, never()).findForConversation(any(), any(), any(), any());
    }
}
