import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  ApiError,
  COUNTABLE_CONTENT_TYPES,
  MAX_INSTRUCTION_LENGTH,
  MAX_ITEMS,
  isContentEditable,
  isContentReleasable,
  isRetryableStatus,
  isTerminalStatus,
  syllabusApi,
  teacherContentApi,
  type GeneratedContent,
  type GenerationContentType,
  type GenerationDetailLevel,
  type GenerationDifficulty,
  type GenerationJob,
  type GenerationLanguage,
  type SyllabusMeta,
} from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { Select, Textarea, Input } from "../components/ui/Input";
import { Container } from "../components/ui/Layout";

const POLL_INTERVAL_MS = 2000;

const CONTENT_TYPE_OPTIONS: Array<{ value: GenerationContentType; label: string }> = [
  { value: "UNIT_NOTES", label: "Unit-wise notes" },
  { value: "TEACHER_STUDY_MATERIAL", label: "Teacher study material" },
  { value: "PPT_SLIDES", label: "PPT slide content" },
  { value: "ASSIGNMENTS_QUESTIONS", label: "Assignments & questions" },
  { value: "MCQS", label: "MCQs" },
  { value: "QUICK_REVISION", label: "Quick revision" },
];

const LANGUAGE_OPTIONS: Array<{ value: GenerationLanguage; label: string }> = [
  { value: "ENGLISH", label: "English" },
  { value: "HINDI", label: "Hindi (हिन्दी)" },
  { value: "HINGLISH", label: "Hinglish" },
];

const DETAIL_OPTIONS: Array<{ value: GenerationDetailLevel; label: string }> = [
  { value: "CONCISE", label: "Concise" },
  { value: "STANDARD", label: "Standard" },
  { value: "DETAILED", label: "Detailed" },
];

const DIFFICULTY_OPTIONS: Array<{ value: GenerationDifficulty; label: string }> = [
  { value: "BEGINNER", label: "Beginner" },
  { value: "INTERMEDIATE", label: "Intermediate" },
  { value: "ADVANCED", label: "Advanced" },
];

