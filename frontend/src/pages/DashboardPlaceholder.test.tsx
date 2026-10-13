import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import DashboardPlaceholder from "./DashboardPlaceholder";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

interface TestUser {
  id: number;
  email: string;
  fullName: string;
  role: "STUDENT" | "TEACHER" | "ADMIN";
  status: "ACTIVE";
  createdAt: string;
}

const STUDENT: TestUser = {
  id: 1,
  email: "ada@example.com",
  fullName: "Ada Lovelace",
  role: "STUDENT",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
};

const TEACHER: TestUser = {
  id: 2,
  email: "bob@example.com",
  fullName: "Bob",
  role: "TEACHER",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
};

const ENROLLED_CLASS = {
  id: 10,
  programId: 1,
  programCode: "BSC",
  programName: "BSc Computer Science",
  semesterId: 2,
  semesterName: "Semester 1",
  academicYearName: "2026-27",
  code: "SEC-A",
  name: "Section A",
  capacity: 30,
  enrolledCount: 1,
};

interface RouteMap {
  user?: TestUser;
  classes?: unknown[];
  subjects?: unknown[];
  enrollments?: unknown[];
  assignments?: unknown[];
  meStatus?: number;
}

function installFetch(map: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = String(input);
    if (url.includes("/auth/me")) {
      return map.meStatus && map.meStatus !== 200
        ? jsonResponse({ message: "Authentication required" }, map.meStatus)
        : jsonResponse(map.user ?? STUDENT);
    }
    if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
    if (url.includes("/me/classes")) {
      return jsonResponse(map.classes ?? []);
    }
    if (url.includes("/me/enrollments")) return jsonResponse(map.enrollments ?? []);
    if (url.includes("/me/assignments")) return jsonResponse(map.assignments ?? []);
    if (/\/classes\/\d+\/subjects/.test(url)) return jsonResponse(map.subjects ?? []);
    throw new TypeError(`Unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderDashboard(roleKey: string) {
  return render(
    <MemoryRouter initialEntries={[`/dashboard/${roleKey}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/dashboard/:role" element={<DashboardPlaceholder />} />
          <Route path="/admin/academic" element={<h1>Admin academic page</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("DashboardPlaceholder academic section", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows a student's enrolled classes", async () => {
    installFetch({ user: STUDENT, classes: [ENROLLED_CLASS], enrollments: [{ id: 1 }] });

    renderDashboard("student");

    expect(await screen.findByText(/my classes/i)).toBeInTheDocument();
    expect(await screen.findByText(/SEC-A/)).toBeInTheDocument();
    expect(await screen.findByText(/enrolled in 1 class/i)).toBeInTheDocument();
  });

  it("explains that an unenrolled student has no classes", async () => {
    installFetch({ user: STUDENT, classes: [], enrollments: [] });

    renderDashboard("student");

    expect(
      await screen.findByText(/not enrolled in any classes yet/i),
    ).toBeInTheDocument();
  });

  it("loads and shows a class's subjects on demand", async () => {
    const fetchMock = installFetch({
      user: STUDENT,
      classes: [ENROLLED_CLASS],
      subjects: [
        {
          id: 5,
          classId: 10,
          subjectId: 7,
          subjectCode: "CS101",
          subjectName: "Intro to CS",
          credits: 4,
          semesterId: 2,
        },
      ],
    });

    renderDashboard("student");
    await screen.findByText(/SEC-A/);

    await userEvent.click(screen.getByRole("button", { name: /view subjects/i }));

    expect(await screen.findByText(/CS101/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("/classes/10/subjects"))).toBe(
      true,
    );
  });

  it("shows a teacher's assignments and classes", async () => {
    installFetch({
      user: TEACHER,
      classes: [ENROLLED_CLASS],
      assignments: [
        {
          id: 3,
          teacherId: 2,
          teacherName: "Bob",
          classId: 10,
          classCode: "SEC-A",
          subjectId: 7,
          subjectCode: "CS101",
          semesterId: 2,
          semesterName: "Semester 1",
          assignedAt: "2026-01-01T00:00:00Z",
        },
      ],
    });

    renderDashboard("teacher");

    expect(await screen.findByText(/my teaching assignments/i)).toBeInTheDocument();
    expect(await screen.findByText(/Teaching CS101 in SEC-A/)).toBeInTheDocument();
  });

  it("tells an unassigned teacher why the list is empty", async () => {
    installFetch({ user: TEACHER, classes: [], assignments: [] });

    renderDashboard("teacher");

    expect(
      await screen.findByText(/no classes have been assigned to you yet/i),
    ).toBeInTheDocument();
  });

  it("points an admin at the academic management page instead of a class list", async () => {
    const admin = { ...STUDENT, role: "ADMIN" as const, email: "root@example.com" };
    installFetch({ user: admin });

    renderDashboard("admin");

    expect(
      await screen.findByRole("link", { name: /open academic management/i }),
    ).toHaveAttribute("href", "/admin/academic");
    // Admins get no personal class list.
    expect(screen.queryByText(/my classes/i)).not.toBeInTheDocument();
  });

  it("surfaces an API error instead of an empty list", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/auth/me")) return jsonResponse(STUDENT);
      if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
      if (url.includes("/me/classes")) {
        return jsonResponse({ message: "Something went wrong" }, 500);
      }
      return jsonResponse([]);
    });
    vi.stubGlobal("fetch", fetchMock);

    renderDashboard("student");

    await waitFor(() =>
      expect(screen.getByText("Something went wrong")).toBeInTheDocument(),
    );
  });
});