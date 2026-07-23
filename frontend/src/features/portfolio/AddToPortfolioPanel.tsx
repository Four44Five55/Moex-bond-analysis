import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { AddTransactionForm } from "./AddTransactionForm";

interface Props {
  secId: string;
  price?: number;
}

export function AddToPortfolioPanel({ secId, price }: Props) {
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
      <h2>Добавить в портфель</h2>
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
        <AddTransactionForm accountId={accountId} defaultSecId={secId} defaultPrice={price} onDone={() => setDone(true)} />
      )}
      {done && <div className="hint">Сделка добавлена. <Link to={`/portfolio/${accountId}`} className="link">Открыть счёт →</Link></div>}
    </section>
  );
}
