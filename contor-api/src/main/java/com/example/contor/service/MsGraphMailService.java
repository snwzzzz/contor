package com.example.contor.service;

import com.example.contor.domain.EmailRecipient;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.mailfolders.item.messages.delta.DeltaGetResponse;
import com.microsoft.graph.users.item.mailfolders.item.messages.delta.DeltaRequestBuilder;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class MsGraphMailService {

    public record UserProfile(String emailAddress, String displayName) {}

    public record DeltaResult(List<Message> messages, String nextLink, String newDeltaLink) {}

    public GraphServiceClient createClient(String accessToken) {
        AuthenticationProvider authProvider = (request, context) ->
                request.headers.add("Authorization", "Bearer " + accessToken);
        return new GraphServiceClient(authProvider);
    }

    public UserProfile fetchUserProfile(String accessToken) {
        GraphServiceClient client = createClient(accessToken);
        User user = client.me().get();
        if (user == null) {
            throw new IllegalStateException("Failed to retrieve user profile from Microsoft Graph");
        }
        return extractProfile(user);
    }

    public static UserProfile extractProfile(User user) {
        String email = user.getMail() != null && !user.getMail().isBlank()
                ? user.getMail()
                : user.getUserPrincipalName();
        return new UserProfile(email, user.getDisplayName());
    }

    public DeltaResult fetchMessagesDelta(String accessToken, String deltaLinkOrNextLink) {
        GraphServiceClient client = createClient(accessToken);
        DeltaGetResponse response;
        if (deltaLinkOrNextLink == null || deltaLinkOrNextLink.isBlank()) {
            response = client.me().mailFolders().byMailFolderId("inbox").messages().delta().get();
        } else {
            response = new DeltaRequestBuilder(deltaLinkOrNextLink, client.getRequestAdapter()).get();
        }

        if (response == null) {
            return new DeltaResult(Collections.emptyList(), null, null);
        }
        return new DeltaResult(
                response.getValue() != null ? response.getValue() : Collections.emptyList(),
                response.getOdataNextLink(),
                response.getOdataDeltaLink()
        );
    }

    public static List<EmailRecipient> mapRecipients(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return Collections.emptyList();
        }
        return recipients.stream()
                .filter(r -> r.getEmailAddress() != null && r.getEmailAddress().getAddress() != null)
                .map(r -> new EmailRecipient(r.getEmailAddress().getName(), r.getEmailAddress().getAddress()))
                .toList();
    }
}
