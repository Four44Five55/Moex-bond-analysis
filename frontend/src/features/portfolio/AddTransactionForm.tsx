import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { AddTransactionRequest, TradeSide } from "./types";
import { InstrumentPicker } from "../instruments/InstrumentPicker";

interface Props {
  accountId: number;
  defaultSecId?: string;
  defaultPrice?: number;
  onDone?: () => void;
}

/** MOEX-код бумаги: латиница/цифры (VTBR, SBER, RU000A…) — в отличие от названия с пробелами/кириллицей. */
const isSecId = (s: string) => /^[A-Za-z0-9]{3,20}$/.test(s);

function minusDays(iso: string, days: number): string {
  const d = new Date(iso);
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function AddTransactionForm({ accountId, defaultSecId = "", defaultPrice, onDone }: Props) {
  const qc = useQueryClient();
  const [secId, setSecId] = useState(defaultSecId);
  const [side, setSide] = useState<TradeSide>("BUY");
  const [tradeDate, setTradeDate] = useState(new Date().toISOString().slice(0, 10));
  const [quantity, setQuantity] = useState(1);
  const [price, setPrice] = useState(defaultPrice ?? 0);
  const [aci, setAci] = useState(0);

  // Цена закрытия на дату сделки (берём последнюю торговую сессию на дату или чуть раньше — окно 7 дней
  // перекрывает выходные/праздники). Свечи облигаций приходят в % номинала — совпадает с полем «Цена».
  const priceQuery = useQuery({
    queryKey: ["trade-price", secId, tradeDate],
    queryFn: () => instrumentsApi.candles(secId, { interval: 24, from: minusDays(tradeDate, 7), to: tradeDate }),
    enabled: isSecId(secId) && !!tradeDate,
  });

  useEffect(() => {
    const cs = priceQuery.data;
    if (cs && cs.length > 0) {
      setPrice(Number(cs[cs.length - 1].close.toFixed(6)));
    }
  }, [priceQuery.data]);

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

  const noCandle = isSecId(secId) && priceQuery.isSuccess && (priceQuery.data?.length ?? 0) === 0;

  return (
    <form className="txn-form" onSubmit={submit}>
      <label>
        Инструмент
        <InstrumentPicker value={secId} onChange={setSecId} required />
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
        <input type="number" min="0" step="any" value={price} onChange={(e) => setPrice(Number(e.target.value))} />
        <span className="field-hint">
          {priceQuery.isFetching ? "загружаю цену на дату…" : noCandle ? "нет котировок на дату — впишите вручную" : "закрытие на дату сделки"}
        </span>
      </label>
      <label>
        НКД
        <input type="number" min="0" step="any" value={aci} onChange={(e) => setAci(Number(e.target.value))} />
      </label>
      <button className="btn-primary" type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "…" : "Добавить"}
      </button>
      {mutation.isError && <div className="error">⚠ {(mutation.error as Error).message}</div>}
    </form>
  );
}
