"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Plus } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { LoadingForm } from "@/components/app/LoadingSkeletons";
import { Stepper } from "@/components/app/Stepper";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  getGetExternalApiQueryKey,
  getListExternalApiEndpointsQueryKey,
  getListExternalApisQueryKey,
  useCreateExternalApi,
  useCreateExternalApiEndpoint,
  useDeleteExternalApi,
  useDeleteExternalApiEndpoint,
  useGetExternalApi,
  useListExternalApiEndpoints,
  useUpdateExternalApi,
  useUpdateExternalApiEndpoint,
} from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import {
  draftOf,
  emptyDraft,
  emptyEndpoint,
  endpointDraftOf,
  newRowId,
  toNewCommand,
  toNewEndpointCommand,
  toUpdateCommand,
  toUpdateEndpointCommand,
  validateAuth,
  validateBasics,
  type AppDraft,
  type EndpointDraft,
  type FieldErrors,
} from "@/lib/external-apis";
import { APP_PARAM, appHref } from "@/lib/navigation";
import { AuthFields, BasicsFields, ScheduleFields } from "./AppFormFields";
import { EndpointEditor, EndpointRows } from "./EndpointEditor";

const STEPS = ["Basics", "Auth & headers", "Endpoints", "Schedule"] as const;
type Step = (typeof STEPS)[number];
const LAST_STEP = STEPS.length - 1;

/** Errors of one step; Endpoints and Schedule have none of their own. */
function stepErrors(step: Step, draft: AppDraft, storedSecretHeaders?: readonly string[]): FieldErrors {
  if (step === "Basics") return validateBasics(draft);
  if (step === "Auth & headers") return validateAuth(draft, storedSecretHeaders);
  return {};
}

function hasErrors(errors: FieldErrors): boolean {
  return Object.keys(errors).length > 0;
}

/**
 * Add or edit an app (external API) as a modal over the current page: `?app=new` walks through the steps,
 * `?app=<id>` shows the same steps as tabs. Open state lives in the URL, like the settings.
 */
export function AppDialog() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const requested = searchParams.get(APP_PARAM);
  const apiId = requested !== null && /^\d+$/.test(requested) ? Number(requested) : null;
  const adding = requested === "new";
  const open = adding || apiId !== null;

  function close() {
    const remaining = new URLSearchParams(searchParams.toString());
    remaining.delete(APP_PARAM);
    const query = remaining.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  function showSaved(id: number) {
    router.replace(appHref(pathname, id), { scroll: false });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? undefined : close())}>
      <DialogContent className="flex max-h-[min(90svh,52rem)] w-[min(46rem,calc(100%-2rem))] max-w-none flex-col gap-6 overflow-clip sm:max-w-none sm:p-8">
        {adding ? <NewApp onDone={close} onPartlySaved={showSaved} /> : null}
        {apiId !== null ? <EditApp key={apiId} apiId={apiId} onDeleted={close} /> : null}
      </DialogContent>
    </Dialog>
  );
}

interface LocalEndpoint {
  key: string;
  draft: EndpointDraft;
}

