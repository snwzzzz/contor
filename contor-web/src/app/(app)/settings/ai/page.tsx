import { Brain } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "AI Models" };

export default function Page() {
  return (
    <>
      <PageHeader title="AI Models" description="Local Ollama models used for routing, extraction and embeddings." />
      <EmptyState icon={Brain} title="AI is not active yet" description="Model selection arrives with the AI pipeline in the next phase. Ollama runs locally and must stay bound to localhost." />
    </>
  );
}
