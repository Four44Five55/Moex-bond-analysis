import type { InstrumentPayment } from "../../shared/api/types";
import { num, date } from "../../shared/lib/format";

const KIND_COLOR: Record<InstrumentPayment["kind"], string> = {
  COUPON: "#4c8dff",
  DIVIDEND: "#4c8dff",
  AMORTIZATION: "#f5a623",
};

const VIEW_W = 100;
const VIEW_H = 40;
const GAP = 0.35; // доля ширины ячейки под зазор между столбцами

/** Компактный столбчатый график выплат по датам. Только отображение. */
export function PaymentsChart({ payments }: { payments: InstrumentPayment[] }) {
  const data = [...payments].sort((a, b) => a.date.localeCompare(b.date));
  if (data.length < 2) return null;

  const max = Math.max(...data.map((p) => p.perUnit));
  const cell = VIEW_W / data.length;
  const barW = cell * (1 - GAP);

  return (
    <div className="payments-chart">
      <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} preserveAspectRatio="none" role="img" aria-label="График выплат">
        {data.map((p, idx) => {
          const h = max > 0 ? (p.perUnit / max) * (VIEW_H - 1) : 0;
          return (
            <rect
              key={idx}
              x={idx * cell + (cell - barW) / 2}
              y={VIEW_H - h}
              width={barW}
              height={h}
              fill={KIND_COLOR[p.kind]}
            >
              <title>{`${date(p.date)}: ${num(p.perUnit)}`}</title>
            </rect>
          );
        })}
      </svg>
      <div className="payments-chart-axis">
        <span>{date(data[0].date)}</span>
        <span>{date(data[data.length - 1].date)}</span>
      </div>
    </div>
  );
}
