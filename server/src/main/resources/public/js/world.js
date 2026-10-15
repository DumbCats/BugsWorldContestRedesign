// Draws the BugsWorld grid and bugs on a canvas, and the species legend.

import { el } from "./api.js";

/** Ten species colors that stay distinguishable on light and dark backgrounds. */
export const SPECIES_COLORS = [
  "#2f6fde", "#e0475b", "#22a06b", "#f2a93b", "#8c5cd6",
  "#17a2b8", "#d6589e", "#9aa62b", "#b0703f", "#7f8b99",
];

export function speciesColor(id) {
  return SPECIES_COLORS[id % SPECIES_COLORS.length];
}

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/**
 * Draws a world sized to fit maxWidth x maxHeight CSS pixels.
 *
 * bugs is flat: `stride` numbers per bug, with species, row, column, facing
 * starting at `offset` (live frames: stride 5, offset 1; practice: 4, 0).
 */
export function drawWorld(canvas, { rows, columns, bugs, stride = 5, offset = 1 }, maxWidth, maxHeight) {
  const cell = Math.max(2, Math.floor(Math.min(maxWidth / columns, maxHeight / rows)));
  const width = cell * columns;
  const height = cell * rows;
  const ratio = window.devicePixelRatio || 1;
  if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
  }
  const g = canvas.getContext("2d");
  g.setTransform(ratio, 0, 0, ratio, 0, 0);
  g.fillStyle = cssVar("--world") || "#fff";
  g.fillRect(0, 0, width, height);

  if (cell >= 6) {
    g.strokeStyle = cssVar("--grid") || "#ddd";
    g.lineWidth = 1;
    g.beginPath();
    for (let r = 1; r < rows; r++) { g.moveTo(0, r * cell + 0.5); g.lineTo(width, r * cell + 0.5); }
    for (let c = 1; c < columns; c++) { g.moveTo(c * cell + 0.5, 0); g.lineTo(c * cell + 0.5, height); }
    g.stroke();
  }

  for (let i = 0; i < bugs.length; i += stride) {
    const species = bugs[i + offset];
    const row = bugs[i + offset + 1];
    const column = bugs[i + offset + 2];
    const facing = bugs[i + offset + 3];
    drawBug(g, column * cell + cell / 2, row * cell + cell / 2, cell, facing, speciesColor(species));
  }
  return cell;
}

/** A bug seen from above: body, head toward its facing, and legs once there is room. */
function drawBug(g, x, y, cell, facing, color) {
  g.save();
  g.translate(x, y);
  g.rotate((facing * Math.PI) / 2);
  const s = cell / 2;
  g.fillStyle = color;
  if (cell < 10) {
    g.beginPath();
    g.moveTo(0, -s * 0.9);
    g.lineTo(s * 0.75, s * 0.75);
    g.lineTo(-s * 0.75, s * 0.75);
    g.closePath();
    g.fill();
    g.restore();
    return;
  }
  if (cell >= 18) {
    g.strokeStyle = color;
    g.lineWidth = Math.max(1, cell / 18);
    g.beginPath();
    for (const ly of [-0.25, 0.1, 0.45]) {
      g.moveTo(-s * 0.35, s * ly); g.lineTo(-s * 0.75, s * (ly - 0.15));
      g.moveTo(s * 0.35, s * ly); g.lineTo(s * 0.75, s * (ly - 0.15));
    }
    g.stroke();
  }
  g.beginPath();
  g.ellipse(0, s * 0.18, s * 0.42, s * 0.6, 0, 0, Math.PI * 2);
  g.fill();
  g.beginPath();
  g.arc(0, -s * 0.55, s * 0.28, 0, Math.PI * 2);
  g.fill();
  g.fillStyle = "rgba(255,255,255,0.85)";
  g.beginPath();
  g.arc(-s * 0.12, -s * 0.62, s * 0.08, 0, Math.PI * 2);
  g.arc(s * 0.12, -s * 0.62, s * 0.08, 0, Math.PI * 2);
  g.fill();
  g.restore();
}

/**
 * Fills a <ul class="legend"> with one row per species:
 * name, current count, a bar of its share, and min/max (and trouble) details.
 */
export function renderLegend(list, species, { details = true } = {}) {
  const total = species.reduce((sum, s) => sum + s.current, 0) || 1;
  const ranked = [...species].sort((a, b) => b.current - a.current || a.id - b.id);
  list.replaceChildren(...ranked.map(s => {
    const color = speciesColor(s.id);
    const bar = el("span");
    bar.style.width = `${(100 * s.current) / total}%`;
    bar.style.background = color;
    const swatch = el("span", { className: "swatch" });
    swatch.style.background = color;
    const trouble = [];
    if (s.stuck) trouble.push(`${s.stuck} stuck`);
    if (s.halted) trouble.push(`${s.halted} stopped`);
    if (s.outOfJumps) trouble.push(`${s.outOfJumps} out of jumps`);
    return el("li", { className: s.current === 0 ? "out" : "" },
      swatch,
      el("span", { className: "name", title: s.name }, s.name),
      el("span", { className: "count" }, String(s.current)),
      el("span", { className: "bar" }, bar),
      details ? el("span", { className: "detail" },
        `min ${s.min} · max ${s.max}${trouble.length ? " · " + trouble.join(", ") : ""}`) : null);
  }));
}
