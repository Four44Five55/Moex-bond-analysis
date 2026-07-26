import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import type { AccountRequest, AccountType, IisType } from "./types";

export function PortfoliosPage() {
  const qc = useQueryClient();
  const accounts = useQuery({ queryKey: ["accounts"], queryFn: portfolioApi.listAccounts });

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

  return (
    <div className="page">
      <h1>Портфель</h1>

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

      <section className="panel">
        <h2>Мои счета</h2>
        {list.length === 0 && <div className="empty">Счетов пока нет — создайте первый выше.</div>}
        <div className="account-grid">
          {list.map((a) => (
            <div className="account-card" key={a.id}>
              <div className="account-head">
                <Link to={`/portfolio/${a.id}`} className="account-name">
                  {a.name}
                </Link>
                <button className="x" onClick={() => remove.mutate(a.id)} title="Удалить">
                  ✕
                </button>
              </div>
              <div className="account-meta">
                {a.type === "IIS" ? `ИИС ${a.iisType}` : "Брокерский"} · комиссия {a.defaultCommissionPct}% · НДФЛ {a.baseTaxRate}%
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
