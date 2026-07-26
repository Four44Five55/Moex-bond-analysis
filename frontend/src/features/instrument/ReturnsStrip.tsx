import type { InstrumentPerformance } from "../../shared/api/types";
import { num, pct, changeColor } from "../../shared/lib/format";

const PERIODS: Array<{ key: string; field: keyof InstrumentPerformance }> = [
  { key: "1Д", field: "change1d" },
  { key: "1Н", field: "change1w" },
  { key: "1М", field: "change1m" },
  { key: "1Г", field: "change1y" },
  { key: "YTD", field: "changeYtd" },
];

/** Полоса доходностей за периоды + 52-недельный диапазон цены. Только отображение. */
export function ReturnsStrip({ data }: { data: InstrumentPerformance }) {
  return (
    <div className="returns-strip">
      {PERIODS.map(({ key, field }) => {
        const v = data[field] as number | null;
        return (
          <div className="return-tile" key={key}>
            <div className="return-label">{key}</div>
            <div className="return-value" style={{ color: changeColor(v) }}>
              {v != null && v > 0 ? "+" : ""}
              {pct(v)}
            </div>
          </div>
        );
      })}
      <div className="return-tile range">
        <div className="return-label">52 недели</div>
        <div className="return-value">
          {num(data.low52w)} – {num(data.high52w)}
        </div>
      </div>
    </div>
  );
}
