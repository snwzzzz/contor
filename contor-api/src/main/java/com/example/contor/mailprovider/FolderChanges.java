package com.example.contor.mailprovider;

import java.util.List;

/**
 * One page of a folder sync: more pages follow while {@code nextPageCursor} is set; the last page carries the
 * {@code newSyncCursor} to store for the next sync ({@code null} = keep the stored one).
 */
public record FolderChanges(List<SyncedMessage> messages, String nextPageCursor, String newSyncCursor) {

    public FolderChanges {
        messages = messages != null ? List.copyOf(messages) : List.of();
    }

    public boolean hasNextPage() {
        return nextPageCursor != null && !nextPageCursor.isBlank();
    }
}
