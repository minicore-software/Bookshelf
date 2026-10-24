#!/usr/bin/env node
/**
 * Generates the Bookshelf adaptive launcher icon from resources/icon.svg.
 *
 * Usage:
 *   node generate.mjs generate   # renders mipmaps, writes XML + color resource, writes out/ preview aids
 *   node generate.mjs check      # fitness-function checks; exits non-zero on failure
 *
 * See docs/plans/8-adaptive-launcher-icon.md for the full design rationale.
 * Do not hand-edit anything this script writes under app/src/main/res/.
 */

import { Resvg } from '@resvg/resvg-js';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(SCRIPT_DIR, '..', '..');
const ICON_SVG_PATH = join(ROOT_DIR, 'resources', 'icon.svg');
const RES_DIR = join(ROOT_DIR, 'app', 'src', 'main', 'res');
const OUT_DIR = join(SCRIPT_DIR, 'out');
const MANIFEST_PATH = join(ROOT_DIR, 'app', 'src', 'main', 'AndroidManifest.xml');

// --- Constants (see plan section 4 for how SCALE/CENTER were measured) ---

const SCALE = 0.12;
const CENTER = { x: 256, y: 254.75 };
const BACKGROUND_COLOR = '#F3EEE3';
const SAFE_ZONE_RADIUS_DP = 33;
const LEGACY_RX = Number(((108 / 512) * 72).toFixed(2)); // 15.19

const FOREGROUND_DP = 108;
const LEGACY_DP = 48;

const DENSITIES = [
  { name: 'mdpi', scale: 1 },
  { name: 'hdpi', scale: 1.5 },
  { name: 'xhdpi', scale: 2 },
  { name: 'xxhdpi', scale: 3 },
  { name: 'xxxhdpi', scale: 4 },
];

const BG_RECT_PATTERN =
  /<rect\s+width="512"\s+height="512"\s+rx="108"\s+fill="url\(#iconBgGradient\)"\s*\/>/g;

// --- SVG source handling ---

function readIconSvgText() {
  return readFileSync(ICON_SVG_PATH, 'utf8');
}

/**
 * Strips the outer <svg> wrapper and removes the background rect, asserting
 * it matches exactly once. Everything else is kept byte-for-byte. Throws
 * loudly if icon.svg no longer matches the expected shape (per plan §8 step 1 /
 * QA case F8), so a redesign can never silently produce a wrong background.
 */
function extractForegroundInner(svgText) {
  const openMatch = svgText.match(/^<svg\b[^>]*>/);
  if (!openMatch) {
    throw new Error('icon.svg: could not find the opening <svg> tag.');
  }
  const closeIndex = svgText.lastIndexOf('</svg>');
  if (closeIndex === -1) {
    throw new Error('icon.svg: could not find the closing </svg> tag.');
  }

  const inner = svgText.slice(openMatch[0].length, closeIndex);

  const matches = inner.match(BG_RECT_PATTERN);
  if (!matches || matches.length !== 1) {
    throw new Error(
      `icon.svg: expected exactly one background rect matching ` +
        `<rect width="512" height="512" rx="108" fill="url(#iconBgGradient)" />, ` +
        `found ${matches ? matches.length : 0}. icon.svg was likely redesigned — ` +
        `review the new background and update generate.mjs's BG_RECT_PATTERN ` +
        `(and the background color/decision) before regenerating.`
    );
  }

  return inner.replace(BG_RECT_PATTERN, '');
}

function foregroundTransform() {
  return `translate(54 54) scale(${SCALE}) translate(-${CENTER.x} -${CENTER.y})`;
}

function buildForegroundSvg(inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <g transform="${foregroundTransform()}">${inner}
  </g>
</svg>
`;
}

function buildFlattenedSvg(inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <rect x="0" y="0" width="108" height="108" fill="${BACKGROUND_COLOR}" />
  <g transform="${foregroundTransform()}">${inner}
  </g>
</svg>
`;
}

