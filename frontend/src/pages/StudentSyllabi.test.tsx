import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken, type SyllabusDetail } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import StudentSyllabi from "./StudentSyllabi";

const STUDENT = {
  id: 1,
  email: "ada@example.com",
  fullName: "Ada Lovelace",
  role: "STUDENT" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
};

function approvedDetail(overrides: Partial<SyllabusDetail> = {}): SyllabusDetail {
  return {
    syllabus: {
      id: 1,
      classId: 10,
      classCode: "SEC-A",
      className: "Section A",
      subjectId: 7,
      subjectCode: "CS101",
      subjectName: "Intro to CS",
      semesterId: 2,
      semesterName: "Semester 1",
      academicYearName: "2026-27",
      uploadedById: 2,
      uploadedByName: "Bob",
      originalFilename: "syllabus.pdf",
      contentType: "application/pdf",
      fileSize: 2048,
      status: "APPROVED",
      version: 1,
      current: true,
      extractionError: null,
      reviewNote: null,
      reviewedById: 2,
      reviewedAt: "2026-01-02T00:00:00Z",
      uploadedAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
    },
    units: [
      {
        id: 100,
        title: "Unit I: Foundations",
        position: 0,
        topics: [
          {
            id: 200,
            title: "1.1 History",
            position: 0,
            subtopics: ["early machines", "first compilers"],
          },
        ],
      },
    ],
    learningOutcomes: [{ id: 300, statement: "Explain binary representation", position: 0, unitId: null }],
    extractedText: null,
    warning: null,
    ...overrides,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function installFetch(payload: unknown, status = 200) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/auth/me")) return jsonResponse(STUDENT);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
      if (url.includes("/student/syllabi")) return jsonResponse(payload, status);
      throw new TypeError(`Unexpected request: ${url}`);
    }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/student/syllabi"]}>
      <AuthProvider>
        <StudentSyllabi />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("StudentSyllabi", () => {
  beforeEach(() => {
    resetCsrfToken();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetCsrfToken();
  });

  it("shows a loading state first", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/me")) return jsonResponse(STUDENT);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        return new Promise(() => {});
      }),
    );

    renderPage();

    expect(screen.getByText(/loading your syllabi/i)).toBeInTheDocument();
  });

  it("renders approved units, topics, subtopics and outcomes", async () => {
    installFetch([approvedDetail()]);

    renderPage();

    expect(await screen.findByText(/CS101 — Intro to CS/)).toBeInTheDocument();
    expect(screen.getByText("Unit I: Foundations")).toBeInTheDocument();
    expect(screen.getByText("1.1 History")).toBeInTheDocument();
    expect(screen.getByText("early machines")).toBeInTheDocument();
    expect(screen.getByText("first compilers")).toBeInTheDocument();
    expect(screen.getByText(/explain binary representation/i)).toBeInTheDocument();
  });

  it("never shows approve, reject or edit controls", async () => {
    installFetch([approvedDetail()]);

    renderPage();
    await screen.findByText(/CS101 — Intro to CS/);

    expect(screen.queryByRole("button", { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /reject/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /save/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/unit 1 title/i)).not.toBeInTheDocument();
  });

  it("shows a clear empty state when nothing is published", async () => {
    installFetch([]);

    renderPage();

    expect(
      await screen.findByText(/no syllabus has been published for your classes yet/i),
    ).toBeInTheDocument();
  });

  it("explains when a teacher has not organised any units", async () => {
    installFetch([approvedDetail({ units: [] })]);

    renderPage();

    expect(
      await screen.findByText(/has not organised any units yet/i),
    ).toBeInTheDocument();
  });

  it("surfaces an API error instead of an empty list", async () => {
    installFetch({ message: "Authentication required" }, 401);

    renderPage();

    await waitFor(() =>
      expect(screen.getByText("Authentication required")).toBeInTheDocument(),
    );
  });

  it("only ever requests the student-scoped endpoint", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/auth/me")) return jsonResponse(STUDENT);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
      if (url.includes("/student/syllabi")) return jsonResponse([]);
      throw new TypeError(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    renderPage();
    await screen.findByText(/no syllabus has been published/i);

    // The teacher API must not be touched from the student page.
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/teacher/"))).toBe(false);
  });
});