/**
 * Typed REST client for the AI Professor Assistant backend.
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

const BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1"
).replace(/\/+$/, "");

async function request<T>(
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
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

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  role: "STUDENT" | "TEACHER" | "ADMIN";
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  /** Phase 1: server validates input, then responds 501 Not Implemented. */
  register: (payload: RegisterPayload) => api.post<void>("/auth/register", payload),
  /** Phase 1: server validates input, then responds 501 Not Implemented. */
  login: (payload: LoginPayload) => api.post<void>("/auth/login", payload),
};
