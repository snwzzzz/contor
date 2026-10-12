import { useEffect, useState } from "react";

export interface Rgb {
  r: number;
  g: number;
  b: number;
}

const SAMPLE_SIZE = 24;

/**
 * Average of the visible, colored pixels of RGBA data. Transparent, near-white and near-black pixels are skipped:
 * they are mostly backgrounds and outlines, not the color a picture stands for.
 */
export function averageColor(data: Uint8ClampedArray): Rgb | null {
  let r = 0;
  let g = 0;
  let b = 0;
  let count = 0;
  for (let i = 0; i + 3 < data.length; i += 4) {
    const [red, green, blue, alpha] = [data[i], data[i + 1], data[i + 2], data[i + 3]];
    if (alpha < 128) continue;
    if (red > 240 && green > 240 && blue > 240) continue;
    if (red < 16 && green < 16 && blue < 16) continue;
    r += red;
    g += green;
    b += blue;
    count++;
  }
  return count === 0 ? null : { r: Math.round(r / count), g: Math.round(g / count), b: Math.round(b / count) };
}

/** The banner background: the color fading into a darker shade of itself. */
export function bannerGradient({ r, g, b }: Rgb): string {
  const dark = (value: number) => Math.round(value * 0.6);
  return `linear-gradient(135deg, rgb(${r} ${g} ${b}), rgb(${dark(r)} ${dark(g)} ${dark(b)}))`;
}

/** The dominant color of a same-origin image (sampled on a small canvas), or null while unknown or unreadable. */
export function useDominantColor(src: string | undefined): Rgb | null {
  const [result, setResult] = useState<{ src: string; color: Rgb | null } | null>(null);

  useEffect(() => {
    if (!src) return;
    let cancelled = false;
    const image = new Image();
    image.onload = () => {
      if (cancelled) return;
      try {
        const canvas = document.createElement("canvas");
        canvas.width = SAMPLE_SIZE;
        canvas.height = SAMPLE_SIZE;
        const context = canvas.getContext("2d");
        if (!context) return;
        context.drawImage(image, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
        setResult({ src, color: averageColor(context.getImageData(0, 0, SAMPLE_SIZE, SAMPLE_SIZE).data) });
      } catch {
        setResult({ src, color: null });
      }
    };
    image.src = src;
    return () => {
      cancelled = true;
    };
  }, [src]);

  return result && result.src === src ? result.color : null;
}
