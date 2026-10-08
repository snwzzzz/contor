"use client";

import { useState, type SyntheticEvent } from "react";

/**
 * HTML mail in a sandboxed frame (no scripts) that grows to its content height, so only the reading pane scrolls.
 * `allow-same-origin` is needed to measure the document; without `allow-scripts` the mail still cannot run code.
 */
export function EmailHtmlBody({ html }: { html: string }) {
  const [height, setHeight] = useState<number>();

  function fitToContent(event: SyntheticEvent<HTMLIFrameElement>) {
    const document = event.currentTarget.contentDocument;
    if (!document) return;
    // The frame is as tall as its content, so its own scrollbar would only appear from rounding.
    document.documentElement.style.overflow = "hidden";
    setHeight(document.documentElement.scrollHeight);
  }

  return (
    <iframe
      sandbox="allow-same-origin"
      srcDoc={html}
      title="Email content"
      onLoad={fitToContent}
      style={height ? { height } : undefined}
      className="h-96 w-full rounded-md"
    />
  );
}
