import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { resetCsrfToken } from "../api/client";
import Register from "./Register";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={["/register"]}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<h1>Login page</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillForm(password = "super-secret", confirm = "super-secret") {
  await userEvent.type(screen.getByLabelText(/full name/i), "Ada Lovelace");
  await userEvent.type(screen.getByLabelText(/email address/i), "ada@example.com");
  await userEvent.type(screen.getByLabelText(/^password$/i), password);
  await userEvent.type(screen.getByLabelText(/confirm password/i), confirm);
}

describe("Register page", () => {
  // The CSRF token is cached at module scope, so each case starts clean.
  afterEach(() => {
    vi.unstubAllGlobals();
    resetCsrfToken();
  });

  it("creates a student account and sends no role to the server", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
      if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
      if (url.includes("/auth/register")) {
        return jsonResponse(
          {
            id: 5,
            email: "ada@example.com",
            role: "STUDENT",
            status: "ACTIVE",
            fullName: "Ada Lovelace",
            createdAt: "2026-01-01T00:00:00Z",
          },
          201,
        );
      }
      throw new TypeError(`Unexpected: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    renderRegister();
    await screen.findByRole("heading", { name: /create your account/i });
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: /create student account/i }));

    await screen.findByText("Login page");

    const registerCall = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/auth/register"),
    );
    const payload = JSON.parse(String((registerCall?.[1] as RequestInit).body));

    // Public registration never sends a privileged role.
    expect(payload).toEqual({
      fullName: "Ada Lovelace",
      email: "ada@example.com",
      password: "super-secret",
    });
  });

  it("explains that public sign-up is student-only", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse({ message: "nope" }, 401)));

    renderRegister();

    expect(await screen.findByText(/public sign-up creates a/i)).toBeInTheDocument();
    // No role picker is offered on the public form.
    expect(screen.queryByLabelText(/i am a/i)).not.toBeInTheDocument();
  });

  it("validates mismatched passwords before calling the API", async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse({ message: "nope" }, 401),
    );
    vi.stubGlobal("fetch", fetchMock);

    renderRegister();
    await screen.findByRole("heading", { name: /create your account/i });

    await fillForm("super-secret", "different-secret");
    await userEvent.click(screen.getByRole("button", { name: /create student account/i }));

    expect(await screen.findByText("Passwords do not match")).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes("/auth/register")),
    ).toBe(false);
  });

  it("surfaces the server conflict message for a duplicate email", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
        return jsonResponse(
          { message: "An account with this email address already exists" },
          409,
        );
      }),
    );

    renderRegister();
    await screen.findByRole("heading", { name: /create your account/i });
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: /create student account/i }));

    expect(
      await screen.findByText("An account with this email address already exists"),
    ).toBeInTheDocument();
  });
});