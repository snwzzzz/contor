package com.example.contor.mapper;

import com.example.contor.api.model.CitationKind;
import com.example.contor.api.model.ClaimCheck;
import com.example.contor.api.model.FindingLevel;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.ReportBlockDto;
import com.example.contor.api.model.ReportBlockType;
import com.example.contor.api.model.ReportClaimDto;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportFindingDto;
import com.example.contor.api.model.ReportQuoteDto;
import com.example.contor.api.model.ReportSectionDto;
import com.example.contor.api.model.ReportSectionType;
import com.example.contor.api.model.ReportSourceDto;
import com.example.contor.api.model.ReportState;
import com.example.contor.api.model.ReportSummaryDto;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.Citation;
import com.example.contor.domain.ClaimVerification;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportBlock;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportClaim;
import com.example.contor.domain.ReportFinding;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.example.contor.mapper.MapperSupport.toOffset;

/**
 * Report → {@link ReportDto}: the visible sentences per section, finding and block (REMOVED ones only in
 * {@code removedClaims}; table cells grouped into rows), supporting quotes per finding, and every cited source once by
 * marker. Whether a source is
 * still current and the records' current titles come from a {@link SourceLookup} the service fills.
 */
public final class ReportMapper {

    private ReportMapper() {
    }

    /** What the mapper cannot know from the report alone. */
    public interface SourceLookup {

        SourceState state(Citation citation);

        /** The record's current title; {@code null} when unknown or deleted. */
        String recordTitle(Long collectionId, Long recordId);
    }

    public static ReportDto toDto(Report report, List<ReportDto> followUps, SourceLookup lookup) {
        if (report == null) {
            return null;
        }
        List<ReportSectionDto> sections = new ArrayList<>();
        List<ReportClaimDto> removed = new ArrayList<>();
        Map<Integer, ReportCitation> sources = new TreeMap<>();
        for (ReportSection section : report.getSections()) {
            List<ReportClaimDto> claims = visible(section.getClaims(), removed, sources);
            List<ReportFindingDto> findings = new ArrayList<>();
            for (ReportFinding finding : section.getFindings()) {
                List<ReportClaimDto> findingClaims = visible(finding.getClaims(), removed, sources);
                if (findingClaims.isEmpty()) {
                    continue;
                }
                List<ReportQuoteDto> quotes = finding.getClaims().stream()
                        .filter(claim -> claim.getVerification() != ClaimVerification.REMOVED)
                        .flatMap(claim -> claim.getCitations().stream())
                        .filter(ReportCitation::isSupportingQuote)
                        .filter(citation -> lookup.state(citation) != SourceState.UNAVAILABLE)
                        .map(citation -> new ReportQuoteDto(citation.getMarker(), citation.getQuote()))
                        .toList();
                findings.add(new ReportFindingDto(finding.getId(), finding.getTitle(), FindingLevel.valueOf(finding.getSeverity().name()),
                        claimsList(findingClaims), new ArrayList<>(quotes)).eventDate(toOffset(finding.getEventDate())));
            }
            List<ReportBlockDto> blocks = new ArrayList<>();
            for (ReportBlock block : section.getBlocks()) {
                ReportBlockDto dto = block(block, removed, sources);
                if (!dto.getItems().isEmpty() || !dto.getRows().isEmpty()) {
                    blocks.add(dto);
                }
            }
            if (!claims.isEmpty() || !findings.isEmpty() || !blocks.isEmpty()) {
                sections.add(new ReportSectionDto(section.getId(), ReportSectionType.valueOf(section.getKind().name()), section.getTitle(),
                        claimsList(claims), findings, blocks));
            }
        }
        List<ReportSourceDto> sourceDtos = sources.values().stream().map(citation -> source(citation, lookup)).toList();
        List<RecordRefDto> records = report.getScope().records().stream()
                .map(ref -> new RecordRefDto(ref.collectionId(), ref.recordId()))
                .toList();
        ReportScope.CollectionScope collection = report.getScope().collection();
        return new ReportDto(report.getId().id(), report.getCollection().getId().id(),
                ReportState.valueOf(report.getStatus().name()), new ArrayList<>(records),
                new ArrayList<>(collection != null ? collection.filters() : List.of()), report.getScope().emailIds().size(), sections,
                new ArrayList<>(sourceDtos), removed, new ArrayList<>(followUps), report.getClaimsTotal(), report.getClaimsVerified(),
                report.getClaimsRemoved(), toOffset(report.getCreatedAt()))
                .recordId(report.getRecordId())
                .search(collection != null ? collection.search() : null)
                .parentReportId(report.getParentReport() != null ? report.getParentReport().getId().id() : null)
                .prompt(report.getPrompt())
                .title(report.getTitle())
                .failureReason(report.getFailureReason())
                .model(report.getModel())
                .promptVersion(report.getPromptVersion())
                .generatedAt(toOffset(report.getGeneratedAt()));
    }

