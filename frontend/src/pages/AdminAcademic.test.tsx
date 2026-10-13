import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import AdminAcademic from "./AdminAcademic";

const ADMIN = {
  id: 1,
  email: "root@example.com",
  fullName: "Root",
  role: "ADMIN" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
};

const DEPARTMENT = {
  id: 1,
  code: "CS",
  name: "Computer Science",
  description: null,
  createdAt: "2026-01-01T00:00:00Z",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const EMPTY_PAGE = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, first: true, last: true };

function installFetch(overrides: { createStatus?: number; createBody?: unknown } = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = String(input);
    const method = (_init?.method ?? "GET").toUpperCase();

    if (url.includes("/auth/me")) return jsonResponse(ADMIN);
    if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });

    if (url.includes("/admin/academic/departments")) {
      if (method === "POST") {
        return jsonResponse(
          overrides.createBody ?? { ...DEPARTMENT, code: "NEW", name: "New department" },
          overrides.createStatus ?? 201,
        );
      }
      return jsonResponse({ ...EMPTY_PAGE, content: [DEPARTMENT] });
    }
    if (url.includes("/admin/academic/")) return jsonResponse(EMPTY_PAGE);
    if (url.includes("/departments")) return jsonResponse({ ...EMPTY_PAGE, content: [DEPARTMENT] });
    if (url.includes("/programs")) return jsonResponse(EMPTY_PAGE);
    if (url.includes("/academic-years")) return jsonResponse(EMPTY_PAGE);
    if (url.includes("/semesters")) return jsonResponse([]);
    if (url.includes("/subjects")) return jsonResponse(EMPTY_PAGE);
    if (url.includes("/classes")) return jsonResponse(EMPTY_PAGE);

    throw new TypeError(`Unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/admin/academic"]}>
      <AuthProvider>
        <AdminAcademic />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("AdminAcademic", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetCsrfToken();
  });

  it("lists existing departments from the real API", async () => {
    installFetch();
    renderPage();

    expect(await screen.findByRole("heading", { name: /academic structure/i })).toBeInTheDocument();
    expect(await screen.findByText(/Computer Science/)).toBeInTheDocument();
  });

  it("creates a department through the admin API", async () => {
    const fetchMock = installFetch();
    renderPage();
    await screen.findByRole("heading", { name: /academic structure/i });

    await userEvent.type(screen.getByLabelText(/^code$/i), "NEW");
    await userEvent.type(screen.getByLabelText(/^name$/i), "New department");
    await userEvent.click(screen.getByRole("button", { name: /add department/i }));

    await waitFor(() => expect(screen.getByText(/Department NEW created/)).toBeInTheDocument());

    const post = fetchMock.mock.calls.find(
      ([url, init]) => String(url).includes("/admin/academic/departments") && init?.method === "POST",
    );
    expect(post).toBeDefined();
    expect(JSON.parse(String((post?.[1] as RequestInit).body))).toEqual({
      code: "NEW",
      name: "New department",
      description: "",
    });
  });

  it("shows the server conflict message when a code already exists", async () => {
    installFetch({
      createStatus: 409,
      createBody: { message: "A department with code CS already exists" },
    });
    renderPage();
    await screen.findByRole("heading", { name: /academic structure/i });

    await userEvent.type(screen.getByLabelText(/^code$/i), "CS");
    await userEvent.type(screen.getByLabelText(/^name$/i), "Duplicate");
    await userEvent.click(screen.getByRole("button", { name: /add department/i }));

    expect(
      await screen.findByText("A department with code CS already exists"),
    ).toBeInTheDocument();
  });

  it("switches between academic sections", async () => {
    installFetch();
    renderPage();
    await screen.findByRole("heading", { name: /departments/i });

    await userEvent.click(screen.getByRole("button", { name: /^subjects$/i }));

    expect(
      await screen.findByRole("heading", { name: /^subjects$/i }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /^classes$/i }));
    expect(await screen.findByRole("heading", { name: /^classes$/i })).toBeInTheDocument();
  });
});