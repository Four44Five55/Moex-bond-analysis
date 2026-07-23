import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import type { Instrument, InstrumentType } from "../../shared/api/types";
import { num, pct, compact, date, reliabilityColor, changeColor } from "../../shared/lib/format";
import { useWatchlist } from "../watchlist/useWatchlist";

interface Column {
  key: string;
  title: string;
  sortable?: boolean;
  numeric?: boolean;
  render: (i: Instrument) => ReactNode;
}

const bondColumns: Column[] = [
  { key: "shortName", title: "Название", sortable: true, render: (i) => i.shortName ?? i.secId },
  { key: "yieldToMaturity", title: "Доходность", sortable: true, numeric: true, render: (i) => pct(i.yieldToMaturity) },
  { key: "duration", title: "Дюрация, лет", sortable: true, numeric: true, render: (i) => num(i.duration) },
  { key: "couponRate", title: "Купон", sortable: true, numeric: true, render: (i) => pct(i.couponRate) },
  { key: "maturityDate", title: "Погашение", sortable: true, render: (i) => date(i.maturityDate) },
  {
    key: "reliabilityScore",
    title: "Надёжность",
    sortable: true,
    numeric: true,
    render: (i) => (
      <span className="reliability" style={{ background: reliabilityColor(i.reliabilityScore) }}>
        {i.reliabilityScore ?? "—"}
      </span>
    ),
  },
];

const shareColumns: Column[] = [
  { key: "shortName", title: "Название", sortable: true, render: (i) => i.shortName ?? i.secId },
  { key: "lastPrice", title: "Цена", sortable: true, numeric: true, render: (i) => num(i.lastPrice) },
  {
    key: "dayChangePct",
    title: "Изм. за день",
    sortable: true,
    numeric: true,
    render: (i) => <span style={{ color: changeColor(i.dayChangePct) }}>{pct(i.dayChangePct)}</span>,
  },
  { key: "tradeVolume", title: "Объём", sortable: true, numeric: true, render: (i) => compact(i.tradeVolume) },
  { key: "capitalization", title: "Капитализация", sortable: true, numeric: true, render: (i) => compact(i.capitalization) },
  { key: "listLevel", title: "Листинг", numeric: true, render: (i) => i.listLevel ?? "—" },
];

interface Props {
  instruments: Instrument[];
  columnType: InstrumentType;
  sort?: string;
  onSort?: (key: string) => void;
}

export function InstrumentsTable({ instruments, columnType, sort, onSort }: Props) {
  const navigate = useNavigate();
  const { isWatched, toggle } = useWatchlist();
  const columns = columnType === "SHARE" ? shareColumns : bondColumns;
  const [sortKey, sortDir] = (sort ?? "").split(",");

  if (instruments.length === 0) {
    return <div className="empty">Ничего не найдено.</div>;
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th className="star-col"></th>
            <th>Код</th>
            {columns.map((c) => (
              <th
                key={c.key}
                className={`${c.numeric ? "num" : ""} ${c.sortable && onSort ? "sortable" : ""}`}
                onClick={() => c.sortable && onSort && onSort(c.key)}
              >
                {c.title}
                {sortKey === c.key ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {instruments.map((i) => (
            <tr key={i.secId}>
              <td className="star-col" onClick={() => toggle(i.secId)}>
                <span className={isWatched(i.secId) ? "star on" : "star"}>★</span>
              </td>
              <td className="code-cell" onClick={() => navigate(`/instrument/${i.secId}`)}>
                <span className="sec">{i.secId}</span>
                <span className="isin">{i.isin}</span>
              </td>
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={c.numeric ? "num" : ""}
                  onClick={() => navigate(`/instrument/${i.secId}`)}
                >
                  {c.render(i)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
