package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.service.BusinessCollections;
import com.example.contor.service.SystemDataSeeder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed schema every evaluation runs against: the real seeded system collections (so the eval follows the product)
 * plus typical categories the discovery step creates for a mailbox. {@link #all()} is the personal shopper view of the
 * synthetic corpus, {@link #business()} the wholesale view of the business mailbox ({@code ai-eval/business}).
 */
final class EvalCollections {

    private EvalCollections() {
    }

    static List<CollectionDto> all() {
        List<NewCollectionCommand> commands = new ArrayList<>(SystemDataSeeder.systemCollections());
        commands.add(orders());
        commands.add(invoices());
        commands.add(subscriptions());
        commands.add(applications());
        return numbered(commands);
    }

    /** The system collections plus the categories of a wholesaler's mailbox ({@link BusinessCollections}, mirrored in scripts/business-dataset/lib/schema.mjs). */
    static List<CollectionDto> business() {
        List<NewCollectionCommand> commands = new ArrayList<>(SystemDataSeeder.systemCollections());
        commands.addAll(businessCommands());
        return numbered(commands);
    }

    /** The business collections on top of the seeded system ones, as commands (the mailbox replay creates them for real). */
    static List<NewCollectionCommand> businessCommands() {
        return BusinessCollections.commands();
    }

    /** The seeded relation types plus those of the business mailbox; ids follow {@link #business()}. */
    static List<RelationTypeDto> businessRelationTypes() {
        Map<String, Long> ids = new LinkedHashMap<>();
        business().forEach(collection -> ids.put(collection.getSlug(), collection.getId()));
        List<RelationTypeDto> types = new ArrayList<>(List.of(
                relationType(1L, "works_at", "works at", "employs", true, ids.get("people"), ids.get("organizations")),
                relationType(2L, "knows", "knows", "knows", false, ids.get("people"), ids.get("people")),
                relationType(3L, "attends", "attends", "attended by", true, ids.get("people"), ids.get("events"))));
        for (BusinessCollections.RelationTypeSpec spec : BusinessCollections.relationTypes()) {
            types.add(relationType(types.size() + 1L, spec.slug(), spec.name(), spec.inverseName(), true, ids.get(spec.from()), ids.get(spec.to())));
        }
        return types;
    }

    private static RelationTypeDto relationType(long id, String slug, String name, String inverse, boolean directed, Long from, Long to) {
        return new RelationTypeDto(id, slug, name, directed, new LinkedHashMap<>(), Creator.USER).inverseName(inverse)
                .fromCollectionId(from).toCollectionId(to);
    }

    private static List<CollectionDto> numbered(List<NewCollectionCommand> commands) {
        List<CollectionDto> collections = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            collections.add(toDto(commands.get(i), i + 1L));
        }
        return collections;
    }

    /** The seeded relation types (works_at, knows, attends) between the system collections of {@link #all()}. */
    static List<RelationTypeDto> relationTypes() {
        Map<String, Long> ids = new LinkedHashMap<>();
        all().forEach(collection -> ids.put(collection.getSlug(), collection.getId()));
        return List.of(
                new RelationTypeDto(1L, "works_at", "works at", true, new LinkedHashMap<>(), Creator.USER).inverseName("employs")
                        .fromCollectionId(ids.get("people")).toCollectionId(ids.get("organizations")),
                new RelationTypeDto(2L, "knows", "knows", false, new LinkedHashMap<>(), Creator.USER).inverseName("knows")
                        .fromCollectionId(ids.get("people")).toCollectionId(ids.get("people")),
                new RelationTypeDto(3L, "attends", "attends", true, new LinkedHashMap<>(), Creator.USER).inverseName("attended by")
                        .fromCollectionId(ids.get("people")).toCollectionId(ids.get("events")));
    }

    private static CollectionDto toDto(NewCollectionCommand command, long id) {
        List<CollectionFieldDto> fields = new ArrayList<>();
        List<NewCollectionFieldCommand> source = command.getFields() != null ? command.getFields() : List.of();
        for (int i = 0; i < source.size(); i++) {
            NewCollectionFieldCommand field = source.get(i);
            fields.add(new CollectionFieldDto(id * 100 + i, field.getSlug(), field.getName(), field.getType(),
                    new LinkedHashMap<>(field.getOptions() != null ? field.getOptions() : Map.of()),
                    Boolean.TRUE.equals(field.getRequired()), false, i).description(field.getDescription()));
        }
        return new CollectionDto().id(id).slug(command.getSlug()).name(command.getName()).description(command.getDescription())
                .matchKeys(command.getMatchKeys() != null ? command.getMatchKeys() : List.of()).fields(fields);
    }

    private static NewCollectionFieldCommand field(String slug, String name, CollectionFieldType type, String description) {
        return new NewCollectionFieldCommand(slug, name, type).description(description);
    }

    private static NewCollectionCommand orders() {
        return new NewCollectionCommand("orders", "Orders")
                .description("Online orders and their delivery. The title is the shop and what was ordered.")
                .matchKeys(List.of("order_number"))
                .fields(List.of(
                        field("order_number", "Order number", CollectionFieldType.TEXT, "The shop's order number").required(true),
                        field("shop", "Shop", CollectionFieldType.TEXT, "Name of the shop"),
                        field("total", "Total", CollectionFieldType.MONEY, "Order total"),
                        field("state", "State", CollectionFieldType.STATUS, "ordered, shipped, delivered or cancelled")
                                .options(Map.of("states", List.of("ordered", "shipped", "delivered", "cancelled"))),
                        field("expected_delivery", "Expected delivery", CollectionFieldType.DATE, "Announced delivery date")));
    }

    private static NewCollectionCommand invoices() {
        return new NewCollectionCommand("invoices", "Invoices")
                .description("Invoices and bills to pay or already paid. The title is the issuer and invoice number.")
                .matchKeys(List.of("invoice_number"))
                .fields(List.of(
                        field("invoice_number", "Invoice number", CollectionFieldType.TEXT, "Invoice or reference number").required(true),
                        field("issuer", "Issuer", CollectionFieldType.TEXT, "Who sent the invoice"),
                        field("amount", "Amount", CollectionFieldType.MONEY, "Amount due"),
                        field("due_date", "Due", CollectionFieldType.DATE, "Payment deadline"),
                        field("state", "State", CollectionFieldType.STATUS, "open or paid")
                                .options(Map.of("states", List.of("open", "paid")))));
    }

    private static NewCollectionCommand subscriptions() {
        return new NewCollectionCommand("subscriptions", "Subscriptions")
                .description("Recurring services and memberships. The title is the service.")
                .matchKeys(List.of("service"))
                .fields(List.of(
                        field("service", "Service", CollectionFieldType.TEXT, "Name of the service").required(true),
                        field("price", "Price", CollectionFieldType.MONEY, "Price per billing period"),
                        field("billing_period", "Billing period", CollectionFieldType.ENUM, "monthly or yearly")
                                .options(Map.of("values", List.of("monthly", "yearly"))),
                        field("next_billing", "Next billing", CollectionFieldType.DATE, "Next charge or renewal date")));
    }

    private static NewCollectionCommand applications() {
        return new NewCollectionCommand("applications", "Job applications")
                .description("Job applications of the user and their progress. The title is the position and company.")
                .fields(List.of(
                        field("company", "Company", CollectionFieldType.TEXT, "Employer applied to").required(true),
                        field("position", "Position", CollectionFieldType.TEXT, "Job title applied for"),
                        field("state", "State", CollectionFieldType.STATUS, "applied, interview, offer or rejected")
                                .options(Map.of("states", List.of("applied", "interview", "offer", "rejected"))),
                        field("applied_on", "Applied on", CollectionFieldType.DATE, "Date of the application")));
    }
}
