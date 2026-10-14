package com.example.contor.service;

import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;

import java.util.List;
import java.util.Map;

/**
 * The categories of a wholesaler's mailbox (customers, suppliers, sales and purchase orders, shipments, complaints,
 * invoices in both directions) and the relation types between them. Used by the dev-only demo mailbox
 * ({@link DemoMailboxService}) and by the AI evaluation of the business dataset, so both see the same schema; mirrored
 * in {@code scripts/business-dataset/lib/schema.mjs}.
 */
public final class BusinessCollections {

    /** A directed relation type between two collections, by slug. */
    public record RelationTypeSpec(String slug, String name, String inverseName, String from, String to) {
    }

    private BusinessCollections() {
    }

    public static List<NewCollectionCommand> commands() {
        return List.of(customers(), suppliers(), salesOrders(), purchaseOrders(), shipments(), complaints(), businessInvoices());
    }

    public static List<RelationTypeSpec> relationTypes() {
        return List.of(
                new RelationTypeSpec("supplies", "supplies", "supplied by", "suppliers", "purchase_orders"),
                new RelationTypeSpec("ships", "ships", "shipped in", "shipments", "sales_orders"),
                new RelationTypeSpec("delivers", "delivers", "delivered in", "shipments", "purchase_orders"),
                new RelationTypeSpec("drives_for", "drives for", "driven for by", "people", "organizations"));
    }

    private static NewCollectionFieldCommand field(String slug, String name, CollectionFieldType type, String description) {
        return new NewCollectionFieldCommand(slug, name, type).description(description);
    }

