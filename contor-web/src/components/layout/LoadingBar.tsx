"use client";

import { useIsFetching } from "@tanstack/react-query";
import { useEffect, useState } from "react";

/** Short loads finish before the bar would show, so it never flickers. */
const SHOW_AFTER_MS = 150;

/**
 * A thin bar running along the bottom edge of the top bar while data loads for the first time (a query without data
 * yet). Background refreshes and polling of data that is already on screen do not count.
 */
export function LoadingBar() {
  const firstLoads = useIsFetching({ predicate: (query) => query.state.status === "pending" });
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (firstLoads === 0) {
      const hide = setTimeout(() => setVisible(false), 0);
      return () => clearTimeout(hide);
    }
    const show = setTimeout(() => setVisible(true), SHOW_AFTER_MS);
    return () => clearTimeout(show);
  }, [firstLoads]);

  if (!visible) return null;
  return (
    <div role="progressbar" aria-label="Loading" className="pointer-events-none absolute inset-x-0 -bottom-px h-0.5 overflow-hidden">
      <div className="h-full w-1/3 animate-loading-bar rounded-full bg-primary motion-reduce:w-full motion-reduce:animate-pulse" />
    </div>
  );
}
