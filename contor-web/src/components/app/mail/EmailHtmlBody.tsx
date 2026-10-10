"use client";

import { useState, type SyntheticEvent } from "react";

/** Links open in a new tab (the frame itself cannot navigate) and leak no referrer. */
const FRAME_HEAD = '<base target="_blank"><meta name="referrer" content="no-referrer">';

/** Images the CSP blocks (`img-src 'self' blob: data:`): everything loaded from elsewhere, e.g. tracking pixels. */
function isBlocked(image: HTMLImageElement): boolean {
  const src = image.getAttribute("src") ?? "";
  return src !== "" && !src.startsWith("data:") && !src.startsWith("blob:") && !src.startsWith("cid:") && /^(https?:)?\/\//i.test(src);
}

/**
 * HTML mail in a sandboxed frame (no scripts) that grows to its content height, so only the reading pane scrolls.
 * `allow-same-origin` is needed to measure the document; without `allow-scripts` the mail still cannot run code.
 * Remote images stay blocked for privacy; they are hidden (keeping their space) instead of showing broken icons.
 */
export function EmailHtmlBody({ html }: { html: string }) {
  const [height, setHeight] = useState<number>();

  function fitToContent(event: SyntheticEvent<HTMLIFrameElement>) {
    const document = event.currentTarget.contentDocument;
    if (!document) return;
    for (const image of document.images) {
      if (isBlocked(image)) image.style.visibility = "hidden";
    }
    // The frame is as tall as its content, so its own scrollbar would only appear from rounding.
    document.documentElement.style.overflow = "hidden";
    setHeight(document.documentElement.scrollHeight);
  }

  return (
    <iframe
      sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
      srcDoc={FRAME_HEAD + html}
      title="Email content"
      onLoad={fitToContent}
      style={height ? { height } : undefined}
      className="h-96 w-full rounded-md"
    />
  );
}