const STATUS_VARIANTS: Record<string, "default" | "brand" | "success" | "warning" | "danger"> = {
  QUEUED: "default",
  RUNNING: "brand",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

/** Human labels for the enums the server echoes back. */
function labelFor<T extends string>(
  value: T | null | undefined,
  options: Array<{ value: T; label: string }>,
  fallback = "—",
): string {
  if (!value) return fallback;
  return options.find((option) => option.value === value)?.label ?? String(value);
}

interface FormError {
  variant: "error" | "warning" | "info";
  message: string;
  hint?: string;
}

/**
 * Turns an ApiError into something a teacher can act on.
 *
 * The backend is deliberately specific here: 503 means the server has no AI
 * credential, 429 carries a machine-readable code in `details`, and 0 means
 * the request never reached the server.
 */
function describeError(error: unknown): FormError {
  if (error instanceof ApiError) {
    if (error.status === 503) {
      return {
        variant: "warning",
        message: error.message,
        hint: "AI generation is not configured on this server. An administrator must set GEMINI_API_KEY in the backend environment.",
      };
    }
    if (error.status === 429) {
      const code =
        error.details && typeof error.details === "object" && "code" in error.details
          ? String((error.details as { code?: unknown }).code ?? "")
          : "";
      return {
        variant: "warning",
        message: error.message,
        hint:
          code === "CONCURRENCY_LIMIT"
            ? "Wait for the current job to finish before starting another."
            : code === "DAILY_QUOTA_EXCEEDED"
              ? "The daily generation limit for your account has been reached."
              : undefined,
      };
    }
    if (error.status === 403) {
      return { variant: "error", message: error.message };
    }
    return { variant: "error", message: error.message };
  }
  return { variant: "error", message: "Something went wrong. Please try again." };
}

export default function TeacherContent() {
  const [syllabi, setSyllabi] = useState<SyllabusMeta[]>([]);
  const [syllabiError, setSyllabiError] = useState<FormError | null>(null);
  const [loadingSyllabi, setLoadingSyllabi] = useState(true);

  const [syllabusId, setSyllabusId] = useState("");
  const [unitId, setUnitId] = useState("");
  const [unitTitles, setUnitTitles] = useState<Array<{ id: number; title: string }>>([]);

  const [contentType, setContentType] = useState<GenerationContentType>("UNIT_NOTES");
  const [language, setLanguage] = useState<GenerationLanguage>("ENGLISH");
  const [detailLevel, setDetailLevel] = useState<GenerationDetailLevel>("STANDARD");
  const [difficulty, setDifficulty] = useState<GenerationDifficulty>("INTERMEDIATE");
  const [itemCount, setItemCount] = useState(5);
  const [includeAnswerKey, setIncludeAnswerKey] = useState(true);
  const [instructions, setInstructions] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<FormError | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [jobs, setJobs] = useState<GenerationJob[]>([]);
  const [activeJob, setActiveJob] = useState<GenerationJob | null>(null);
  const [content, setContent] = useState<GeneratedContent | null>(null);
  const [contentError, setContentError] = useState<FormError | null>(null);
  const [saving, setSaving] = useState(false);

  // Guards against a poll response landing after the component has moved on.
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cancelled = useRef(false);

  useEffect(() => {
    cancelled.current = false;
    return () => {
      cancelled.current = true;
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  useEffect(() => {
    let active = true;
    syllabusApi
      .listMine(0, 100)
      .then((page) => {
        if (!active) return;
        // Only APPROVED syllabi are valid generation sources; the server
        // refuses anything else, so they are not offered here.
        setSyllabi(page.content.filter((item) => item.status === "APPROVED"));
      })
      .catch((error) => {
        if (active) setSyllabiError(describeError(error));
      })
      .finally(() => {
        if (active) setLoadingSyllabi(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    teacherContentApi
      .listJobs(0, 20)
      .then((page) => {
        if (active) setJobs(page.content);
      })
      .catch(() => {
        /* The job list is supplementary; a failure here must not block the form. */
      });
    return () => {
      active = false;
    };
  }, []);

  /** Loads the unit list for the selected syllabus. */
  useEffect(() => {
    if (!syllabusId) {
      setUnitTitles([]);
      setUnitId("");
      return;
    }
    let active = true;
    syllabusApi
      .byId(Number(syllabusId))
      .then((detail) => {
        if (!active) return;
        setUnitTitles(
          detail.units
            .filter((unit): unit is typeof unit & { id: number } => typeof unit.id === "number")
            .map((unit) => ({ id: unit.id, title: unit.title })),
        );
      })
      .catch((error) => {
        if (active) {
          setUnitTitles([]);
          setFormError(describeError(error));
        }
      });
    return () => {
      active = false;
    };
  }, [syllabusId]);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  /**
   * Polls one job until it reaches a terminal state.
   *
   * Scheduled with setTimeout rather than setInterval so a slow response can
   * never queue up overlapping requests, and every timer is cleared on
   * unmount or when the tracked job changes.
   */
  const pollJob = useCallback(
    (jobId: number) => {
      stopPolling();
      const tick = async () => {
        if (cancelled.current) return;
        try {
          const job = await teacherContentApi.job(jobId);
          if (cancelled.current) return;
          setActiveJob(job);
          setJobs((current) => current.map((item) => (item.id === job.id ? job : item)));
          if (!isTerminalStatus(job.status)) {
            pollTimer.current = setTimeout(tick, POLL_INTERVAL_MS);
          }
        } catch (error) {
          if (cancelled.current) return;
          stopPolling();
          setContentError(describeError(error));
        }
      };
      pollTimer.current = setTimeout(tick, POLL_INTERVAL_MS);
    },
    [stopPolling],
  );

  /** Watches a tracked job and drives the poll loop. */
  useEffect(() => {
    if (!activeJob || isTerminalStatus(activeJob.status)) return;
    pollJob(activeJob.id);
    return stopPolling;
  }, [activeJob, pollJob, stopPolling]);

  /**
   * Loads the artifact for a completed job.
   *
   * Keyed on the content id rather than the job object so it runs once per
   * completion. It also covers a submit response that is already COMPLETED —
   * duplicate suppression can hand back a finished job with no polling at all.
   */
  const completedContentId =
    activeJob?.status === "COMPLETED" ? activeJob.contentId : null;
  useEffect(() => {
    if (completedContentId == null) return;
    let active = true;
    setContentError(null);
    teacherContentApi
      .content(completedContentId)
      .then((generated) => {
        if (active) setContent(generated);
      })
      .catch((error) => {
        if (active) setContentError(describeError(error));
      });
    return () => {
      active = false;
    };
  }, [completedContentId]);

  const countable = COUNTABLE_CONTENT_TYPES.includes(contentType);

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!syllabusId) errors.syllabusId = "Choose an approved syllabus.";
    if (countable) {
      if (itemCount < 1) errors.itemCount = "Number of items must be at least 1.";
      else if (itemCount > MAX_ITEMS) errors.itemCount = `Number of items must be at most ${MAX_ITEMS}.`;
    }
    if (instructions.length > MAX_INSTRUCTION_LENGTH) {
      errors.instructions = `Instructions must be at most ${MAX_INSTRUCTION_LENGTH} characters.`;
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    setContentError(null);
    setContent(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      const job = await teacherContentApi.submit({
        syllabusId: Number(syllabusId),
        unitId: unitId ? Number(unitId) : null,
        contentType,
        language,
        detailLevel,
        difficulty,
        itemCount: countable ? itemCount : 1,
        includeAnswerKey: contentType === "ASSIGNMENTS_QUESTIONS" && includeAnswerKey,
        instructions: instructions.trim() ? instructions.trim() : null,
      });
      setActiveJob(job);
      setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
    } catch (error) {
      setFormError(describeError(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRetry(jobId: number) {
    setContentError(null);
    try {
      const job = await teacherContentApi.retry(jobId);
      setActiveJob(job);
      setJobs((current) => current.map((item) => (item.id === job.id ? job : item)));
    } catch (error) {
      setContentError(describeError(error));
    }
  }

  async function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!content) return;
    setSaving(true);
    setContentError(null);
    try {
      const updated = await teacherContentApi.update(content.id, {
        title: content.title,
        body: content.body,
        structuredJson: content.structuredJson,
      });
      setContent(updated);
    } catch (error) {
      setContentError(describeError(error));
    } finally {
      setSaving(false);
    }
  }

  async function handleApprove(release: boolean) {
    if (!content) return;
    setContentError(null);
    try {
      setContent(await teacherContentApi.approve(content.id, release));
    } catch (error) {
      setContentError(describeError(error));
    }
  }

  async function handleReject(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!content) return;
    const data = new FormData(event.currentTarget as HTMLFormElement);
    const reason = String(data.get("reason") ?? "").trim();
    setContentError(null);
    try {
      setContent(await teacherContentApi.reject(content.id, reason));
    } catch (error) {
      setContentError(describeError(error));
    }
  }

  return (
    <Container className="py-10">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">AI content generation</h1>
        <p className="mt-2 text-slate-600">
          Generate study material grounded in one of your approved syllabi. Generated drafts stay
          private until you approve them, and teacher study material is never released to students.
        </p>
      </header>

      {syllabiError && (
        <div className="mb-6">
          <Alert variant="error">{syllabiError.message}</Alert>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>New generation</CardTitle>
            <CardDescription>
              Pick an approved syllabus and choose what to generate from it.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={handleSubmit} noValidate>
              <Select
                label="Syllabus"
                value={syllabusId}
                error={fieldErrors.syllabusId}
                disabled={loadingSyllabi || syllabi.length === 0}
                onChange={(event) => setSyllabusId(event.target.value)}
                options={[
                  { value: "", label: loadingSyllabi ? "Loading syllabi…" : "Select a syllabus" },
                  ...syllabi.map((item) => ({
                    value: String(item.id),
                    label: `${item.subjectCode} · ${item.classCode} (v${item.version})`,
                  })),
                ]}
              />

              <Select
                label="Unit"
                value={unitId}
                disabled={!syllabusId}
                onChange={(event) => setUnitId(event.target.value)}
                options={[
                  { value: "", label: "Whole syllabus" },
                  ...unitTitles.map((unit) => ({ value: String(unit.id), label: unit.title })),
                ]}
              />

              <Select
                label="Content type"
                value={contentType}
                onChange={(event) => setContentType(event.target.value as GenerationContentType)}
                options={CONTENT_TYPE_OPTIONS}
              />

              <Select
                label="Language"
                value={language}
                onChange={(event) => setLanguage(event.target.value as GenerationLanguage)}
                options={LANGUAGE_OPTIONS}
              />

              <div className="grid gap-4 sm:grid-cols-2">
                <Select
                  label="Detail level"
                  value={detailLevel}
                  onChange={(event) => setDetailLevel(event.target.value as GenerationDetailLevel)}
                  options={DETAIL_OPTIONS}
                />
                <Select
                  label="Difficulty"
                  value={difficulty}
                  onChange={(event) => setDifficulty(event.target.value as GenerationDifficulty)}
                  options={DIFFICULTY_OPTIONS}
                />
              </div>

              {countable && (
                <Input
                  label="Number of items"
                  type="number"
                  min={1}
                  max={MAX_ITEMS}
                  value={String(itemCount)}
                  error={fieldErrors.itemCount}
                  onChange={(event) => setItemCount(Number(event.target.value))}
                />
              )}

              {contentType === "ASSIGNMENTS_QUESTIONS" && (
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={includeAnswerKey}
                    onChange={(event) => setIncludeAnswerKey(event.target.checked)}
                  />
                  Include an answer key for my own use
                </label>
              )}

              <div>
                <Textarea
                  id="instructions"
                  label="Extra instructions (optional)"
                  rows={3}
                  value={instructions}
                  error={fieldErrors.instructions}
                  onChange={(event) => setInstructions(event.target.value)}
                />
                <p className="mt-1 text-xs text-slate-500">
                  {instructions.length}/{MAX_INSTRUCTION_LENGTH}
                </p>
              </div>

              {formError && (
                <Alert variant={formError.variant}>
                  <p>{formError.message}</p>
                  {formError.hint && <p className="mt-1 text-xs">{formError.hint}</p>}
                </Alert>
              )}

              <Button type="submit" disabled={submitting || loadingSyllabi}>
                {submitting ? "Starting…" : "Start generation"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Jobs</CardTitle>
            <CardDescription>Your generation jobs, newest first.</CardDescription>
          </CardHeader>
          <CardContent>
            {jobs.length === 0 ? (
              <p className="text-sm text-slate-500">No generation jobs yet.</p>
            ) : (
              <ul className="space-y-3">
                {jobs.map((job) => (
                  <li key={job.id} className="rounded-lg border border-slate-200 p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-slate-900">
                          {labelFor(job.contentType, CONTENT_TYPE_OPTIONS)} ·{" "}
                          {labelFor(job.language, LANGUAGE_OPTIONS)}
                        </p>
                        <p className="text-xs text-slate-500">
                          {job.unitTitle ?? "Whole syllabus"}
                        </p>
                      </div>
                      <Badge variant={STATUS_VARIANTS[job.status]}>{job.status}</Badge>
                    </div>

                    {job.status === "FAILED" && job.errorMessage && (
                      <p className="mt-2 text-xs text-red-700">{job.errorMessage}</p>
                    )}
                    {job.status === "CANCELLED" && job.errorMessage && (
                      <p className="mt-2 text-xs text-amber-700">{job.errorMessage}</p>
                    )}
                    {job.stale && (
                      <p className="mt-2 text-xs text-amber-700">
                        The syllabus has been replaced since this job ran.
                      </p>
                    )}

                    <div className="mt-3 flex gap-2">
                      {job.status === "COMPLETED" && job.contentId && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={async () => {
                            setContentError(null);
                            try {
                              setContent(await teacherContentApi.content(job.contentId!));
                              setActiveJob(job);
                            } catch (error) {
                              setContentError(describeError(error));
                            }
                          }}
                        >
                          Open content
                        </Button>
                      )}
                      {isRetryableStatus(job.status) && (
                        <Button size="sm" variant="secondary" onClick={() => void handleRetry(job.id)}>
                          Retry
                        </Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {activeJob && !isTerminalStatus(activeJob.status) && (
        <div className="mt-6">
          <Alert variant="info">
            Job #{activeJob.id} is {activeJob.status.toLowerCase()}. This page updates
            automatically; it stops polling once the job finishes.
          </Alert>
        </div>
      )}

      {contentError && (
        <div className="mt-6">
          <Alert variant="error">
            <p>{contentError.message}</p>
            {contentError.hint && <p className="mt-1 text-xs">{contentError.hint}</p>}
          </Alert>
        </div>
      )}

      {content && (
        <Card className="mt-6">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle>Review content</CardTitle>
              <div className="flex gap-2">
                <Badge>{content.reviewStatus}</Badge>
                <Badge variant={content.audience === "TEACHER" ? "warning" : "brand"}>
                  {content.audience === "TEACHER" ? "Teacher only" : "Student facing"}
                </Badge>
                {content.releasedToStudents && <Badge variant="success">Released</Badge>}
                {content.stale && <Badge variant="warning">Outdated</Badge>}
              </div>
            </div>
            <CardDescription>
              {content.subjectCode} · {content.classCode}
              {content.unitTitle ? ` · ${content.unitTitle}` : ""}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {!isContentEditable(content) && (
              <Alert variant="info">
                {content.stale
                  ? "The source syllabus has been replaced, so this artifact is outdated and can no longer be edited."
                  : `Content in ${content.reviewStatus} state is no longer editable.`}
              </Alert>
            )}

            {isContentEditable(content) ? (
              <form className="space-y-4" onSubmit={handleSave}>
                <Input
                  label="Title"
                  value={content.title}
                  onChange={(event) => setContent({ ...content, title: event.target.value })}
                />
                <Textarea
                  id="content-body"
                  label="Body"
                  rows={12}
                  className="font-mono text-xs"
                  value={content.body}
                  onChange={(event) => setContent({ ...content, body: event.target.value })}
                />
                <Button type="submit" disabled={saving}>
                  {saving ? "Saving…" : "Save draft"}
                </Button>
              </form>
            ) : (
              <>
                <h4 className="text-sm font-semibold text-slate-900">{content.title}</h4>
                <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
                  {content.body}
                </pre>
              </>
            )}

            {content.structuredJson && (
              <details>
                <summary className="cursor-pointer text-sm font-medium text-slate-700">
                  Structured data (questions / slides)
                </summary>
                <pre className="mt-2 max-h-64 overflow-auto rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
                  {content.structuredJson}
                </pre>
              </details>
            )}

            <div className="flex flex-wrap gap-2 border-t border-slate-200 pt-4">
              <Button
                onClick={() => void handleApprove(isContentReleasable(content))}
                disabled={content.reviewStatus === "APPROVED"}
              >
                {isContentReleasable(content) ? "Approve and release" : "Approve"}
              </Button>
            </div>

            {!isContentReleasable(content) && (
              <p className="text-xs text-slate-500">
                This is teacher-only material, so it can never be released to students.
              </p>
            )}

            <form className="flex flex-wrap items-end gap-2 border-t border-slate-200 pt-4" onSubmit={handleReject}>
              <div className="min-w-56 flex-1">
                <Input label="Rejection reason" name="reason" placeholder="Why is this wrong?" />
              </div>
              <Button type="submit" variant="danger">
                Reject
              </Button>
            </form>

            <p className="text-xs text-slate-400">
              Students see released content on{" "}
              <Link className="underline" to="/student/content">
                their content page
              </Link>
              .
            </p>
          </CardContent>
        </Card>
      )}
    </Container>
  );
}