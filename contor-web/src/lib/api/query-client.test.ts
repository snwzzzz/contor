import { describe, expect, it, vi } from "vitest";
import { ApiError } from "./fetcher";
import { createQueryClient } from "./query-client";

function setup() {
  const onUnauthorized = vi.fn();
  const notifyError = vi.fn();
  const client = createQueryClient({ onUnauthorized, notifyError, retry: false });
  return { client, onUnauthorized, notifyError };
}

describe("createQueryClient", () => {
  it("sends the user to the login when a query hits 401", async () => {
    const { client, onUnauthorized } = setup();

    await client.fetchQuery({ queryKey: ["me"], queryFn: () => Promise.reject(new ApiError("Authentication required", 401)) }).catch(() => {});

    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("does not redirect queries that expect a 401", async () => {
    const { client, onUnauthorized } = setup();

    await client
      .fetchQuery({
        queryKey: ["setup"],
        queryFn: () => Promise.reject(new ApiError("Authentication required", 401)),
        meta: { skipAuthRedirect: true },
      })
      .catch(() => {});

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("reports failed mutations with the server message", async () => {
    const { client, notifyError, onUnauthorized } = setup();
    const mutation = client.getMutationCache().build(client, {
      mutationFn: () => Promise.reject(new ApiError("Slug already taken", 409)),
    });

    await mutation.execute(undefined).catch(() => {});

    expect(notifyError).toHaveBeenCalledWith("Slug already taken");
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("stays quiet for silent mutations and redirects on 401", async () => {
    const { client, notifyError, onUnauthorized } = setup();
    const silent = client.getMutationCache().build(client, {
      mutationFn: () => Promise.reject(new ApiError("Invalid", 400)),
      meta: { silent: true },
    });
    const expired = client.getMutationCache().build(client, {
      mutationFn: () => Promise.reject(new ApiError("Session expired", 401)),
    });

    await silent.execute(undefined).catch(() => {});
    await expired.execute(undefined).catch(() => {});

    expect(notifyError).not.toHaveBeenCalled();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("uses a generic message for unexpected errors", async () => {
    const { client, notifyError } = setup();
    const mutation = client.getMutationCache().build(client, { mutationFn: () => Promise.reject(new TypeError("Failed to fetch")) });

    await mutation.execute(undefined).catch(() => {});

    expect(notifyError).toHaveBeenCalledWith("Something went wrong. Please try again.");
  });
});
