import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { num, date } from "../../shared/lib/format";
import { DateField } from "../../shared/ui/DateField";
import type { CashDirection } from "./types";

const today = () => new Date().toISOString().slice(0, 10);

/**
 * Пополнения и выводы по счёту. Остаток здесь не вводится и не хранится — он считается
 * на бэкенде из этой истории вместе со сделками и выплатами.
 */
export function CashPanel({ accountId, balance }: { accountId: number; balance: number | undefined }) {
  const qc = useQueryClient();
  const movements = useQuery({
    queryKey: ["account", accountId, "cash"],
    queryFn: () => portfolioApi.listCash(accountId),
  });

  const [direction, setDirection] = useState<CashDirection>("IN");
  const [movedOn, setMovedOn] = useState(today);
  const [amount, setAmount] = useState(0);
  const [note, setNote] = useState("");

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["account", accountId] });
    qc.invalidateQueries({ queryKey: ["accounts"] });
  };

  const add = useMutation({
    mutationFn: () => portfolioApi.addCash(accountId, { direction, movedOn, amount, note: note || null }),
    onSuccess: () => {
      invalidate();
      setAmount(0);
      setNote("");
    },
  });
  const remove = useMutation({
    mutationFn: (movementId: number) => portfolioApi.deleteCash(accountId, movementId),
    onSuccess: invalidate,
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (amount > 0) add.mutate();
  };

  const list = movements.data ?? [];

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Свободные деньги</h2>
        <div className="cash-balance">{num(balance)} ₽</div>
      </div>

      {list.length === 0 && (
        <div className="hint">
          Движений денег нет, поэтому остаток неизвестен и в суммах капитала идёт нулём.
          Добавьте пополнение на сумму, с которой начинали, — появится и остаток,
          и доходность от реально вложенных денег.
        </div>
      )}

      {list.length > 0 && balance != null && balance < 0 && (
        <div className="hint">
          Остаток отрицательный: на сделки и комиссии ушло больше, чем заведено пополнений.
          Проверьте, все ли пополнения внесены.
        </div>
      )}

      <form className="txn-form" onSubmit={submit}>
        <label>
          Операция
          <select value={direction} onChange={(e) => setDirection(e.target.value as CashDirection)}>
            <option value="IN">Пополнение</option>
            <option value="OUT">Вывод</option>
          </select>
        </label>
        <label>
          Дата
          <DateField value={movedOn} onChange={setMovedOn} required />
        </label>
        <label>
          Сумма, ₽
          <input
            type="number"
            min="0"
            step="any"
            value={amount}
            onChange={(e) => setAmount(Number(e.target.value))}
            required
          />
        </label>
        <label>
          Комментарий
          <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="необязательно" />
        </label>
        <button className="btn-primary" type="submit" disabled={add.isPending || amount <= 0}>
          Добавить
        </button>
      </form>

      {list.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Дата</th><th>Операция</th><th className="num">Сумма</th><th>Комментарий</th><th></th>
              </tr>
            </thead>
            <tbody>
              {list.map((m) => (
                <tr key={m.id}>
                  <td>{date(m.movedOn)}</td>
                  <td>{m.direction === "IN" ? "Пополнение" : "Вывод"}</td>
                  <td className="num" style={{ color: m.direction === "IN" ? "#3fb950" : "#f85149" }}>
                    {m.direction === "IN" ? "+" : "−"}
                    {num(m.amount)}
                  </td>
                  <td>{m.note ?? ""}</td>
                  <td className="num">
                    <button className="x" onClick={() => remove.mutate(m.id)}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
