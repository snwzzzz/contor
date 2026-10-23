import { useQuery } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { LoadingBar } from "./LoadingBar";

function Pending() {
  useQuery({ queryKey: ["never"], queryFn: () => new Promise<never>(() => undefined) });
  return null;
}

function Refetching() {
  useQuery({ queryKey: ["cached"], queryFn: () => new Promise<string>(() => undefined), initialData: "cached", staleTime: 0 });
  return null;
}

describe("LoadingBar", () => {
  it("runs along the top while something is loading for the first time", async () => {
    renderWithProviders(
      <>
        <LoadingBar />
        <Pending />
      </>,
    );

    expect(await screen.findByRole("progressbar", { name: "Loading" })).toBeInTheDocument();
  });

  it("stays hidden for background refreshes of data that is already shown", async () => {
    renderWithProviders(
      <>
        <LoadingBar />
        <Refetching />
      </>,
    );

    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });
});
