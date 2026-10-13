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
  // A FormData body must not carry an explicit Content-Type: the browser has
  // to set the multipart boundary itself.
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isFormData) {
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
      body:
        body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
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

/** Syllabus lifecycle, mirroring SyllabusStatus on the backend. */
export type SyllabusStatus =
  | "UPLOADED"
  | "EXTRACTING"
  | "EXTRACTION_FAILED"
  | "REVIEW_REQUIRED"
  | "APPROVED"
  | "REJECTED";

export interface SyllabusMeta {
  id: number;
  classId: number;
  classCode: string;
  className: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  semesterId: number;
  semesterName: string;
  academicYearName: string;
  uploadedById: number;
  uploadedByName: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  status: SyllabusStatus;
  version: number;
  current: boolean;
  extractionError: string | null;
  reviewNote: string | null;
  reviewedById: number | null;
  reviewedAt: string | null;
  uploadedAt: string;
  updatedAt: string;
}

export interface SyllabusUnitView {
  id?: number | null;
  title: string;
  position: number;
  topics: SyllabusTopicView[];
}

export interface SyllabusTopicView {
  id?: number | null;
  title: string;
  position: number;
  subtopics: string[];
}

export interface SyllabusOutcomeView {
  id?: number | null;
  statement: string;
  position: number;
  unitId: number | null;
}

export interface SyllabusDetail {
  syllabus: SyllabusMeta;
  units: SyllabusUnitView[];
  learningOutcomes: SyllabusOutcomeView[];
  /** Raw extracted text, kept so the teacher can correct bad parsing. */
  extractedText: string | null;
  /** Extraction/parsing guidance; null when everything went cleanly. */
  warning: string | null;
}

export interface SyllabusStructurePayload {
  units: Array<{ title: string; topics: Array<{ title: string; subtopics: string[] }> }>;
  learningOutcomes: Array<{ statement: string; unitIndex: number | null }>;
}

export const syllabusApi = {
  /** Multipart upload; CSRF and credentials are handled by the client. */
  upload: (classId: number, subjectId: number, file: File) => {
    const form = new FormData();
    form.append("classId", String(classId));
    form.append("subjectId", String(subjectId));
    form.append("file", file);
    return api.post<SyllabusDetail>("/teacher/syllabi", form);
  },

  listMine: (page = 0, size = 20) =>
    api.get<PageResponse<SyllabusMeta>>(`/teacher/syllabi${query({ page, size })}`),

  byId: (id: number) => api.get<SyllabusDetail>(`/teacher/syllabi/${id}`),

  downloadUrl: (id: number) => `${BASE_URL}/teacher/syllabi/${id}/file`,

  extractedContent: (id: number) =>
    api.get<SyllabusDetail>(`/teacher/syllabi/${id}/extracted-content`),

  updateStructure: (id: number, structure: SyllabusStructurePayload) =>
    api.put<SyllabusDetail>(`/teacher/syllabi/${id}/structure`, structure),

  approve: (id: number) => api.post<SyllabusDetail>(`/teacher/syllabi/${id}/approve`),

  reject: (id: number, reason: string) =>
    api.post<SyllabusDetail>(`/teacher/syllabi/${id}/reject`, { reason }),

  retryExtraction: (id: number) =>
    api.post<SyllabusDetail>(`/teacher/syllabi/${id}/retry-extraction`),

  remove: (id: number) => api.del<void>(`/teacher/syllabi/${id}`),

  /** Approved syllabi for the signed-in student's enrollments. */
  studentList: () => api.get<SyllabusDetail[]>("/student/syllabi"),
  studentById: (id: number) => api.get<SyllabusDetail>(`/student/syllabi/${id}`),
};

/* ---------------------------- Academic structure --------------------------- */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  createdAt: string;
}

export type ProgramLevel = "UNDERGRADUATE" | "POSTGRADUATE";

export interface Program {
  id: number;
  departmentId: number;
  departmentCode: string;
  code: string;
  name: string;
  level: ProgramLevel;
  durationYears: number;
  description?: string | null;
}

export interface AcademicYear {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  current: boolean;
}

export interface Semester {
  id: number;
  academicYearId: number;
  academicYearName: string;
  name: string;
  semesterNumber: number;
  startDate?: string | null;
  endDate?: string | null;
}

export interface Subject {
  id: number;
  departmentId: number;
  departmentCode: string;
  programId?: number | null;
  code: string;
  name: string;
  credits: number;
  description?: string | null;
}

export interface AcademicClass {
  id: number;
  programId: number;
  programCode: string;
  programName: string;
  semesterId: number;
  semesterName: string;
  academicYearName: string;
  code: string;
  name: string;
  capacity: number;
  enrolledCount: number;
}

export interface ClassSubject {
  id: number;
  classId: number;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number;
  semesterId: number;
}

export interface Enrollment {
  id: number;
  studentId: number;
  studentName: string;
  studentEmail: string;
  classId: number;
  classCode: string;
  className: string;
  status: "ENROLLED" | "DROPPED";
  enrolledAt: string;
}

