import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { AddTransactionForm } from "./AddTransactionForm";
import { DonutChart } from "../../shared/charts/DonutChart";
import { LineChart } from "../../shared/charts/LineChart";
import { num, pct, date } from "../../shared/lib/format";
import type { AllocationDimension, IncomeKind } from "./types";

const ALLOCATION_DIMS: Array<{ key: AllocationDimension; label: string }> = [
  { key: "type", label: "Тип" },
  { key: "sector", label: "Сектор" },
  { key: "currency", label: "Валюта" },
];

const HISTORY_PERIODS = [
  { months: 3, label: "3М" },
  { months: 6, label: "6М" },
  { months: 12, label: "1Г" },
  { months: 60, label: "5Л" },
];

const changeColor = (v: number) => (v > 0 ? "#3fb950" : v < 0 ? "#f85149" : "inherit");

const incomeLabel: Record<IncomeKind, string> = {
  COUPON: "Купон",
  AMORTIZATION: "Амортизация",
  DIVIDEND: "Дивиденд",
};

export function AccountPage() {
  const { accountId = "" } = useParams();
  const id = Number(accountId);
  const qc = useQueryClient();

  const [allocBy, setAllocBy] = useState<AllocationDimension>("type");
  const [historyMonths, setHistoryMonths] = useState(12);

  const account = useQuery({ queryKey: ["account", id, "meta"], queryFn: () => portfolioApi.getAccount(id) });
  // Сводка, позиции, выплаты и налог приходят вместе: серверу это один снапшот портфеля вместо пяти
  const overview = useQuery({ queryKey: ["account", id, "overview"], queryFn: () => portfolioApi.overview(id) });
  const allocation = useQuery({
    queryKey: ["account", id, "allocation", allocBy],
    queryFn: () => portfolioApi.allocation(id, allocBy),
  });
  const history = useQuery({
    queryKey: ["account", id, "history", historyMonths],
    queryFn: () => portfolioApi.valueSeries(id, historyMonths),
  });
  const txns = useQuery({ queryKey: ["account", id, "txns"], queryFn: () => portfolioApi.listTransactions(id) });

  const removeTxn = useMutation({
    mutationFn: (txId: number) => portfolioApi.deleteTransaction(id, txId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["account", id] }),
  });

  const s = overview.data?.summary;
  const t = overview.data?.tax;
  const positions = overview.data?.positions;
  const income = overview.data?.income;
  const received = overview.data?.received;

  // Пересоздание массива на каждый рендер заставило бы график пересобираться заново
  const historySeries = useMemo(
    () => [
      {
        label: "Стоимость",
        color: "#3987e5",
        data: (history.data?.points ?? []).map((p) => ({ date: p.date, value: p.value })),
      },
      {
        label: "Вложено",
        color: "#8b94a7",
        dashed: true,
        data: (history.data?.points ?? []).map((p) => ({ date: p.date, value: p.invested })),
      },
    ],
    [history.data]
  );

  const cards = [
    { label: "Вложено", value: num(s?.invested) },
    { label: "Рыночная стоимость", value: num(s?.marketValue) },
    { label: "Нереализованный P&L", value: num(s?.unrealizedPnl), color: changeColor(s?.unrealizedPnl ?? 0) },
    { label: "Реализованный P&L", value: num(s?.realizedPnl), color: changeColor(s?.realizedPnl ?? 0) },
    { label: "Получено выплат", value: num(s?.receivedIncome) },
    { label: "Прогноз дохода (12 мес)", value: num(s?.projectedAnnualIncome) },
    { label: "Оценка налога", value: num(s?.estimatedTax) },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <h1>{account.data?.name ?? "Счёт"}</h1>
        <Link to="/portfolio" className="link">← Все счета</Link>
      </div>

      <div className="cards">
        {cards.map((c) => (
          <div className="card" key={c.label}>
            <div className="card-value" style={{ color: c.color }}>{c.value}</div>
            <div className="card-label">{c.label}</div>
          </div>
        ))}
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Стоимость портфеля</h2>
          <div className="seg">
            {HISTORY_PERIODS.map((p) => (
              <button
                key={p.months}
                className={historyMonths === p.months ? "on" : ""}
                onClick={() => setHistoryMonths(p.months)}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
        {history.data && history.data.points.length === 0 && (
          <div className="empty">График появится после первой сделки.</div>
        )}
        {history.data && history.data.points.length > 0 && (
          <>
            <div className="xirr">
              <span>Годовая доходность (XIRR)</span>
              <b style={{ color: changeColor(history.data.xirrPct ?? 0) }}>{pct(history.data.xirrPct)}</b>
            </div>
            <LineChart series={historySeries} />
            <div className="hint">
              XIRR учитывает все сделки за историю счёта, полученные выплаты и текущую стоимость позиций.
            </div>
          </>
        )}
      </section>

      <section className="panel">
        <h2>Позиции</h2>
        {positions && positions.length === 0 && <div className="empty">Нет открытых позиций.</div>}
        {positions && positions.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Код</th><th>Название</th><th className="num">Кол-во</th><th className="num">Ср. цена</th>
                  <th className="num">Вложено</th><th className="num">Тек. цена</th><th className="num">Стоимость</th>
                  <th className="num">P&L</th><th className="num">P&L %</th>
                </tr>
              </thead>
              <tbody>
                {positions.map((p) => (
                  <tr key={p.secId}>
                    <td><Link to={`/instrument/${p.secId}`} className="link">{p.secId}</Link></td>
                    <td>{p.name}</td>
                    <td className="num">{num(p.quantity, 0)}</td>
                    <td className="num">{num(p.avgCost)}</td>
                    <td className="num">{num(p.invested)}</td>
                    <td className="num">{num(p.currentPrice)}</td>
                    <td className="num">{num(p.marketValue)}</td>
                    <td className="num" style={{ color: changeColor(p.unrealizedPnl) }}>{num(p.unrealizedPnl)}</td>
                    <td className="num" style={{ color: changeColor(p.unrealizedPnl) }}>{pct(p.unrealizedPct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Аллокация</h2>
          <div className="seg">
            {ALLOCATION_DIMS.map((d) => (
              <button
                key={d.key}
                className={allocBy === d.key ? "on" : ""}
                onClick={() => setAllocBy(d.key)}
              >
                {d.label}
              </button>
            ))}
          </div>
        </div>
        {allocation.data && allocation.data.length === 0 && (
          <div className="empty">Нет открытых позиций для разбивки.</div>
        )}
        {allocation.data && allocation.data.length > 0 && (
          <DonutChart data={allocation.data.map((a) => ({ label: a.category, value: a.value }))} />
        )}
      </section>

      <div className="two-col">
        <section className="panel">
          <h2>Доходный календарь</h2>
          {income && income.length === 0 && <div className="empty">Нет предстоящих выплат.</div>}
          {income && income.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Дата</th><th>Бумага</th><th>Тип</th><th className="num">Начислено</th><th className="num">На руки</th></tr>
                </thead>
                <tbody>
                  {income.slice(0, 20).map((e, i) => (
                    <tr key={i}>
                      <td>{date(e.date)}</td>
                      <td>{e.secId}</td>
                      <td>{incomeLabel[e.kind]}</td>
                      <td className="num">{num(e.gross)}</td>
                      <td className="num">{num(e.net)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="hint">Показаны только объявленные MOEX выплаты (купоны, амортизации, дивиденды с назначенной датой отсечки).</div>
        </section>

        <section className="panel">
          <h2>Налоги (НДФЛ)</h2>
          {t && (
            <div className="tax-list">
              <div><span>Реализованная прибыль</span><b>{num(t.realizedPnl)}</b></div>
              <div><span>Освобождено по ЛДВ</span><b>{num(t.ldvExemptGain)}</b></div>
              <div><span>Налог на прибыль</span><b>{num(t.gainTax)}</b></div>
              <div><span>Купоны (12 мес) → налог</span><b>{num(t.couponIncome)} → {num(t.couponTax)}</b></div>
              <div><span>Дивиденды (12 мес) → налог</span><b>{num(t.dividendIncome)} → {num(t.dividendTax)}</b></div>
              <div className="tax-total"><span>Итого налог</span><b>{num(t.totalTax)}</b></div>
              <div className="hint">{t.notes}</div>
            </div>
          )}
        </section>
      </div>

      <section className="panel">
        <h2>Полученные выплаты</h2>
        {received && received.length === 0 && (
          <div className="empty">Пока не было выплат: на даты отсечек по этому счёту позиций не было.</div>
        )}
        {received && received.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Дата</th><th>Бумага</th><th>Тип</th><th className="num">Начислено</th><th className="num">На руки</th></tr>
              </thead>
              <tbody>
                {received.slice(0, 50).map((e, i) => (
                  <tr key={i}>
                    <td>{date(e.date)}</td>
                    <td>{e.secId}</td>
                    <td>{incomeLabel[e.kind]}</td>
                    <td className="num">{num(e.gross)}</td>
                    <td className="num">{num(e.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="hint">Учтены только выплаты, где на дату отсечки у тебя реально была позиция (× количество на тот момент).</div>
      </section>

      <section className="panel">
        <h2>Добавить сделку</h2>
        <AddTransactionForm accountId={id} />
      </section>

      <section className="panel">
        <h2>Сделки</h2>
        {txns.data && txns.data.length === 0 && <div className="empty">Сделок ещё нет.</div>}
        {txns.data && txns.data.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Дата</th><th>Код</th><th>Тип</th><th className="num">Кол-во</th>
                  <th className="num">Цена</th><th className="num">НКД</th><th className="num">Комиссия</th><th></th>
                </tr>
              </thead>
              <tbody>
                {txns.data.map((tx) => (
                  <tr key={tx.id}>
                    <td>{date(tx.tradeDate)}</td>
                    <td>{tx.secId}</td>
                    <td>{tx.side === "BUY" ? "Покупка" : "Продажа"}</td>
                    <td className="num">{num(tx.quantity, 0)}</td>
                    <td className="num">{num(tx.price)}</td>
                    <td className="num">{num(tx.aci)}</td>
                    <td className="num">{num(tx.commission)}</td>
                    <td className="num">
                      <button className="x" onClick={() => removeTxn.mutate(tx.id)}>✕</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
