#!/usr/bin/env node
/**
 * Generates the Bookshelf two-stage splash screen artwork from
 * resources/splash-screen.svg.
 *
 * Usage:
 *   node generate.mjs generate   # renders density-bucketed PNGs, writes out/ preview aids
 *   node generate.mjs check      # fitness-function checks; exits non-zero on failure
 *
 * See docs/plans/9-splash-screen.md for the full design rationale.
 * Do not hand-edit anything this script writes under app/src/main/res/.
 */

import { Resvg } from '@resvg/resvg-js';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(SCRIPT_DIR, '..', '..');
const SOURCE_SVG_PATH = join(ROOT_DIR, 'resources', 'splash-screen.svg');
const RES_DIR = join(ROOT_DIR, 'app', 'src', 'main', 'res');
const OUT_DIR = join(SCRIPT_DIR, 'out');
const FONT_PATH = join(SCRIPT_DIR, 'fonts', 'PlayfairDisplay-Bold.ttf');

// --- Constants (see plan sections 2 (S3/S4) and 4) ---

const SOURCE_SIZE = { width: 1080, height: 1920 };
// Source: Fontsource's Playfair Display latin-subset static build, weight 700
// (Bold), font version 1.203 (per the font's own name table), from the
// fontsource npm package version 5.3.0 —
// https://cdn.jsdelivr.net/fontsource/fonts/playfair-display@5.3.0/latin-700-normal.ttf
// Pinned to this package version (not @latest) so re-downloading reproduces
// the same file even after Fontsource publishes a newer package. Re-download
// that exact URL to verify/regenerate; do not substitute the full Google
// Fonts variable-font build, which hashes differently. Verified 2026-10-24:
// downloading that URL reproduces this exact SHA-256.
const FONT_SHA256 = '36be40f50b34ae2da51608e36eae9eeaf90202d7395fa1be0186f7e257af62cc';
const BASE_DP = { width: 360, height: 640 };

const DENSITIES = [
  { name: 'mdpi', scale: 1 },
  { name: 'hdpi', scale: 1.5 },
  { name: 'xhdpi', scale: 2 },
  { name: 'xxhdpi', scale: 3 },
  { name: 'xxxhdpi', scale: 4 },
];

// Wordmark band, in source (1080x1920) pixel space. Measured at xxhdpi
// (scale 3, which renders at native 1080px width, i.e. 1:1 with source
// coordinates) against the pinned Playfair Display Bold font.
const WORDMARK_BAND = { x0: 300, x1: 780, y0: 250, y1: 360 };
const WORDMARK_REFERENCE_COUNT = 9180; // spike value at xxhdpi, see plan §4 check 4
const WORDMARK_MIN_FRACTION = 0.5;

// --- Source SVG handling ---

function readSourceSvgText() {
  return readFileSync(SOURCE_SVG_PATH, 'utf8');
}

/**
 * Asserts the source SVG still declares the expected 1080x1920 viewBox.
 * A re-proportioned artwork would invalidate the density bucket sizes and
 * the wordmark detection band, so this fails loudly instead of silently
 * rendering wrong-sized or mis-cropped assets.
 */
function assertSourceViewBox(svgText) {
  const match = svgText.match(/viewBox="([^"]+)"/);
  if (!match) {
    throw new Error('splash-screen.svg: could not find a viewBox attribute.');
  }
  const parts = match[1].trim().split(/\s+/).map(Number);
  const [, , width, height] = parts;
  if (width !== SOURCE_SIZE.width || height !== SOURCE_SIZE.height) {
    throw new Error(
      `splash-screen.svg: expected viewBox "0 0 ${SOURCE_SIZE.width} ${SOURCE_SIZE.height}", ` +
        `found "${match[1]}". The artwork was likely redesigned — review the new proportions ` +
        `and update generate.mjs's SOURCE_SIZE/density math before regenerating.`
    );
  }
}

