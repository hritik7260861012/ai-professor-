import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import Landing from "../pages/Landing";

function renderLanding() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Landing />
    </MemoryRouter>,
  );
}

describe("Landing page", () => {
  it("renders the hero and role cards", () => {
    renderLanding();

    expect(
      screen.getByRole("heading", {
        name: /the ai-powered assistant for modern college teaching/i,
      }),
    ).toBeInTheDocument();

    expect(screen.getByRole("link", { name: /teacher dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /student dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /admin dashboard/i })).toBeInTheDocument();
  });

  it("shows a loading state while the health check is in flight", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => new Promise(() => {})),
    );

    renderLanding();

    expect(screen.getByText(/checking service status/i)).toBeInTheDocument();
  });

  it("reports the backend as offline when the API is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    renderLanding();

    expect(await screen.findByText(/backend offline/i)).toBeInTheDocument();
  });
});
