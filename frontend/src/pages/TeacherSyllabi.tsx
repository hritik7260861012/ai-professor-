import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  ApiError,
  academicApi,
  syllabusApi,
  type AcademicClass,
  type SyllabusDetail,
  type SyllabusMeta,
  type SyllabusStructurePayload,
  type SyllabusTopicView,
  type SyllabusUnitView,
} from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { FileInput, Input, Select, Textarea } from "../components/ui/Input";

const ACCEPTED = ".pdf,.docx,.txt";

/** Client-side mirror of the server rule; the server is the authority. */
function validateFile(file: File | null): string | null {
  if (!file) return "Choose a syllabus file to upload";
  const name = file.name.toLowerCase();
  if (!/\.(pdf|docx|txt)$/.test(name)) {
    return "Only PDF, DOCX and TXT files are supported";
  }
  if (file.size === 0) return "The selected file is empty";
  if (file.size > 10 * 1024 * 1024) return "The file is larger than 10 MB";
  return null;
}

function statusVariant(status: string): "default" | "success" | "warning" | "danger" {
  switch (status) {
    case "APPROVED":
      return "success";
    case "REJECTED":
    case "EXTRACTION_FAILED":
      return "danger";
    case "REVIEW_REQUIRED":
      return "warning";
    default:
      return "default";
  }
}

function statusLabel(status: string): string {
  return status.replace(/_/g, " ").toLowerCase();
}

/**
 * Teacher syllabus workspace: upload, review the extracted outline, edit
 * it, then approve or reject.
 *
 * <p>The class and subject pickers are populated from the teacher's own
 * assignments via the Phase 3 scoped endpoints, so a teacher can only ever
 * choose a class/subject they actually teach.
 */
