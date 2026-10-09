"use client";

import { useQueries } from "@tanstack/react-query";
import { Bot, Database, Network } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/app/EmptyState";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { getGetRecordQueryOptions } from "@/lib/api/generated/endpoints/records/records";
import { useListRelationTypes } from "@/lib/api/generated/endpoints/relations/relations";
import type { RelationDto } from "@/lib/api/generated/model/relationDto";
import { collectionTint } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";
import { recordHref } from "./CollectionTable";

interface Connection {
  relation: RelationDto;
  collectionId: number;
  recordId: number;
  outgoing: boolean;
}

/** Records connected to this one through the relation graph, with the relation in words. */
export function RecordConnectionsTab({ collectionId, recordId, relations }: { collectionId: number; recordId: number; relations: RelationDto[] | undefined }) {
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
  const collectionName = new Map((collections ?? []).map((collection) => [collection.id, collection.name]));
  const collectionSlug = new Map((collections ?? []).map((collection) => [collection.id, collection.slug]));

  return (
    <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {connections.map((connection, index) => {
        const type = typeById.get(connection.relation.relationTypeId);
        const label = connection.outgoing ? type?.name : (type?.inverseName ?? type?.name);
        const title = records[index]?.data?.title ?? `Record #${connection.recordId}`;
        return (
          <li key={connection.relation.id}>
            <Link
              href={recordHref(connection.collectionId, connection.recordId)}
              className="flex items-center gap-3 rounded-xl border p-4 transition-colors hover:bg-muted/40"
            >
              <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-lg", collectionTint(connection.collectionId, collectionSlug.get(connection.collectionId)))} aria-hidden="true">
                <Database className="size-4" />
              </span>
              <span className="grid min-w-0 gap-0.5">
                <span className="truncate font-medium">{title}</span>
                <span className="truncate text-xs text-muted-foreground">{collectionName.get(connection.collectionId) ?? "Collection"}</span>
              </span>
              <span className="ml-auto flex shrink-0 items-center gap-1.5">
                {connection.relation.createdBy === "AI" ? (
                  <span role="img" aria-label="Added by the assistant" title="Added by the assistant" className="text-muted-foreground">
                    <Bot className="size-3.5" aria-hidden="true" />
                  </span>
                ) : null}
                {label ? <span className="rounded-md bg-muted px-2 py-0.5 text-xs">{label}</span> : null}
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
