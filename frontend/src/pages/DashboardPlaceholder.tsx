import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { academicApi, type Enrollment, type TeachingAssignment } from "../api/client";
import { ClassBrowser, useMyClasses } from "../components/academic/ClassBrowser";
import { useAuth } from "../auth/AuthContext";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";

type RoleKey = "student" | "teacher" | "admin";

interface ModuleSpec {
  name: string;
  description: string;
}

const ROLE_CONFIG: Record<RoleKey, { title: string; tagline: string; modules: ModuleSpec[] }> = {
  student: {
    title: "Student dashboard",
    tagline: "Your courses, deadlines, and AI study help - in one place.",
    modules: [
      { name: "My assignments", description: "Upcoming deadlines, submissions, and feedback." },
      { name: "AI tutor", description: "Syllabus-grounded Q&A with escalation to teachers." },
      { name: "Grades", description: "Progress across every enrolled course." },
    ],
  },
  teacher: {
    title: "Teacher dashboard",
    tagline: "Prepare lessons, review work, and keep every student on track.",
    modules: [
      { name: "Lesson planner", description: "AI-assisted lesson and quiz preparation." },
      { name: "Grading queue", description: "Rubric-based draft feedback with final teacher review." },
      { name: "Class insights", description: "Attendance and engagement at a glance." },
    ],
  },
  admin: {
    title: "Admin dashboard",
    tagline: "College-wide analytics, accounts, and platform configuration.",
    modules: [
      { name: "Usage analytics", description: "Adoption and engagement across departments." },
      { name: "Account management", description: "Teachers, students, and role assignments." },
      { name: "Integrations", description: "LMS and SSO configuration." },
    ],
  },
};

export default function DashboardPlaceholder() {
  const { role } = useParams<{ role: string }>();
  const { user, logout } = useAuth();
  const config = (Object.keys(ROLE_CONFIG) as RoleKey[]).includes(role as RoleKey)
    ? ROLE_CONFIG[role as RoleKey]
    : null;

  if (!config || !user) {
    return null;
  }

  return (
    <Container className="py-10 sm:py-14">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Badge variant="success">Signed in as {user.role}</Badge>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{config.title}</h1>
          <p className="mt-2 text-lg text-slate-600">{config.tagline}</p>
          <p className="mt-1 text-sm text-slate-500">
            {user.fullName} · {user.email}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {/* ADMIN already has a dedicated card below with this link. */}
          {user.role === "TEACHER" && (
            <Button>
              <Link to="/teacher/syllabi">Manage syllabi</Link>
            </Button>
          )}
          {user.role === "STUDENT" && (
            <Button>
              <Link to="/student/syllabi">My syllabi</Link>
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      </div>

      {user.role === "ADMIN" ? (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>Academic structure</CardTitle>
            <CardDescription>
              Manage departments, programs, semesters, subjects, classes, enrollments and
              teaching assignments.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button>
              <Link to="/admin/academic">Open academic management</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <AcademicSection roleKey={role as RoleKey} />
      )}

      <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {config.modules.map((module) => (
          <Card key={module.name} className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>{module.name}</CardTitle>
                <Badge variant="default">Planned</Badge>
              </div>
            </CardHeader>
            <CardContent className="flex-1">
              <CardDescription>{module.description}</CardDescription>
            </CardContent>
          </Card>
        ))}
      </div>
    </Container>
  );
}

/**
 * Role-scoped academic overview.
 *
 * <p>Students see their enrolled classes and subjects; teachers see the
 * classes and subjects assigned to them. Both come from the scoped
 * endpoints, so the UI never has to filter anything itself.
 */
function AcademicSection({ roleKey }: { roleKey: RoleKey }) {
  const { classes, loading, error, reload } = useMyClasses();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [assignments, setAssignments] = useState<TeachingAssignment[]>([]);

  const loadExtras = useCallback(async () => {
    if (roleKey === "student") {
      try {
        setEnrollments(await academicApi.myEnrollments());
      } catch {
        setEnrollments([]);
      }
    }
    if (roleKey === "teacher") {
      try {
        setAssignments(await academicApi.myAssignments());
      } catch {
        setAssignments([]);
      }
    }
  }, [roleKey]);

  useEffect(() => {
    void loadExtras();
  }, [loadExtras]);

  return (
    <section className="mt-8">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-900">
            {roleKey === "student" ? "My classes" : "My teaching assignments"}
          </h2>
          <p className="text-sm text-slate-600">
            {roleKey === "student"
              ? "Classes you are enrolled in, with the subjects they offer."
              : "Classes and subjects assigned to you, plus your rosters."}
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => void reload()}>
          Refresh
        </Button>
      </div>

      {error && (
        <Alert variant="error" className="mb-4">
          {error}
        </Alert>
      )}

      {loading ? (
        <p className="text-sm text-slate-500">Loading your academic structure…</p>
      ) : (
        <>
          {roleKey === "teacher" && assignments.length > 0 && (
            <ul className="mb-4 space-y-1">
              {assignments.map((assignment) => (
                <li key={assignment.id} className="rounded bg-slate-50 px-3 py-1.5 text-sm">
                  Teaching {assignment.subjectCode} in {assignment.classCode}
                </li>
              ))}
            </ul>
          )}
          {roleKey === "student" && enrollments.length > 0 && (
            <p className="mb-4 text-sm text-slate-600">
              You are enrolled in {enrollments.length}{" "}
              {enrollments.length === 1 ? "class" : "classes"}.
            </p>
          )}
          <ClassBrowser
            classes={classes}
            emptyMessage={
              roleKey === "student"
                ? "You are not enrolled in any classes yet."
                : "No classes have been assigned to you yet."
            }
            subjectHeading="View subjects"
          />
        </>
      )}
    </section>
  );
}