import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { AiModelStatus } from "./AiModelStatus";

const missing = {
  reachable: true,
  version: "0.40.1",
  ready: false,
  models: [
    { name: "qwen3.5:9b", roles: ["EXTRACTION", "ROUTING"], installed: true, pullState: "IDLE" },
    { name: "nomic-embed-text", roles: ["EMBEDDING"], installed: false, pullState: "IDLE" },
  ],
};

describe("AiModelStatus", () => {
  it("explains how to start Ollama when it is not reachable", async () => {
    mockApi({ "GET /api/ai/status": { body: { reachable: false, ready: false, models: missing.models } } });

    renderWithProviders(<AiModelStatus />);

    expect(await screen.findByText(/Ollama is not running/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /download/i })).not.toBeInTheDocument();
  });

  it("lists the models and downloads the missing ones", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: missing },
      "POST /api/ai/models/pull": {
        status: 202,
        body: { ...missing, models: [missing.models[0], { ...missing.models[1], pullState: "PULLING", pullPercent: 40 }] },
      },
    });

    renderWithProviders(<AiModelStatus />);

    expect(await screen.findByText("qwen3.5:9b")).toBeInTheDocument();
    expect(screen.getByText("Ollama 0.40.1")).toBeInTheDocument();
    expect(screen.getByText("Installed")).toBeInTheDocument();
    expect(screen.getByText("Not installed")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /download missing models/i }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/ai/models/pull")).toBe(true));
    expect(await screen.findByText("Downloading 40%")).toBeInTheDocument();
  });

  it("confirms when everything is ready", async () => {
    mockApi({
      "GET /api/ai/status": {
        body: { ...missing, ready: true, models: missing.models.map((model) => ({ ...model, installed: true })) },
      },
    });

    renderWithProviders(<AiModelStatus />);

    expect(await screen.findByText(/ready/i, { selector: "p" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /download/i })).not.toBeInTheDocument();
  });
});