/** The steps of a new app; endpoints stay local until the app is saved, then they are added one by one. */
function NewApp({ onDone, onPartlySaved }: { onDone: () => void; onPartlySaved: (apiId: number) => void }) {
  const queryClient = useQueryClient();
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState(emptyDraft);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [endpoints, setEndpoints] = useState<LocalEndpoint[]>([]);
  const [editing, setEditing] = useState<string | "new" | null>(null);
  const [saving, setSaving] = useState(false);
  const createApi = useCreateExternalApi();
  const createEndpoint = useCreateExternalApiEndpoint();

  function goTo(next: number) {
    if (next > step) {
      // Every step before the target must be fine.
      for (let index = step; index < next; index++) {
        const found = stepErrors(STEPS[index], draft);
        if (hasErrors(found)) {
          setErrors(found);
          setStep(index);
          return;
        }
      }
    }
    setErrors({});
    setEditing(null);
    setStep(next);
  }

  function keepEndpoint(endpoint: EndpointDraft) {
    setEndpoints((current) =>
      editing === "new" ? [...current, { key: newRowId(), draft: endpoint }] : current.map((entry) => (entry.key === editing ? { ...entry, draft: endpoint } : entry)),
    );
    setEditing(null);
  }

  async function save() {
    setSaving(true);
    let saved: ExternalApiDto;
    try {
      saved = await createApi.mutateAsync({ data: toNewCommand(draft) });
    } catch {
      // Shown as a toast by the query client; the form stays as it is.
      setSaving(false);
      return;
    }
    let complete = true;
    for (const endpoint of endpoints) {
      try {
        await createEndpoint.mutateAsync({ apiId: saved.id, data: toNewEndpointCommand(endpoint.draft) });
      } catch {
        complete = false;
      }
    }
    await queryClient.invalidateQueries({ queryKey: getListExternalApisQueryKey() });
    setSaving(false);
    if (complete) {
      toast.success(`${saved.name} added`);
      onDone();
    } else {
      // The app exists; what failed can be fixed in its edit view.
      onPartlySaved(saved.id);
    }
  }

  const editingDraft = editing === "new" ? emptyEndpoint() : endpoints.find((entry) => entry.key === editing)?.draft;

  return (
    <>
      <div className="grid gap-1.5 pr-8">
        <DialogTitle>Add app</DialogTitle>
        <DialogDescription>Connect a shop, a job platform or any other HTTPS API. contor calls it now and then and only reads.</DialogDescription>
      </div>
      <Stepper label="Steps" steps={STEPS} current={step} onSelect={goTo} />
      <div className="-mx-1 min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-1">
        {step === 0 ? <BasicsFields draft={draft} onChange={setDraft} errors={errors} /> : null}
        {step === 1 ? <AuthFields draft={draft} onChange={setDraft} errors={errors} /> : null}
        {step === 2 ? (
          <div className="grid gap-4">
            <p className="text-sm text-muted-foreground">What contor calls. Try each one with a test call; nothing is saved before the last step.</p>
            {editingDraft ? (
              <EndpointEditor initial={editingDraft} app={draft} saveLabel="Keep endpoint" onSave={keepEndpoint} onCancel={() => setEditing(null)} />
            ) : (
              <>
                <EndpointRows
                  rows={endpoints.map(({ key, draft: endpoint }) => ({ key, ...endpoint }))}
                  onEdit={(key) => setEditing(String(key))}
                  onRemove={(key) => setEndpoints((current) => current.filter((entry) => entry.key !== key))}
                />
                <AppButton variant="outline" size="sm" className="w-fit" onClick={() => setEditing("new")}>
                  <Plus aria-hidden="true" data-icon="inline-start" />
                  Add endpoint
                </AppButton>
              </>
            )}
          </div>
        ) : null}
        {step === LAST_STEP ? <ScheduleFields draft={draft} onChange={setDraft} /> : null}
      </div>
      <div className="flex justify-between gap-2">
        {step > 0 ? (
          <AppButton variant="ghost" onClick={() => goTo(step - 1)}>
            <ArrowLeft aria-hidden="true" data-icon="inline-start" />
            Back
          </AppButton>
        ) : (
          <span />
        )}
        {step < LAST_STEP ? (
          <AppButton disabled={editing !== null} onClick={() => goTo(step + 1)}>
            Next step
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </AppButton>
        ) : (
          <AppButton loading={saving} onClick={save}>
            Add app
          </AppButton>
        )}
      </div>
    </>
  );
}

function EditApp({ apiId, onDeleted }: { apiId: number; onDeleted: () => void }) {
  const { data: api, isPending, error } = useGetExternalApi(apiId);
  if (isPending) {
    return (
      <>
        <DialogTitle className="sr-only">App</DialogTitle>
        <DialogDescription className="sr-only">Loading the app.</DialogDescription>
        <LoadingForm label="Loading app" sections={2} />
      </>
    );
  }
  if (error || !api) {
    return (
      <>
        <DialogTitle>App</DialogTitle>
        <DialogDescription>{error?.status === 404 ? "This app does not exist (any more)." : "The app could not be loaded."}</DialogDescription>
      </>
    );
  }
  // Keyed by the last change: a save elsewhere starts the form from the saved values.
  return <EditForm key={api.lastModified ?? api.createdAt} api={api} onDeleted={onDeleted} />;
}

