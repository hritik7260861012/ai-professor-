import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { dashboardPathFor, useAuth } from "../../auth/AuthContext";
import { cx } from "../../lib/utils";
import { Button } from "../ui/Button";

const publicLinks = [
  { to: "/#features", label: "Features", external: true },
  { to: "/#roles", label: "Roles", external: true },
];

export function Navbar() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const menuRef = useRef<HTMLDivElement>(null);
  const { user, logout } = useAuth();

  // Close the mobile menu on navigation.
  useEffect(() => {
    setOpen(false);
  }, [location.pathname, location.hash]);

  // Close the mobile menu on outside click / Escape.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    const onClick = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("mousedown", onClick);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("mousedown", onClick);
    };
  }, [open]);

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <Link to="/" className="flex items-center gap-2.5" aria-label="AI Professor Assistant home">
          <img src="/logo.svg" alt="" className="h-8 w-8" />
          <span className="text-lg font-bold tracking-tight text-slate-900">
            AI Professor<span className="text-brand-600"> Assistant</span>
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="Primary">
          {publicLinks.map((link) => (
            <a
              key={link.label}
              href={link.to}
              className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            >
              {link.label}
            </a>
          ))}
          {user && (
            <NavLink
              to={dashboardPathFor(user.role)}
              className={({ isActive }) =>
                cx(
                  "rounded-lg px-3 py-2 text-sm font-medium",
                  isActive
                    ? "bg-brand-50 text-brand-700"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                )
              }
            >
              Dashboard
            </NavLink>
          )}
        </nav>

        <div className="hidden items-center gap-2 md:flex">
          {user ? (
            <>
              <span className="text-sm text-slate-600">{user.fullName}</span>
              <Button variant="outline" size="sm" onClick={() => void logout()}>
                Sign out
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" className="hidden sm:inline-flex">
                <Link to="/login">Sign in</Link>
              </Button>
              <Button variant="primary" size="sm" className="hidden sm:inline-flex">
                <Link to="/register">Get started</Link>
              </Button>
            </>
          )}
        </div>

        <div className="md:hidden" ref={menuRef}>
          <button
            type="button"
            onClick={() => setOpen((value) => !value)}
            aria-expanded={open}
            aria-controls="mobile-menu"
            aria-label="Toggle navigation menu"
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100"
          >
            <svg
              className="h-6 w-6"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.5}
              stroke="currentColor"
              aria-hidden="true"
            >
              {open ? (
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              ) : (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M3.75 6.75h16.5M3.75 12h16.5M3.75 17.25h16.5"
                />
              )}
            </svg>
          </button>
        </div>
      </div>

      {open && (
        <div id="mobile-menu" className="border-t border-slate-200 bg-white md:hidden">
          <nav className="space-y-1 px-4 py-3" aria-label="Mobile">
            {publicLinks.map((link) => (
              <a
                key={link.label}
                href={link.to}
                onClick={() => setOpen(false)}
                className="block rounded-lg px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                {link.label}
              </a>
            ))}
            {user && (
              <NavLink
                to={dashboardPathFor(user.role)}
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  cx(
                    "block rounded-lg px-3 py-2.5 text-sm font-medium",
                    isActive ? "bg-brand-50 text-brand-700" : "text-slate-700 hover:bg-slate-100",
                  )
                }
              >
                Dashboard
              </NavLink>
            )}
            <div className="flex gap-2 pt-2">
              {user ? (
                <Button
                  variant="outline"
                  size="sm"
                  className="flex-1"
                  onClick={() => {
                    setOpen(false);
                    void logout();
                  }}
                >
                  Sign out
                </Button>
              ) : (
                <>
                  <Button variant="outline" size="sm" className="flex-1">
                    <Link to="/login">Sign in</Link>
                  </Button>
                  <Button variant="primary" size="sm" className="flex-1">
                    <Link to="/register">Get started</Link>
                  </Button>
                </>
              )}
            </div>
          </nav>
        </div>
      )}
    </header>
  );
}
