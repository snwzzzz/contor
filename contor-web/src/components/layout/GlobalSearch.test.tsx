import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetNavigation, router } from "@/test/next-navigation";
import { GlobalSearch } from "./GlobalSearch";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("GlobalSearch", () => {
  beforeEach(() => resetNavigation());

  it("searches the inbox", async () => {
    render(<GlobalSearch />);

    await userEvent.type(screen.getByRole("searchbox", { name: "Search mail" }), "invoice ACME{Enter}");

    expect(router.push).toHaveBeenCalledWith("/inbox?q=invoice+ACME");
  });

  it("ignores empty searches", async () => {
    render(<GlobalSearch />);

    await userEvent.type(screen.getByRole("searchbox", { name: "Search mail" }), "   {Enter}");

    expect(router.push).not.toHaveBeenCalled();
  });
});
