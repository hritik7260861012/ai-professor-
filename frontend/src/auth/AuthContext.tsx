import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiError, authApi, resetCsrfToken, type LoginPayload, type RegisterPayload, type Role, type UserDto } from "../api/client";

interface AuthContextValue {
  user: UserDto | null;
  /** True until the initial session probe against /auth/me completes. */
  loading: boolean;
  login: (payload: LoginPayload) => Promise<UserDto>;
  register: (payload: RegisterPayload) => Promise<UserDto>;
  logout: () => Promise<void>;
  /** Re-reads the current user from the server. */
  refresh: () => Promise<UserDto | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async (): Promise<UserDto | null> => {
    try {
      const current = await authApi.me();
      setUser(current);
      return current;
    } catch (error) {
      // 401 simply means "not signed in"; anything else is still a signed-out
      // state from the UI's point of view, so never surface a hard error here.
      if (!(error instanceof ApiError) || error.status !== 401) {
        // Swallow: the guard renders the signed-out experience regardless.
      }
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(async (payload: LoginPayload) => {
    const signedIn = await authApi.login(payload);
    setUser(signedIn);
    setLoading(false);
    return signedIn;
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    const created = await authApi.register(payload);
    // Registration deliberately does not start a session: the user must sign in.
    return created;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Signing out locally is what matters: a failed network call must not
      // leave the user stuck in a half-signed-in UI state, and callers
      // fire-and-forget this promise.
    } finally {
      resetCsrfToken();
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, login, register, logout, refresh }),
    [user, loading, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside an <AuthProvider>");
  }
  return context;
}

export function dashboardPathFor(role: Role): string {
  return `/dashboard/${role.toLowerCase()}`;
}