    public static ReportSummaryDto toSummary(Report report, int followUpCount) {
        if (report == null) {
            return null;
        }
        String first = firstSentence(report, section -> section.getKind() == ReportSectionKind.SUMMARY)
                .or(() -> firstSentence(report, section -> section.getKind() != ReportSectionKind.TIMELINE))
                .orElse(null);
        return new ReportSummaryDto(report.getId().id(), ReportState.valueOf(report.getStatus().name()), report.getClaimsTotal(),
                report.getClaimsVerified(), followUpCount, toOffset(report.getCreatedAt()))
                .title(report.getTitle())
                .prompt(report.getPrompt())
                .summary(first)
                .generatedAt(toOffset(report.getGeneratedAt()));
    }

    /** The first shown sentence or list item of the matching sections (not a table cell). */
    private static Optional<String> firstSentence(Report report, Predicate<ReportSection> sections) {
        return report.getSections().stream()
                .filter(sections)
                .flatMap(section -> Stream.concat(section.getClaims().stream(), section.getBlocks().stream()
                        .filter(block -> block.getKind() != ReportBlockKind.TABLE)
                        .flatMap(block -> block.getClaims().stream())))
                .filter(claim -> claim.getVerification() != ClaimVerification.REMOVED)
                .map(ReportClaim::getText)
                .findFirst();
    }

    /** A block with its items, or for a table its cells grouped into rows by row index. */
    private static ReportBlockDto block(ReportBlock block, List<ReportClaimDto> removed, Map<Integer, ReportCitation> sources) {
        List<ReportClaimDto> items = new ArrayList<>();
        Map<Integer, List<ReportClaimDto>> rows = new TreeMap<>();
        for (ReportClaim claim : block.getClaims()) {
            List<ReportClaimDto> shown = visible(List.of(claim), removed, sources);
            if (block.getKind() == ReportBlockKind.TABLE && claim.getRowIndex() != null) {
                rows.computeIfAbsent(claim.getRowIndex(), row -> new ArrayList<>()).addAll(shown);
            } else {
                items.addAll(shown);
            }
        }
        List<String> columns = block.getColumns() != null ? new ArrayList<>(block.getColumns()) : new ArrayList<>();
        return new ReportBlockDto(block.getId(), ReportBlockType.valueOf(block.getKind().name()), columns, items,
                new ArrayList<>(rows.values()));
    }

    /** The shown sentences; REMOVED ones go to {@code removed}; cited sources are collected by marker (first one wins). */
    private static List<ReportClaimDto> visible(List<ReportClaim> claims, List<ReportClaimDto> removed, Map<Integer, ReportCitation> sources) {
        List<ReportClaimDto> shown = new ArrayList<>();
        for (ReportClaim claim : claims) {
            if (claim.getVerification() == ClaimVerification.REMOVED) {
                removed.add(claim(claim, List.of()));
                continue;
            }
            List<Integer> markers = new ArrayList<>();
            for (ReportCitation citation : claim.getCitations()) {
                sources.putIfAbsent(citation.getMarker(), citation);
                if (!citation.isSupportingQuote() && !markers.contains(citation.getMarker())) {
                    markers.add(citation.getMarker());
                }
            }
            shown.add(claim(claim, markers));
        }
        return shown;
    }

