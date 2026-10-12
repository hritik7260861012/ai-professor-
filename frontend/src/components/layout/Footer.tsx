import { Link } from "react-router-dom";
import { Container } from "../ui/Layout";

export function Footer() {
  return (
    <footer className="border-t border-slate-200 bg-white">
      <Container className="py-8">
        <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
          <p className="text-sm text-slate-500">
            © {new Date().getFullYear()} AI Professor Assistant. Phase 1 — scaffolding and design
            system.
          </p>
          <nav className="flex flex-wrap gap-x-6 gap-y-2" aria-label="Footer">
            <Link to="/" className="text-sm font-medium text-slate-600 hover:text-brand-600">
              Home
            </Link>
            <Link to="/login" className="text-sm font-medium text-slate-600 hover:text-brand-600">
              Sign in
            </Link>
            <Link to="/register" className="text-sm font-medium text-slate-600 hover:text-brand-600">
              Register
            </Link>
            <a
              href="/swagger-ui.html"
              className="text-sm font-medium text-slate-600 hover:text-brand-600"
              target="_blank"
              rel="noreferrer"
            >
              API docs
            </a>
          </nav>
        </div>
      </Container>
    </footer>
  );
}
