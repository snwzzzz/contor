import { describe, expect, it } from "vitest";
import { averageColor, bannerGradient } from "./dominant-color";

function pixels(...rgba: [number, number, number, number][]): Uint8ClampedArray {
  return new Uint8ClampedArray(rgba.flat());
}

describe("averageColor", () => {
  it("averages the visible colored pixels", () => {
    expect(averageColor(pixels([200, 40, 40, 255], [100, 20, 20, 255]))).toEqual({ r: 150, g: 30, b: 30 });
  });

  it("ignores transparent, near-white and near-black pixels (backgrounds, outlines)", () => {
    expect(averageColor(pixels([0, 120, 200, 255], [255, 255, 255, 255], [3, 3, 3, 255], [10, 200, 10, 0]))).toEqual({ r: 0, g: 120, b: 200 });
  });

  it("has no color for an image without usable pixels", () => {
    expect(averageColor(pixels([255, 255, 255, 255], [0, 0, 0, 0]))).toBeNull();
  });
});

describe("bannerGradient", () => {
  it("fades the color into a darker shade", () => {
    expect(bannerGradient({ r: 200, g: 100, b: 50 })).toBe("linear-gradient(135deg, rgb(200 100 50), rgb(120 60 30))");
  });
});