    private static List<ReportClaimDto> claimsList(List<ReportClaimDto> claims) {
        return new ArrayList<>(claims);
    }

    private static ReportClaimDto claim(ReportClaim claim, List<Integer> markers) {
        return new ReportClaimDto(claim.getId(), claim.getText(), ClaimCheck.valueOf(claim.getVerification().name()), new ArrayList<>(markers))
                .note(claim.getVerifierNote());
    }

    /** A cited source for the source list (also used for chat answers). */
    public static ReportSourceDto source(Citation citation, SourceLookup lookup) {
        SourceState state = lookup.state(citation);
        Long collectionId = citation.getCollection() != null ? citation.getCollection().getId().id() : null;
        ReportSourceDto dto = new ReportSourceDto(citation.getMarker(), CitationKind.valueOf(citation.getType().name()), "", state)
                .collectionId(collectionId)
                .recordId(citation.getRecordId())
                .recordTitle(collectionId != null && citation.getRecordId() != null ? lookup.recordTitle(collectionId, citation.getRecordId()) : null)
                .relationId(citation.getRelationId());
        switch (citation.getType()) {
            case FIELD -> {
                String name = fieldName(citation);
                dto.fieldSlug(citation.getFieldSlug()).fieldName(name).label(name)
                        .value(citation.getValueSnapshot() != null ? citation.getValueSnapshot().get("value") : null);
            }
            case RECORD -> dto.label(citation.getCollection() != null ? citation.getCollection().getName() : "Record");
            case RELATION -> dto.label("Connection");
            case APP_ITEM -> {
                ExternalApiItem item = citation.getApiItem();
                dto.quote(citation.getQuote());
                if (item != null && item.getEndpoint() != null && item.getEndpoint().getApi() != null) {
                    dto.apiId(item.getEndpoint().getApi().getId() != null ? item.getEndpoint().getApi().getId().id() : null)
                            .apiItemId(item.getId())
                            .label(item.getEndpoint().getApi().getName() + " · " + item.getEndpoint().getName() + " · " + item.getItemKey());
                } else {
                    dto.label("Removed app item");
                }
            }
            case EMAIL_PARAGRAPH -> {
                EmailMessage email = citation.getEmail();
                if (state == SourceState.UNAVAILABLE) {
                    dto.label("Email in another mailbox");
                } else {
                    dto.paragraphIndex(citation.getParagraphIndex()).quote(citation.getQuote());
                    if (email != null) {
                        dto.emailId(email.getId().id()).emailSubject(email.getSubject())
                                .emailFrom(Optional.ofNullable(email.getFromName()).filter(name -> !name.isBlank()).orElse(domain(email.getFromAddress())))
                                .emailDate(toOffset(email.getReceivedAt()))
                                .label(Optional.ofNullable(email.getSubject()).filter(subject -> !subject.isBlank()).orElse("Email"));
                    } else {
                        dto.label("Deleted email");
                    }
                }
            }
        }
        return dto;
    }

    private static String fieldName(Citation citation) {
        if (citation.getCollection() == null) {
            return citation.getFieldSlug();
        }
        return citation.getCollection().getFields().stream()
                .filter(field -> field.getSlug() != null && field.getSlug().value().equals(citation.getFieldSlug()))
                .map(CollectionField::getName)
                .findFirst().orElse(citation.getFieldSlug());
    }

    /** Only the domain of an address (CLAUDE.md §5: never full addresses where a name will do). */
    private static String domain(String address) {
        return address != null && address.contains("@") ? address.substring(address.indexOf('@') + 1) : null;
    }
}
