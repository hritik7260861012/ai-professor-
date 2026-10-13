import { FormEvent, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { dashboardPathFor, useAuth } from "../auth/AuthContext";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { Input } from "../components/ui/Input";

interface FormState {
  email: string;
  password: string;
}

type Status = { kind: "info" | "error" | "success"; message: string } | null;

export default function Login() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [form, setForm] = useState<FormState>({ email: "", password: "" });
  const [errors, setErrors] = useState<Partial<FormState>>({});
  const [status, setStatus] = useState<Status>(null);
  const [submitting, setSubmitting] = useState(false);

  // Where to go after a successful sign-in: the originally requested page,
  // or the dashboard matching the account's role.
  const from = (location.state as { from?: string } | null)?.from;

  function validate(): boolean {
    const next: Partial<FormState> = {};
    if (!form.email.trim()) {
      next.email = "Email is required";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      next.email = "Email must be a valid address";
    }
    if (!form.password) {
      next.password = "Password is required";
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
      const signedIn = await login({
        email: form.email.trim(),
        password: form.password,
      });
      setStatus({ kind: "success", message: "Signed in." });
      navigate(from ?? dashboardPathFor(signedIn.role), { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        // 403 = suspended account; 401 = bad credentials. Both are the
        // server's message, which is safe to show verbatim.
        setStatus({ kind: "error", message: error.message });
      } else {
        setStatus({ kind: "error", message: "Something unexpected happened. Please try again." });
      }
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <Container className="py-24 text-center text-sm text-slate-600">
        Checking your session…
      </Container>
    );
  }

  // Already signed in — never show the login form again.
  if (user) {
    return <Navigate to={dashboardPathFor(user.role)} replace />;
  }

  return (
    <Container className="flex flex-col items-center py-12 sm:py-16">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Welcome back</CardTitle>
          <CardDescription>Sign in to your AI Professor Assistant account</CardDescription>
        </CardHeader>
        <CardContent>
          {status && (
            <Alert
              variant={status.kind === "error" ? "error" : status.kind === "success" ? "success" : "info"}
              className="mb-5"
            >
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
              onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
            />
            <Button type="submit" size="lg" className="w-full" disabled={submitting}>
              {submitting ? "Signing in…" : "Sign in"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-600">
            New to campus?{" "}
            <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
              Create a student account
            </Link>
          </p>
        </CardContent>
      </Card>
    </Container>
  );
}