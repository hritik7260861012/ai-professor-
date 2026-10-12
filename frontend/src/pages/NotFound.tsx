import { Link } from "react-router-dom";
import { Button } from "../components/ui/Button";
import { Container } from "../components/ui/Layout";

export default function NotFound() {
  return (
    <Container className="flex flex-col items-center py-24 text-center">
      <p className="text-7xl font-extrabold tracking-tight text-brand-600">404</p>
      <h1 className="mt-4 text-2xl font-bold text-slate-900">Page not found</h1>
      <p className="mt-2 max-w-md text-slate-600">
        The page you are looking for does not exist or has moved.
      </p>
      <Button className="mt-8" variant="outline">
        <Link to="/">Return home</Link>
      </Button>
    </Container>
  );
}