export default function TeacherSyllabi() {
  const [classes, setClasses] = useState<AcademicClass[]>([]);
  const [loadingClasses, setLoadingClasses] = useState(true);
  const [classId, setClassId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [selected, setSelected] = useState<SyllabusDetail | null>(null);
  const [existing, setExisting] = useState<SyllabusMeta[]>([]);

  const loadClasses = useCallback(async () => {
    setLoadingClasses(true);
    try {
      // Scoped endpoint: the server already filters to this teacher's classes.
      const mine = await academicApi.myClasses();
      setClasses(mine);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load your classes");
    } finally {
      setLoadingClasses(false);
    }
  }, []);

  const loadExisting = useCallback(async () => {
    try {
      const page = await syllabusApi.listMine(0, 50);
      setExisting(page.content);
    } catch {
      setExisting([]);
    }
  }, []);

  useEffect(() => {
    void loadClasses();
    void loadExisting();
  }, [loadClasses, loadExisting]);

  // Subjects offered in the selected class, loaded on demand.
  const [subjects, setSubjects] = useState<Array<{ id: number; code: string; name: string }>>([]);
  useEffect(() => {
    if (!classId) {
      setSubjects([]);
      return;
    }
    let cancelled = false;
    academicApi
      .classSubjects(Number(classId))
      .then((links) => {
        if (!cancelled) {
          setSubjects(
            links.map((link) => ({ id: link.subjectId, code: link.subjectCode, name: link.subjectName })),
          );
        }
      })
      .catch(() => {
        if (!cancelled) setSubjects([]);
      });
    return () => {
      cancelled = true;
    };
  }, [classId]);

  async function onUpload(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);

    const localError = validateFile(file);
    if (localError) {
      setFileError(localError);
      return;
    }
    if (!classId || !subjectId) {
      setError("Choose a class and a subject first");
      return;
    }
    setFileError(null);
    setUploading(true);
    try {
      const uploaded = await syllabusApi.upload(Number(classId), Number(subjectId), file as File);
      setSelected(uploaded);
      setNotice(
        uploaded.warning
          ? "Uploaded. See the guidance below before approving."
          : "Uploaded and extracted. Review the structure below.",
      );
      await loadExisting();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "The upload failed. Please try again.");
    } finally {
      setUploading(false);
    }
  }

  async function refreshSelected(id: number) {
    try {
      setSelected(await syllabusApi.byId(id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load the syllabus");
    }
  }

  return (
    <Container className="py-10">
      <header className="mb-8">
        <Badge variant="success">TEACHER</Badge>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">Syllabi</h1>
        <p className="mt-2 text-slate-600">
          Upload a syllabus for a class and subject you teach, review what was extracted, then
          approve it so students can read it.
        </p>
      </header>

      {error && (
        <Alert variant="error" className="mb-5">
          {error}
        </Alert>
      )}
      {notice && (
        <Alert variant="success" className="mb-5">
          {notice}
        </Alert>
      )}

      <div className="space-y-8">
        <Card>
          <CardHeader>
            <CardTitle>Upload a syllabus</CardTitle>
            <CardDescription>
              PDF, DOCX or TXT, up to 10 MB. Scanned images cannot be read — OCR is not
              supported yet.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={onUpload} className="grid gap-4 sm:grid-cols-2">
              <Select
                label="Class"
                value={classId}
                options={[
                  { value: "", label: loadingClasses ? "Loading…" : "Select a class" },
                  ...classes.map((c) => ({
                    value: String(c.id),
                    label: `${c.code} — ${c.academicYearName}, ${c.semesterName}`,
                  })),
                ]}
                onChange={(e) => {
                  setClassId(e.target.value);
                  setSubjectId("");
                }}
              />
              <Select
                label="Subject"
                value={subjectId}
                options={[
                  { value: "", label: classId ? "Select a subject" : "Choose a class first" },
                  ...subjects.map((s) => ({ value: String(s.id), label: `${s.code} — ${s.name}` })),
                ]}
                onChange={(e) => setSubjectId(e.target.value)}
              />
              <FileInput
                label="Syllabus file"
                accept={ACCEPTED}
                error={fileError ?? undefined}
                hint="Supported formats: .pdf, .docx, .txt"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setFileError(null);
                }}
              />
              <div className="flex items-end">
                <Button type="submit" disabled={uploading || classes.length === 0}>
                  {uploading ? "Uploading and extracting…" : "Upload syllabus"}
                </Button>
              </div>
            </form>
            {classes.length === 0 && !loadingClasses && (
              <p className="mt-4 text-sm text-slate-500">
                You are not assigned to any classes yet, so there is nothing to upload for.
              </p>
            )}
          </CardContent>
        </Card>

        {existing.length > 0 && (
          <Card>
            <CardHeader>
              <CardTitle>Your syllabi</CardTitle>
              <CardDescription>Everything you have uploaded, newest first.</CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="space-y-2">
                {existing.map((meta) => (
                  <li
                    key={meta.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2"
                  >
                    <span className="text-sm">
                      <span className="font-mono font-semibold">{meta.subjectCode}</span> ·{" "}
                      {meta.classCode} · v{meta.version}
                      <span className="ml-2 text-slate-500">{meta.originalFilename}</span>
                    </span>
                    <span className="flex items-center gap-2">
                      <Badge variant={statusVariant(meta.status)}>{statusLabel(meta.status)}</Badge>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void refreshSelected(meta.id)}
                      >
                        Open
                      </Button>
                    </span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}

        {selected && (
          <ReviewPanel
            detail={selected}
            onChanged={(next) => {
              setSelected(next);
              void loadExisting();
            }}
            onError={setError}
          />
        )}
      </div>
    </Container>
  );
}

interface ReviewPanelProps {
  detail: SyllabusDetail;
  onChanged: (detail: SyllabusDetail) => void;
  onError: (message: string) => void;
}

/**
 * The review screen: shows the raw extracted text, an editable unit/topic
 * tree, and the approve / reject actions.
 */
function ReviewPanel({ detail, onChanged, onError }: ReviewPanelProps) {
  const editable =
    detail.syllabus.status === "REVIEW_REQUIRED" ||
    detail.syllabus.status === "REJECTED" ||
    detail.syllabus.status === "EXTRACTION_FAILED";

  const [units, setUnits] = useState<SyllabusUnitView[]>(() => toEditableUnits(detail));
  const [outcomes, setOutcomes] = useState<Array<{ statement: string; unitIndex: number | null }>>(
    () => detail.learningOutcomes.map((o) => ({ statement: o.statement, unitIndex: null })),
  );
  const [saving, setSaving] = useState(false);
  const [reason, setReason] = useState("");
  const [localNotice, setLocalNotice] = useState<string | null>(null);

  // Reset the draft whenever a different syllabus is opened.
  useEffect(() => {
    setUnits(toEditableUnits(detail));
    setOutcomes(
      detail.learningOutcomes.map((o) => ({ statement: o.statement, unitIndex: null })),
    );
    setReason("");
    setLocalNotice(null);
  }, [detail.syllabus.id]);

  const move = <T,>(list: T[], index: number, delta: number): T[] => {
    const next = [...list];
    const target = index + delta;
    if (target < 0 || target >= next.length) return next;
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  };

  async function onSave() {
    setSaving(true);
    onError("");
    const payload: SyllabusStructurePayload = {
      units: units
        .filter((unit) => unit.title.trim())
        .map((unit) => ({
          title: unit.title.trim(),
          topics: unit.topics
            .filter((topic) => topic.title.trim())
            .map((topic) => ({
              title: topic.title.trim(),
              subtopics: topic.subtopics.filter((sub) => sub.trim()),
            })),
        })),
      learningOutcomes: outcomes
        .filter((outcome) => outcome.statement.trim())
        .map((outcome) => ({ statement: outcome.statement.trim(), unitIndex: outcome.unitIndex })),
    };
    try {
      const updated = await syllabusApi.updateStructure(detail.syllabus.id, payload);
      onChanged(updated);
      setLocalNotice("Structure saved.");
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "Could not save the structure");
    } finally {
      setSaving(false);
    }
  }

  async function onApprove() {
    setSaving(true);
    onError("");
    try {
      onChanged(await syllabusApi.approve(detail.syllabus.id));
      setLocalNotice("Approved. Students enrolled in this class can now read it.");
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "Could not approve");
    } finally {
      setSaving(false);
    }
  }

  async function onReject() {
    setSaving(true);
    onError("");
    try {
      onChanged(await syllabusApi.reject(detail.syllabus.id, reason));
      setLocalNotice("Rejected.");
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "Could not reject");
    } finally {
      setSaving(false);
    }
  }

  async function onRetry() {
    setSaving(true);
    onError("");
    try {
      onChanged(await syllabusApi.retryExtraction(detail.syllabus.id));
    } catch (cause) {
      onError(cause instanceof ApiError ? cause.message : "Could not retry extraction");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>
              {detail.syllabus.subjectCode} — {detail.syllabus.classCode}
            </CardTitle>
            <CardDescription>
              {detail.syllabus.academicYearName}, {detail.syllabus.semesterName} · v
              {detail.syllabus.version} · {detail.syllabus.originalFilename}
            </CardDescription>
          </div>
          <Badge variant={statusVariant(detail.syllabus.status)}>
            {statusLabel(detail.syllabus.status)}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {localNotice && <Alert variant="success">{localNotice}</Alert>}

        {detail.warning && (
          <Alert variant="warning" data-testid="syllabus-warning">
            {detail.warning}
          </Alert>
        )}

        {detail.syllabus.status === "EXTRACTION_FAILED" && (
          <div>
            <Button variant="outline" size="sm" onClick={() => void onRetry()} disabled={saving}>
              Retry extraction
            </Button>
          </div>
        )}

        {detail.extractedText && (
          <details>
            <summary className="cursor-pointer text-sm font-semibold text-slate-900">
              Extracted text
            </summary>
            <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
              {detail.extractedText}
            </pre>
          </details>
        )}

        {/* ------------------------------ units ----------------------------- */}
        <section>
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">
              Units ({units.length})
            </h3>
            {editable && (
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  setUnits((prev) => [
                    ...prev,
                    { title: "", position: prev.length, topics: [] },
                  ])
                }
              >
                Add unit
              </Button>
            )}
          </div>

          {units.length === 0 && (
            <p className="mt-2 text-sm text-slate-500">
              No units yet. Add them yourself, or re-upload a document that has clear unit
              headings.
            </p>
          )}

          <ul className="mt-3 space-y-4">
            {units.map((unit, unitIndex) => (
              <li key={unitIndex} className="rounded-lg border border-slate-200 p-3">
                <div className="flex flex-wrap items-end gap-2">
                  <div className="min-w-[12rem] flex-1">
                    <Input
                      label={`Unit ${unitIndex + 1} title`}
                      value={unit.title}
                      disabled={!editable}
                      onChange={(e) =>
                        setUnits((prev) =>
                          prev.map((u, i) =>
                            i === unitIndex ? { ...u, title: e.target.value } : u,
                          ),
                        )
                      }
                    />
                  </div>
                  {editable && (
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setUnits((prev) => move(prev, unitIndex, -1))}
                        aria-label={`Move unit ${unitIndex + 1} up`}
                      >
                        ↑
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setUnits((prev) => move(prev, unitIndex, 1))}
                        aria-label={`Move unit ${unitIndex + 1} down`}
                      >
                        ↓
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setUnits((prev) => prev.filter((_, i) => i !== unitIndex))}
                        aria-label={`Remove unit ${unitIndex + 1}`}
                      >
                        Remove
                      </Button>
                    </div>
                  )}
                </div>

                <ul className="mt-3 space-y-2">
                  {unit.topics.map((topic, topicIndex) => (
                    <li key={topicIndex} className="rounded bg-slate-50 p-2">
                      <div className="flex flex-wrap items-end gap-2">
                        <div className="min-w-[10rem] flex-1">
                          <Input
                            label={`Topic ${topicIndex + 1}`}
                            value={topic.title}
                            disabled={!editable}
                            onChange={(e) =>
                              setUnits((prev) =>
                                prev.map((u, i) =>
                                  i === unitIndex
                                    ? {
                                        ...u,
                                        topics: u.topics.map((t, j) =>
                                          j === topicIndex ? { ...t, title: e.target.value } : t,
                                        ),
                                      }
                                    : u,
                                ),
                              )
                            }
                          />
                        </div>
                        {editable && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() =>
                              setUnits((prev) =>
                                prev.map((u, i) =>
                                  i === unitIndex
                                    ? {
                                        ...u,
                                        topics: u.topics.filter((_, j) => j !== topicIndex),
                                      }
                                    : u,
                                ),
                              )
                            }
                            aria-label={`Remove topic ${topicIndex + 1} from unit ${unitIndex + 1}`}
                          >
                            Remove
                          </Button>
                        )}
                      </div>

                      {topic.subtopics.length > 0 && (
                        <ul className="mt-2 space-y-1 pl-4 text-sm text-slate-600">
                          {topic.subtopics.map((sub, subIndex) => (
                            <li key={subIndex} className="list-disc">{sub}</li>
                          ))}
                        </ul>
                      )}
                    </li>
                  ))}
                </ul>

                {editable && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-2"
                    onClick={() =>
                      setUnits((prev) =>
                        prev.map((u, i) =>
                          i === unitIndex
                            ? {
                                ...u,
                                topics: [
                                  ...u.topics,
                                  emptyTopic(u.topics.length),
                                ],
                              }
                            : u,
                        ),
                      )
                    }
                  >
                    Add topic to unit {unitIndex + 1}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </section>

        {/* -------------------------- learning outcomes --------------------- */}
        <section>
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">
              Learning outcomes ({outcomes.length})
            </h3>
            {editable && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setOutcomes((prev) => [...prev, { statement: "", unitIndex: null }])}
              >
                Add outcome
              </Button>
            )}
          </div>
          <ul className="mt-3 space-y-2">
            {outcomes.map((outcome, index) => (
              <li key={index} className="flex flex-wrap items-end gap-2">
                <div className="min-w-[16rem] flex-1">
                  <Textarea
                    label={`Outcome ${index + 1}`}
                    rows={2}
                    value={outcome.statement}
                    disabled={!editable}
                    onChange={(e) =>
                      setOutcomes((prev) =>
                        prev.map((o, i) =>
                          i === index ? { ...o, statement: e.target.value } : o,
                        ),
                      )
                    }
                  />
                </div>
                {editable && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setOutcomes((prev) => prev.filter((_, i) => i !== index))}
                    aria-label={`Remove outcome ${index + 1}`}
                  >
                    Remove
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </section>

        {/* ------------------------------ actions --------------------------- */}
        {editable && (
          <div className="flex flex-wrap items-end gap-3 border-t border-slate-200 pt-4">
            <Button onClick={() => void onSave()} disabled={saving}>
              {saving ? "Saving…" : "Save as draft"}
            </Button>
            <Button
              variant="outline"
              onClick={() => void onApprove()}
              disabled={saving || units.filter((u) => u.title.trim()).length === 0}
            >
              Approve
            </Button>
            <div className="min-w-[14rem] flex-1">
              <Input
                label="Rejection reason (optional)"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </div>
            <Button variant="danger" onClick={() => void onReject()} disabled={saving}>
              Reject
            </Button>
          </div>
        )}

        {detail.syllabus.status === "APPROVED" && (
          <Alert variant="success">
            This syllabus is approved and visible to enrolled students. To change it, upload a
            new version.
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

function emptyTopic(position: number): SyllabusTopicView {
  return { title: "", position, subtopics: [] };
}

/** Converts the server shape into a locally editable copy. */
function toEditableUnits(detail: SyllabusDetail): SyllabusUnitView[] {
  return detail.units.map((unit, unitIndex) => ({
    id: unit.id,
    title: unit.title,
    position: unitIndex,
    topics: (unit.topics ?? []).map((topic, topicIndex) => ({
      id: topic.id,
      title: topic.title,
      position: topicIndex,
      subtopics: topic.subtopics ?? [],
    })),
  }));
}

/** Exposed for tests. */
export { validateFile, toEditableUnits };