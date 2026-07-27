import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { AddTransactionForm } from "./AddTransactionForm";
import type { TradeSide } from "./types";

interface Props {
  secId: string;
  price?: number;
  /** Сторона сделки, выбранная кнопкой «Купить» / «Продать» у названия бумаги. */
  side?: TradeSide;
}

export function AddToPortfolioPanel({ secId, price, side = "BUY" }: Props) {
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: portfolioApi.listAccounts });
  const [accountId, setAccountId] = useState<number | null>(null);
  const [done, setDone] = useState(false);

  const list = accounts.data ?? [];

  if (list.length === 0) {
    return (
      <section className="panel">
        <h2>В портфель</h2>
        <div className="empty">
          Сначала <Link to="/portfolio" className="link">создайте счёт</Link>, чтобы добавлять сделки.
        </div>
      </section>
    );
  }

  return (
    <section className="panel">
      <h2>{side === "SELL" ? "Продать из портфеля" : "Добавить в портфель"}</h2>
      <div className="filters" style={{ marginBottom: 12 }}>
        <label>
          Счёт
          <select
            value={accountId ?? ""}
            onChange={(e) => {
              setAccountId(e.target.value ? Number(e.target.value) : null);
              setDone(false);
            }}
          >
            <option value="">— выберите —</option>
            {list.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      {accountId && !done && (
        <AddTransactionForm
          accountId={accountId}
          defaultSecId={secId}
          defaultPrice={price}
          side={side}
          onDone={() => setDone(true)}
        />
      )}
      {done && <div className="hint">Сделка добавлена. <Link to={`/portfolio/${accountId}`} className="link">Открыть счёт →</Link></div>}
    </section>
  );
}
