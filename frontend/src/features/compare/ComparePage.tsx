import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { Instrument } from "../../shared/api/types";
import { num, pct, compact, date } from "../../shared/lib/format";

const STORAGE_KEY = "compare-list";

function loadSelected(): string[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");
  } catch {
    return [];
  }
}

const rows: Array<[string, (i: Instrument) => string]> = [
  ["Тип", (i) => (i.type === "BOND" ? "Облигация" : "Акция")],
  ["Цена", (i) => num(i.lastPrice)],
  ["Доходность", (i) => pct(i.yieldToMaturity)],
  ["Дюрация, лет", (i) => num(i.duration)],
  ["Купон", (i) => pct(i.couponRate)],
  ["Погашение", (i) => date(i.maturityDate)],
  ["Надёжность", (i) => (i.reliabilityScore != null ? `${i.reliabilityScore}/10` : "—")],
  ["Изм. за день", (i) => pct(i.dayChangePct)],
  ["Объём", (i) => compact(i.tradeVolume)],
  ["Капитализация", (i) => compact(i.capitalization)],
  ["Сектор", (i) => i.sector ?? "—"],
];

export function ComparePage() {
  const [selected, setSelected] = useState<string[]>(loadSelected);
  const [input, setInput] = useState("");

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(selected));
  }, [selected]);

  const { data, isFetching } = useQuery({
    queryKey: ["compare", selected],
    queryFn: () => instrumentsApi.compare(selected),
    enabled: selected.length > 0,
  });

  const add = () => {
    const code = input.trim().toUpperCase();
    if (code && !selected.includes(code)) setSelected((s) => [...s, code]);
    setInput("");
  };
  const remove = (code: string) => setSelected((s) => s.filter((c) => c !== code));

  const instruments = data ?? [];

  return (
    <div className="page">
      <h1>Сравнение инструментов</h1>
      <div className="compare-add">
        <input
          placeholder="Код бумаги (напр. SBER, SU29008RMFS8)"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
        />
        <button className="btn-primary" onClick={add}>
          Добавить
        </button>
      </div>

      {selected.length === 0 && (
        <div className="empty">Добавьте инструменты по коду, чтобы сравнить их бок-о-бок.</div>
      )}

      {isFetching && <div className="loading">Загрузка…</div>}

      {instruments.length > 0 && (
        <div className="table-wrap">
          <table className="compare-table">
            <thead>
              <tr>
                <th>Показатель</th>
                {instruments.map((i) => (
                  <th key={i.secId}>
                    <div className="cmp-head">
                      <span>{i.shortName ?? i.secId}</span>
                      <button className="x" onClick={() => remove(i.secId)}>
                        ✕
                      </button>
                    </div>
                    <div className="cmp-sec">{i.secId}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(([label, fn]) => (
                <tr key={label}>
                  <td className="cmp-label">{label}</td>
                  {instruments.map((i) => (
                    <td key={i.secId} className="num">
                      {fn(i)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
