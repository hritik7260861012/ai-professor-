import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { ProtectedRoute } from "./ProtectedRoute";

const fetchStub = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<h1>Login page</h1>} />
          <Route
            path="/dashboard/student"
            element={
              <ProtectedRoute roles={["STUDENT"]}>
                <h1>Student area</h1>
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard/admin"
            element={
              <ProtectedRoute roles={["ADMIN"]}>
                <h1>Admin area</h1>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("redirects anonymous visitors to the login page", async () => {
    fetchStub.mockReset();
    fetchStub.mockResolvedValue(jsonResponse({ message: "Authentication required" }, 401));
    vi.stubGlobal("fetch", fetchStub);

    renderAt("/dashboard/admin");

    expect(await screen.findByText("Login page")).toBeInTheDocument();
  });

  it("shows a session check placeholder before the probe resolves", () => {
    fetchStub.mockReset();
    fetchStub.mockImplementation(() => new Promise(() => {}));
    vi.stubGlobal("fetch", fetchStub);

    renderAt("/dashboard/student");

    expect(screen.getByText(/checking your session/i)).toBeInTheDocument();
  });

  it("renders the page for a user with the matching role", async () => {
    fetchStub.mockReset();
    fetchStub.mockResolvedValue(
      jsonResponse({ id: 1, email: "ada@example.com", role: "ADMIN" }),
    );
    vi.stubGlobal("fetch", fetchStub);

    renderAt("/dashboard/admin");

    expect(await screen.findByText("Admin area")).toBeInTheDocument();
  });

  it("sends a signed-in user with the wrong role to their own dashboard", async () => {
    fetchStub.mockReset();
    fetchStub.mockResolvedValue(
      jsonResponse({ id: 2, email: "bob@example.com", role: "STUDENT" }),
    );
    vi.stubGlobal("fetch", fetchStub);

    // A student asking for /dashboard/admin is bounced to /dashboard/student.
    renderAt("/dashboard/admin");

    expect(await screen.findByText("Student area")).toBeInTheDocument();
  });
});