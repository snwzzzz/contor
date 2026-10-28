package com.example.contor.persistence;

import com.example.contor.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Unsaved, valid entities for repository tests. Save the chain in the test so it stays visible. */
final class Fixtures {

    private Fixtures() {
    }

    static AppUser user(String username) {
        return AppUser.builder()
                .username(new Username(username))
                .email(new Email(username + "@example.com"))
                .passwordHash("$argon2id$test")
                .role(UserRole.OWNER)
                .build();
    }

    static MailAccount mailAccount(AppUser owner, String address) {
        return MailAccount.builder()
                .owner(owner)
                .provider(MailProvider.MICROSOFT_GRAPH)
                .emailAddress(new Email(address))
                .displayName("Inbox")
                .build();
    }

    static EmailMessage email(MailAccount account, String providerMessageId) {
        return EmailMessage.builder()
                .account(account)
                .providerMessageId(providerMessageId)
                .conversationId("conv-1")
                .folder("inbox")
                .fromAddress("shop@example.com")
                .fromName("Shop")
                .toRecipients(List.of(new EmailRecipient("Me", "me@example.com")))
                .subject("Your order has shipped")
                .receivedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    static Collection collection(String slug) {
        return Collection.builder()
                .slug(new Slug(slug))
                .name(slug)
                .tableName("c_" + slug)
                .createdBy(CreatedBy.USER)
                .build();
    }

    static ExternalApi externalApi(AppUser owner, String slug) {
        return ExternalApi.builder()
                .owner(owner)
                .name(slug)
                .slug(new Slug(slug))
                .baseUrl(new BaseUrl("https://api." + slug + ".example/v1"))
                .build();
    }

    static ExternalApiEndpoint endpoint(ExternalApi api, String name) {
        return ExternalApiEndpoint.builder()
                .api(api)
                .name(name)
                .path(new ApiPath("/" + name))
                .build();
    }

    static CollectionField field(Collection collection, String slug, FieldType type, int position) {
        return CollectionField.builder()
                .collection(collection)
                .slug(new Slug(slug))
                .name(slug)
                .type(type)
                .position(position)
                .columnName(slug)
                .options(Map.of())
                .build();
    }
}