function buildCompositeSvg(inner, clipId, clipShapeMarkup) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="72" height="72">
  <defs>
    <clipPath id="${clipId}">
      ${clipShapeMarkup}
    </clipPath>
  </defs>
  <g clip-path="url(#${clipId})">
    <rect x="18" y="18" width="72" height="72" fill="${BACKGROUND_COLOR}" />
    <g transform="${foregroundTransform()}">${inner}
    </g>
  </g>
</svg>
`;
}

function buildLegacySvg(inner) {
  return buildCompositeSvg(
    inner,
    'legacyClip',
    `<rect x="18" y="18" width="72" height="72" rx="${LEGACY_RX}" />`
  );
}

function buildRoundSvg(inner) {
  return buildCompositeSvg(inner, 'roundClip', `<circle cx="54" cy="54" r="36" />`);
}

// --- Rendering ---

function renderSvgToPng(svgString, pixelSize) {
  const resvg = new Resvg(svgString, { fitTo: { mode: 'width', value: pixelSize } });
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

function writeTextFileLf(filePath, content) {
  mkdirSync(dirname(filePath), { recursive: true });
  const normalized = content.replace(/\r\n/g, '\n');
  const withFinalNewline = normalized.endsWith('\n') ? normalized : `${normalized}\n`;
  writeFileSync(filePath, withFinalNewline, { encoding: 'utf8' });
}

function writeBinaryFile(filePath, buffer) {
  mkdirSync(dirname(filePath), { recursive: true });
  writeFileSync(filePath, buffer);
}

const ADAPTIVE_ICON_XML = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
`;

const COLOR_XML = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">${BACKGROUND_COLOR}</color>
</resources>
`;

function mipmapPaths(densityName) {
  const dir = join(RES_DIR, `mipmap-${densityName}`);
  return {
    dir,
    foreground: join(dir, 'ic_launcher_foreground.png'),
    legacy: join(dir, 'ic_launcher.png'),
    round: join(dir, 'ic_launcher_round.png'),
  };
}

// --- Preview aids (out/, gitignored) ---

function roundedRectPath(x, y, w, h, [rTL, rTR, rBR, rBL]) {
  const seg = [];
  seg.push(`M ${x + rTL} ${y}`);
  seg.push(`L ${x + w - rTR} ${y}`);
  if (rTR) seg.push(`A ${rTR} ${rTR} 0 0 1 ${x + w} ${y + rTR}`);
  seg.push(`L ${x + w} ${y + h - rBR}`);
  if (rBR) seg.push(`A ${rBR} ${rBR} 0 0 1 ${x + w - rBR} ${y + h}`);
  seg.push(`L ${x + rBL} ${y + h}`);
  if (rBL) seg.push(`A ${rBL} ${rBL} 0 0 1 ${x} ${y + h - rBL}`);
  seg.push(`L ${x} ${y + rTL}`);
  if (rTL) seg.push(`A ${rTL} ${rTL} 0 0 1 ${x + rTL} ${y}`);
  seg.push('Z');
  return seg.join(' ');
}

function squirclePath(cx, cy, a, n = 4, steps = 72) {
  const pts = [];
  for (let i = 0; i <= steps; i++) {
    const t = (i / steps) * Math.PI * 2;
    const cosT = Math.cos(t);
    const sinT = Math.sin(t);
    const x = cx + a * Math.sign(cosT) * Math.abs(cosT) ** (2 / n);
    const y = cy + a * Math.sign(sinT) * Math.abs(sinT) ** (2 / n);
    pts.push(`${x.toFixed(3)},${y.toFixed(3)}`);
  }
  return `M ${pts.join(' L ')} Z`;
}

function maskPanel(label, clipPathD, flattenedB64, offsetX) {
  const clipId = `mask-${label.toLowerCase().replace(/[^a-z]/g, '')}`;
  return `
  <g transform="translate(${offsetX} 0)">
    <defs>
      <clipPath id="${clipId}">
        <path d="${clipPathD}" />
      </clipPath>
    </defs>
    <image x="0" y="0" width="108" height="108" clip-path="url(#${clipId})"
           href="data:image/png;base64,${flattenedB64}" />
    <circle cx="54" cy="54" r="${SAFE_ZONE_RADIUS_DP}" fill="none" stroke="#C1693C"
            stroke-width="0.6" stroke-dasharray="2,1.5" />
    <text x="54" y="117" font-size="7" text-anchor="middle" fill="#231F20">${label}</text>
  </g>`;
}

function buildMaskPreviewSheet(flattenedPngBuffer) {
  const b64 = flattenedPngBuffer.toString('base64');
  const panelWidth = 120;
  const panels = [
    maskPanel('Circle', `M 90 54 A 36 36 0 1 1 18 54 A 36 36 0 1 1 90 54 Z`, b64, 0 * panelWidth),
    maskPanel('Squircle', squirclePath(54, 54, 36, 4), b64, 1 * panelWidth),
    maskPanel(
      'Rounded square',
      roundedRectPath(18, 18, 72, 72, [18, 18, 18, 18]),
      b64,
      2 * panelWidth
    ),
    maskPanel('Teardrop', roundedRectPath(18, 18, 72, 72, [36, 36, 10, 36]), b64, 3 * panelWidth),
  ];
  const sheetWidth = panelWidth * panels.length;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${sheetWidth} 124" width="${
    sheetWidth * 3
  }" height="${124 * 3}">
  <rect width="${sheetWidth}" height="124" fill="#ffffff" />
  ${panels.join('\n')}
</svg>
`;
}

