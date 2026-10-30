"use client";

import { Pencil, Play, Trash2 } from "lucide-react";
import { useId, useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FormField } from "@/components/app/FormField";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { useTestExternalApi } from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ApiTestResultDto } from "@/lib/api/generated/model/apiTestResultDto";
import type { EndpointMethod } from "@/lib/api/generated/model/endpointMethod";
import {
  detectItems,
  prettyBody,
  toTestCommand,
  validateBasics,
  validateEndpoint,
  type AppDraft,
  type EndpointDraft,
  type FieldErrors,
} from "@/lib/external-apis";
import { cn } from "@/lib/utils";
import { KeyValueRows } from "./AppFormFields";

interface EndpointEditorProps {
  initial: EndpointDraft;
  /** The app as currently edited (unsaved changes included), for the test call. */
  app: AppDraft;
  /** The saved app: its stored secrets fill in what the form keeps. */
  apiId?: number;
  saveLabel: string;
  saving?: boolean;
  onSave: (endpoint: EndpointDraft) => void;
  onCancel: () => void;
}

/** One endpoint: what to call, a test call with the real answer, and where the items are in it. */
export function EndpointEditor({ initial, app, apiId, saveLabel, saving = false, onSave, onCancel }: EndpointEditorProps) {
  const bodyId = useId();
  const [endpoint, setEndpoint] = useState(initial);
  const [errors, setErrors] = useState<FieldErrors>({});
  const test = useTestExternalApi({ mutation: { meta: { silent: true } } });

  function change(next: Partial<EndpointDraft>) {
    setEndpoint((current) => ({ ...current, ...next }));
  }

  function check(): boolean {
    const found = validateEndpoint(endpoint);
    setErrors(found);
    return Object.keys(found).length === 0;
  }

  function runTest() {
    if (!check()) return;
    test.mutate({ data: toTestCommand(app, endpoint, apiId) });
  }

  const baseProblem = validateBasics(app).baseUrl;

  return (
    <div className="grid gap-5 rounded-lg border p-4">
      <div className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_7rem]">
        <FormField
          id="endpoint-name"
          label="Endpoint name"
          value={endpoint.name}
          maxLength={80}
          error={errors.name}
          placeholder="Open orders"
          onChange={(event) => change({ name: event.target.value })}
        />
        <div className="grid content-start gap-2">
          <Label htmlFor="endpoint-method">Method</Label>
          <NativeSelect id="endpoint-method" value={endpoint.method} onChange={(event) => change({ method: event.target.value as EndpointMethod })}>
            <option value="GET">GET</option>
            <option value="POST">POST</option>
          </NativeSelect>
        </div>
      </div>
      <FormField
        id="endpoint-path"
        label="Path"
        value={endpoint.path}
        error={errors.path}
        placeholder="/orders"
        description={endpoint.method === "POST" ? "Only for queries that read: contor never changes data in an app." : undefined}
        onChange={(event) => change({ path: event.target.value })}
      />
      <div className="grid gap-2">
        <span className="text-sm font-medium">Query parameters</span>
        <KeyValueRows
          rows={endpoint.query}
          onChange={(query) => change({ query })}
          errors={errors}
          errorPrefix="query"
          label="Parameter"
          addLabel="Add parameter"
        />
      </div>
      {endpoint.method === "POST" ? (
        <div className="grid gap-2">
          <Label htmlFor={bodyId}>Body</Label>
          <Textarea
            id={bodyId}
            value={endpoint.body}
            className="font-mono text-xs"
            aria-invalid={errors.body ? true : undefined}
            aria-describedby={errors.body ? `${bodyId}-error` : undefined}
            onChange={(event) => change({ body: event.target.value })}
          />
          {errors.body ? (
            <p id={`${bodyId}-error`} className="text-sm text-destructive">
              {errors.body}
            </p>
          ) : null}
        </div>
      ) : null}

      <div className="grid gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <AppButton variant="outline" size="sm" loading={test.isPending} disabled={baseProblem !== undefined} onClick={runTest}>
            <Play aria-hidden="true" data-icon="inline-start" />
            Test call
          </AppButton>
          {baseProblem ? <span className="text-sm text-muted-foreground">Enter a valid base URL first.</span> : null}
        </div>
        <ErrorMessage message={test.error?.message} />
        {test.data ? (
          <TestCallPreview
            result={test.data}
            onUseItems={(itemsPath, itemKeyPath) => change({ itemsPath, itemKeyPath: endpoint.itemKeyPath || itemKeyPath || "" })}
          />
        ) : null}
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <FormField
          id="endpoint-items-path"
          label="Items path"
          value={endpoint.itemsPath}
          placeholder="$.data[*]"
          description="Where the items are in the answer. Empty: the whole answer is one item."
          onChange={(event) => change({ itemsPath: event.target.value })}
        />
        <FormField
          id="endpoint-item-key"
          label="Item key"
          value={endpoint.itemKeyPath}
          placeholder="$.id"
          description="What identifies an item across calls."
          onChange={(event) => change({ itemKeyPath: event.target.value })}
        />
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="size-4 accent-primary" checked={endpoint.enabled} onChange={(event) => change({ enabled: event.target.checked })} />
        Call this endpoint on schedule
      </label>

      <div className="flex justify-end gap-2">
        <AppButton variant="ghost" size="sm" onClick={onCancel}>
          Cancel
        </AppButton>
        <AppButton size="sm" loading={saving} onClick={() => (check() ? onSave(endpoint) : undefined)}>
          {saveLabel}
        </AppButton>
      </div>
    </div>
  );
}

