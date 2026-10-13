/**
 * Typed REST client for the AI Professor Assistant backend.
 *
 * Session-based auth: the browser holds an HttpOnly JSESSIONID cookie, so
 * every request must opt into sending cookies (`credentials: "include"`).
 * No token is ever read by or written to JavaScript.
 *
 * Every non-2xx response is converted into an `ApiError` that mirrors
 * the backend's `ApiError` envelope:
 *   { timestamp, status, error, message, path, details }
 */

export class ApiError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

interface ApiErrorBody {
  message?: string;
  details?: unknown;
}

export const BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1"
).replace(/\/+$/, "");

/** Must match SecurityConfig.CSRF_HEADER_NAME on the backend. */
export const CSRF_HEADER = "X-CSRF-TOKEN";

let csrfToken: string | null = null;
let csrfRequest: Promise<string | null> | null = null;

/**
 * Fetches the CSRF token for this session (cached).
 *
 * The backend stores the token in the HttpSession and expects it back in
 * the CSRF_HEADER on every state-changing request. Failures are swallowed
 * on purpose: the server is the authority, and a missing token should
 * surface as the API's own 403 rather than a client-side crash.
 */
export async function ensureCsrfToken(): Promise<string | null> {
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    csrfRequest = fetch(`${BASE_URL}/auth/csrf`, {
      method: "GET",
      credentials: "include",
    })
      .then(async (response) => {
        if (!response.ok) return null;
        const body = (await response.json()) as { token?: string } | null;
        csrfToken = body?.token ?? null;
        return csrfToken;
      })
      .catch(() => null)
      .finally(() => {
        csrfRequest = null;
      });
  }
  return csrfRequest;
}

/** Clears the cached token (used on sign-out). */
export function resetCsrfToken(): void {
  csrfToken = null;
}

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

async function request<T>(
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (UNSAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      // Required for the HttpOnly session cookie to be sent.
      credentials: "include",
      headers: Object.keys(headers).length ? headers : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // Network-level failure (backend offline, CORS block, DNS failure).
    throw new ApiError(0, "Unable to reach the server. Is the backend running?");
  }

  if (!response.ok) {
    let message = response.statusText || `Request failed (${response.status})`;
    let details: unknown;
    try {
      const parsed = (await response.json()) as ApiErrorBody | null;
      if (parsed && typeof parsed.message === "string" && parsed.message) {
        message = parsed.message;
      }
      details = parsed?.details;
    } catch {
      // Response body was not JSON — keep the default message.
    }
    throw new ApiError(response.status, message, details);
  }

  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get<T>(path: string): Promise<T> {
    return request<T>("GET", path);
  },
  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>("POST", path, body);
  },
  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>("PUT", path, body);
  },
  patch<T>(path: string, body?: unknown): Promise<T> {
    return request<T>("PATCH", path, body);
  },
  del<T>(path: string): Promise<T> {
    return request<T>("DELETE", path);
  },
};

/* ------------------------------- Domain APIs ------------------------------ */

export interface HealthStatus {
  status: "UP" | "DOWN" | string;
  version: string;
  database: string;
  timestamp: string;
}

export const healthApi = {
  get: () => api.get<HealthStatus>("/health"),
};

/** Roles recognised by the platform. Public signup only ever yields STUDENT. */
export type Role = "STUDENT" | "TEACHER" | "ADMIN";

export type AccountStatus = "ACTIVE" | "SUSPENDED";

/** Public view of an account. The server never returns the password hash. */
export interface UserDto {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  status: AccountStatus;
  createdAt: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  role?: Role;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AdminCreateUserPayload {
  fullName: string;
  email: string;
  password: string;
  role: Role;
}

export const authApi = {
  /** Creates a STUDENT account. The server rejects any other role. */
  register: (payload: RegisterPayload) => api.post<UserDto>("/auth/register", payload),
  login: (payload: LoginPayload) => api.post<UserDto>("/auth/login", payload),
  logout: () => api.post<void>("/auth/logout"),
  /** Current session user; rejects with ApiError(401) when signed out. */
  me: () => api.get<UserDto>("/auth/me"),
};

export const adminApi = {
  createUser: (payload: AdminCreateUserPayload) =>
    api.post<UserDto>("/admin/users", payload),
  changeRole: (id: number, role: Role) =>
    api.patch<UserDto>(`/admin/users/${id}/role`, { role }),
};