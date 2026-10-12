import { describe, expect, it, vi } from "vitest";
import { ApiError, api } from "./client";

describe("api client", () => {
  it("parses successful JSON responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), { status: 200 }),
      ),
    );

    const data = await api.get<{ ok: boolean }>("/health");

    expect(data).toEqual({ ok: true });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/health"),
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("throws ApiError with the server-provided message", async () => {
    const body = {
      timestamp: "2026-01-01T00:00:00Z",
      status: 400,
      error: "Bad Request",
      message: "Validation failed",
      path: "/api/v1/auth/register",
      details: { fields: [{ field: "email", message: "Email must be a valid address" }] },
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 400 })),
    );

    await expect(api.post("/auth/register", {})).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "Validation failed",
    });
  });

  it("returns undefined for 204 No Content", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
    );

    await expect(api.post("/auth/login", {})).resolves.toBeUndefined();
  });

  it("converts network failures into a friendly ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(api.get("/health")).rejects.toMatchObject({
      status: 0,
      message: "Unable to reach the server. Is the backend running?",
    });
  });
});

describe("ApiError", () => {
  it("exposes status and details", () => {
    const error = new ApiError(501, "Not implemented", { phase: 2 });
    expect(error.status).toBe(501);
    expect(error.message).toBe("Not implemented");
    expect(error.details).toEqual({ phase: 2 });
    expect(error.name).toBe("ApiError");
  });
});
