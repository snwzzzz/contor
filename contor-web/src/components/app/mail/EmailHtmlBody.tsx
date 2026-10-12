"use client";

import { useEffect, useRef, useState, type CSSProperties, type SyntheticEvent } from "react";

/** Links open in a new tab (the frame itself cannot navigate) and leak no referrer. */
const FRAME_HEAD = '<base target="_blank"><meta name="referrer" content="no-referrer">';

/**
 * The mail sits on the reading pane without a page of its own. `contor-paper` gives it the light page senders expect
 * (system `Canvas` color; "original colors" in dark mode). Dark mode inverts the whole frame (see DARK), so pictures are
 * inverted back here and keep their colors, while the frame's root has the class `contor-dark`.
 */
const FRAME_STYLE = `<style>
html.contor-paper { color-scheme: light; background: Canvas; }
html.contor-dark img, html.contor-dark picture, html.contor-dark video, html.contor-dark svg,
html.contor-dark [style*="background-image"], html.contor-dark [background] { filter: invert(1) hue-rotate(180deg); }
</style>`;

/** Mail is written for a white page: in dark mode it is inverted, hue kept (how Outlook and Gmail darken mail). */
const DARK: CSSProperties = { filter: "invert(1) hue-rotate(180deg)" };

/** Images the CSP blocks (`img-src 'self' blob: data:`): everything loaded from elsewhere, e.g. tracking pixels. */
function isBlocked(image: HTMLImageElement): boolean {
  const src = image.getAttribute("src") ?? "";
  return src !== "" && !src.startsWith("data:") && !src.startsWith("blob:") && !src.startsWith("cid:") && /^(https?:)?\/\//i.test(src);
}

/**
 * Height of the mail's content: the body (not the root, whose scroll height never drops below the frame's own height,
 * so short mails would keep a tall frame) plus the body's margins. `undefined` while the frame's document is still empty
 * (it is replaced on every load: a resize can come in between).
 */
function contentHeight(document: Document): number | undefined {
  const body = document.body;
  if (!body) return document.documentElement?.scrollHeight;
  const style = document.defaultView?.getComputedStyle(body);
  const margins = style ? (Number.parseFloat(style.marginTop) || 0) + (Number.parseFloat(style.marginBottom) || 0) : 0;
  return Math.ceil(body.scrollHeight + margins);
}

interface EmailHtmlBodyProps {
  html: string;
  /** Show the mail darkened (inverted, pictures in their own colors). */
  dark?: boolean;
  /** Put the mail on its own light page (its original colors while the app is dark). */
  paper?: boolean;
}

/**
 * HTML mail in a sandboxed frame (no scripts) that grows to its content height, so only the reading pane scrolls. The
 * height is measured again whenever the frame's width changes (window resize, a scrollbar appearing), as the mail reflows.
 * `allow-same-origin` is needed to measure the document; without `allow-scripts` the mail still cannot run code.
 * Remote images stay blocked for privacy; they are hidden (keeping their space) instead of showing broken icons.
 */
export function EmailHtmlBody({ html, dark = false, paper = false }: EmailHtmlBodyProps) {
  const frame = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState<number>();

  // The frame's document is outside React: keep its classes in step with the props.
  useEffect(() => {
    const root = frame.current?.contentDocument?.documentElement;
    root?.classList.toggle("contor-dark", dark);
    root?.classList.toggle("contor-paper", paper);
  }, [dark, paper]);

  // A different width reflows the mail: measure its height again.
  useEffect(() => {
    const element = frame.current;
    if (!element) return;
    const observer = new ResizeObserver(() => {
      const measured = element.contentDocument ? contentHeight(element.contentDocument) : undefined;
      if (measured !== undefined) setHeight(measured);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  function fitToContent(event: SyntheticEvent<HTMLIFrameElement>) {
    const document = event.currentTarget.contentDocument;
    if (!document?.documentElement) return;
    for (const image of document.images) {
      if (isBlocked(image)) image.style.visibility = "hidden";
    }
    document.documentElement.classList.toggle("contor-dark", dark);
    document.documentElement.classList.toggle("contor-paper", paper);
    // The frame is as tall as its content, so its own scrollbar would only appear from rounding.
    document.documentElement.style.overflow = "hidden";
    const measured = contentHeight(document);
    if (measured !== undefined) setHeight(measured);
  }

  return (
    <iframe
      ref={frame}
      sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
      srcDoc={FRAME_HEAD + FRAME_STYLE + html}
      title="Email content"
      onLoad={fitToContent}
      data-dark={dark ? "true" : undefined}
      data-paper={paper ? "true" : undefined}
      style={{ ...(height ? { height } : {}), ...(dark ? DARK : {}) }}
      className={paper ? "h-96 w-full rounded-lg" : "h-96 w-full"}
    />
  );
}
