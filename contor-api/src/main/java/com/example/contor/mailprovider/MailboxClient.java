package com.example.contor.mailprovider;

import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SyncFolder;
import com.example.contor.exception.SyncCursorExpiredException;
import com.example.contor.service.ImageData;

import java.util.List;
import java.util.Optional;

/**
 * Reads one provider's mailbox with an access token and answers in provider-neutral types, so the mail sync never
 * sees provider SDK classes. Plug-in point together with {@link MailOAuthClient} (see there): add a bean to extend.
 */
public interface MailboxClient {

    MailProvider provider();

    /** Address and display name of the signed-in mailbox. */
    MailboxProfile fetchProfile(String accessToken);

    /** The mailbox's profile picture; empty when there is none or the provider does not answer (never fails a sync). */
    Optional<ImageData> fetchProfilePhoto(String accessToken);

    /** Photos of the mailbox's contacts, one entry per contact address; may throw when the permission is missing. */
    List<ContactPhotoData> fetchContactPhotos(String accessToken);

    /**
     * One page of new or changed messages of a folder.
     *
     * @param cursor {@code null} = start from scratch, else a {@link FolderChanges#nextPageCursor()} or
     *               {@link FolderChanges#newSyncCursor()} this client returned earlier (opaque to the caller)
     * @throws SyncCursorExpiredException when the provider no longer accepts the cursor; the caller restarts with null
     */
    FolderChanges fetchChanges(String accessToken, SyncFolder folder, String cursor);
}
