import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { resetCsrfToken } from "../api/client";
import Login from "./Login";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/dashboard/student" element={<h1>Student dashboard</h1>} />
          <Route path="/dashboard/admin" element={<h1>Admin dashboard</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("Login page", () => {
  // The CSRF token is cached at module scope, so each case starts clean.
  afterEach(() => {
    vi.unstubAllGlobals();
    resetCsrfToken();
  });

  it("signs the user in and routes to the dashboard for their role", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
      if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
      if (url.includes("/auth/login")) {
        return jsonResponse({
          id: 1,
          email: "ada@example.com",
          role: "STUDENT",
          fullName: "Ada",
        });
      }
      throw new TypeError(`Unexpected: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    renderLogin();

    await screen.findByRole("heading", { name: /welcome back/i });

    await userEvent.type(screen.getByLabelText(/email address/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "super-secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Student dashboard")).toBeInTheDocument();

    // The session cookie must be sent on the sign-in request.
    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/auth/login"));
    expect(loginCall?.[1]).toMatchObject({ credentials: "include" });
  });

  it("shows the server message for bad credentials", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
        return jsonResponse({ message: "Invalid email or password" }, 401);
      }),
    );

    renderLogin();
    await screen.findByRole("heading", { name: /welcome back/i });

    await userEvent.type(screen.getByLabelText(/email address/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
  });

  it("shows the suspended message when the server returns 403", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
        return jsonResponse({ message: "This account has been suspended" }, 403);
      }),
    );

    renderLogin();
    await screen.findByRole("heading", { name: /welcome back/i });

    await userEvent.type(screen.getByLabelText(/email address/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "super-secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("This account has been suspended")).toBeInTheDocument();
  });

  it("attaches the CSRF header to the sign-in request", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "csrf-123" });
      if (url.includes("/auth/me")) return jsonResponse({ message: "nope" }, 401);
      return jsonResponse({ id: 1, email: "ada@example.com", role: "ADMIN", fullName: "Ada" });
    });
    vi.stubGlobal("fetch", fetchMock);

    renderLogin();
    await screen.findByRole("heading", { name: /welcome back/i });

    await userEvent.type(screen.getByLabelText(/email address/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "super-secret");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await screen.findByText("Admin dashboard");

    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/auth/login"));
    expect((loginCall?.[1] as RequestInit).headers).toMatchObject({
      "X-CSRF-TOKEN": "csrf-123",
    });
  });
});