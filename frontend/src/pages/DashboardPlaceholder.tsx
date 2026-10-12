import { Navigate, Link, useParams } from "react-router-dom";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { cx } from "../lib/utils";

type RoleKey = "student" | "teacher" | "admin";

interface ModuleSpec {
  name: string;
  description: string;
}

const ROLE_CONFIG: Record<RoleKey, { title: string; tagline: string; modules: ModuleSpec[] }> = {
  student: {
    title: "Student dashboard",
    tagline: "Your courses, deadlines, and AI study help — in one place.",
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
  const config = (Object.keys(ROLE_CONFIG) as RoleKey[]).includes(role as RoleKey)
    ? ROLE_CONFIG[role as RoleKey]
    : null;

  if (!config) {
    return <Navigate to="/" replace />;
  }

  const others = (Object.keys(ROLE_CONFIG) as RoleKey[]).filter((key) => key !== role);

  return (
    <Container className="py-10 sm:py-14">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Badge variant="warning">Phase 1 placeholder</Badge>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
            {config.title}
          </h1>
          <p className="mt-2 text-lg text-slate-600">{config.tagline}</p>
        </div>
        <div className="flex gap-2" aria-label="Switch role preview">
          {others.map((other) => (
            <Button key={other} variant="outline" size="sm">
              <Link to={`/dashboard/${other}`} className="capitalize">
                {other} view
              </Link>
            </Button>
          ))}
        </div>
      </div>

      <Alert variant="info" className="mt-6">
        This is a placeholder dashboard. Authentication, real data, and AI features are
        delivered in later phases — the REST API surface they will consume is already
        versioned under <code className="font-mono">/api/v1</code>.
      </Alert>

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

      <div className="mt-10 text-center">
        <Button variant="outline" className={cx()}>
          <Link to="/">Back to home</Link>
        </Button>
      </div>
    </Container>
  );
}