/** The saved app as tabs; Basics, Auth & headers and Schedule save together, endpoints save one by one. */
function EditForm({ api, onDeleted }: { api: ExternalApiDto; onDeleted: () => void }) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Step>("Basics");
  const [draft, setDraft] = useState(() => draftOf(api));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const updateApi = useUpdateExternalApi();
  const deleteApi = useDeleteExternalApi();

  const command = toUpdateCommand(draft, api);
  // Replace pressed: the empty secret field must be checked even though nothing would be sent yet.
  const dirty = Object.keys(command).length > 0 || (api.hasSecret && draft.secret !== null);

  async function save() {
    for (const step of STEPS) {
      const found = stepErrors(step, draft, api.secretHeaderNames);
      if (hasErrors(found)) {
        setErrors(found);
        setTab(step);
        return;
      }
    }
    setErrors({});
    try {
      const saved = await updateApi.mutateAsync({ apiId: api.id, data: command });
      queryClient.setQueryData(getGetExternalApiQueryKey(api.id), saved);
      await queryClient.invalidateQueries({ queryKey: getListExternalApisQueryKey() });
      toast.success("Saved");
    } catch {
      // Shown as a toast by the query client.
    }
  }

  async function remove() {
    try {
      await deleteApi.mutateAsync({ apiId: api.id });
      queryClient.removeQueries({ queryKey: getGetExternalApiQueryKey(api.id) });
      await queryClient.invalidateQueries({ queryKey: getListExternalApisQueryKey() });
      toast.success(`${api.name} deleted`);
      onDeleted();
    } catch {
      // Shown as a toast by the query client.
    }
  }

  return (
    <>
      <div className="grid gap-1.5 pr-8">
        <DialogTitle>{api.name}</DialogTitle>
        <DialogDescription>{api.lastError ?? "Changes take effect with the next call."}</DialogDescription>
      </div>
      <Tabs value={tab} onValueChange={(value) => setTab(value as Step)} className="flex min-h-0 flex-1 flex-col gap-5">
        <TabsList variant="line" className="w-full justify-start">
          {STEPS.map((step) => (
            <TabsTrigger key={step} value={step} className="flex-none text-xs">
              {step}
            </TabsTrigger>
          ))}
        </TabsList>
        <div className="-mx-1 min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-1">
          <TabsContent value="Basics">
            <BasicsFields draft={draft} onChange={setDraft} errors={errors} />
          </TabsContent>
          <TabsContent value="Auth & headers">
            <AuthFields draft={draft} onChange={setDraft} errors={errors} hasStoredSecret={api.hasSecret} />
          </TabsContent>
          <TabsContent value="Endpoints">
            <SavedEndpoints apiId={api.id} app={draft} />
          </TabsContent>
          <TabsContent value="Schedule">
            <ScheduleFields draft={draft} onChange={setDraft} />
          </TabsContent>
        </div>
      </Tabs>
      {confirmDelete ? (
        <div className="flex flex-wrap items-center justify-end gap-2">
          <span className="mr-auto text-sm">Delete {api.name}? Its endpoints and call history go too.</span>
          <AppButton variant="ghost" onClick={() => setConfirmDelete(false)}>
            Cancel
          </AppButton>
          <AppButton variant="destructive" loading={deleteApi.isPending} onClick={remove}>
            Yes, delete {api.name}
          </AppButton>
        </div>
      ) : (
        <div className="flex justify-between gap-2">
          <AppButton variant="ghost" onClick={() => setConfirmDelete(true)}>
            Delete app
          </AppButton>
          <AppButton disabled={!dirty} loading={updateApi.isPending} onClick={save}>
            Save
          </AppButton>
        </div>
      )}
    </>
  );
}

/** The saved endpoints; adding, editing and removing take effect right away. */
function SavedEndpoints({ apiId, app }: { apiId: number; app: AppDraft }) {
  const queryClient = useQueryClient();
  const { data: endpoints, isPending, error } = useListExternalApiEndpoints(apiId);
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const createEndpoint = useCreateExternalApiEndpoint();
  const updateEndpoint = useUpdateExternalApiEndpoint();
  const deleteEndpoint = useDeleteExternalApiEndpoint();

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: getListExternalApiEndpointsQueryKey(apiId) }),
      queryClient.invalidateQueries({ queryKey: getListExternalApisQueryKey() }),
    ]);
  }

  async function saveEndpoint(endpoint: EndpointDraft) {
    try {
      if (editing === "new") await createEndpoint.mutateAsync({ apiId, data: toNewEndpointCommand(endpoint) });
      else if (editing !== null) await updateEndpoint.mutateAsync({ apiId, endpointId: editing, data: toUpdateEndpointCommand(endpoint) });
      await refresh();
      setEditing(null);
    } catch {
      // Shown as a toast by the query client; the editor stays open.
    }
  }

  async function removeEndpoint(endpointId: number) {
    try {
      await deleteEndpoint.mutateAsync({ apiId, endpointId });
      await refresh();
    } catch {
      // Shown as a toast by the query client.
    }
  }

  if (isPending) return <LoadingForm label="Loading endpoints" sections={1} />;
  if (error) return <ErrorMessage message={error.message} />;

  const current = editing === "new" ? emptyEndpoint() : endpoints.find((endpoint) => endpoint.id === editing);
  if (current) {
    return (
      <EndpointEditor
        initial={"id" in current ? endpointDraftOf(current) : current}
        app={app}
        apiId={apiId}
        saveLabel="Save endpoint"
        saving={createEndpoint.isPending || updateEndpoint.isPending}
        onSave={saveEndpoint}
        onCancel={() => setEditing(null)}
      />
    );
  }
  return (
    <div className="grid gap-4">
      <EndpointRows rows={endpoints.map((endpoint) => ({ ...endpoint, key: endpoint.id }))} onEdit={(key) => setEditing(Number(key))} onRemove={(key) => removeEndpoint(Number(key))} />
      <AppButton variant="outline" size="sm" className="w-fit" onClick={() => setEditing("new")}>
        <Plus aria-hidden="true" data-icon="inline-start" />
        Add endpoint
      </AppButton>
    </div>
  );
}
