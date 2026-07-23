import type { Summary } from "../../shared/api/types";
import { num, pct } from "../../shared/lib/format";

export function SummaryCards({ summary }: { summary: Summary | null }) {
  const cards = [
    { label: "Бумаг в выборке", value: summary ? String(summary.count) : "—" },
    { label: "Средняя доходность", value: summary ? pct(summary.averageYield) : "—" },
    { label: "Средняя надёжность", value: summary ? `${num(summary.averageReliability, 1)} / 10` : "—" },
    { label: "Инв. уровень", value: summary ? pct(summary.investmentGradeShare * 100, 0) : "—" },
    { label: "Макс. доходность", value: summary ? pct(summary.maxYield) : "—" },
  ];
  return (
    <div className="cards">
      {cards.map((c) => (
        <div className="card" key={c.label}>
          <div className="card-value">{c.value}</div>
          <div className="card-label">{c.label}</div>
        </div>
      ))}
    </div>
  );
}
