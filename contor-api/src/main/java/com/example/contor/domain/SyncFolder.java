package com.example.contor.domain;

/** The folders the mail sync reads from every provider, stored as {@link EmailMessage#getFolder()}. */
public enum SyncFolder {
    INBOX(EmailMessage.FOLDER_INBOX),
    SENT(EmailMessage.FOLDER_SENT);

    private final String storedName;

    SyncFolder(String storedName) {
        this.storedName = storedName;
    }

    public String storedName() {
        return storedName;
    }
}
