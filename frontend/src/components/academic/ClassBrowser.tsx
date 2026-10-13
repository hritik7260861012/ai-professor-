import { useCallback, useEffect, useState } from "react";
import { academicApi, type AcademicClass, type ClassSubject } from "../../api/client";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../ui/Card";

interface ClassBrowserProps {
  /** Classes to render. Already scoped by the API. */
  classes: AcademicClass[];
  /** Shown when the caller has no classes yet. */
  emptyMessage: string;
  /** Teachers only see their own subjects, so the wording differs. */
  subjectHeading: string;
}

/**
 * Lists classes and lazily reveals their subjects on demand.
 *
 * <p>Subjects are fetched per class so that a teacher (who only sees the
 * subjects assigned to them) and a student (who sees the whole enrolled
 * syllabus) both get the server-scoped view.
 */
export function ClassBrowser({ classes, emptyMessage, subjectHeading }: ClassBrowserProps) {
  const [expanded, setExpanded] = useState<number | null>(null);
  const [subjects, setSubjects] = useState<ClassSubject[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = useCallback(
    async (classId: number) => {
      if (expanded === classId) {
        setExpanded(null);
        return;
      }
      setExpanded(classId);
      setLoading(true);
      setError(null);
      try {
        setSubjects(await academicApi.classSubjects(classId));
      } catch (cause) {
        setSubjects([]);
        setError(cause instanceof Error ? cause.message : "Could not load subjects");
      } finally {
        setLoading(false);
      }
    },
    [expanded],
  );

  if (classes.length === 0) {
    return (
      <Card>
        <CardContent className="pt-5">
          <p className="text-sm text-slate-600">{emptyMessage}</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <ul className="space-y-4">
      {classes.map((academicClass) => {
        const isOpen = expanded === academicClass.id;
        return (
          <li key={academicClass.id}>
            <Card>
              <CardHeader>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <CardTitle>
                      {academicClass.code} · {academicClass.name}
                    </CardTitle>
                    <CardDescription>
                      {academicClass.programName} — {academicClass.academicYearName},{" "}
                      {academicClass.semesterName}
                    </CardDescription>
                  </div>
                  <Badge variant={academicClass.enrolledCount >= academicClass.capacity ? "warning" : "default"}>
                    {academicClass.enrolledCount}/{academicClass.capacity} enrolled
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => void toggle(academicClass.id)}
                  aria-expanded={isOpen}
                >
                  {isOpen ? "Hide subjects" : subjectHeading}
                </Button>

                {isOpen && (
                  <div className="mt-4 border-t border-slate-100 pt-4">
                    {loading && <p className="text-sm text-slate-500">Loading subjects…</p>}
                    {error && <p className="text-sm text-red-600">{error}</p>}
                    {!loading && !error && subjects.length === 0 && (
                      <p className="text-sm text-slate-500">No subjects available to you here.</p>
                    )}
                    {!loading && !error && subjects.length > 0 && (
                      <ul className="space-y-2">
                        {subjects.map((subject) => (
                          <li
                            key={subject.id}
                            className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2"
                          >
                            <span className="text-sm font-medium text-slate-900">
                              {subject.subjectCode} · {subject.subjectName}
                            </span>
                            <Badge variant="default">{subject.credits} credits</Badge>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          </li>
        );
      })}
    </ul>
  );
}

/** Loads the caller's own classes once. */
export function useMyClasses() {
  const [classes, setClasses] = useState<AcademicClass[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setClasses(await academicApi.myClasses());
    } catch (cause) {
      setClasses([]);
      setError(cause instanceof Error ? cause.message : "Could not load classes");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { classes, loading, error, reload };
}