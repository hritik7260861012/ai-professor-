import { Route, Routes } from "react-router-dom";
import { ScrollToTop } from "./components/ScrollToTop";
import { Layout } from "./components/layout/Layout";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import AdminAcademic from "./pages/AdminAcademic";
import DashboardPlaceholder from "./pages/DashboardPlaceholder";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import NotFound from "./pages/NotFound";
import Register from "./pages/Register";
import StudentSyllabi from "./pages/StudentSyllabi";
import TeacherSyllabi from "./pages/TeacherSyllabi";

export default function App() {
  return (
    <AuthProvider>
      <ScrollToTop />
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/dashboard/student"
            element={
              <ProtectedRoute roles={["STUDENT"]}>
                <DashboardPlaceholder />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard/teacher"
            element={
              <ProtectedRoute roles={["TEACHER"]}>
                <DashboardPlaceholder />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard/admin"
            element={
              <ProtectedRoute roles={["ADMIN"]}>
                <DashboardPlaceholder />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
        <Route
          element={
            <ProtectedRoute roles={["TEACHER"]}>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route path="/teacher/syllabi" element={<TeacherSyllabi />} />
        </Route>
        <Route
          element={
            <ProtectedRoute roles={["STUDENT"]}>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route path="/student/syllabi" element={<StudentSyllabi />} />
        </Route>
        <Route
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route path="/admin/academic" element={<AdminAcademic />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}