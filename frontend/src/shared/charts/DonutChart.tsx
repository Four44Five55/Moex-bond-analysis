import { num, pct, compact } from "../lib/format";

export interface DonutDatum {
  label: string;
  value: number;
}

/** Категориальная палитра (тёмная тема) из dataviz-навыка — валидирована на adjacent-парах. */
const PALETTE = [
  "#3987e5", // blue
  "#d95926", // orange
  "#199e70", // aqua
  "#c98500", // yellow
  "#d55181", // magenta
  "#008300", // green
  "#9085e9", // violet
];
const OTHER_COLOR = "#6b7280";
const MAX_SLICES = PALETTE.length; // сверх — сворачиваем в «Прочее»

const R_OUTER = 46;
const R_INNER = 29;

function polar(r: number, angle: number): [number, number] {
  return [r * Math.cos(angle), r * Math.sin(angle)];
}

function annularSector(start: number, end: number): string {
  const [x1, y1] = polar(R_OUTER, start);
  const [x2, y2] = polar(R_OUTER, end);
  const [x3, y3] = polar(R_INNER, end);
  const [x4, y4] = polar(R_INNER, start);
  const large = end - start > Math.PI ? 1 : 0;
  return `M ${x1} ${y1} A ${R_OUTER} ${R_OUTER} 0 ${large} 1 ${x2} ${y2} `
    + `L ${x3} ${y3} A ${R_INNER} ${R_INNER} 0 ${large} 0 ${x4} ${y4} Z`;
}

/** Кольцевая диаграмма долей с легендой. Только отображение; данные — от бэкенда. */
export function DonutChart({ data }: { data: DonutDatum[] }) {
  const sorted = [...data].filter((d) => d.value > 0).sort((a, b) => b.value - a.value);
  if (sorted.length === 0) return null;

  const head = sorted.slice(0, MAX_SLICES);
  const tail = sorted.slice(MAX_SLICES).reduce((s, d) => s + d.value, 0);
  const slices = tail > 0 ? [...head, { label: "Прочее", value: tail }] : head;

  const total = slices.reduce((s, d) => s + d.value, 0);
  const color = (idx: number) => (idx < MAX_SLICES ? PALETTE[idx] : OTHER_COLOR);

  let acc = -Math.PI / 2; // старт сверху, по часовой стрелке
  const arcs = slices.map((d, idx) => {
    const start = acc;
    const end = acc + (d.value / total) * Math.PI * 2;
    acc = end;
    const percent = (d.value / total) * 100;
    const single = slices.length === 1;
    return { d, idx, start, end, percent, single };
  });

  return (
    <div className="donut">
      <svg viewBox="-50 -50 100 100" role="img" aria-label="Диаграмма аллокации">
        {arcs.map(({ d, idx, start, end, percent, single }) =>
          single ? (
            <circle
              key={idx}
              cx={0}
              cy={0}
              r={(R_OUTER + R_INNER) / 2}
              fill="none"
              stroke={color(idx)}
              strokeWidth={R_OUTER - R_INNER}
            >
              <title>{`${d.label}: ${num(d.value)} (${pct(percent)})`}</title>
            </circle>
          ) : (
            <path key={idx} d={annularSector(start, end)} fill={color(idx)} stroke="var(--panel)" strokeWidth={0.8}>
              <title>{`${d.label}: ${num(d.value)} (${pct(percent)})`}</title>
            </path>
          )
        )}
        <text className="donut-center" x={0} y={0} textAnchor="middle" dominantBaseline="central">
          {compact(total)}
        </text>
      </svg>
      <ul className="donut-legend">
        {arcs.map(({ d, idx, percent }) => (
          <li key={idx}>
            <span className="swatch" style={{ background: color(idx) }} />
            <span className="donut-label">{d.label}</span>
            <span className="donut-num">{pct(percent)}</span>
            <span className="donut-num muted">{num(d.value)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
