package com.example.contor.service;

import com.example.contor.ai.EmailTextCleaner;
import com.example.contor.ai.ReplyThread;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;

import java.util.List;
import java.util.Optional;

/** Emails as the models read them: detached from JPA, the body cleaned, the user's own (sent) mail marked. */
final class ThreadMessages {

    /** A body never takes more than this after cleaning. */
    static final int BODY_CHARS = 4000;

    private ThreadMessages() {
    }

    static ReplyThread.Message of(EmailMessage message) {
        List<String> to = Optional.ofNullable(message.getToRecipients()).orElse(List.of()).stream().map(EmailRecipient::address).toList();
        return new ReplyThread.Message(EmailMessage.FOLDER_SENT.equals(message.getFolder()), message.getFromName(),
                message.getFromAddress(), to, message.getSubject(), message.getReceivedAt(),
                EmailTextCleaner.clean(AiAnalysisWorker.plainText(message), BODY_CHARS));
    }
}
