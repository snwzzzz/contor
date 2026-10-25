"use client";

import { AiModelStatus } from "./AiModelStatus";

/** Settings → AI Models: only the local models (whether Ollama runs, which models are installed, downloads). */
export function AiSettingsPanel() {
  return (
    <section className="grid gap-3">
      <h3 className="text-sm font-semibold">Local model</h3>
      <AiModelStatus />
    </section>
  );
}
