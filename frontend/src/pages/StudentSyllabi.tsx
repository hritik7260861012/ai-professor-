import { useCallback, useEffect, useState } from "react";
import { syllabusApi, type SyllabusDetail } from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";

/**
 * Student-facing syllabus list.
 *
 * <p>Shows only what the server already authorised: APPROVED syllabi for
 * classes the student is enrolled in. There are deliberately no edit,
 * approve or reject controls here.
 */
export default function StudentSyllabi() {
  const [items, setItems] = useState<SyllabusDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await syllabusApi.studentList());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load your syllabi");
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Container className="py-10">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">My syllabi</h1>
        <p className="mt-2 text-slate-600">
          Approved syllabus content for the classes you are enrolled in.
        </p>
      </header>

      {error && (
        <Alert variant="error" className="mb-5">
          {error}
        </Alert>
      )}

      {loading ? (
        <p className="text-sm text-slate-600">Loading your syllabi…</p>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="pt-5">
            <p className="text-sm text-slate-600">
              No syllabus has been published for your classes yet. Your teacher publishes one
              after reviewing it.
            </p>
          </CardContent>
        </Card>
      ) : (
        <ul className="space-y-5">
          {items.map((item) => (
            <li key={item.syllabus.id}>
              <Card>
                <CardHeader>
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <CardTitle>
                        {item.syllabus.subjectCode} — {item.syllabus.subjectName}
                      </CardTitle>
                      <CardDescription>
                        {item.syllabus.className} · {item.syllabus.academicYearName},{" "}
                        {item.syllabus.semesterName} · {item.syllabus.uploadedByName}
                      </CardDescription>
                    </div>
                    <Badge variant="success">Approved</Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {item.units.length === 0 ? (
                    <p className="text-sm text-slate-500">
                      The teacher has not organised any units yet.
                    </p>
                  ) : (
                    item.units.map((unit, unitIndex) => (
                      <section key={unit.id ?? unitIndex}>
                        <h3 className="text-sm font-semibold text-slate-900">{unit.title}</h3>
                        {unit.topics.length > 0 && (
                          <ul className="mt-1 space-y-1 pl-5 text-sm text-slate-700">
                            {unit.topics.map((topic, topicIndex) => (
                              <li key={topic.id ?? topicIndex} className="list-disc">
                                <span>{topic.title}</span>
                                {topic.subtopics.length > 0 && (
                                  <ul className="mt-1 space-y-1 pl-4 text-slate-600">
                                    {topic.subtopics.map((sub, subIndex) => (
                                      <li key={subIndex} className="list-circle">
                                        {sub}
                                      </li>
                                    ))}
                                  </ul>
                                )}
                              </li>
                            ))}
                          </ul>
                        )}
                      </section>
                    ))
                  )}

                  {item.learningOutcomes.length > 0 && (
                    <section>
                      <h3 className="text-sm font-semibold text-slate-900">Learning outcomes</h3>
                      <ul className="mt-1 space-y-1 pl-5 text-sm text-slate-700">
                        {item.learningOutcomes.map((outcome, index) => (
                          <li key={outcome.id ?? index} className="list-disc">
                            {outcome.statement}
                          </li>
                        ))}
                      </ul>
                    </section>
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