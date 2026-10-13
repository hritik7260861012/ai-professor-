import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken, type SyllabusDetail } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import TeacherSyllabi, { validateFile } from "./TeacherSyllabi";

const TEACHER = {
  id: 2,
  email: "bob@example.com",
  fullName: "Bob",
  role: "TEACHER" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
};

const CLASS = {
  id: 10,
  programId: 1,
  programCode: "BSC",
  programName: "BSc CS",
  semesterId: 2,
  semesterName: "Semester 1",
  academicYearName: "2026-27",
  code: "SEC-A",
  name: "Section A",
  capacity: 30,
  enrolledCount: 12,
};

function detail(overrides: Partial<SyllabusDetail> = {}): SyllabusDetail {
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
      originalFilename: "syllabus.txt",
      contentType: "text/plain",
      fileSize: 120,
      status: "REVIEW_REQUIRED",
      version: 1,
      current: true,
      extractionError: null,
      reviewNote: null,
      reviewedById: null,
      reviewedAt: null,
      uploadedAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    units: [
      {
        id: 100,
        title: "Unit I: Foundations",
        position: 0,
        topics: [
          { id: 200, title: "1.1 History", position: 0, subtopics: ["early machines"] },
        ],
      },
      {
        id: 101,
        title: "Unit II: Programming",
        position: 1,
        topics: [{ id: 201, title: "2.1 Variables", position: 0, subtopics: [] }],
      },
    ],
    learningOutcomes: [{ id: 300, statement: "Explain binary", position: 0, unitId: null }],
    extractedText: "Unit I: Foundations\n1.1 History",
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

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

interface Routes {
  upload?: { body: unknown; status?: number };
  structure?: { body: unknown; status?: number };
  approve?: { body: unknown; status?: number };
  reject?: { body: unknown; status?: number };
  listContent?: unknown[];
}

function installFetch(routes: Routes = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();

    if (url.includes("/auth/me")) return jsonResponse(TEACHER);
    if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });

    if (url.includes("/me/classes")) return jsonResponse([CLASS]);
    if (/\/classes\/\d+\/subjects/.test(url)) {
      return jsonResponse([
        { id: 1, classId: 10, subjectId: 7, subjectCode: "CS101", subjectName: "Intro to CS", credits: 4, semesterId: 2 },
      ]);
    }
    if (url.includes("/teacher/syllabi") && method === "POST" && !url.includes("/approve") && !url.includes("/reject") && !url.includes("/retry")) {
      return jsonResponse(routes.upload?.body ?? detail(), routes.upload?.status ?? 201);
    }
    if (url.includes("/structure")) {
      return jsonResponse(routes.structure?.body ?? detail(), routes.structure?.status ?? 200);
    }
    if (url.includes("/approve")) {
      return jsonResponse(routes.approve?.body ?? detail({ syllabus: { ...detail().syllabus, status: "APPROVED" } }), routes.approve?.status ?? 200);
    }
    if (url.includes("/reject")) {
      return jsonResponse(routes.reject?.body ?? detail({ syllabus: { ...detail().syllabus, status: "REJECTED" } }), routes.reject?.status ?? 200);
    }
    if (url.includes("/teacher/syllabi")) return jsonResponse({ ...EMPTY_PAGE, content: routes.listContent ?? [] });

    throw new TypeError(`Unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/teacher/syllabi"]}>
      <AuthProvider>
        <TeacherSyllabi />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function completeUploadForm(fileName = "syllabus.txt") {
  await screen.findByRole("heading", { name: /upload a syllabus/i });
  await waitFor(() => expect(screen.getByLabelText(/class/i)).toBeEnabled());

  await userEvent.selectOptions(screen.getByLabelText(/class/i), "10");
  await waitFor(() => expect(screen.getByLabelText(/subject/i)).toBeEnabled());
  await userEvent.selectOptions(screen.getByLabelText(/subject/i), "7");

  const file = new File(["Unit I: Foundations content"], fileName, { type: "text/plain" });
  await userEvent.upload(screen.getByLabelText(/syllabus file/i), file);
  return file;
}

describe("TeacherSyllabi", () => {
  beforeEach(() => {
    resetCsrfToken();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetCsrfToken();
  });

  it("validates the file type client-side before uploading", async () => {
    const fetchMock = installFetch();
    renderPage();
    await screen.findByRole("heading", { name: /upload a syllabus/i });

    // The accept attribute already stops most bad picks in a real browser;
    // fireEvent bypasses it the way a drag-and-drop or a scripted client
    // would, so the validator is exercised directly.
    const exe = new File(["x"], "syllabus.exe", { type: "application/x-msdownload" });
    fireEvent.change(screen.getByLabelText(/syllabus file/i), { target: { files: [exe] } });
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    expect(await screen.findByText(/only pdf, docx and txt files are supported/i)).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(
        ([url, init]) => String(url).includes("/teacher/syllabi") && init?.body instanceof FormData,
      ),
    ).toBe(false);
  });

  it("requires a class and subject to be chosen", async () => {
    installFetch();
    renderPage();
    await screen.findByRole("heading", { name: /upload a syllabus/i });

    const file = new File(["data"], "syllabus.txt", { type: "text/plain" });
    await userEvent.upload(screen.getByLabelText(/syllabus file/i), file);
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    expect(await screen.findByText(/choose a class and a subject first/i)).toBeInTheDocument();
  });

  it("uploads a file and shows the extracted review panel", async () => {
    const fetchMock = installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    expect(await screen.findByText(/Unit I: Foundations/)).toBeInTheDocument();
    // Topics render inside editable inputs, so assert on their value.
    expect(screen.getByDisplayValue("1.1 History")).toBeInTheDocument();

    // Multipart upload must not set an explicit Content-Type; the browser
    // supplies the boundary.
    const uploadCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url).includes("/teacher/syllabi") && init?.body instanceof FormData,
    );
    expect(uploadCall).toBeDefined();
    const body = uploadCall?.[1]?.body as FormData;
    expect(body.get("classId")).toBe("10");
    expect(body.get("subjectId")).toBe("7");
    expect(body.get("file")).toBeInstanceOf(File);
    // A multipart body must not carry Content-Type: application/json (the
    // browser sets the boundary), but it must still carry the CSRF token.
    const uploadHeaders = uploadCall?.[1]?.headers as Record<string, string> | undefined;
    expect(uploadHeaders?.["Content-Type"]).toBeUndefined();
    expect(uploadHeaders?.["X-CSRF-TOKEN"]).toBe("tok");
  });

  it("surfaces the server error when the upload is rejected", async () => {
    installFetch({ upload: { body: { message: "You are not assigned to teach this subject in this class" }, status: 400 } });
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    expect(
      await screen.findByText(/you are not assigned to teach this subject/i),
    ).toBeInTheDocument();
  });

  it("shows an OCR warning when extraction found no text", async () => {
    installFetch({
      upload: {
        body: detail({
          extractedText: null,
          warning: "No text could be read from this document. OCR is not supported yet.",
          syllabus: { ...detail().syllabus, status: "EXTRACTION_FAILED" },
        }),
      },
    });
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    expect(await screen.findByTestId("syllabus-warning")).toHaveTextContent(/OCR is not supported/i);
    // A retry action is offered instead of approve.
    expect(screen.getByRole("button", { name: /retry extraction/i })).toBeInTheDocument();
  });

  it("adds, edits and removes a unit", async () => {
    installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    await userEvent.click(screen.getByRole("button", { name: /add unit/i }));
    expect(screen.getByLabelText(/unit 3 title/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/unit 3 title/i), "Unit III: Review");
    expect(screen.getByLabelText(/unit 3 title/i)).toHaveValue("Unit III: Review");

    await userEvent.click(screen.getByRole("button", { name: /remove unit 3/i }));
    expect(screen.queryByLabelText(/unit 3 title/i)).not.toBeInTheDocument();
  });

  it("reorders units", async () => {
    installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    await userEvent.click(screen.getByRole("button", { name: /move unit 1 down/i }));

    // The second unit now occupies the first slot.
    expect(screen.getByLabelText(/unit 1 title/i)).toHaveValue("Unit II: Programming");
    expect(screen.getByLabelText(/unit 2 title/i)).toHaveValue("Unit I: Foundations");
  });

  it("saves the edited structure", async () => {
    const fetchMock = installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    const titleField = screen.getByLabelText(/unit 1 title/i);
    await userEvent.clear(titleField);
    await userEvent.type(titleField, "Unit I: Corrected");
    await userEvent.click(screen.getByRole("button", { name: /save as draft/i }));

    await waitFor(() => expect(screen.getByText(/structure saved/i)).toBeInTheDocument());

    const saveCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/structure"));
    expect(saveCall?.[0]).toContain("/structure");
    const payload = JSON.parse(String(saveCall?.[1]?.body));
    expect(payload.units[0].title).toBe("Unit I: Corrected");
    expect(payload.units[0].topics[0].subtopics).toEqual(["early machines"]);
    expect(payload.learningOutcomes[0].statement).toBe("Explain binary");
  });

  it("approves the syllabus", async () => {
    installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    await userEvent.click(screen.getByRole("button", { name: /^approve$/i }));

    await waitFor(() =>
      expect(screen.getByText(/approved and visible to enrolled students/i)).toBeInTheDocument(),
    );
  });

  it("rejects the syllabus with a reason", async () => {
    const fetchMock = installFetch();
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    await userEvent.type(screen.getByLabelText(/rejection reason/i), "Unit headings are wrong");
    await userEvent.click(screen.getByRole("button", { name: /^reject$/i }));

    await waitFor(() => expect(screen.getByText(/rejected\./i)).toBeInTheDocument());
    const rejectCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/reject"));
    expect(JSON.parse(String(rejectCall?.[1]?.body))).toEqual({ reason: "Unit headings are wrong" });
  });

  it("shows an error when approving fails", async () => {
    installFetch({
      approve: { body: { message: "Add at least one unit before approving this syllabus." }, status: 409 },
    });
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));
    await screen.findByText(/Unit I: Foundations/);

    await userEvent.click(screen.getByRole("button", { name: /^approve$/i }));

    expect(
      await screen.findByText(/add at least one unit before approving/i),
    ).toBeInTheDocument();
  });

  it("locks editing once the syllabus is approved", async () => {
    installFetch({
      upload: {
        body: detail({ syllabus: { ...detail().syllabus, status: "APPROVED" } }),
      },
    });
    renderPage();
    await completeUploadForm();
    await userEvent.click(screen.getByRole("button", { name: /upload syllabus/i }));

    await screen.findByText(/this syllabus is approved and visible/i);
    expect(screen.getByLabelText(/unit 1 title/i)).toBeDisabled();
    expect(screen.queryByRole("button", { name: /^approve$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /save as draft/i })).not.toBeInTheDocument();
  });

  it("explains when the teacher has no classes", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/auth/me")) return jsonResponse(TEACHER);
        if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });
        if (url.includes("/me/classes")) return jsonResponse([]);
        return jsonResponse({ ...EMPTY_PAGE, content: [] });
      }),
    );

    renderPage();

    expect(
      await screen.findByText(/you are not assigned to any classes yet/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /upload syllabus/i })).toBeDisabled();
  });

  it("lists previously uploaded syllabi with their status", async () => {
    installFetch({
      listContent: [{ ...detail().syllabus, id: 5, status: "APPROVED", version: 2 }],
    });
    renderPage();

    const list = await screen.findByText(/your syllabi/i);
    expect(list).toBeInTheDocument();
    const item = screen.getByText(/CS101/);
    expect(within(item.closest("li") as HTMLElement).getByText(/approved/i)).toBeInTheDocument();
  });
});

describe("validateFile", () => {
  it("accepts supported formats", () => {
    expect(validateFile(new File(["a"], "a.pdf", { type: "application/pdf" }))).toBeNull();
    expect(validateFile(new File(["a"], "a.docx", { type: "x" }))).toBeNull();
    expect(validateFile(new File(["a"], "a.txt", { type: "text/plain" }))).toBeNull();
  });

  it("rejects missing, unsupported, empty and oversized files", () => {
    expect(validateFile(null)).toMatch(/choose a syllabus file/i);
    expect(validateFile(new File(["a"], "a.exe", { type: "x" }))).toMatch(/only pdf, docx and txt/i);
    expect(validateFile(new File([], "a.txt", { type: "text/plain" }))).toMatch(/empty/i);
    expect(validateFile(new File(["a"], "a.pdf", { type: "application/pdf" }))).toBeNull();
  });

  it("rejects a file above the size limit", () => {
    const big = new File(["a"], "big.txt", { type: "text/plain" });
    Object.defineProperty(big, "size", { value: 11 * 1024 * 1024 });
    expect(validateFile(big)).toMatch(/larger than 10 MB/i);
  });
});