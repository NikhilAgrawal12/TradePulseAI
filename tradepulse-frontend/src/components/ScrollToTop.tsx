import { useEffect, useLayoutEffect, useRef } from "react";
import { useLocation } from "react-router";

export function ScrollToTop() {
  const { pathname, search } = useLocation();
  const previousRouteRef = useRef<string>(`${pathname}${search}`);
  const scrollByRouteRef = useRef<Record<string, number>>({});

  useEffect(() => {
    if ("scrollRestoration" in window.history) {
      window.history.scrollRestoration = "manual";
    }
  }, []);

  useLayoutEffect(() => {
    const previousRoute = previousRouteRef.current;
    // Capture the route being left before applying any scroll for the next route.
    scrollByRouteRef.current[previousRoute] = window.scrollY || document.documentElement.scrollTop || 0;

    const currentRoute = `${pathname}${search}`;

    const resetScroll = () => {
      window.scrollTo({ top: 0, left: 0, behavior: "auto" });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
    };

    const applyScroll = () => {
      if (pathname === "/") {
        const savedHomeScroll = scrollByRouteRef.current[currentRoute] ?? 0;
        window.scrollTo({ top: savedHomeScroll, left: 0, behavior: "auto" });
        document.documentElement.scrollTop = savedHomeScroll;
        document.body.scrollTop = savedHomeScroll;
        return;
      }

      resetScroll();
    };

    applyScroll();
    const rafId = window.requestAnimationFrame(applyScroll);
    const timeoutId = window.setTimeout(applyScroll, 0);
    previousRouteRef.current = currentRoute;

    return () => {
      window.cancelAnimationFrame(rafId);
      window.clearTimeout(timeoutId);
    };
  }, [pathname, search]);

  return null;
}

