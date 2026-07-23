import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import type { AddTransactionRequest, TradeSide } from "./types";

interface Props {
  accountId: number;
  defaultSecId?: string;
  defaultPrice?: number;
  onDone?: () => void;
}

export function AddTransactionForm({ accountId, defaultSecId = "", defaultPrice, onDone }: Props) {
  const qc = useQueryClient();
  const [secId, setSecId] = useState(defaultSecId);
  const [side, setSide] = useState<TradeSide>("BUY");
  const [tradeDate, setTradeDate] = useState(new Date().toISOString().slice(0, 10));
  const [quantity, setQuantity] = useState(1);
  const [price, setPrice] = useState(defaultPrice ?? 0);
  const [aci, setAci] = useState(0);

  const mutation = useMutation({
    mutationFn: (req: AddTransactionRequest) => portfolioApi.addTransaction(accountId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["account", accountId] });
      onDone?.();
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate({ secId: secId.trim().toUpperCase(), side, tradeDate, quantity, price, aci });
  };

  return (
    <form className="txn-form" onSubmit={submit}>
      <label>
        Код
        <input value={secId} onChange={(e) => setSecId(e.target.value)} required placeholder="SBER" />
      </label>
      <label>
        Тип
        <select value={side} onChange={(e) => setSide(e.target.value as TradeSide)}>
          <option value="BUY">Покупка</option>
          <option value="SELL">Продажа</option>
        </select>
      </label>
      <label>
        Дата
        <input type="date" value={tradeDate} onChange={(e) => setTradeDate(e.target.value)} />
      </label>
      <label>
        Кол-во
        <input type="number" min="0" step="1" value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
      </label>
      <label>
        Цена
        <input type="number" min="0" step="0.01" value={price} onChange={(e) => setPrice(Number(e.target.value))} />
      </label>
      <label>
        НКД
        <input type="number" min="0" step="0.01" value={aci} onChange={(e) => setAci(Number(e.target.value))} />
      </label>
      <button className="btn-primary" type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "…" : "Добавить"}
      </button>
      {mutation.isError && <div className="error">⚠ {(mutation.error as Error).message}</div>}
    </form>
  );
}
