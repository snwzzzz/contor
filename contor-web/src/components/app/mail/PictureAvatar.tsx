"use client";

import Image from "next/image";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { avatarTone, initialOf } from "./mail-format";

interface PictureAvatarProps {
  /** Who the avatar stands for; gives the initial and the tint when there is no picture. */
  label: string;
  /** Same-origin image URL (account photo, contact photo, sender logo). */
  src?: string;
  size?: number;
  className?: string;
}

/** Round picture, or a tinted initial when there is none or it fails to load. */
export function PictureAvatar({ label, src, size = 20, className }: PictureAvatarProps) {
  const [failedSrc, setFailedSrc] = useState<string>();

  if (src && src !== failedSrc) {
    return (
      <Image
        src={src}
        alt=""
        width={size}
        height={size}
        unoptimized
        onError={() => setFailedSrc(src)}
        // Fixed square: as a flex child the image would otherwise stretch to the row height.
        style={{ width: size, height: size }}
        className={cn("shrink-0 rounded-full bg-muted object-cover", className)}
      />
    );
  }
  return (
    <span
      aria-hidden="true"
      style={{ width: size, height: size, fontSize: Math.max(10, Math.round(size * 0.42)) }}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full font-medium",
        avatarTone(label),
        className,
      )}
    >
      {/* Trimmed to the cap height: centers the visible letter, not its line box (which has room for descenders). */}
      <span className="block leading-none [text-box:trim-both_cap_alphabetic]">{initialOf(label)}</span>
    </span>
  );
}
