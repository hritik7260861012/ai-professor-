import { useEffect, useState } from "react";
import {
  ApiError,
  studentContentApi,
  type GenerationContentType,
  type GenerationLanguage,
  type StudentContent,
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
import { Container } from "../components/ui/Layout";

const CONTENT_TYPE_LABELS: Record<GenerationContentType, string> = {
  UNIT_NOTES: "Unit-wise notes",
  TEACHER_STUDY_MATERIAL: "Teacher study material",
  PPT_SLIDES: "Slide content",
  ASSIGNMENTS_QUESTIONS: "Assignments & questions",
  MCQS: "MCQs",
  QUICK_REVISION: "Quick revision",
};

const LANGUAGE_LABELS: Record<GenerationLanguage, string> = {
  ENGLISH: "English",
  HINDI: "Hindi",
  HINGLISH: "Hinglish",
};

export default function StudentContent() {
  const [items, setItems] = useState<StudentContent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    studentContentApi
      .list()
      .then((content) => {
        if (active) setItems(content);
      })
      .catch((cause) => {
        if (!active) return;
        setError(
          cause instanceof ApiError
            ? cause.message
            : "Unable to load your content. Please try again.",
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <Container className="py-10">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">Study material</h1>
        <p className="mt-2 text-slate-600">
          Notes, slides and questions your teachers have approved and released for your classes.
        </p>
      </header>

      {error && (
        <div className="mb-6">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-slate-500">Loading your material…</p>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="pt-5">
            <p className="text-sm text-slate-600">
              Nothing has been released yet. Your teachers publish material here once they have
              reviewed and approved it.
            </p>
          </CardContent>
        </Card>
      ) : (
        <ul className="space-y-4">
          {items.map((item) => (
            <li key={item.id}>
              <Card>
                <CardHeader>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <CardTitle>{item.title}</CardTitle>
                      <CardDescription>
                        {item.subjectCode} · {item.classCode}
                        {item.unitTitle ? ` · ${item.unitTitle}` : ""}
                      </CardDescription>
                    </div>
                    <div className="flex gap-2">
                      <Badge variant="brand">
                        {CONTENT_TYPE_LABELS[item.contentType] ?? item.contentType}
                      </Badge>
                      <Badge>{LANGUAGE_LABELS[item.language] ?? item.language}</Badge>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {expanded === item.id ? (
                    <>
                      <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
                        {item.body}
                      </pre>
                      <button
                        type="button"
                        className="mt-3 text-sm font-medium text-brand-700 underline"
                        onClick={() => setExpanded(null)}
                      >
                        Hide content
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      className="text-sm font-medium text-brand-700 underline"
                      onClick={() => setExpanded(item.id)}
                    >
                      View content
                    </button>
                  )}
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </Container>
  );
}