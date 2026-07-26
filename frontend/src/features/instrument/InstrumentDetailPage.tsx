import { type ReactNode } from "react";
import { useParams, useSearchParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { DividendInfo, Instrument } from "../../shared/api/types";
import { num, pct, compact, date, daysUntil, reliabilityColor, changeColor } from "../../shared/lib/format";
import { PriceChart } from "./PriceChart";
import { PaymentsChart } from "./PaymentsChart";
import { ReturnsStrip } from "./ReturnsStrip";
import { useWatchlist } from "../watchlist/useWatchlist";
import { AddToPortfolioPanel } from "../portfolio/AddToPortfolioPanel";

function metrics(i: Instrument, div?: DividendInfo | null): Array<[string, ReactNode]> {
  if (i.type === "BOND") {
    return [
      ["Доходность к погашению", pct(i.yieldToMaturity)],
      ["Текущая доходность", pct(i.currentYield)],
      ["Дюрация, лет", num(i.duration)],
      ["Ставка купона", pct(i.couponRate)],
      ["Годовой купон", num(i.annualCoupon)],
      ["НКД", num(i.accruedInterest)],
      ["Номинал", num(i.faceValue)],
      ["Цена, %", num(i.lastPrice)],
      ["Текущая цена", num(i.currentPrice)],
      ["Погашение", `${date(i.maturityDate)} · ${daysUntil(i.maturityDate)}`],
      ["Оферта", i.offerDate ? `${date(i.offerDate)} · ${daysUntil(i.offerDate)}` : "—"],
      ["След. купон", date(i.nextCouponDate)],
      ["Рейтинг", i.issuerRating ?? "—"],
      ["Инв. уровень", i.investmentGrade ? "Да" : "Нет"],
    ];
  }
  return [
    ["Цена", num(i.lastPrice)],
    ["Изм. за день", pct(i.dayChangePct)],
    ["Див. доходность (12м)", div?.dividendYield != null ? pct(div.dividendYield) : "—"],
    ["Дивиденды за 12м", div?.trailingSum != null ? num(div.trailingSum) : "—"],
    ["Объём торгов", compact(i.tradeVolume)],
    ["Капитализация", compact(i.capitalization)],
    ["Размер лота", i.lotSize ?? "—"],
    ["Уровень листинга", i.listLevel ?? "—"],
    ["Сектор", i.sector ?? "—"],
    ["Валюта", i.currency ?? "—"],
  ];
}

const RANGES: Array<{ key: string; months: number }> = [
  { key: "1М", months: 1 },
  { key: "3М", months: 3 },
  { key: "6М", months: 6 },
  { key: "1Г", months: 12 },
  { key: "3Г", months: 36 },
  { key: "Макс", months: 0 },
];

const INTERVALS: Array<{ key: string; code: number }> = [
  { key: "Д", code: 24 },
  { key: "Н", code: 7 },
  { key: "М", code: 31 },
];

const paymentLabel: Record<"COUPON" | "AMORTIZATION" | "DIVIDEND", string> = {
  COUPON: "Купон",
  AMORTIZATION: "Амортизация",
  DIVIDEND: "Дивиденд",
};

/** ISO-дата начала диапазона (months=0 → «Макс», глубоко в прошлое). */
function rangeFrom(months: number): string {
  if (months === 0) return "2010-01-01";
  const d = new Date();
  d.setMonth(d.getMonth() - months);
  return d.toISOString().slice(0, 10);
}

/** Значение из URL, только если оно из списка допустимых, иначе — дефолт. */
function pick(raw: string | null, allowed: number[], fallback: number): number {
  const n = Number(raw);
  return raw != null && allowed.includes(n) ? n : fallback;
}

export function InstrumentDetailPage() {
  const { secId = "" } = useParams();
  const { isWatched, toggle } = useWatchlist();
  const [searchParams, setSearchParams] = useSearchParams();

  const months = pick(searchParams.get("range"), RANGES.map((r) => r.months), 12);
  const interval = pick(searchParams.get("iv"), INTERVALS.map((iv) => iv.code), 24);

  const setParam = (key: string, value: number) => {
    const next = new URLSearchParams(searchParams);
    next.set(key, String(value));
    setSearchParams(next, { replace: true });
  };

  const detail = useQuery({
    queryKey: ["instrument", secId],
    queryFn: () => instrumentsApi.detail(secId),
  });
  const candles = useQuery({
    queryKey: ["candles", secId, interval, months],
    queryFn: () => instrumentsApi.candles(secId, { interval, from: rangeFrom(months) }),
    enabled: !!secId,
  });
  const payments = useQuery({
    queryKey: ["payments", secId],
    queryFn: () => instrumentsApi.payments(secId),
    enabled: !!secId,
  });
  const performance = useQuery({
    queryKey: ["performance", secId],
    queryFn: () => instrumentsApi.performance(secId),
    enabled: !!secId,
  });
  const dividend = useQuery({
    queryKey: ["dividendYield", secId],
    queryFn: () => instrumentsApi.dividendYield(secId),
    enabled: !!secId && detail.data?.type === "SHARE",
  });

  if (detail.isLoading) return <div className="loading">Загрузка…</div>;
  if (detail.isError) return <div className="error">⚠ {(detail.error as Error).message}</div>;
  const i = detail.data!;

  return (
    <div className="page">
      <div className="detail-head">
        <div>
          <div className="detail-title">
            <span className={`tag tag-${i.type.toLowerCase()}`}>{i.type === "BOND" ? "Облигация" : "Акция"}</span>
            <h1>{i.shortName ?? i.secId}</h1>
            <button className={isWatched(i.secId) ? "star-btn on" : "star-btn"} onClick={() => toggle(i.secId)}>
              ★
            </button>
          </div>
          <div className="detail-sub">
            {i.secId} · {i.isin ?? "—"} · {i.fullName ?? ""}
          </div>
        </div>
        <div className="detail-price">
          <div className="price">{num(i.lastPrice)}</div>
          {i.type === "SHARE" && (
            <div style={{ color: changeColor(i.dayChangePct) }}>{pct(i.dayChangePct)}</div>
          )}
          {i.type === "BOND" && (
            <div className="reliability" style={{ background: reliabilityColor(i.reliabilityScore) }}>
              Надёжность {i.reliabilityScore}
            </div>
          )}
        </div>
      </div>

      {performance.data && <ReturnsStrip data={performance.data} />}

      <section className="panel">
        <div className="panel-head">
          <h2>График цены</h2>
          <div className="chart-controls">
            <div className="seg-group">
              <span className="seg-label">Интервал</span>
              <div className="seg">
                {INTERVALS.map((iv) => (
                  <button
                    key={iv.code}
                    className={interval === iv.code ? "on" : ""}
                    onClick={() => setParam("iv", iv.code)}
                  >
                    {iv.key}
                  </button>
                ))}
              </div>
            </div>
            <div className="seg-group">
              <span className="seg-label">Период</span>
              <div className="seg">
                {RANGES.map((r) => (
                  <button
                    key={r.key}
                    className={months === r.months ? "on" : ""}
                    onClick={() => setParam("range", r.months)}
                  >
                    {r.key}
                  </button>
                ))}
              </div>
            </div>
            <Link to="/compare" className="link">Сравнить →</Link>
          </div>
        </div>
        {candles.isLoading && <div className="loading">Загрузка графика…</div>}
        {candles.data && candles.data.length > 0 && <PriceChart candles={candles.data} />}
        {candles.data && candles.data.length === 0 && (
          <div className="empty">Нет исторических данных за выбранный период.</div>
        )}
      </section>

      <section className="panel">
        <h2>Показатели</h2>
        <div className="metrics">
          {metrics(i, dividend.data).map(([label, value]) => (
            <div className="metric" key={label}>
              <div className="metric-label">{label}</div>
              <div className="metric-value">{value}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <h2>{i.type === "BOND" ? "Купоны и амортизация" : "История дивидендов"}</h2>
        {payments.isLoading && <div className="loading">Загрузка…</div>}
        {payments.data && payments.data.length === 0 && (
          <div className="empty">Нет данных о выплатах в MOEX.</div>
        )}
        {payments.data && payments.data.length > 0 && (
          <>
            <PaymentsChart payments={payments.data} />
            <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Дата</th><th>Тип</th>
                  <th className="num">На {i.type === "BOND" ? "облигацию" : "акцию"}</th>
                </tr>
              </thead>
              <tbody>
                {payments.data.slice(0, 40).map((p, idx) => (
                  <tr key={idx}>
                    <td>{date(p.date)}</td>
                    <td>{paymentLabel[p.kind]}</td>
                    <td className="num">{num(p.perUnit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          </>
        )}
      </section>

      <AddToPortfolioPanel secId={i.secId} price={i.lastPrice ?? undefined} />
    </div>
  );
}
