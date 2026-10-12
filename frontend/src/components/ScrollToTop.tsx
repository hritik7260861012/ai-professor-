import { useEffect } from "react";
import { useLocation } from "react-router-dom";

/** Scrolls to the top whenever the route (path or hash) changes. */
export function ScrollToTop() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (!hash) {
      window.scrollTo({ top: 0, behavior: "instant" as ScrollBehavior });
    }
  }, [pathname, hash]);

  return null;
}
