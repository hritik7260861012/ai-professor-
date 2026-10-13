import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { AuthProvider, dashboardPathFor, useAuth } from "./AuthContext";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function routeFetch(routes: Array<[RegExp, () => Response]>) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const match = routes.find(([pattern]) => pattern.test(url));
    if (!match) throw new TypeError(`Unexpected request: ${url}`);
    return match[1]();
  });
}

function Probe() {
  const { user, loading } = useAuth();
  if (loading) return <span>loading</span>;
  return <span>{user ? `${user.email}:${user.role}` : "anonymous"}</span>;
}

function SignOutButton() {
  const { logout } = useAuth();
  return (
    <button type="button" onClick={() => void logout()}>
      sign out
    </button>
  );
}

function renderAuth() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Probe />
        <SignOutButton />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("resolves the signed-in user from /auth/me on mount", async () => {
    vi.stubGlobal(
      "fetch",
      routeFetch([
        [/\/auth\/me$/, () => jsonResponse({ id: 7, email: "ada@example.com", role: "ADMIN" })],
      ]),
    );

    renderAuth();

    expect(await screen.findByText("ada@example.com:ADMIN")).toBeInTheDocument();
  });

  it("treats a 401 from /auth/me as a signed-out visitor", async () => {
    vi.stubGlobal(
      "fetch",
      routeFetch([
        [
          /\/auth\/me$/,
          () =>
            jsonResponse(
              { status: 401, message: "Authentication required" },
              401,
            ),
        ],
      ]),
    );

    renderAuth();

    expect(await screen.findByText("anonymous")).toBeInTheDocument();
  });

  it("clears local state after signing out even if the server call fails", async () => {
    vi.stubGlobal(
      "fetch",
      routeFetch([
        [/\/auth\/me$/, () => jsonResponse({ id: 7, email: "ada@example.com", role: "STUDENT" })],
        [
          /\/auth\/logout$/,
          () => {
            throw new TypeError("network down");
          },
        ],
      ]),
    );

    renderAuth();
    await screen.findByText("ada@example.com:STUDENT");

    await userEvent.click(screen.getByRole("button", { name: "sign out" }));

    await waitFor(() => expect(screen.getByText("anonymous")).toBeInTheDocument());
  });

  it("does not request /auth/me again when the probe fails", async () => {
    const fetchMock = routeFetch([
      [/\/auth\/me$/, () => jsonResponse({ message: "boom" }, 500)],
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderAuth();

    await screen.findByText("anonymous");
    const meCalls = fetchMock.mock.calls.filter(([url]) => String(url).includes("/auth/me"));
    expect(meCalls).toHaveLength(1);
  });
});

describe("dashboardPathFor", () => {
  it("maps roles to their dashboard path", () => {
    expect(dashboardPathFor("STUDENT")).toBe("/dashboard/student");
    expect(dashboardPathFor("TEACHER")).toBe("/dashboard/teacher");
    expect(dashboardPathFor("ADMIN")).toBe("/dashboard/admin");
  });
});

describe("ApiError usage in auth", () => {
  it("exposes the status so guards can distinguish 401", () => {
    expect(new ApiError(401, "Authentication required").status).toBe(401);
  });
});