function buildComparisonSheet(originalIconSvgText, legacyPngBuffer) {
  const originalRender = renderSvgToPng(originalIconSvgText, 216);
  const originalB64 = originalRender.png.toString('base64');
  const legacyB64 = legacyPngBuffer.toString('base64');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 460 260" width="920" height="520">
  <rect width="460" height="260" fill="#ffffff" />
  <image x="10" y="10" width="216" height="216" href="data:image/png;base64,${originalB64}" />
  <text x="118" y="248" font-size="12" text-anchor="middle" fill="#231F20">resources/icon.svg (original)</text>
  <image x="250" y="34" width="192" height="192" href="data:image/png;base64,${legacyB64}" />
  <text x="346" y="248" font-size="12" text-anchor="middle" fill="#231F20">ic_launcher.png (legacy, xxxhdpi)</text>
</svg>
`;
}

function writePreviewAids(inner, originalIconSvgText, legacyPngBuffer) {
  mkdirSync(OUT_DIR, { recursive: true });

  const foregroundSvg = buildForegroundSvg(inner);
  writeTextFileLf(join(OUT_DIR, 'foreground.svg'), foregroundSvg);

  const flattenedSvg = buildFlattenedSvg(inner);
  const flattened = renderSvgToPng(flattenedSvg, 432);
  writeBinaryFile(join(OUT_DIR, 'flattened.png'), flattened.png);

  const maskSheetSvg = buildMaskPreviewSheet(flattened.png);
  const maskSheetPng = renderSvgToPng(maskSheetSvg, 1440);
  writeBinaryFile(join(OUT_DIR, 'mask-preview.png'), maskSheetPng.png);

  const comparisonSvg = buildComparisonSheet(originalIconSvgText, legacyPngBuffer);
  const comparisonPng = renderSvgToPng(comparisonSvg, 920);
  writeBinaryFile(join(OUT_DIR, 'comparison.png'), comparisonPng.png);
}

// --- generate mode ---

function generate() {
  const svgText = readIconSvgText();
  const inner = extractForegroundInner(svgText);

  const foregroundSvg = buildForegroundSvg(inner);
  const legacySvg = buildLegacySvg(inner);
  const roundSvg = buildRoundSvg(inner);

  let xxxhdpiLegacyPng = null;

  for (const density of DENSITIES) {
    const paths = mipmapPaths(density.name);
    mkdirSync(paths.dir, { recursive: true });

    const fgPx = Math.round(FOREGROUND_DP * density.scale);
    const legacyPx = Math.round(LEGACY_DP * density.scale);

    const foreground = renderSvgToPng(foregroundSvg, fgPx);
    writeBinaryFile(paths.foreground, foreground.png);

    const legacy = renderSvgToPng(legacySvg, legacyPx);
    writeBinaryFile(paths.legacy, legacy.png);
    if (density.name === 'xxxhdpi') xxxhdpiLegacyPng = legacy.png;

    const round = renderSvgToPng(roundSvg, legacyPx);
    writeBinaryFile(paths.round, round.png);

    console.log(
      `  ${density.name}: foreground ${fgPx}x${fgPx}px, legacy/round ${legacyPx}x${legacyPx}px`
    );
  }

  const anydpiDir = join(RES_DIR, 'mipmap-anydpi');
  writeTextFileLf(join(anydpiDir, 'ic_launcher.xml'), ADAPTIVE_ICON_XML);
  writeTextFileLf(join(anydpiDir, 'ic_launcher_round.xml'), ADAPTIVE_ICON_XML);
  writeTextFileLf(join(RES_DIR, 'values', 'ic_launcher_background.xml'), COLOR_XML);

  writePreviewAids(inner, svgText, xxxhdpiLegacyPng);

  console.log('\nLauncher icon generation complete.');
  console.log(`Preview aids written to ${OUT_DIR}`);
}

// --- check mode ---

function maxAlphaRadiusPx(pixels, width, height) {
  let maxR = 0;
  const cx = width / 2;
  const cy = height / 2;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const alpha = pixels[(y * width + x) * 4 + 3];
      if (alpha > 0) {
        const dx = x + 0.5 - cx;
        const dy = y + 0.5 - cy;
        const r = Math.sqrt(dx * dx + dy * dy);
        if (r > maxR) maxR = r;
      }
    }
  }
  return maxR;
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
        `render from icon.svg. Re-run "npm run generate" and commit the result, or ` +
        `investigate the renderer/source change.`,
    ];
  }
  return [];
}

function check() {
  const errors = [];
  const svgText = readIconSvgText();
  const inner = extractForegroundInner(svgText); // throws loudly per F8 if bg rect pattern changed

  const foregroundSvg = buildForegroundSvg(inner);
  const legacySvg = buildLegacySvg(inner);
  const roundSvg = buildRoundSvg(inner);

  let maxSafeZoneRadiusDp = 0;

  console.log('Safe zone + dimensions + drift:');
  for (const density of DENSITIES) {
    const paths = mipmapPaths(density.name);
    const fgPx = Math.round(FOREGROUND_DP * density.scale);
    const legacyPx = Math.round(LEGACY_DP * density.scale);

    const freshForeground = renderSvgToPng(foregroundSvg, fgPx);
    const freshLegacy = renderSvgToPng(legacySvg, legacyPx);
    const freshRound = renderSvgToPng(roundSvg, legacyPx);

    // Safe zone (fresh render; this is also what gets committed by `generate`).
    const radiusPx = maxAlphaRadiusPx(freshForeground.pixels, freshForeground.width, freshForeground.height);
    const radiusDp = radiusPx / density.scale;
    if (radiusDp > maxSafeZoneRadiusDp) maxSafeZoneRadiusDp = radiusDp;
    if (radiusDp > SAFE_ZONE_RADIUS_DP) {
      errors.push(
        `${density.name}: foreground content reaches ${radiusDp.toFixed(2)}dp from center, ` +
          `outside the ${SAFE_ZONE_RADIUS_DP}dp safe-zone radius.`
      );
    }

    // Dimensions + drift against committed files.
    for (const [kind, freshRender, filePath] of [
      ['foreground', freshForeground, paths.foreground],
      ['legacy', freshLegacy, paths.legacy],
      ['round', freshRound, paths.round],
    ]) {
      if (!existsSync(filePath)) {
        errors.push(`${density.name}/${kind}: missing committed file ${filePath}`);
        continue;
      }
      const buffer = readFileSync(filePath);
      const expectedPx = kind === 'foreground' ? fgPx : legacyPx;
      const { width, height } = readPngDimensions(buffer);
      if (width !== expectedPx || height !== expectedPx) {
        errors.push(
          `${density.name}/${kind}: expected ${expectedPx}x${expectedPx}px, found ${width}x${height}px`
        );
      }
      const committed = decodePng(buffer);
      errors.push(...comparePixels(freshRender.pixels, committed.pixels, `${density.name}/${kind}`));
    }

    console.log(
      `  ${density.name}: safe-zone radius ${radiusDp.toFixed(2)}dp, foreground ${fgPx}px, legacy/round ${legacyPx}px`
    );
  }

  console.log(`\nMax safe-zone radius across all densities: ${maxSafeZoneRadiusDp.toFixed(2)}dp (limit ${SAFE_ZONE_RADIUS_DP}dp)`);

  // Background coverage.
  const colorXmlPath = join(RES_DIR, 'values', 'ic_launcher_background.xml');
  const colorXml = readFileSync(colorXmlPath, 'utf8');
  const colorMatch = colorXml.match(/<color name="ic_launcher_background">(#[0-9A-Fa-f]+)<\/color>/);
  if (!colorMatch) {
    errors.push(`${colorXmlPath}: could not find ic_launcher_background color value`);
  } else if (!/^#[0-9A-Fa-f]{6}$/.test(colorMatch[1])) {
    errors.push(
      `${colorXmlPath}: ic_launcher_background must be a fully opaque 6-digit hex color ` +
        `(no alpha byte), found "${colorMatch[1]}"`
    );
  } else if (colorMatch[1].toUpperCase() !== BACKGROUND_COLOR.toUpperCase()) {
    errors.push(
      `${colorXmlPath}: ic_launcher_background is ${colorMatch[1]}, expected ${BACKGROUND_COLOR}`
    );
  }

  // Adaptive XML: byte-for-byte drift against the exact content `generate` would write.
  for (const xmlName of ['ic_launcher.xml', 'ic_launcher_round.xml']) {
    const xmlPath = join(RES_DIR, 'mipmap-anydpi', xmlName);
    if (!existsSync(xmlPath)) {
      errors.push(`missing ${xmlPath}`);
      continue;
    }
    const xml = readFileSync(xmlPath, 'utf8');
    // Normalize line endings before comparing: a fresh Windows checkout with
    // core.autocrlf=true rewrites LF-stored files as CRLF on disk, which would
    // otherwise register as false drift even though no content actually changed.
    if (xml.replace(/\r\n/g, '\n') !== ADAPTIVE_ICON_XML) {
      errors.push(
        `${xmlPath}: drift detected — committed file no longer matches the generated ` +
          `adaptive-icon XML. Re-run "npm run generate" and commit the result, or revert ` +
          `the hand-edit.`
      );
    }
  }

  // Color resource: byte-for-byte drift, same idea as the adaptive XML above.
  if (existsSync(colorXmlPath) && colorXml.replace(/\r\n/g, '\n') !== COLOR_XML) {
    errors.push(
      `${colorXmlPath}: drift detected — committed file no longer matches the generated ` +
        `color resource. Re-run "npm run generate" and commit the result, or revert the ` +
        `hand-edit.`
    );
  }

  // Manifest: both icon attributes must point at the generated mipmaps.
  if (!existsSync(MANIFEST_PATH)) {
    errors.push(`missing ${MANIFEST_PATH}`);
  } else {
    const manifest = readFileSync(MANIFEST_PATH, 'utf8');
    if (!manifest.includes('android:icon="@mipmap/ic_launcher"')) {
      errors.push(`${MANIFEST_PATH}: missing android:icon="@mipmap/ic_launcher"`);
    }
    if (!manifest.includes('android:roundIcon="@mipmap/ic_launcher_round"')) {
      errors.push(`${MANIFEST_PATH}: missing android:roundIcon="@mipmap/ic_launcher_round"`);
    }
  }

  console.log('\nBackground coverage, drift and manifest checks complete.');

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