    private static NewCollectionCommand customers() {
        return new NewCollectionCommand("customers", "Customers")
                .description("Companies that buy from us. Record one when an email is about the customer relationship itself "
                        + "(a new customer, terms, a customer at risk or leaving). The title is the customer's name.")
                .matchKeys(List.of("customer_number"))
                .fields(List.of(
                        field("name", "Name", CollectionFieldType.TEXT, "Company name").required(true),
                        field("customer_number", "Customer number", CollectionFieldType.TEXT, "Our customer number, e.g. K-10083"),
                        field("country", "Country", CollectionFieldType.TEXT, "Country of the customer"),
                        field("segment", "Segment", CollectionFieldType.ENUM, "loyal, growing, at_risk, lost or new")
                                .options(Map.of("values", List.of("loyal", "growing", "at_risk", "lost", "new"))),
                        field("payment_terms", "Payment terms", CollectionFieldType.TEXT, "Agreed payment terms"),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Agreements and remarks")));
    }

    private static NewCollectionCommand suppliers() {
        return new NewCollectionCommand("suppliers", "Suppliers")
                .description("Companies we buy from. Record one when an email is about the supplier relationship itself "
                        + "(prices, terms, lead times). The title is the supplier's name.")
                .fields(List.of(
                        field("name", "Name", CollectionFieldType.TEXT, "Company name").required(true),
                        field("country", "Country", CollectionFieldType.TEXT, "Country of the supplier"),
                        field("lead_time_days", "Lead time (days)", CollectionFieldType.NUMBER, "Usual lead time in days"),
                        field("notes", "Notes", CollectionFieldType.LONG_TEXT, "Price changes, terms, remarks")));
    }

    private static NewCollectionCommand salesOrders() {
        return new NewCollectionCommand("sales_orders", "Sales orders")
                .description("Orders our customers place with us, also when the customer calls it their order, Bestellung or PO with "
                        + "their own number. The title is the customer and our order number, else their reference.")
                .matchKeys(List.of("order_number"))
                .fields(List.of(
                        field("order_number", "Order number", CollectionFieldType.TEXT, "Our order number, e.g. SO-26-0142").required(true),
                        field("customer", "Customer", CollectionFieldType.TEXT, "Customer name"),
                        field("total", "Total", CollectionFieldType.MONEY, "Order total"),
                        field("state", "State", CollectionFieldType.STATUS, "received, confirmed, in_production, shipped, delivered or cancelled")
                                .options(Map.of("states", List.of("received", "confirmed", "in_production", "shipped", "delivered", "cancelled"))),
                        field("requested_delivery", "Requested delivery", CollectionFieldType.DATE, "Delivery date the customer asked for"),
                        field("confirmed_delivery", "Confirmed delivery", CollectionFieldType.DATE, "Delivery date we confirmed")));
    }

    private static NewCollectionCommand purchaseOrders() {
        return new NewCollectionCommand("purchase_orders", "Purchase orders")
                .description("Orders we place with our suppliers, with our own PO numbers. Never an order a customer sends us. "
                        + "The title is the supplier and the PO number.")
                .matchKeys(List.of("po_number"))
                .fields(List.of(
                        field("po_number", "PO number", CollectionFieldType.TEXT, "Our purchase order number, e.g. PO-26-1088").required(true),
                        field("supplier", "Supplier", CollectionFieldType.TEXT, "Supplier name"),
                        field("total", "Total", CollectionFieldType.MONEY, "Order total"),
                        field("state", "State", CollectionFieldType.STATUS, "ordered, confirmed, shipped, received or cancelled")
                                .options(Map.of("states", List.of("ordered", "confirmed", "shipped", "received", "cancelled"))),
                        field("expected_arrival", "Expected arrival", CollectionFieldType.DATE, "When the goods should arrive")));
    }

    private static NewCollectionCommand shipments() {
        return new NewCollectionCommand("shipments", "Shipments")
                .description("Consignments to customers (outbound) and from suppliers (inbound), including dispatch, pickup, tracking, "
                        + "delay and delivery notices of carriers and parcel services. The title is the carrier and the tracking number.")
                .matchKeys(List.of("tracking_number"))
                .fields(List.of(
                        field("tracking_number", "Tracking number", CollectionFieldType.TEXT, "Tracking, consignment or CMR number").required(true),
                        field("carrier", "Carrier", CollectionFieldType.TEXT, "Carrier or forwarder"),
                        field("direction", "Direction", CollectionFieldType.ENUM, "outbound (to a customer) or inbound (from a supplier)")
                                .options(Map.of("values", List.of("outbound", "inbound"))),
                        field("order_ref", "Order", CollectionFieldType.TEXT, "Sales or purchase order number(s) it carries"),
                        field("state", "State", CollectionFieldType.STATUS, "booked, picked_up, in_transit, delayed, delivered or damaged")
                                .options(Map.of("states", List.of("booked", "picked_up", "in_transit", "delayed", "delivered", "damaged"))),
                        field("eta", "ETA", CollectionFieldType.DATE, "Expected delivery date"),
                        field("driver", "Driver", CollectionFieldType.TEXT, "Name of the driver, if given")));
    }

    private static NewCollectionCommand complaints() {
        return new NewCollectionCommand("complaints", "Complaints")
                .description("Complaints of our customers about delays, damage, wrong items, quality or invoices. The title is the "
                        + "reference, the customer and the topic.")
                .matchKeys(List.of("reference"))
                .fields(List.of(
                        field("reference", "Reference", CollectionFieldType.TEXT, "Complaint number, e.g. RMA-26-041").required(true),
                        field("customer", "Customer", CollectionFieldType.TEXT, "Customer name"),
                        field("topic", "Topic", CollectionFieldType.ENUM, "delay, damage, wrong_item, quality, invoice or service")
                                .options(Map.of("values", List.of("delay", "damage", "wrong_item", "quality", "invoice", "service"))),
                        field("severity", "Severity", CollectionFieldType.ENUM, "low, medium or high")
                                .options(Map.of("values", List.of("low", "medium", "high"))),
                        field("state", "State", CollectionFieldType.STATUS, "open, in_progress or resolved")
                                .options(Map.of("states", List.of("open", "in_progress", "resolved"))),
                        field("opened_on", "Opened on", CollectionFieldType.DATE, "When the complaint came in")));
    }

    /** Invoices in both directions. */
    private static NewCollectionCommand businessInvoices() {
        return new NewCollectionCommand("invoices", "Invoices")
                .description("Invoices we receive from suppliers and service providers (incoming: we pay) and invoices and credit "
                        + "notes we send to customers (outgoing). The title is the issuer and invoice number.")
                .matchKeys(List.of("invoice_number"))
                .fields(List.of(
                        field("invoice_number", "Invoice number", CollectionFieldType.TEXT, "Invoice or credit note number").required(true),
                        field("issuer", "Issuer", CollectionFieldType.TEXT, "Who issued the invoice"),
                        field("amount", "Amount", CollectionFieldType.MONEY, "Invoice amount"),
                        field("due_date", "Due", CollectionFieldType.DATE, "Payment deadline"),
                        field("state", "State", CollectionFieldType.STATUS, "open or paid")
                                .options(Map.of("states", List.of("open", "paid"))),
                        field("direction", "Direction", CollectionFieldType.ENUM, "incoming (we pay) or outgoing (we are paid)")
                                .options(Map.of("values", List.of("incoming", "outgoing")))));
    }
}
