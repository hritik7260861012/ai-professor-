import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError, authApi, RegisterPayload } from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { Input } from "../components/ui/Input";
import { Select } from "../components/ui/Input";

interface FormState {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
  role: RegisterPayload["role"];
}

const initialForm: FormState = {
  fullName: "",
  email: "",
  password: "",
  confirmPassword: "",
  role: "STUDENT",
};

type Status = { kind: "info" | "error"; message: string } | null;

type FormErrors = Partial<Record<keyof FormState, string>>;

export default function Register() {
  const [form, setForm] = useState<FormState>(initialForm);
  const [errors, setErrors] = useState<FormErrors>({});
  const [status, setStatus] = useState<Status>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): boolean {
    const next: FormErrors = {};
    if (form.fullName.trim().length < 2) {
      next.fullName = "Full name must be at least 2 characters";
    }
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
    if (form.confirmPassword !== form.password) {
      next.confirmPassword = "Passwords do not match";
    }
    if (!["STUDENT", "TEACHER", "ADMIN"].includes(form.role)) {
      next.role = "Choose a valid role";
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
      // Account creation (password hashing, verification email) is Phase 2.
      await authApi.register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      setStatus({ kind: "info", message: "Account created." });
    } catch (error) {
      if (error instanceof ApiError && error.status === 501) {
        setStatus({
          kind: "info",
          message:
            "Your input passed server-side validation, but registration goes live in Phase 2 — no account was created.",
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
          <CardTitle className="text-2xl">Create your account</CardTitle>
          <CardDescription>Join AI Professor Assistant as a teacher, student, or admin</CardDescription>
        </CardHeader>
        <CardContent>
          <Alert variant="info" className="mb-5">
            Phase 1 preview — registration is not wired up yet. The form is fully
            validated, and the API round-trip is exercised end to end.
          </Alert>

          {status && (
            <Alert variant={status.kind === "error" ? "error" : "info"} className="mb-5">
              {status.message}
            </Alert>
          )}

          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <Input
              label="Full name"
              autoComplete="name"
              placeholder="Ada Lovelace"
              value={form.fullName}
              error={errors.fullName}
              onChange={(event) => setForm((prev) => ({ ...prev, fullName: event.target.value }))}
            />
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
              autoComplete="new-password"
              placeholder="••••••••"
              value={form.password}
              error={errors.password}
              hint="At least 8 characters."
              onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
            />
            <Input
              label="Confirm password"
              type="password"
              autoComplete="new-password"
              placeholder="••••••••"
              value={form.confirmPassword}
              error={errors.confirmPassword}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, confirmPassword: event.target.value }))
              }
            />
            <Select
              label="I am a…"
              value={form.role}
              error={errors.role}
              options={[
                { value: "STUDENT", label: "Student" },
                { value: "TEACHER", label: "Teacher" },
                { value: "ADMIN", label: "Administrator" },
              ]}
              onChange={(event) =>
                setForm((prev) => ({
                  ...prev,
                  role: event.target.value as RegisterPayload["role"],
                }))
              }
            />
            <Button type="submit" size="lg" className="w-full" disabled={submitting}>
              {submitting ? "Creating account…" : "Create account"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-600">
            Already have an account?{" "}
            <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </Container>
  );
}
