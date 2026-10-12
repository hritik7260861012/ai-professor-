import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError, authApi } from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { Input } from "../components/ui/Input";

interface FormState {
  email: string;
  password: string;
}

type Status = { kind: "info" | "error"; message: string } | null;

export default function Login() {
  const [form, setForm] = useState<FormState>({ email: "", password: "" });
  const [errors, setErrors] = useState<Partial<FormState>>({});
  const [status, setStatus] = useState<Status>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): boolean {
    const next: Partial<FormState> = {};
    if (!form.email.trim()) {
      next.email = "Email is required";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      next.email = "Email must be a valid address";
    }
    if (!form.password) {
      next.password = "Password is required";
    } else if (form.password.length < 8) {
      next.password = "Password must be at least 8 characters";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setStatus(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      // Phase 1: the server validates the payload and responds 501.
      // Full authentication is implemented in Phase 2.
      await authApi.login({ email: form.email.trim(), password: form.password });
      setStatus({ kind: "info", message: "Signed in." });
    } catch (error) {
      if (error instanceof ApiError && error.status === 501) {
        setStatus({
          kind: "info",
          message:
            "Your input passed server-side validation, but sign-in goes live in Phase 2 — no session was created.",
        });
      } else if (error instanceof ApiError) {
        setStatus({ kind: "error", message: error.message });
      } else {
        setStatus({ kind: "error", message: "Something unexpected happened. Please try again." });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container className="flex flex-col items-center py-12 sm:py-16">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Welcome back</CardTitle>
          <CardDescription>Sign in to your AI Professor Assistant account</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="info" className="mb-5">
            Phase 1 preview — sign-in is not wired up yet. The form is fully
            validated, and the API round-trip is exercised end to end.
          </Alert>

          {status && (
            <Alert variant={status.kind === "error" ? "error" : "info"} className="mb-5">
              {status.message}
            </Alert>
          )}

          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <Input
              label="Email address"
              type="email"
              autoComplete="email"
              placeholder="you@college.edu"
              value={form.email}
              error={errors.email}
              onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={form.password}
              error={errors.password}
              hint="At least 8 characters."
              onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
            />
            <Button type="submit" size="lg" className="w-full" disabled={submitting}>
              {submitting ? "Signing in…" : "Sign in"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-600">
            New to campus?{" "}
            <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>
        </CardContent>
      </Card>
    </Container>
  );
}