function assertFontHash() {
  const buffer = readFileSync(FONT_PATH);
  const hash = createHash('sha256').update(buffer).digest('hex');
  if (hash !== FONT_SHA256) {
    throw new Error(
      `${FONT_PATH}: SHA-256 is ${hash}, expected ${FONT_SHA256}. The committed font file ` +
        `changed — verify its source before regenerating (see plan §4, S3).`
    );
  }
}

// --- Rendering ---

function renderSvgToPng(svgString, pixelWidth) {
  const resvg = new Resvg(svgString, {
    fitTo: { mode: 'width', value: pixelWidth },
    font: {
      loadSystemFonts: false,
      fontFiles: [FONT_PATH],
      defaultFontFamily: 'Playfair Display',
    },
  });
  const rendered = resvg.render();
  return {
    width: rendered.width,
    height: rendered.height,
    pixels: rendered.pixels,
    png: rendered.asPng(),
  };
}

/** Decodes a committed PNG back to raw RGBA pixels using resvg as the decoder
 * (no extra PNG-decoding dependency): embed it as a data URI <image> and
 * render that 1:1. */
function decodePng(buffer) {
  const { width, height } = readPngDimensions(buffer);
  const b64 = buffer.toString('base64');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}"><image x="0" y="0" width="${width}" height="${height}" preserveAspectRatio="none" href="data:image/png;base64,${b64}" /></svg>`;
  const rendered = new Resvg(svg, { fitTo: { mode: 'width', value: width } }).render();
  return { width: rendered.width, height: rendered.height, pixels: rendered.pixels };
}

function readPngDimensions(buffer) {
  const signatureOk =
    buffer.length > 24 &&
    buffer[0] === 0x89 &&
    buffer.toString('ascii', 1, 4) === 'PNG';
  const ihdrOk = buffer.toString('ascii', 12, 16) === 'IHDR';
  if (!signatureOk || !ihdrOk) {
    throw new Error('Not a valid PNG file (unexpected signature/IHDR).');
  }
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20),
  };
}

// --- File writing ---

function writeBinaryFile(filePath, buffer) {
  mkdirSync(dirname(filePath), { recursive: true });
  writeFileSync(filePath, buffer);
}

function splashScreenPath(densityName) {
  return join(RES_DIR, `drawable-${densityName}`, 'splash_screen.png');
}

// --- Pixel analysis ---

function minAlpha(pixels) {
  let min = 255;
  for (let i = 3; i < pixels.length; i += 4) {
    if (pixels[i] < min) min = pixels[i];
    if (min === 0) break;
  }
  return min;
}

/** Counts pixels matching the dark-green wordmark color (R<60, G<90, B<80)
 * within the density-scaled wordmark band. Catches resvg silently dropping
 * the "Bookshelf" <text> when no matching font is available (plan §4 check 4). */
function countWordmarkPixels(pixels, width, height, factor) {
  const x0 = Math.max(0, Math.floor(WORDMARK_BAND.x0 * factor));
  const x1 = Math.min(width, Math.ceil(WORDMARK_BAND.x1 * factor));
  const y0 = Math.max(0, Math.floor(WORDMARK_BAND.y0 * factor));
  const y1 = Math.min(height, Math.ceil(WORDMARK_BAND.y1 * factor));
  let count = 0;
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const i = (y * width + x) * 4;
      const r = pixels[i];
      const g = pixels[i + 1];
      const b = pixels[i + 2];
      if (r < 60 && g < 90 && b < 80) count++;
    }
  }
  return count;
}

function comparePixels(a, b, label, tolerance = 1) {
  if (a.length !== b.length) {
    return [`${label}: pixel buffer length mismatch (${a.length} vs ${b.length})`];
  }
  let worst = 0;
  let mismatches = 0;
  for (let i = 0; i < a.length; i++) {
    const diff = Math.abs(a[i] - b[i]);
    if (diff > worst) worst = diff;
    if (diff > tolerance) mismatches++;
  }
  if (mismatches > 0) {
    return [
      `${label}: drift detected — ${mismatches} channel value(s) differ by more than ` +
        `${tolerance} level (worst diff ${worst}). Committed PNG no longer matches a fresh ` +
        `render from splash-screen.svg. Re-run "npm run generate" and commit the result, or ` +
        `investigate the renderer/source change.`,
    ];
  }
  return [];
}

