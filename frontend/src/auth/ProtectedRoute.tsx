import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import type { Role } from "../api/client";
import { dashboardPathFor, useAuth } from "./AuthContext";

interface ProtectedRouteProps {
  children: ReactNode;
  /**
   * Restricts the route to specific roles. A signed-in user with the wrong
   * role is redirected to their own dashboard instead of being shown an
   * empty page. The server enforces this too — this only shapes the UX.
   */
  roles?: Role[];
}

/**
 * Route guard for authenticated areas.
 *
 * Note this is presentation only: authorization is enforced server-side by
 * Spring Security, so bypassing this guard gains nothing.
 */
export function ProtectedRoute({ children, roles }: ProtectedRouteProps) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-live="polite">
        <span className="text-sm text-slate-600">Checking your session…</span>
      </div>
    );
  }

  if (!user) {
    // Remember where they were headed so sign-in can return them there.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to={dashboardPathFor(user.role)} replace />;
  }

  return <>{children}</>;
}