/** The answer of a test call: status line, where the items are (with Use) and the body to look through. */
function TestCallPreview({ result, onUseItems }: { result: ApiTestResultDto; onUseItems: (itemsPath: string, itemKeyPath?: string) => void }) {
  const items = result.ok && !result.bodyTruncated ? detectItems(result.body) : null;
  return (
    <div role="status" className="grid gap-2 text-sm">
      <p className="flex flex-wrap items-center gap-2">
        <span aria-hidden="true" className={cn("size-1.5 rounded-full", result.ok ? "bg-success" : "bg-destructive")} />
        {result.error ? (
          <span className="text-destructive">{result.error}</span>
        ) : (
          <span className="tabular-nums">
            {[result.status, `${result.durationMs} ms`, result.contentType].filter(Boolean).join(" · ")}
          </span>
        )}
      </p>
      {items ? (
        <p className="flex flex-wrap items-center gap-2 text-muted-foreground">
          <span>
            {items.count} {items.count === 1 ? "item" : "items"} at {items.itemsPath}
          </span>
          <AppButton variant="outline" size="xs" onClick={() => onUseItems(items.itemsPath, items.itemKeyPath)}>
            Use
          </AppButton>
        </p>
      ) : null}
      {result.body ? (
        <details className="rounded-md border">
          <summary className="cursor-pointer px-3 py-1.5 text-xs text-muted-foreground">
            Answer{result.bodyTruncated ? " (first part only)" : ""}
          </summary>
          <pre className="max-h-80 overflow-auto border-t px-3 py-2 font-mono text-xs whitespace-pre-wrap break-all">{prettyBody(result.body)}</pre>
        </details>
      ) : null}
    </div>
  );
}

export interface EndpointRow {
  key: string | number;
  name: string;
  method: EndpointMethod;
  path: string;
  enabled: boolean;
}

/** The endpoints of an app as plain rows with Edit and Remove. */
export function EndpointRows({ rows, onEdit, onRemove }: { rows: readonly EndpointRow[]; onEdit: (key: EndpointRow["key"]) => void; onRemove: (key: EndpointRow["key"]) => void }) {
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">No endpoints yet. Add the first one to tell contor what to call.</p>;
  return (
    <ul className="-mx-2 grid">
      {rows.map((row) => (
        <li key={row.key} aria-label={row.name} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-muted/50">
          <span className="grid min-w-0 flex-1">
            <span className="truncate text-sm font-medium">
              {row.name}
              {row.enabled ? null : <span className="font-normal text-muted-foreground"> · off</span>}
            </span>
            <span className="truncate font-mono text-xs text-muted-foreground">
              <span className="mr-1.5">{row.method}</span>
              {row.path}
            </span>
          </span>
          <AppButton variant="ghost" size="icon-xs" aria-label={`Edit ${row.name}`} onClick={() => onEdit(row.key)}>
            <Pencil aria-hidden="true" />
          </AppButton>
          <AppButton variant="ghost" size="icon-xs" aria-label={`Remove ${row.name}`} onClick={() => onRemove(row.key)}>
            <Trash2 aria-hidden="true" />
          </AppButton>
        </li>
      ))}
    </ul>
  );
}