// --- generate mode ---

function generate() {
  const svgText = readSourceSvgText();
  assertSourceViewBox(svgText);
  assertFontHash();

  let xxhdpiPng = null;

  for (const density of DENSITIES) {
    const pixelWidth = Math.round(BASE_DP.width * density.scale);
    const rendered = renderSvgToPng(svgText, pixelWidth);
    writeBinaryFile(splashScreenPath(density.name), rendered.png);
    if (density.name === 'xxhdpi') xxhdpiPng = rendered.png;

    console.log(`  ${density.name}: ${rendered.width}x${rendered.height}px`);
  }

  mkdirSync(OUT_DIR, { recursive: true });
  writeBinaryFile(join(OUT_DIR, 'preview.png'), xxhdpiPng);

  console.log('\nSplash screen generation complete.');
  console.log(`Preview written to ${join(OUT_DIR, 'preview.png')}`);
}

// --- check mode ---

function check() {
  const errors = [];
  const svgText = readSourceSvgText();

  try {
    assertSourceViewBox(svgText);
  } catch (error) {
    errors.push(error.message);
  }

  try {
    assertFontHash();
  } catch (error) {
    errors.push(error.message);
  }

  if (errors.length > 0) {
    // Font/viewBox problems make every further render meaningless.
    console.error('\nFAILED:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }

  console.log('Dimensions, opacity, wordmark presence and drift:');
  for (const density of DENSITIES) {
    const expectedWidth = Math.round(BASE_DP.width * density.scale);
    const expectedHeight = Math.round(BASE_DP.height * density.scale);
    const filePath = splashScreenPath(density.name);

    if (!existsSync(filePath)) {
      errors.push(`${density.name}: missing committed file ${filePath}`);
      continue;
    }

    const freshRender = renderSvgToPng(svgText, expectedWidth);
    const buffer = readFileSync(filePath);
    const { width, height } = readPngDimensions(buffer);

    if (width !== expectedWidth || height !== expectedHeight) {
      errors.push(
        `${density.name}: expected ${expectedWidth}x${expectedHeight}px, found ${width}x${height}px`
      );
    }

    const committed = decodePng(buffer);

    const alpha = minAlpha(committed.pixels);
    if (alpha !== 255) {
      errors.push(
        `${density.name}: not fully opaque (min alpha ${alpha}). The splash must be full-bleed.`
      );
    }

    const factor = density.scale / 3; // xxhdpi (scale 3) == 1:1 with source px
    const wordmarkCount = countWordmarkPixels(committed.pixels, width, height, factor);
    const threshold = WORDMARK_MIN_FRACTION * WORDMARK_REFERENCE_COUNT * factor * factor;
    if (wordmarkCount < threshold) {
      errors.push(
        `${density.name}: wordmark pixel count ${wordmarkCount} is below the ${threshold.toFixed(
          0
        )} threshold. The "Bookshelf" text may have been silently dropped (missing/mismatched font).`
      );
    }

    errors.push(...comparePixels(freshRender.pixels, committed.pixels, density.name));

    console.log(
      `  ${density.name}: ${width}x${height}px, min alpha ${alpha}, wordmark pixels ${wordmarkCount} (threshold ${threshold.toFixed(0)})`
    );
  }

  console.log('\nAll checks complete.');

  if (errors.length > 0) {
    console.error('\nFAILED:');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
    return;
  }

  console.log('\nAll checks passed.');
}

// --- entry point ---

const mode = process.argv[2];
if (mode === 'generate') {
  generate();
} else if (mode === 'check') {
  check();
} else {
  console.error('Usage: node generate.mjs <generate|check>');
  process.exitCode = 1;
}