export interface TeachingAssignment {
  id: number;
  teacherId: number;
  teacherName: string;
  classId: number;
  classCode: string;
  subjectId: number;
  subjectCode: string;
  semesterId: number;
  semesterName: string;
  assignedAt: string;
}

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const academicApi = {
  departments: (page = 0, size = 20) =>
    api.get<PageResponse<Department>>(`/departments${query({ page, size })}`),
  programs: (departmentId?: number, page = 0, size = 50) =>
    api.get<PageResponse<Program>>(`/programs${query({ departmentId, page, size })}`),
  academicYears: (page = 0, size = 20) =>
    api.get<PageResponse<AcademicYear>>(`/academic-years${query({ page, size })}`),
  semesters: (academicYearId?: number) =>
    api.get<Semester[]>(`/semesters${query({ academicYearId })}`),

  /** Role-scoped: ADMIN sees all, TEACHER assigned, STUDENT enrolled. */
  classes: (page = 0, size = 20) =>
    api.get<PageResponse<AcademicClass>>(`/classes${query({ page, size })}`),
  classById: (id: number) => api.get<AcademicClass>(`/classes/${id}`),
  classSubjects: (id: number) => api.get<ClassSubject[]>(`/classes/${id}/subjects`),
  classRoster: (id: number) => api.get<Enrollment[]>(`/classes/${id}/students`),
  classAssignments: (id: number) => api.get<TeachingAssignment[]>(`/classes/${id}/teachers`),

  /** Role-scoped subjects. */
  subjects: (departmentId?: number, page = 0, size = 20) =>
    api.get<PageResponse<Subject>>(`/subjects${query({ departmentId, page, size })}`),
  subjectById: (id: number) => api.get<Subject>(`/subjects/${id}`),

  myClasses: () => api.get<AcademicClass[]>("/me/classes"),
  myEnrollments: () => api.get<Enrollment[]>("/me/enrollments"),
  myAssignments: () => api.get<TeachingAssignment[]>("/me/assignments"),
};

export interface DepartmentPayload {
  code: string;
  name: string;
  description?: string | null;
}

export interface ProgramPayload {
  departmentId: number;
  code: string;
  name: string;
  level: ProgramLevel;
  durationYears: number;
  description?: string | null;
}

export interface AcademicYearPayload {
  name: string;
  startDate: string;
  endDate: string;
  current?: boolean;
}

export interface SemesterPayload {
  academicYearId: number;
  name: string;
  semesterNumber: number;
  startDate?: string | null;
  endDate?: string | null;
}

export interface SubjectPayload {
  departmentId: number;
  programId?: number | null;
  code: string;
  name: string;
  credits: number;
  description?: string | null;
}

export interface ClassPayload {
  programId: number;
  semesterId: number;
  code: string;
  name: string;
  capacity: number;
}

/** ADMIN-only academic structure management. */
export const adminAcademicApi = {
  createDepartment: (p: DepartmentPayload) =>
    api.post<Department>("/admin/academic/departments", p),
  updateDepartment: (id: number, p: DepartmentPayload) =>
    api.put<Department>(`/admin/academic/departments/${id}`, p),
  deleteDepartment: (id: number) => api.del<void>(`/admin/academic/departments/${id}`),

  createProgram: (p: ProgramPayload) =>
    api.post<Program>("/admin/academic/programs", p),
  updateProgram: (id: number, p: ProgramPayload) =>
    api.put<Program>(`/admin/academic/programs/${id}`, p),
  deleteProgram: (id: number) => api.del<void>(`/admin/academic/programs/${id}`),

  createAcademicYear: (p: AcademicYearPayload) =>
    api.post<AcademicYear>("/admin/academic/academic-years", p),
  updateAcademicYear: (id: number, p: AcademicYearPayload) =>
    api.put<AcademicYear>(`/admin/academic/academic-years/${id}`, p),
  deleteAcademicYear: (id: number) =>
    api.del<void>(`/admin/academic/academic-years/${id}`),

  createSemester: (p: SemesterPayload) =>
    api.post<Semester>("/admin/academic/semesters", p),
  updateSemester: (id: number, p: SemesterPayload) =>
    api.put<Semester>(`/admin/academic/semesters/${id}`, p),
  deleteSemester: (id: number) => api.del<void>(`/admin/academic/semesters/${id}`),

  createSubject: (p: SubjectPayload) =>
    api.post<Subject>("/admin/academic/subjects", p),
  updateSubject: (id: number, p: SubjectPayload) =>
    api.put<Subject>(`/admin/academic/subjects/${id}`, p),
  deleteSubject: (id: number) => api.del<void>(`/admin/academic/subjects/${id}`),

  createClass: (p: ClassPayload) => api.post<AcademicClass>("/admin/academic/classes", p),
  updateClass: (id: number, p: ClassPayload) =>
    api.put<AcademicClass>(`/admin/academic/classes/${id}`, p),
  deleteClass: (id: number) => api.del<void>(`/admin/academic/classes/${id}`),

  attachSubject: (classId: number, subjectId: number) =>
    api.post<ClassSubject>(`/admin/academic/classes/${classId}/subjects`, { subjectId }),
  detachSubject: (classId: number, linkId: number) =>
    api.del<void>(`/admin/academic/classes/${classId}/subjects/${linkId}`),

  enroll: (studentId: number, classId: number) =>
    api.post<Enrollment>("/admin/academic/enrollments", { studentId, classId }),
  unenroll: (id: number) => api.del<void>(`/admin/academic/enrollments/${id}`),

  assignTeacher: (teacherId: number, classId: number, subjectId: number) =>
    api.post<TeachingAssignment>("/admin/academic/teaching-assignments", {
      teacherId,
      classId,
      subjectId,
    }),
  removeAssignment: (id: number) =>
    api.del<void>(`/admin/academic/teaching-assignments/${id}`),
};