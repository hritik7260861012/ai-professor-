import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from "react";
import {
  ApiError,
  academicApi,
  adminAcademicApi,
  type AcademicClass,
  type AcademicYear,
  type ClassSubject,
  type Department,
  type Enrollment,
  type Program,
  type Semester,
  type Subject,
  type TeachingAssignment,
} from "../api/client";
import { Alert } from "../components/ui/Alert";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/Card";
import { Container } from "../components/ui/Layout";
import { Input, Select } from "../components/ui/Input";

type Section = "departments" | "programs" | "years" | "semesters" | "subjects" | "classes";

const SECTIONS: Array<{ key: Section; label: string }> = [
  { key: "departments", label: "Departments" },
  { key: "programs", label: "Programs" },
  { key: "years", label: "Academic years" },
  { key: "semesters", label: "Semesters" },
  { key: "subjects", label: "Subjects" },
  { key: "classes", label: "Classes" },
];

/**
 * ADMIN workspace for the academic structure.
 *
 * <p>Every mutation goes to /api/v1/admin/academic/**, which the server
 * restricts to the ADMIN role; this page only reflects that server truth.
 */
export default function AdminAcademic() {
  const [section, setSection] = useState<Section>("departments");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const report = useCallback((message: string) => {
    setStatus(message);
    setError(null);
  }, []);

  const fail = useCallback((cause: unknown, fallback: string) => {
    setStatus(null);
    setError(cause instanceof ApiError ? cause.message : fallback);
  }, []);

  return (
    <Container className="py-10">
      <header className="mb-8">
        <Badge variant="success">ADMIN</Badge>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
          Academic structure
        </h1>
        <p className="mt-2 text-slate-600">
          Departments, programs, academic years, semesters, subjects and classes.
        </p>
      </header>

      <nav className="mb-6 flex flex-wrap gap-2" aria-label="Academic sections">
        {SECTIONS.map((item) => (
          <Button
            key={item.key}
            variant={section === item.key ? "primary" : "outline"}
            size="sm"
            onClick={() => {
              setSection(item.key);
              setStatus(null);
              setError(null);
            }}
          >
            {item.label}
          </Button>
        ))}
      </nav>

      {error && (
        <Alert variant="error" className="mb-5">
          {error}
        </Alert>
      )}
      {status && (
        <Alert variant="success" className="mb-5">
          {status}
        </Alert>
      )}

      {section === "departments" && <DepartmentsSection report={report} fail={fail} />}
      {section === "programs" && <ProgramsSection report={report} fail={fail} />}
      {section === "years" && <AcademicYearsSection report={report} fail={fail} />}
      {section === "semesters" && <SemestersSection report={report} fail={fail} />}
      {section === "subjects" && <SubjectsSection report={report} fail={fail} />}
      {section === "classes" && <ClassesSection report={report} fail={fail} />}
    </Container>
  );
}

interface SectionProps {
  report: (message: string) => void;
  fail: (cause: unknown, fallback: string) => void;
}

/* -------------------------------------------------------------- departments */

function DepartmentsSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<Department[]>([]);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const load = useCallback(async () => {
    try {
      setRows((await academicApi.departments(0, 50)).content);
    } catch (cause) {
      fail(cause, "Could not load departments");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      const created = await adminAcademicApi.createDepartment({ code, name, description });
      report(`Department ${created.code} created`);
      setCode("");
      setName("");
      setDescription("");
      await load();
    } catch (cause) {
      fail(cause, "Could not create department");
    }
  }

  async function onDelete(id: number) {
    try {
      await adminAcademicApi.deleteDepartment(id);
      report("Department deleted");
      await load();
    } catch (cause) {
      fail(cause, "Could not delete department");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Departments</CardTitle>
        <CardDescription>The top level of the academic hierarchy.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-3">
          <Input
            label="Code"
            value={code}
            placeholder="CS"
            onChange={(e) => setCode(e.target.value)}
          />
          <Input
            label="Name"
            value={name}
            placeholder="Computer Science"
            onChange={(e) => setName(e.target.value)}
          />
          <div className="flex items-end">
            <Button type="submit">Add department</Button>
          </div>
        </form>

        <ul className="mt-5 space-y-2">
          {rows.map((row) => (
            <li
              key={row.id}
              className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2"
            >
              <span className="text-sm">
                <span className="font-mono font-semibold">{row.code}</span> — {row.name}
              </span>
              <Button variant="outline" size="sm" onClick={() => void onDelete(row.id)}>
                Delete
              </Button>
            </li>
          ))}
          {rows.length === 0 && <p className="text-sm text-slate-500">No departments yet.</p>}
        </ul>
      </CardContent>
    </Card>
  );
}

/* ----------------------------------------------------------------- programs */

function ProgramsSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<Program[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentId, setDepartmentId] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [level, setLevel] = useState("UNDERGRADUATE");
  const [durationYears, setDurationYears] = useState("3");

  const load = useCallback(async () => {
    try {
      const [allPrograms, allDepartments] = await Promise.all([
        academicApi.programs(undefined, 0, 50),
        academicApi.departments(0, 50),
      ]);
      setRows(allPrograms.content);
      setDepartments(allDepartments.content);
    } catch (cause) {
      fail(cause, "Could not load programs");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!departmentId) {
      fail(new Error("Choose a department"), "Choose a department");
      return;
    }
    try {
      await adminAcademicApi.createProgram({
        departmentId: Number(departmentId),
        code,
        name,
        level: level as Program["level"],
        durationYears: Number(durationYears),
      });
      report(`Program ${code} created`);
      setCode("");
      setName("");
      await load();
    } catch (cause) {
      fail(cause, "Could not create program");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Programs</CardTitle>
        <CardDescription>Degree programs offered by a department.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-5">
          <Select
            label="Department"
            value={departmentId}
            options={[
              { value: "", label: "Select…" },
              ...departments.map((d) => ({ value: String(d.id), label: `${d.code} — ${d.name}` })),
            ]}
            onChange={(e) => setDepartmentId(e.target.value)}
          />
          <Input label="Code" value={code} onChange={(e) => setCode(e.target.value)} />
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Select
            label="Level"
            value={level}
            options={[
              { value: "UNDERGRADUATE", label: "Undergraduate" },
              { value: "POSTGRADUATE", label: "Postgraduate" },
            ]}
            onChange={(e) => setLevel(e.target.value)}
          />
          <Input
            label="Years"
            type="number"
            value={durationYears}
            onChange={(e) => setDurationYears(e.target.value)}
          />
          <div className="sm:col-span-5">
            <Button type="submit">Add program</Button>
          </div>
        </form>

        <ul className="mt-5 space-y-2">
          {rows.map((row) => (
            <li key={row.id} className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
              <span className="font-mono font-semibold">{row.code}</span> — {row.name} (
              {row.level}, {row.durationYears}y) · {row.departmentCode}
            </li>
          ))}
          {rows.length === 0 && <p className="text-sm text-slate-500">No programs yet.</p>}
        </ul>
      </CardContent>
    </Card>
  );
}

/* ----------------------------------------------------------- academic years */

function AcademicYearsSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<AcademicYear[]>([]);
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [current, setCurrent] = useState(false);

  const load = useCallback(async () => {
    try {
      setRows((await academicApi.academicYears(0, 50)).content);
    } catch (cause) {
      fail(cause, "Could not load academic years");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      await adminAcademicApi.createAcademicYear({ name, startDate, endDate, current });
      report(`Academic year ${name} created`);
      setName("");
      setStartDate("");
      setEndDate("");
      setCurrent(false);
      await load();
    } catch (cause) {
      fail(cause, "Could not create academic year");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Academic years</CardTitle>
        <CardDescription>Exactly one year may be flagged as current.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-5">
          <Input label="Name" value={name} placeholder="2026-27" onChange={(e) => setName(e.target.value)} />
          <Input label="Start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <Input label="End" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          <Select
            label="Current"
            value={String(current)}
            options={[
              { value: "false", label: "No" },
              { value: "true", label: "Yes" },
            ]}
            onChange={(e) => setCurrent(e.target.value === "true")}
          />
          <div className="flex items-end">
            <Button type="submit">Add year</Button>
          </div>
        </form>

        <ul className="mt-5 space-y-2">
          {rows.map((row) => (
            <li key={row.id} className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2 text-sm">
              <span>
                <span className="font-semibold">{row.name}</span> · {row.startDate} → {row.endDate}
              </span>
              {row.current && <Badge variant="success">Current</Badge>}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

/* ---------------------------------------------------------------- semesters */

function SemestersSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<Semester[]>([]);
  const [years, setYears] = useState<AcademicYear[]>([]);
  const [academicYearId, setAcademicYearId] = useState("");
  const [name, setName] = useState("");
  const [number, setNumber] = useState("1");

  const load = useCallback(async () => {
    try {
      const [allYears, allSemesters] = await Promise.all([
        academicApi.academicYears(0, 50),
        academicApi.semesters(),
      ]);
      setYears(allYears.content);
      setRows(allSemesters);
    } catch (cause) {
      fail(cause, "Could not load semesters");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!academicYearId) {
      fail(new Error("Choose an academic year"), "Choose an academic year");
      return;
    }
    try {
      await adminAcademicApi.createSemester({
        academicYearId: Number(academicYearId),
        name,
        semesterNumber: Number(number),
      });
      report(`Semester ${name} created`);
      setName("");
      await load();
    } catch (cause) {
      fail(cause, "Could not create semester");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Semesters</CardTitle>
        <CardDescription>Semesters belong to an academic year.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-4">
          <Select
            label="Academic year"
            value={academicYearId}
            options={[
              { value: "", label: "Select…" },
              ...years.map((y) => ({ value: String(y.id), label: y.name })),
            ]}
            onChange={(e) => setAcademicYearId(e.target.value)}
          />
          <Input label="Name" value={name} placeholder="Semester 1" onChange={(e) => setName(e.target.value)} />
          <Input label="Number" type="number" value={number} onChange={(e) => setNumber(e.target.value)} />
          <div className="flex items-end">
            <Button type="submit">Add semester</Button>
          </div>
        </form>

        <ul className="mt-5 space-y-2">
          {rows.map((row) => (
            <li key={row.id} className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
              <span className="font-semibold">{row.name}</span> · {row.academicYearName}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

/* ----------------------------------------------------------------- subjects */

function SubjectsSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<Subject[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentId, setDepartmentId] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [credits, setCredits] = useState("4");

  const load = useCallback(async () => {
    try {
      const [allSubjects, allDepartments] = await Promise.all([
        academicApi.subjects(undefined, 0, 50),
        academicApi.departments(0, 50),
      ]);
      setRows(allSubjects.content);
      setDepartments(allDepartments.content);
    } catch (cause) {
      fail(cause, "Could not load subjects");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!departmentId) {
      fail(new Error("Choose a department"), "Choose a department");
      return;
    }
    try {
      await adminAcademicApi.createSubject({
        departmentId: Number(departmentId),
        code,
        name,
        credits: Number(credits),
      });
      report(`Subject ${code} created`);
      setCode("");
      setName("");
      await load();
    } catch (cause) {
      fail(cause, "Could not create subject");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Subjects</CardTitle>
        <CardDescription>Owned by a department; offered in classes via the Classes tab.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-5">
          <Select
            label="Department"
            value={departmentId}
            options={[
              { value: "", label: "Select…" },
              ...departments.map((d) => ({ value: String(d.id), label: d.code })),
            ]}
            onChange={(e) => setDepartmentId(e.target.value)}
          />
          <Input label="Code" value={code} onChange={(e) => setCode(e.target.value)} />
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Credits" type="number" value={credits} onChange={(e) => setCredits(e.target.value)} />
          <div className="flex items-end">
            <Button type="submit">Add subject</Button>
          </div>
        </form>

        <ul className="mt-5 space-y-2">
          {rows.map((row) => (
            <li key={row.id} className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
              <span className="font-mono font-semibold">{row.code}</span> — {row.name} ·{" "}
              {row.credits} credits · {row.departmentCode}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ classes */

function ClassesSection({ report, fail }: SectionProps) {
  const [rows, setRows] = useState<AcademicClass[]>([]);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [programId, setProgramId] = useState("");
  const [semesterId, setSemesterId] = useState("");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [capacity, setCapacity] = useState("60");

  const [detail, setDetail] = useState<AcademicClass | null>(null);
  const [detailSubjects, setDetailSubjects] = useState<ClassSubject[]>([]);
  const [roster, setRoster] = useState<Enrollment[]>([]);
  const [teachers, setTeachers] = useState<TeachingAssignment[]>([]);
  const [offerSubjectId, setOfferSubjectId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [teacherId, setTeacherId] = useState("");
  const [assignSubjectId, setAssignSubjectId] = useState("");

  const load = useCallback(async () => {
    try {
      const [allClasses, allPrograms, allSemesters, allSubjects] = await Promise.all([
        academicApi.classes(0, 50),
        academicApi.programs(undefined, 0, 50),
        academicApi.semesters(),
        academicApi.subjects(undefined, 0, 50),
      ]);
      setRows(allClasses.content);
      setPrograms(allPrograms.content);
      setSemesters(allSemesters);
      setSubjects(allSubjects.content);
    } catch (cause) {
      fail(cause, "Could not load classes");
    }
  }, [fail]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = useCallback(
    async (academicClass: AcademicClass) => {
      setDetail(academicClass);
      try {
        const [offered, enrolled, assigned] = await Promise.all([
          academicApi.classSubjects(academicClass.id),
          academicApi.classRoster(academicClass.id),
          academicApi.classAssignments(academicClass.id),
        ]);
        setDetailSubjects(offered);
        setRoster(enrolled);
        setTeachers(assigned);
      } catch (cause) {
        fail(cause, "Could not load class detail");
      }
    },
    [fail],
  );

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!programId || !semesterId) {
      fail(new Error("Choose a program and semester"), "Choose a program and semester");
      return;
    }
    try {
      await adminAcademicApi.createClass({
        programId: Number(programId),
        semesterId: Number(semesterId),
        code,
        name,
        capacity: Number(capacity),
      });
      report(`Class ${code} created`);
      setCode("");
      setName("");
      await load();
    } catch (cause) {
      fail(cause, "Could not create class");
    }
  }

  async function onOfferSubject(event: FormEvent) {
    event.preventDefault();
    if (!detail || !offerSubjectId) return;
    try {
      await adminAcademicApi.attachSubject(detail.id, Number(offerSubjectId));
      report("Subject offered in class");
      setOfferSubjectId("");
      await openDetail(detail);
      await load();
    } catch (cause) {
      fail(cause, "Could not offer subject");
    }
  }

  async function onEnroll(event: FormEvent) {
    event.preventDefault();
    if (!detail || !studentId) return;
    try {
      await adminAcademicApi.enroll(Number(studentId), detail.id);
      report("Student enrolled");
      setStudentId("");
      await openDetail(detail);
      await load();
    } catch (cause) {
      fail(cause, "Could not enroll student");
    }
  }

  async function onAssignTeacher(event: FormEvent) {
    event.preventDefault();
    if (!detail || !teacherId || !assignSubjectId) return;
    try {
      await adminAcademicApi.assignTeacher(
        Number(teacherId),
        detail.id,
        Number(assignSubjectId),
      );
      report("Teacher assigned");
      setTeacherId("");
      setAssignSubjectId("");
      await openDetail(detail);
    } catch (cause) {
      fail(cause, "Could not assign teacher");
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Classes</CardTitle>
          <CardDescription>A class is a section of a program in a semester.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="grid gap-3 sm:grid-cols-5">
            <Select
              label="Program"
              value={programId}
              options={[
                { value: "", label: "Select…" },
                ...programs.map((p) => ({ value: String(p.id), label: p.code })),
              ]}
              onChange={(e) => setProgramId(e.target.value)}
            />
            <Select
              label="Semester"
              value={semesterId}
              options={[
                { value: "", label: "Select…" },
                ...semesters.map((s) => ({ value: String(s.id), label: `${s.academicYearName} ${s.name}` })),
              ]}
              onChange={(e) => setSemesterId(e.target.value)}
            />
            <Input label="Code" value={code} onChange={(e) => setCode(e.target.value)} />
            <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} />
            <Input label="Capacity" type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} />
            <div className="sm:col-span-5">
              <Button type="submit">Add class</Button>
            </div>
          </form>

          <ul className="mt-5 space-y-2">
            {rows.map((row) => (
              <li key={row.id} className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <span className="text-sm">
                  <span className="font-mono font-semibold">{row.code}</span> — {row.name} ·{" "}
                  {row.academicYearName} {row.semesterName}
                </span>
                <Button variant="outline" size="sm" onClick={() => void openDetail(row)}>
                  Manage
                </Button>
              </li>
            ))}
            {rows.length === 0 && <p className="text-sm text-slate-500">No classes yet.</p>}
          </ul>
        </CardContent>
      </Card>

      {detail && (
        <Card>
          <CardHeader>
            <CardTitle>
              {detail.code} — management
            </CardTitle>
            <CardDescription>Offered subjects, roster and teaching assignments.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <Panel title="Offer a subject">
              <InlineForm onSubmit={onOfferSubject}>
                <Select
                  label="Subject"
                  value={offerSubjectId}
                  options={[
                    { value: "", label: "Select…" },
                    ...subjects.map((s) => ({ value: String(s.id), label: `${s.code} — ${s.name}` })),
                  ]}
                  onChange={(e) => setOfferSubjectId(e.target.value)}
                />
                <Button type="submit">Offer</Button>
              </InlineForm>
              <List
                items={detailSubjects.map((s) => `${s.subjectCode} — ${s.subjectName}`)}
                empty="No subjects offered yet."
              />
            </Panel>

            <Panel title="Enrolled students">
              <InlineForm onSubmit={onEnroll}>
                <Input
                  label="Student id"
                  type="number"
                  value={studentId}
                  placeholder="User id"
                  onChange={(e) => setStudentId(e.target.value)}
                />
                <Button type="submit">Enroll</Button>
              </InlineForm>
              <List
                items={roster.map((r) => `${r.studentName} (${r.studentEmail})`)}
                empty="Nobody enrolled yet."
              />
            </Panel>

            <Panel title="Teachers">
              <InlineForm onSubmit={onAssignTeacher}>
                <Input
                  label="Teacher id"
                  type="number"
                  value={teacherId}
                  placeholder="User id"
                  onChange={(e) => setTeacherId(e.target.value)}
                />
                <Select
                  label="Subject"
                  value={assignSubjectId}
                  options={[
                    { value: "", label: "Select…" },
                    ...detailSubjects.map((s) => ({ value: String(s.subjectId), label: s.subjectCode })),
                  ]}
                  onChange={(e) => setAssignSubjectId(e.target.value)}
                />
                <Button type="submit">Assign</Button>
              </InlineForm>
              <List
                items={teachers.map((t) => `${t.teacherName} → ${t.subjectCode}`)}
                empty="No teachers assigned."
              />
            </Panel>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="mb-2 text-sm font-semibold text-slate-900">{title}</h3>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function InlineForm({ children, onSubmit }: { children: ReactNode; onSubmit: (e: FormEvent) => void }) {
  return (
    <form onSubmit={onSubmit} className="grid items-end gap-3 sm:grid-cols-3">
      {children}
    </form>
  );
}

function List({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) {
    return <p className="text-sm text-slate-500">{empty}</p>;
  }
  return (
    <ul className="space-y-1">
      {items.map((item) => (
        <li key={item} className="rounded bg-slate-50 px-3 py-1.5 text-sm">
          {item}
        </li>
      ))}
    </ul>
  );
}