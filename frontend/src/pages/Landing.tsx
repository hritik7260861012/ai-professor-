import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { healthApi } from "../api/client";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container, SectionHeading } from "../components/ui/Layout";

type HealthState =
  | { kind: "loading" }
  | { kind: "up"; version: string }
  | { kind: "down" };

function useHealth(): HealthState {
  const [state, setState] = useState<HealthState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    healthApi
      .get()
      .then((status) => {
        if (!cancelled) {
          setState(
            status.status === "UP"
              ? { kind: "up", version: status.version }
              : { kind: "down" },
          );
        }
      })
      .catch(() => {
        if (!cancelled) setState({ kind: "down" });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

function HealthPill() {
  const health = useHealth();

  if (health.kind === "loading") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-1 text-xs font-medium text-slate-600">
        <span className="h-2 w-2 animate-pulse rounded-full bg-slate-400" />
        Checking service status…
      </span>
    );
  }
  if (health.kind === "up") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-800">
        <span className="h-2 w-2 rounded-full bg-emerald-500" />
        All systems operational · API v{health.version}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-800">
      <span className="h-2 w-2 rounded-full bg-amber-500" />
      Backend offline — start Spring Boot on port 8080
    </span>
  );
}

const features = [
  {
    title: "AI lesson preparation",
    description:
      "Generate lesson plans, quizzes, and reading lists aligned to your syllabus in seconds.",
    icon: "M12 6.25 6.5 9.5v5L12 17.75 17.5 14.5v-5L12 6.25Zm0 0L12 2.75m0 3.5L17.5 9.5M12 17.75 6.5 14.5m5.5 3.25V21.25",
  },
  {
    title: "Smart grading assistance",
    description:
      "Draft rubric-based feedback on submissions while you keep final review and control.",
    icon: "M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z",
  },
  {
    title: "Attendance insights",
    description:
      "Spot at-risk students early with clear, privacy-respecting attendance analytics.",
    icon: "M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75ZM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V8.625ZM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V4.125Z",
  },
  {
    title: "Assignment tracking",
    description:
      "One place for deadlines, submissions, and late-work policies across every course.",
    icon: "M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 0 1 0 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a2.999 2.999 0 0 1 0-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375Z",
  },
  {
    title: "Student Q&A",
    description:
      "Students get instant, syllabus-grounded answers — with escalation to a human when needed.",
    icon: "M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z",
  },
  {
    title: "Admin analytics",
    description:
      "Department-wide overview of engagement, outcomes, and platform adoption.",
    icon: "M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75ZM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V8.625ZM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V4.125Z",
  },
];

const roles = [
  {
    to: "/dashboard/teacher",
    name: "For teachers",
    description: "Prepare lessons, review submissions, and track class health.",
    cta: "Teacher dashboard",
  },
  {
    to: "/dashboard/student",
    name: "For students",
    description: "Assignments, deadlines, and AI help grounded in your courses.",
    cta: "Student dashboard",
  },
  {
    to: "/dashboard/admin",
    name: "For administrators",
    description: "Usage analytics and account management for the whole college.",
    cta: "Admin dashboard",
  },
];

export default function Landing() {
  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-brand-50 to-white">
        <Container className="flex flex-col items-center py-20 text-center sm:py-28">
          <HealthPill />
          <h1 className="mt-6 max-w-3xl text-4xl font-extrabold tracking-tight text-slate-900 sm:text-6xl">
            The AI-powered assistant for{" "}
            <span className="text-brand-600">modern college teaching</span>
          </h1>
          <p className="mt-6 max-w-2xl text-lg text-slate-600">
            AI Professor Assistant helps teachers prepare lessons, students stay on
            track, and administrators understand their campus — all through one
            web-first platform.
          </p>
          <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row">
            <Button size="lg" className="w-full sm:w-auto">
              <Link to="/register">Get started free</Link>
            </Button>
            <Button size="lg" variant="outline" className="w-full sm:w-auto">
              <a href="#features">Explore features</a>
            </Button>
          </div>
          <p className="mt-4 text-xs text-slate-400">
            Phase 1 preview — accounts and sign-in arrive in Phase 2.
          </p>
        </Container>
      </section>

      {/* Features */}
      <section id="features" className="py-16 sm:py-24">
        <Container>
          <SectionHeading
            eyebrow="Features"
            title="Everything a college course needs"
            description="Purpose-built tools for the full teaching lifecycle — from syllabus to final grades."
          />
          <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {features.map((feature) => (
              <Card key={feature.title} className="transition-shadow hover:shadow-soft">
                <CardHeader>
                  <span className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
                    <svg
                      className="h-6 w-6"
                      fill="none"
                      viewBox="0 0 24 24"
                      strokeWidth={1.5}
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" d={feature.icon} />
                    </svg>
                  </span>
                  <CardTitle className="mt-2">{feature.title}</CardTitle>
                </CardHeader>
                <CardContent>
                  <CardDescription>{feature.description}</CardDescription>
                </CardContent>
              </Card>
            ))}
          </div>
        </Container>
      </section>

      {/* Roles */}
      <section id="roles" className="border-y border-slate-200 bg-white py-16 sm:py-24">
        <Container>
          <SectionHeading
            eyebrow="Roles"
            title="Built for every campus role"
            description="One platform, three purpose-built experiences. Each dashboard is stubbed in Phase 1 and fills in during later phases."
          />
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {roles.map((role) => (
              <Card key={role.to} className="flex flex-col transition-shadow hover:shadow-soft">
                <CardHeader>
                  <CardTitle>{role.name}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-1 flex-col">
                  <CardDescription className="flex-1">{role.description}</CardDescription>
                  <Button variant="outline" size="sm" className="mt-4 self-start">
                    <Link to={role.to}>{role.cta}</Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </Container>
      </section>

      {/* CTA */}
      <section className="py-16 sm:py-24">
        <Container>
          <div className="rounded-2xl bg-brand-600 px-6 py-14 text-center sm:px-12">
            <h2 className="text-3xl font-bold tracking-tight text-white">
              Ready when you are
            </h2>
            <p className="mx-auto mt-4 max-w-xl text-brand-100">
              Phase 1 establishes the platform foundation. Authentication, courses,
              assignments, and AI features ship in the upcoming phases.
            </p>
            <div className="mt-8">
              <Button size="lg" variant="secondary" className="bg-white text-brand-700 hover:bg-brand-50">
                <Link to="/register">Create an account</Link>
              </Button>
            </div>
          </div>
        </Container>
      </section>
    </>
  );
}
