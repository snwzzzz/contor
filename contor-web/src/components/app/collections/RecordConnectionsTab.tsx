"use client";

import { useQueries } from "@tanstack/react-query";
import { Bot, Database, Mail, Network } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/app/EmptyState";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { getGetRecordQueryOptions } from "@/lib/api/generated/endpoints/records/records";
import { useListRelationTypes } from "@/lib/api/generated/endpoints/relations/relations";
import type { RelationDto } from "@/lib/api/generated/model/relationDto";
import { collectionTint } from "@/lib/collection-tint";
import { formatValue, humanize } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { recordHref } from "./CollectionTable";

const MONTH = new Intl.DateTimeFormat("en-GB", { month: "short", year: "numeric" });

interface Connection {
  relation: RelationDto;
  collectionId: number;
  recordId: number;
  outgoing: boolean;
}

/** When the relation held, in words: "Since Mar 2025", "Until Jun 2026" or "Mar 2025 – Jun 2026". */
function period(relation: RelationDto): string | null {
  const from = relation.validFrom ? MONTH.format(new Date(relation.validFrom)) : null;
  const to = relation.validTo ? MONTH.format(new Date(relation.validTo)) : null;
  if (from && to) return `${from} – ${to}`;
  if (from) return `Since ${from}`;
  if (to) return `Until ${to}`;
  return null;
}

/** The relation's attributes (role, …) as "Role: Recruiter". */
function attributeLines(relation: RelationDto): string[] {
  return Object.entries(relation.attributes ?? {})
    .filter(([, value]) => value !== null && value !== undefined && value !== "")
    .map(([key, value]) => `${humanize(key)}: ${formatValue(value)}`);
}

interface RecordConnectionsTabProps {
  collectionId: number;
  recordId: number;
  /** Title of this record, the subject of the sentences ("Anna works at Acme"). */
  recordTitle: string;
  relations: RelationDto[] | undefined;
}

/** Records connected to this one through the relation graph, each described in a sentence with its details. */
export function RecordConnectionsTab({ collectionId, recordId, recordTitle, relations }: RecordConnectionsTabProps) {
  const { data: types } = useListRelationTypes();
  const { data: collections } = useListCollections();
  const connections: Connection[] = (relations ?? []).map((relation) => {
    const outgoing = relation.fromCollectionId === collectionId && relation.fromRecordId === recordId;
    return {
      relation,
      outgoing,
      collectionId: outgoing ? relation.toCollectionId : relation.fromCollectionId,
      recordId: outgoing ? relation.toRecordId : relation.fromRecordId,
    };
  });
  const records = useQueries({
    queries: connections.map((connection) => getGetRecordQueryOptions(connection.collectionId, connection.recordId)),
  });

  if (!relations) {
    return <p className="text-sm text-muted-foreground">Loading connections…</p>;
  }
  if (connections.length === 0) {
    return <EmptyState icon={Network} title="No connections yet" description="People, organizations and other records linked to this one show up here." />;
  }

  const typeById = new Map((types ?? []).map((type) => [type.id, type]));
  const collectionById = new Map((collections ?? []).map((collection) => [collection.id, collection]));

  return (
    <ul className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
      {connections.map((connection, index) => {
        const type = typeById.get(connection.relation.relationTypeId);
        const label = connection.outgoing ? type?.name : (type?.inverseName ?? type?.name);
        const title = records[index]?.data?.title ?? `Record #${connection.recordId}`;
        const collection = collectionById.get(connection.collectionId);
        const verb = type?.name ?? "is connected to";
        const sentence = connection.outgoing ? `${recordTitle} ${verb} ${title}` : `${title} ${verb} ${recordTitle}`;
        const details = [...attributeLines(connection.relation), period(connection.relation)].filter((line): line is string => line !== null);
        return (
          <li key={connection.relation.id}>
            <Link
              href={recordHref(connection.collectionId, connection.recordId)}
              className="flex h-full flex-col gap-3 rounded-xl border bg-card p-4 transition-colors hover:border-primary/40 hover:bg-muted/30"
            >
              <span className="flex items-center gap-3">
                <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-lg", collectionTint(connection.collectionId, collection?.slug))} aria-hidden="true">
                  <Database className="size-4" />
                </span>
                <span className="grid min-w-0 gap-0.5">
                  <span className="truncate font-medium">{title}</span>
                  <span className="truncate text-xs text-muted-foreground">{collection?.name ?? "Collection"}</span>
                </span>
                <span className="ml-auto flex shrink-0 items-center gap-1.5">
                  {connection.relation.createdBy === "AI" ? (
                    <span role="img" aria-label="Added by the assistant" title="Added by the assistant" className="text-muted-foreground">
                      <Bot className="size-3.5" aria-hidden="true" />
                    </span>
                  ) : null}
                  {label ? <span className="rounded-md bg-muted px-2 py-0.5 text-xs">{label}</span> : null}
                </span>
              </span>

              <span className="text-sm text-muted-foreground">{sentence}</span>

              {details.length > 0 || connection.relation.sourceEmailId != null ? (
                <span className="flex flex-wrap gap-1.5 border-t pt-3 text-xs">
                  {details.map((detail) => (
                    <span key={detail} className="rounded-md bg-muted px-2 py-0.5">{detail}</span>
                  ))}
                  {connection.relation.sourceEmailId != null ? (
                    <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-muted-foreground">
                      <Mail className="size-3" aria-hidden="true" />
                      Found in an email
                    </span>
                  ) : null}
                </span>
              ) : null}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
