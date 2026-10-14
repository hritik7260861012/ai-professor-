import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken, type StudentContent } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import StudentContentPage from "./StudentContent";

const STUDENT = {
  id: 3,
  email: "carol@example.com",
  fullName: "Carol",
  role: "STUDENT" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
};

function item(overrides: Partial<StudentContent> = {}): StudentContent {
  return {
    id: 900,
    syllabusId: 11,
    subjectCode: "CS101",
    subjectName: "Intro to CS",
    classCode: "SEC-A",
    unitId: 100,
    unitTitle: "Unit I: Foundations",
    contentType: "UNIT_NOTES",
    language: "ENGLISH",
    title: "Unit I notes",
    body: "Released body text.",
    structuredJson: null,
    ...overrides,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function installFetch(listResponse: { body?: unknown; status?: number } = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("/auth/me")) return jsonResponse(STUDENT);
    if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
    if (url.includes("/student/content")) {
      return jsonResponse(listResponse.body ?? [], listResponse.status ?? 200);
    }
    throw new TypeError(`Unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/student/content"]}>
      <AuthProvider>
        <StudentContentPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("StudentContent", () => {
  beforeEach(() => {
    resetCsrfToken();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lists released content returned by the API", async () => {
    installFetch({
      body: [
        item({ id: 900, title: "Unit I notes" }),
        item({ id: 901, title: "MCQ practice", contentType: "MCQS", language: "HINDI" }),
      ],
    });
    renderPage();

    expect(await screen.findByText("Unit I notes")).toBeInTheDocument();
    expect(screen.getByText("MCQ practice")).toBeInTheDocument();
    expect(screen.getByText("Unit-wise notes")).toBeInTheDocument();
    expect(screen.getByText("MCQs")).toBeInTheDocument();
    expect(screen.getByText("Hindi")).toBeInTheDocument();
  });

  it("shows an empty state when nothing has been released", async () => {
    installFetch({ body: [] });
    renderPage();

    expect(await screen.findByText(/nothing has been released yet/i)).toBeInTheDocument();
  });

  it("hides the body until the student asks to open it", async () => {
    installFetch({ body: [item()] });
    renderPage();

    await screen.findByText("Unit I notes");
    expect(screen.queryByText("Released body text.")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /view content/i }));
    expect(screen.getByText("Released body text.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /hide content/i }));
    expect(screen.queryByText("Released body text.")).not.toBeInTheDocument();
  });

  it("never renders review status, release flags or provider metadata", async () => {
    // Even if a server were to leak extra fields, the projection does not
    // define them, so nothing about review state or provenance is shown.
    installFetch({
      body: [
        {
          ...item(),
          reviewStatus: "DRAFT",
          releasedToStudents: false,
          providerName: "GEMINI",
          modelName: "gemini-3.8-flash",
          audience: "TEACHER",
          containsAnswerKey: true,
          rejectionReason: "internal",
        } as unknown as StudentContent,
      ],
    });
    renderPage();

    await screen.findByText("Unit I notes");
    for (const forbidden of [
      "DRAFT",
      "REJECTED",
      "GEMINI",
      "gemini-3.8-flash",
      "Teacher only",
      "Teacher study material",
      "internal",
    ]) {
      expect(screen.queryByText(forbidden)).not.toBeInTheDocument();
    }
  });

  it("surfaces the server message when the list cannot be loaded", async () => {
    installFetch({
      body: { message: "You do not have permission to access this resource" },
      status: 403,
    });
    renderPage();

    expect(
      await screen.findByText(/do not have permission/i),
    ).toBeInTheDocument();
  });

  it("reports a network failure instead of rendering an empty list", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/me")) return jsonResponse(STUDENT);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        throw new TypeError("Failed to fetch");
      }),
    );
    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/unable to reach the server/i)).toBeInTheDocument(),
    );
  });

  it("does not attempt to fetch a draft or teacher-only endpoint", async () => {
    const fetchMock = installFetch({ body: [item()] });
    renderPage();

    await screen.findByText("Unit I notes");
    const contentRequests = fetchMock.mock.calls
      .map(([url]) => String(url))
      .filter((url) => url.includes("/student/content"));

    expect(contentRequests).toHaveLength(1);
    expect(contentRequests[0]).not.toMatch(/\/\d+$/);
  });
});