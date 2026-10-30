package com.example.contor.mapper;

import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatConversationSummaryDto;
import com.example.contor.api.model.ChatMentionDto;
import com.example.contor.api.model.ChatMentionType;
import com.example.contor.api.model.ChatMessageDto;
import com.example.contor.api.model.ChatMessageRole;
import com.example.contor.api.model.ChatMessageStatus;
import com.example.contor.api.model.ClaimCheck;
import com.example.contor.api.model.ReportBlockDto;
import com.example.contor.api.model.ReportBlockType;
import com.example.contor.api.model.ReportClaimDto;
import com.example.contor.api.model.ReportSourceDto;
import com.example.contor.domain.ChatBlock;
import com.example.contor.domain.ChatCitation;
import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMention;
import com.example.contor.domain.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.example.contor.mapper.MapperSupport.toOffset;

/**
 * Chat conversation → {@link ChatConversationDto}. Answers use the report shapes: blocks as {@link ReportBlockDto} (ids
 * are positions within the message, every sentence SUPPORTED) and sources as {@link ReportSourceDto}, once per marker,
 * with their states from the {@link ReportMapper.SourceLookup} the service fills.
 */
public final class ChatMapper {

    private ChatMapper() {
    }

    public static ChatConversationDto toDto(ChatConversation conversation, ReportMapper.SourceLookup lookup) {
        if (conversation == null) {
            return null;
        }
        List<ChatMessageDto> messages = conversation.getMessages().stream().map(message -> toDto(message, lookup)).toList();
        return new ChatConversationDto(id(conversation), conversation.getTitle(), new ArrayList<>(messages), toOffset(conversation.getCreatedAt()))
                .lastModified(toOffset(conversation.getLastModified()));
    }

    /** @param answering an answer of the conversation is still being written */
    public static ChatConversationSummaryDto toSummary(ChatConversation conversation, boolean answering) {
        if (conversation == null) {
            return null;
        }
        return new ChatConversationSummaryDto(id(conversation), conversation.getTitle(), answering, toOffset(conversation.getCreatedAt()))
                .lastModified(toOffset(conversation.getLastModified()));
    }

    private static ChatMessageDto toDto(ChatMessage message, ReportMapper.SourceLookup lookup) {
        List<ReportBlockDto> blocks = new ArrayList<>();
        for (int position = 0; position < message.getBlocks().size(); position++) {
            blocks.add(block(position, message.getBlocks().get(position)));
        }
        Map<Integer, ChatCitation> byMarker = new TreeMap<>();
        message.getCitations().forEach(citation -> byMarker.putIfAbsent(citation.getMarker(), citation));
        List<ReportSourceDto> sources = byMarker.values().stream().map(citation -> ReportMapper.source(citation, lookup)).toList();
        List<ChatMentionDto> mentions = message.getMentions().stream().map(ChatMapper::mention).toList();
        return new ChatMessageDto(message.getId() != null ? message.getId().id() : null,
                ChatMessageRole.valueOf(message.getRole().name()), ChatMessageStatus.valueOf(message.getState().name()),
                blocks, new ArrayList<>(sources), new ArrayList<>(mentions), toOffset(message.getCreatedAt()))
                .text(message.getContent())
                .error(message.getError());
    }

    private static ReportBlockDto block(int position, ChatBlock block) {
        List<List<ReportClaimDto>> rows = block.rows().stream().map(ChatMapper::claims).toList();
        return new ReportBlockDto((long) position, ReportBlockType.valueOf(block.kind().name()), new ArrayList<>(block.columns()),
                claims(block.items()), new ArrayList<>(rows));
    }

    private static List<ReportClaimDto> claims(List<ChatBlock.Item> items) {
        List<ReportClaimDto> claims = new ArrayList<>();
        for (int position = 0; position < items.size(); position++) {
            ChatBlock.Item item = items.get(position);
            claims.add(new ReportClaimDto((long) position, item.text(), ClaimCheck.SUPPORTED, new ArrayList<>(item.markers())));
        }
        return claims;
    }

    private static ChatMentionDto mention(ChatMention mention) {
        return new ChatMentionDto(ChatMentionType.valueOf(mention.kind().name()), mention.label())
                .collectionId(mention.collectionId())
                .recordId(mention.recordId())
                .emailId(mention.emailId())
                .collectionName(mention.collectionName());
    }

    private static Long id(ChatConversation conversation) {
        return conversation.getId() != null ? conversation.getId().id() : null;
    }
}
