import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { num, pct, date } from "../../shared/lib/format";
import { DateField } from "../../shared/ui/DateField";
import type { Account, Deposit, DepositTaxBreakdown, PayoutPeriod } from "./types";

const PERIODS: Array<{ value: PayoutPeriod; label: string }> = [
  { value: "MONTHLY", label: "Ежемесячно" },
  { value: "QUARTERLY", label: "Ежеквартально" },
  { value: "SEMIANNUAL", label: "Раз в полгода" },
  { value: "ANNUAL", label: "Раз в год" },
  { value: "AT_MATURITY", label: "В конце срока" },
];

const periodLabel = (p: PayoutPeriod) => PERIODS.find((x) => x.value === p)?.label ?? p;

const today = () => new Date().toISOString().slice(0, 10);
const inAYear = () => {
  const d = new Date();
  d.setFullYear(d.getFullYear() + 1);
  return d.toISOString().slice(0, 10);
};

/**
 * Банковские вклады. Привязаны к клиенту, а не к брокерскому счёту: у вклада нет сделок,
 * бумаг и котировок — его стоимость на любую дату считает бэкенд по ставке и условиям.
 */
export function DepositsPanel({
  deposits,
  tax,
  accounts,
}: {
  deposits: Deposit[];
  tax: DepositTaxBreakdown | undefined;
  /** Счета клиента — из какого сняты деньги на вклад. */
  accounts: Account[];
}) {
  const qc = useQueryClient();

  const [open, setOpen] = useState(false);
  const [sourceAccountId, setSourceAccountId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [principal, setPrincipal] = useState(0);
  const [ratePct, setRatePct] = useState(20);
  const [openedOn, setOpenedOn] = useState(today);
  const [maturesOn, setMaturesOn] = useState(inAYear);
  const [capitalized, setCapitalized] = useState(false);
  const [payoutPeriod, setPayoutPeriod] = useState<PayoutPeriod>("AT_MATURITY");

  const invalidate = () => qc.invalidateQueries({ queryKey: ["accounts"] });

  const create = useMutation({
    mutationFn: () =>
      portfolioApi.createDeposit({
        name: name.trim(),
        principal,
        ratePct,
        openedOn,
        maturesOn,
        capitalized,
        payoutPeriod,
        sourceAccountId,
      }),
    onSuccess: () => {
      invalidate();
      setName("");
      setPrincipal(0);
      setOpen(false);
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => portfolioApi.deleteDeposit(id),
    onSuccess: invalidate,
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (name.trim() && principal > 0) create.mutate();
  };

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Вклады</h2>
        <button className="btn-secondary" onClick={() => setOpen((v) => !v)}>
          {open ? "Отмена" : "+ Вклад"}
        </button>
      </div>

      {open && (
        <form className="txn-form" onSubmit={submit}>
          <label>
            Банк / название
            <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="Вклад «Накопительный»" />
          </label>
          <label>
            Сумма, ₽
            <input type="number" min="0" step="any" value={principal} onChange={(e) => setPrincipal(Number(e.target.value))} required />
          </label>
          <label>
            Ставка, %
            <input type="number" min="0" step="any" value={ratePct} onChange={(e) => setRatePct(Number(e.target.value))} required />
          </label>
          <label>
            Открыт
            <DateField value={openedOn} onChange={setOpenedOn} required />
          </label>
          <label>
            Закрывается
            <DateField value={maturesOn} onChange={setMaturesOn} required />
          </label>
          <label>
            Проценты
            <select value={payoutPeriod} onChange={(e) => setPayoutPeriod(e.target.value as PayoutPeriod)}>
              {PERIODS.map((p) => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </label>
          <label>
            Деньги с
            <select
              value={sourceAccountId ?? ""}
              onChange={(e) => setSourceAccountId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">со стороны</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
            <span className="field-hint">
              {sourceAccountId == null
                ? "новые деньги в портфеле"
                : "спишется с этого счёта, не считается новым вложением"}
            </span>
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={capitalized} onChange={(e) => setCapitalized(e.target.checked)} />
            С капитализацией
          </label>
          <button className="btn-primary" type="submit" disabled={create.isPending}>
            Добавить
          </button>
        </form>
      )}

      {deposits.length === 0 && <div className="empty">Вкладов пока нет.</div>}

      {deposits.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Вклад</th><th className="num">Сумма</th><th>Деньги с</th><th className="num">Ставка</th>
                  <th>Проценты</th><th>Срок</th><th className="num">Сейчас стоит</th>
                  <th className="num">Заработано</th><th>Ближайшая выплата</th><th></th>
                </tr>
              </thead>
              <tbody>
                {deposits.map((d) => (
                  <tr key={d.id} className={d.matured ? "muted-row" : undefined}>
                    <td>{d.name}</td>
                    <td className="num">{num(d.principal)}</td>
                    <td>{d.sourceAccountName ?? "со стороны"}</td>
                    <td className="num">{pct(d.ratePct)}</td>
                    <td>{periodLabel(d.payoutPeriod)}{d.capitalized ? ", с кап." : ""}</td>
                    <td>{date(d.openedOn)} — {date(d.maturesOn)}</td>
                    <td className="num">{num(d.currentValue)}</td>
                    <td className="num" style={{ color: "#3fb950" }}>{num(d.accruedNotPaid)}</td>
                    <td>{d.matured ? "закрыт" : date(d.nextPayoutOn)}</td>
                    <td className="num">
                      <button className="x" onClick={() => remove.mutate(d.id)}>✕</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {tax && (
            <div className="tax-list">
              <div><span>Проценты за год</span><b>{num(tax.interestIncome)}</b></div>
              <div><span>Ключевая ставка ЦБ (макс. за год)</span><b>{pct(tax.keyRatePct)}</b></div>
              <div><span>Необлагаемый вычет</span><b>{num(tax.deduction)}</b></div>
              <div className="tax-total"><span>НДФЛ с процентов</span><b>{num(tax.tax)}</b></div>
              <div className="hint">{tax.notes}</div>
            </div>
          )}
          <div className="hint">
            Вычет по вкладам даётся один на все вклады сразу, поэтому налог считается от годовой
            суммы процентов целиком, а не по каждому вкладу отдельно. Ставка берётся из веб-сервиса
            ЦБ — это максимум на 1-е число каждого месяца года, поэтому меняться она может не чаще
            раза в месяц. Данные обновляются не реже раза в 12 часов; если ЦБ недоступен, берётся
            запасное значение из конфигурации бэкенда.
          </div>
        </>
      )}
    </section>
  );
}
