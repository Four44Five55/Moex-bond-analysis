import type { ReactNode } from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { Instrument } from "../../shared/api/types";
import { num, pct, compact, date, reliabilityColor, changeColor } from "../../shared/lib/format";
import { PriceChart } from "./PriceChart";
import { useWatchlist } from "../watchlist/useWatchlist";
import { AddToPortfolioPanel } from "../portfolio/AddToPortfolioPanel";

function metrics(i: Instrument): Array<[string, ReactNode]> {
  if (i.type === "BOND") {
    return [
      ["Доходность к погашению", pct(i.yieldToMaturity)],
      ["Дюрация, лет", num(i.duration)],
      ["Ставка купона", pct(i.couponRate)],
      ["Годовой купон", num(i.annualCoupon)],
      ["Номинал", num(i.faceValue)],
      ["Цена, %", num(i.lastPrice)],
      ["Текущая цена", num(i.currentPrice)],
      ["Погашение", date(i.maturityDate)],
      ["След. купон", date(i.nextCouponDate)],
      ["Рейтинг", i.issuerRating ?? "—"],
      ["Инв. уровень", i.investmentGrade ? "Да" : "Нет"],
      ["Оферта/отзыв", `${i.callable ? "call " : ""}${i.puttable ? "put" : ""}`.trim() || "—"],
    ];
  }
  return [
    ["Цена", num(i.lastPrice)],
    ["Изм. за день", pct(i.dayChangePct)],
    ["Объём торгов", compact(i.tradeVolume)],
    ["Капитализация", compact(i.capitalization)],
    ["Размер лота", i.lotSize ?? "—"],
    ["Уровень листинга", i.listLevel ?? "—"],
    ["Сектор", i.sector ?? "—"],
    ["Валюта", i.currency ?? "—"],
  ];
}

export function InstrumentDetailPage() {
  const { secId = "" } = useParams();
  const { isWatched, toggle } = useWatchlist();

  const detail = useQuery({
    queryKey: ["instrument", secId],
    queryFn: () => instrumentsApi.detail(secId),
  });
  const candles = useQuery({
    queryKey: ["candles", secId],
    queryFn: () => instrumentsApi.candles(secId),
    enabled: !!secId,
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

      <section className="panel">
        <div className="panel-head">
          <h2>График цены</h2>
          <Link to="/compare" className="link">Сравнить →</Link>
        </div>
        {candles.isLoading && <div className="loading">Загрузка графика…</div>}
        {candles.data && candles.data.length > 0 && <PriceChart candles={candles.data} />}
        {candles.data && candles.data.length === 0 && (
          <div className="empty">Нет исторических данных по этому инструменту.</div>
        )}
      </section>

      <section className="panel">
        <h2>Показатели</h2>
        <div className="metrics">
          {metrics(i).map(([label, value]) => (
            <div className="metric" key={label}>
              <div className="metric-label">{label}</div>
              <div className="metric-value">{value}</div>
            </div>
          ))}
        </div>
      </section>

      <AddToPortfolioPanel secId={i.secId} price={i.lastPrice ?? undefined} />
    </div>
  );
}
