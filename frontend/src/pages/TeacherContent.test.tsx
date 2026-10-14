import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken, type GeneratedContent, type GenerationJob } from "../api/client";
import { AuthProvider } from "../auth/AuthContext";
import TeacherContent from "./TeacherContent";

const TEACHER = {
  id: 2,
  email: "bob@example.com",
  fullName: "Bob",
  role: "TEACHER" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
};

const EMPTY_PAGE = {
  content: [] as unknown[],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

const SYLLABUS = {
  id: 11,
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
  status: "APPROVED" as const,
  version: 2,
  current: true,
  extractionError: null,
  reviewNote: null,
  reviewedById: 2,
  reviewedAt: "2026-01-02T00:00:00Z",
  uploadedAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

function syllabusDetail() {
  return {
    syllabus: SYLLABUS,
    units: [
      { id: 100, title: "Unit I: Foundations", position: 0, topics: [] },
      { id: 101, title: "Unit II: Programming", position: 1, topics: [] },
    ],
    learningOutcomes: [],
    extractedText: "Unit I: Foundations",
    warning: null,
  };
}

function job(overrides: Partial<GenerationJob> = {}): GenerationJob {
  return {
    id: 501,
    syllabusId: 11,
    syllabusVersion: 2,
    unitId: null,
    unitTitle: null,
    contentType: "UNIT_NOTES",
    language: "ENGLISH",
    detailLevel: "STANDARD",
    difficulty: "INTERMEDIATE",
    itemCount: 1,
    instructions: null,
    status: "QUEUED",
    attemptCount: 0,
    errorCode: null,
    errorMessage: null,
    providerName: "GEMINI",
    modelName: "gemini-3.8-flash",
    contentId: null,
    stale: false,
    createdAt: "2026-02-01T10:00:00Z",
    startedAt: null,
    completedAt: null,
    note: null,
    ...overrides,
  };
}

function content(overrides: Partial<GeneratedContent> = {}): GeneratedContent {
  return {
    id: 900,
    jobId: 501,
    syllabusId: 11,
    syllabusVersion: 2,
    subjectCode: "CS101",
    subjectName: "Intro to CS",
    classCode: "SEC-A",
    unitId: null,
    unitTitle: null,
    contentType: "UNIT_NOTES",
    language: "ENGLISH",
    title: "Whole syllabus — Unit notes",
    body: "Generated body text.",
    structuredJson: null,
    audience: "STUDENT",
    containsAnswerKey: false,
    reviewStatus: "DRAFT",
    releasedToStudents: false,
    rejectionReason: null,
    providerName: "GEMINI",
    modelName: "gemini-3.8-flash",
    stale: false,
    createdAt: "2026-02-01T10:00:00Z",
    updatedAt: "2026-02-01T10:00:00Z",
    reviewedAt: null,
    ...overrides,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

interface Routes {
  /** Success/failure of the submit call. */
  submit?: { body: unknown; status?: number };
  /** Responses returned in order by successive job polls. */
  polls?: Array<{ body: unknown; status?: number }>;
  content?: { body: unknown; status?: number };
  approve?: { body: unknown; status?: number };
  reject?: { body: unknown; status?: number };
  save?: { body: unknown; status?: number };
  retry?: { body: unknown; status?: number };
  /** Syllabi returned by listMine; defaults to one APPROVED entry. */
  syllabi?: unknown[];
  jobs?: GenerationJob[];
}

function installFetch(routes: Routes = {}) {
  let pollIndex = 0;
  const pollCalls: number[] = [];

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();

    if (url.includes("/auth/me")) return jsonResponse(TEACHER);
    if (url.includes("/auth/csrf")) return jsonResponse({ token: "tok" });

    if (url.includes("/teacher/syllabi") && method === "GET" && /\/teacher\/syllabi\/\d+$/.test(url)) {
      return jsonResponse(syllabusDetail());
    }
    if (url.includes("/teacher/syllabi")) {
      return jsonResponse({
        ...EMPTY_PAGE,
        content: routes.syllabi ?? [SYLLABUS],
      });
    }

    if (/\/teacher\/content\/generations\/\d+\/retry/.test(url)) {
      return jsonResponse(
        routes.retry?.body ?? job({ status: "QUEUED" }),
        routes.retry?.status ?? 202,
      );
    }
    if (/\/teacher\/content\/generations\/\d+$/.test(url)) {
      pollCalls.push(pollIndex);
      const entry = routes.polls?.[Math.min(pollIndex, (routes.polls?.length ?? 1) - 1)];
      pollIndex += 1;
      return jsonResponse(entry?.body ?? job({ status: "RUNNING" }), entry?.status ?? 200);
    }
    if (url.includes("/teacher/content/generations") && method === "POST") {
      return jsonResponse(routes.submit?.body ?? job(), routes.submit?.status ?? 202);
    }
    if (url.includes("/teacher/content/generations")) {
      return jsonResponse({ ...EMPTY_PAGE, content: routes.jobs ?? [] });
    }

    if (/\/teacher\/content\/\d+\/approve/.test(url)) {
      return jsonResponse(
        routes.approve?.body ?? content({ reviewStatus: "APPROVED", releasedToStudents: true }),
        routes.approve?.status ?? 200,
      );
    }
    if (/\/teacher\/content\/\d+\/reject/.test(url)) {
      return jsonResponse(
        routes.reject?.body ?? content({ reviewStatus: "REJECTED", rejectionReason: "Wrong" }),
        routes.reject?.status ?? 200,
      );
    }
    if (/\/teacher\/content\/\d+$/.test(url) && method === "PUT") {
      return jsonResponse(routes.save?.body ?? content(), routes.save?.status ?? 200);
    }
    if (/\/teacher\/content\/\d+$/.test(url)) {
      return jsonResponse(routes.content?.body ?? content(), routes.content?.status ?? 200);
    }

    throw new TypeError(`Unexpected request: ${url}`);
  });

  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, pollCalls };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/teacher/content"]}>
      <AuthProvider>
        <TeacherContent />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillForm(options: { contentType?: string } = {}) {
  await screen.findByLabelText(/syllabus/i);
  await waitFor(() => expect(screen.getByLabelText(/^syllabus$/i)).toBeEnabled());
  await userEvent.selectOptions(screen.getByLabelText(/^syllabus$/i), "11");

  if (options.contentType) {
    await userEvent.selectOptions(screen.getByLabelText(/content type/i), options.contentType);
  }
  await userEvent.click(screen.getByRole("button", { name: /start generation/i }));
}

describe("TeacherContent", () => {
  beforeEach(() => {
    resetCsrfToken();
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("submits a generation request with the selected options", async () => {
    const { fetchMock } = installFetch();
    renderPage();

    await fillForm();

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url, init]) =>
        String(url).includes("/teacher/content/generations") &&
        (init as RequestInit)?.method === "POST",
      );
      expect(call).toBeDefined();
      const body = JSON.parse(String((call![1] as RequestInit).body));
      expect(body).toMatchObject({
        syllabusId: 11,
        unitId: null,
        contentType: "UNIT_NOTES",
        language: "ENGLISH",
        itemCount: 1,
        includeAnswerKey: false,
      });
    });
  });

  it("sends the unit, language and MCQ item count that were chosen", async () => {
    const { fetchMock } = installFetch();
    renderPage();

    await screen.findByLabelText(/syllabus/i);
    await waitFor(() => expect(screen.getByLabelText(/^syllabus$/i)).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText(/^syllabus$/i), "11");

    await waitFor(() =>
      expect(screen.getByRole("option", { name: /Unit I: Foundations/i })).toBeInTheDocument(),
    );
    await userEvent.selectOptions(screen.getByLabelText(/^unit$/i), "100");
    await userEvent.selectOptions(screen.getByLabelText(/content type/i), "MCQS");
    await userEvent.selectOptions(screen.getByLabelText(/^language$/i), "HINGLISH");
    await userEvent.selectOptions(screen.getByLabelText(/difficulty/i), "ADVANCED");

    const countInput = screen.getByLabelText(/number of items/i);
    await userEvent.clear(countInput);
    await userEvent.type(countInput, "7");

    await userEvent.click(screen.getByRole("button", { name: /start generation/i }));

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url, init]) =>
        String(url).includes("/teacher/content/generations") &&
        (init as RequestInit)?.method === "POST",
      );
      expect(call).toBeDefined();
      expect(JSON.parse(String((call![1] as RequestInit).body))).toMatchObject({
        unitId: 100,
        contentType: "MCQS",
        language: "HINGLISH",
        difficulty: "ADVANCED",
        itemCount: 7,
      });
    });
  });

  it("offers the answer key option only for assignments", async () => {
    installFetch();
    renderPage();
    await screen.findByLabelText(/^content type$/i);

    expect(screen.queryByLabelText(/answer key/i)).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText(/content type/i), "ASSIGNMENTS_QUESTIONS");
    expect(await screen.findByLabelText(/answer key/i)).toBeInTheDocument();
  });

  it("requires a syllabus before submitting", async () => {
    const { fetchMock } = installFetch();
    renderPage();
    await screen.findByLabelText(/^content type$/i);

    await userEvent.click(screen.getByRole("button", { name: /start generation/i }));

    expect(await screen.findByText(/choose an approved syllabus/i)).toBeInTheDocument();
    const posted = fetchMock.mock.calls.some(
      ([url, init]) =>
        String(url).includes("/teacher/content/generations") &&
        (init as RequestInit)?.method === "POST",
    );
    expect(posted).toBe(false);
  });

  it("rejects an item count above the configured maximum before calling the API", async () => {
    const { fetchMock } = installFetch();
    renderPage();
    await screen.findByLabelText(/^content type$/i);

    await userEvent.selectOptions(screen.getByLabelText(/content type/i), "MCQS");
    const countInput = screen.getByLabelText(/number of items/i);
    await userEvent.clear(countInput);
    await userEvent.type(countInput, "500");
    await userEvent.click(screen.getByRole("button", { name: /start generation/i }));

    expect(await screen.findByText(/at most 30/i)).toBeInTheDocument();
    const posted = fetchMock.mock.calls.some(
      ([url, init]) =>
        String(url).includes("/teacher/content/generations") &&
        (init as RequestInit)?.method === "POST",
    );
    expect(posted).toBe(false);
  });

  it("shows a configuration message when the provider has no API key", async () => {
    installFetch({
      submit: {
        body: {
          timestamp: "2026-02-01T10:00:00Z",
          status: 503,
          error: "Service Unavailable",
          message: "AI content generation is not configured on this server.",
          path: "/api/v1/teacher/content/generations",
        },
        status: 503,
      },
    });
    renderPage();
    await fillForm();

    const alerts = await screen.findAllByText(/not configured on this server/i);
    expect(alerts.length).toBeGreaterThan(0);
    // The actionable hint tells the teacher/admin what is actually missing.
    expect(screen.getByText(/GEMINI_API_KEY/)).toBeInTheDocument();
  });

  it("explains a rate limit and shows the daily quota hint", async () => {
    installFetch({
      submit: {
        body: {
          message: "You have reached the daily generation limit of 50. Try again tomorrow.",
          details: { code: "DAILY_QUOTA_EXCEEDED" },
        },
        status: 429,
      },
    });
    renderPage();
    await fillForm();

    const alerts = await screen.findAllByText(/daily generation limit/i);
    expect(alerts.length).toBeGreaterThan(0);
    expect(screen.getByText(/daily generation limit for your account/i)).toBeInTheDocument();
  });

  it("shows the concurrency hint while another job is in flight", async () => {
    installFetch({
      submit: {
        body: {
          message: "You already have 1 generation(s) in progress.",
          details: { code: "CONCURRENCY_LIMIT" },
        },
        status: 429,
      },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByText(/already have 1 generation/i)).toBeInTheDocument();
    expect(screen.getByText(/wait for the current job/i)).toBeInTheDocument();
  });

  it("surfaces a server-side validation error", async () => {
    installFetch({
      submit: { body: { message: "That unit does not belong to this syllabus" }, status: 400 },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByText(/does not belong to this syllabus/i)).toBeInTheDocument();
  });

  it("polls until the job completes and then shows the generated content", async () => {
    installFetch({
      polls: [
        { body: job({ status: "RUNNING", attemptCount: 1 }) },
        { body: job({ status: "COMPLETED", contentId: 900, attemptCount: 1 }) },
      ],
    });
    renderPage();
    await fillForm();

    // Accepted but not yet started.
    await waitFor(() => expect(screen.getByText(/is queued/i)).toBeInTheDocument());

    // First scheduled poll reports RUNNING and re-schedules.
    await vi.advanceTimersByTimeAsync(2500);
    await waitFor(() => expect(screen.getByText(/is running/i)).toBeInTheDocument());

    // Second scheduled poll reports COMPLETED and loads the artifact.
    await vi.advanceTimersByTimeAsync(2500);
    await waitFor(() => expect(screen.getByLabelText(/^title$/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/^title$/i)).toHaveValue("Whole syllabus — Unit notes");
    expect(screen.getByRole("button", { name: /save draft/i })).toBeInTheDocument();
  });

  it("stops polling once the job reaches a terminal state", async () => {
    const { fetchMock } = installFetch({
      polls: [{ body: job({ status: "COMPLETED", contentId: 900 }) }],
    });
    renderPage();
    await fillForm();

    await waitFor(() => expect(screen.getByText(/is queued/i)).toBeInTheDocument());
    await vi.advanceTimersByTimeAsync(2500);
    await waitFor(() => expect(screen.getByLabelText(/^title$/i)).toBeInTheDocument());

    const countPolls = () =>
      fetchMock.mock.calls.filter(([url]) =>
        /\/teacher\/content\/generations\/\d+$/.test(String(url)),
      ).length;

    const afterCompletion = countPolls();
    expect(afterCompletion).toBeGreaterThan(0);

    await vi.advanceTimersByTimeAsync(10_000);
    expect(countPolls()).toBe(afterCompletion);
  });

  it("cleans up the poll timer on unmount", async () => {
    const { fetchMock, pollCalls } = installFetch({
      polls: [{ body: job({ status: "RUNNING" }) }],
    });
    const view = renderPage();
    await fillForm();

    await waitFor(() => expect(screen.getByText(/is queued/i)).toBeInTheDocument());
    await vi.advanceTimersByTimeAsync(2500);
    await waitFor(() => expect(pollCalls.length).toBeGreaterThan(0));

    const callsBeforeUnmount = fetchMock.mock.calls.length;
    view.unmount();
    await vi.advanceTimersByTimeAsync(20_000);

    // No further requests may be issued after the component is gone.
    expect(fetchMock.mock.calls.length).toBe(callsBeforeUnmount);
  });

  it("shows a failed job with its safe error message and offers a retry", async () => {
    installFetch({
      submit: {
        body: job({
          status: "FAILED",
          errorCode: "PROVIDER_UNAVAILABLE",
          errorMessage: "The AI provider is temporarily unavailable.",
          attemptCount: 3,
        }),
      },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByText(/temporarily unavailable/i)).toBeInTheDocument();
    const retryButtons = await screen.findAllByRole("button", { name: /^retry$/i });
    expect(retryButtons.length).toBeGreaterThan(0);
  });

  it("retries a failed job when asked", async () => {
    const { fetchMock } = installFetch({
      submit: {
        body: job({ status: "FAILED", errorMessage: "Boom" }),
      },
    });
    renderPage();
    await fillForm();

    const retryButtons = await screen.findAllByRole("button", { name: /^retry$/i });
    await userEvent.click(retryButtons[0]);

    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(([url, init]) =>
          String(url).includes("/retry") && (init as RequestInit)?.method === "POST",
        ),
      ).toBe(true);
    });
  });

  it("saves an edited draft", async () => {
    const { fetchMock } = installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900 }) },
    });
    renderPage();
    await fillForm();

    const titleField = await screen.findByLabelText(/^title$/i);
    await userEvent.clear(titleField);
    await userEvent.type(titleField, "Corrected title");
    await userEvent.click(screen.getByRole("button", { name: /save draft/i }));

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) =>
          /\/teacher\/content\/\d+$/.test(String(url)) && (init as RequestInit)?.method === "PUT",
      );
      expect(call).toBeDefined();
      expect(JSON.parse(String((call![1] as RequestInit).body)).title).toBe("Corrected title");
    });
  });

  it("approves and releases student-facing content", async () => {
    const { fetchMock } = installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900 }) },
    });
    renderPage();
    await fillForm();

    await userEvent.click(await screen.findByRole("button", { name: /approve and release/i }));

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/approve"));
      expect(call).toBeDefined();
      expect(JSON.parse(String((call![1] as RequestInit).body))).toEqual({
        releaseToStudents: true,
      });
    });
  });

  it("never offers to release teacher-only material", async () => {
    installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900, contentType: "TEACHER_STUDY_MATERIAL" }) },
      content: { body: content({ audience: "TEACHER" }) },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByRole("button", { name: /^approve$/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /approve and release/i })).not.toBeInTheDocument();
    expect(screen.getByText(/never be released to students/i)).toBeInTheDocument();
  });

  it("locks editing once the content is approved", async () => {
    installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900 }) },
      content: { body: content({ reviewStatus: "APPROVED" }) },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByText(/no longer editable/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /save draft/i })).not.toBeInTheDocument();
  });

  it("rejects content with a reason", async () => {
    const { fetchMock } = installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900 }) },
    });
    renderPage();
    await fillForm();

    await userEvent.type(await screen.findByLabelText(/rejection reason/i), "Not aligned");
    await userEvent.click(screen.getByRole("button", { name: /^reject$/i }));

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url).includes("/reject"));
      expect(call).toBeDefined();
      expect(JSON.parse(String((call![1] as RequestInit).body))).toEqual({ reason: "Not aligned" });
    });
    expect(await screen.findAllByText("REJECTED")).not.toHaveLength(0);
  });

  it("marks content derived from a replaced syllabus as outdated and locks it", async () => {
    installFetch({
      submit: { body: job({ status: "COMPLETED", contentId: 900, stale: true }) },
      content: { body: content({ stale: true, reviewStatus: "OUTDATED" }) },
    });
    renderPage();
    await fillForm();

    expect(await screen.findByText("OUTDATED")).toBeInTheDocument();
    expect(screen.getByText(/no longer be edited/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /save draft/i })).not.toBeInTheDocument();
  });

  it("only offers approved syllabi as generation sources", async () => {
    installFetch({
      syllabi: [
        SYLLABUS,
        { ...SYLLABUS, id: 12, status: "REVIEW_REQUIRED", subjectCode: "CS102" },
      ],
    });
    renderPage();
    await screen.findByLabelText(/^syllabus$/i);

    const options = screen.getAllByRole("option").map((option) => option.textContent ?? "");
    expect(options.some((text) => text.includes("CS101"))).toBe(true);
    expect(options.some((text) => text.includes("CS102"))).toBe(false);
  });
});