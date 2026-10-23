import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { DonutChart } from "../../shared/charts/DonutChart";
import { DepositsPanel } from "./DepositsPanel";
import { PaymentCalendarPanel } from "./PaymentCalendarPanel";
import { DefaultLossPanel } from "./DefaultLossPanel";
import { num, pct, changeColor } from "../../shared/lib/format";
import type { AccountRequest, AccountType, ConsolidatedOverview, IisType } from "./types";

/** Считать есть что, если где-то есть бумаги, деньги или вклад — одного пополнения уже достаточно. */
const hasSomething = (o: ConsolidatedOverview) =>
  o.totals.positionsCount > 0 || o.totals.cashBalance !== 0 || o.deposits.length > 0;

const accountKind = (type: AccountType, iisType: IisType | null) =>
  type === "IIS" ? `ИИС ${iisType ?? ""}`.trim() : "Брокерский";

export function PortfoliosPage() {
  const qc = useQueryClient();
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: portfolioApi.listAccounts });
  const overview = useQuery({ queryKey: ["accounts", "consolidated"], queryFn: portfolioApi.consolidated });

  const [name, setName] = useState("");
  const [type, setType] = useState<AccountType>("BROKERAGE");
  const [iisType, setIisType] = useState<IisType>("B");
  const [commission, setCommission] = useState(0.05);
  const [taxRate, setTaxRate] = useState(13);

  const create = useMutation({
    mutationFn: (req: AccountRequest) => portfolioApi.createAccount(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["accounts"] });
      setName("");
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => portfolioApi.deleteAccount(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["accounts"] }),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate({
      name: name.trim(),
      type,
      iisType: type === "IIS" ? iisType : null,
      defaultCommissionPct: commission,
      baseTaxRate: taxRate,
    });
  };

  const list = accounts.data ?? [];
  const o = overview.data;
  const t = o?.totals;
  // Строки счетов из сводки несут суммы; список счетов — источник правды по составу и порядку
  const lineById = new Map((o?.accounts ?? []).map((a) => [a.id, a]));

  // Три величины подряд, которые нельзя складывать: внесённое → капитал → разница между ними.
  // Всё остальное — расшифровка того, из чего капитал состоит и откуда взялась прибыль.
  const cards = [
    { label: "Внесено своих денег", value: num(t?.externalIn) },
    { label: "Всего капитала", value: num(o?.grandTotal) },
    {
      label: "Заработано за всё время",
      value: num(t?.totalProfit),
      color: changeColor(t?.totalProfit),
    },
    { label: "Доходность (XIRR)", value: pct(o?.xirrPct), color: changeColor(o?.xirrPct) },
    { label: "Бумаги по рынку", value: num(t?.marketValue) },
    { label: "Вклады", value: num(o?.depositsValue) },
    { label: "Свободные деньги", value: num(t?.cashBalance), color: changeColor(t?.cashBalance) },
    {
      label: "Переоценка бумаг",
      value: num(t?.unrealizedPnl),
      color: changeColor(t?.unrealizedPnl),
    },
    { label: "Получено выплат", value: num(t?.receivedIncome) },
    { label: "Прогноз дохода (12 мес)", value: num(t?.projectedAnnualIncome) },
    { label: "Оценка налога", value: num(t?.estimatedTax) },
  ];

  return (
    <div className="page">
      <h1>Портфель</h1>

      {o && !hasSomething(o) && (
        <div className="empty">
          Пока считать нечего: заведите пополнение, сделку или вклад — сводка появится сразу.
        </div>
      )}

      {o && t && hasSomething(o) && (
        <>
          <div className="cards">
            {cards.map((c) => (
              <div className="card" key={c.label}>
                <div className="card-value" style={{ color: c.color }}>{c.value}</div>
                <div className="card-label">{c.label}</div>
              </div>
            ))}
          </div>

          <div className="hint">
            {t.externalIn == null ? (
              <>
                «Внесено своих денег» не посчитать: хотя бы по одному счёту со сделками нет
                движений денег. Заведите пополнения — появится и внесённое, и заработанное.
              </>
            ) : (
              <>
                Заработано = капитал − внесённое, и это единственная строка, которую можно читать
                как итог. «Получено выплат» складывать с капиталом нельзя: реинвестированные
                выплаты уже превратились в бумаги и сидят в их стоимости.
              </>
            )}
          </div>

          <div className="two-col">
            <section className="panel">
              <h2>Капитал по счетам</h2>
              <DonutChart data={o.byAccount.map((a) => ({ label: a.category, value: a.value }))} />
            </section>
            <section className="panel">
              <h2>Капитал по типам бумаг</h2>
              <DonutChart data={o.byType.map((a) => ({ label: a.category, value: a.value }))} />
            </section>
          </div>

          {/* Ближайшие выплаты живут в календаре: тот же список, но с помесячными суммами */}
          <PaymentCalendarPanel />

          <DefaultLossPanel />
        </>
      )}

      <DepositsPanel deposits={o?.deposits ?? []} tax={o?.depositTax} accounts={list} />

      <section className="panel">
        <h2>Мои счета</h2>
        {list.length === 0 && <div className="empty">Счетов пока нет — создайте первый ниже.</div>}
        <div className="account-grid">
          {list.map((a) => {
            const line = lineById.get(a.id);
            return (
              <div className="account-card" key={a.id}>
                <div className="account-head">
                  <Link to={`/portfolio/${a.id}`} className="account-name">
                    {a.name}
                  </Link>
                  <button className="x" onClick={() => remove.mutate(a.id)} title="Удалить">
                    ✕
                  </button>
                </div>
                <div className="account-value">{num(line?.totalValue)}</div>
                <div className="account-pnl" style={{ color: changeColor(line?.unrealizedPnl) }}>
                  {num(line?.unrealizedPnl)} · {pct(line?.unrealizedPct)}
                </div>
                <div className="account-meta">
                  бумаги {num(line?.marketValue)} · деньги {num(line?.cashBalance)}
                </div>
                <div className="account-meta">
                  {accountKind(a.type, a.iisType)} · позиций {line?.positionsCount ?? 0} · комиссия{" "}
                  {a.defaultCommissionPct}% · НДФЛ {a.baseTaxRate}%
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel">
        <h2>Новый счёт</h2>
        <form className="txn-form" onSubmit={submit}>
          <label>
            Название
            <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="Брокер / ИИС" />
          </label>
          <label>
            Тип
            <select value={type} onChange={(e) => setType(e.target.value as AccountType)}>
              <option value="BROKERAGE">Брокерский</option>
              <option value="IIS">ИИС</option>
            </select>
          </label>
          {type === "IIS" && (
            <label>
              Тип ИИС
              <select value={iisType} onChange={(e) => setIisType(e.target.value as IisType)}>
                <option value="A">А (вычет на взносы)</option>
                <option value="B">Б (освобождение дохода)</option>
              </select>
            </label>
          )}
          <label>
            Комиссия, %
            <input type="number" min="0" step="any" value={commission} onChange={(e) => setCommission(Number(e.target.value))} />
          </label>
          <label>
            Ставка НДФЛ, %
            <input type="number" min="0" step="any" value={taxRate} onChange={(e) => setTaxRate(Number(e.target.value))} />
          </label>
          <button className="btn-primary" type="submit" disabled={create.isPending}>
            Создать
          </button>
        </form>
      </section>
    </div>
  );
}
