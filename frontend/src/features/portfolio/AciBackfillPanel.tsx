import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { num, date } from "../../shared/lib/format";
import type { AciBackfill } from "./types";

/**
 * Дозаполнение НКД в сделках, заведённых до появления автоподстановки.
 * <p>
 * Сначала примерка, потом запись: НКД меняет себестоимость позиций, налоговую базу и остаток
 * денег, поэтому пользователь должен увидеть, что именно изменится, до того как это случится.
 */
export function AciBackfillPanel({ accountId }: { accountId: number }) {
  const qc = useQueryClient();
  const [preview, setPreview] = useState<AciBackfill | null>(null);
  const [applied, setApplied] = useState(false);

  const run = useMutation({
    mutationFn: (dryRun: boolean) => portfolioApi.backfillAci(accountId, dryRun),
    onSuccess: (result) => {
      setPreview(result);
      setApplied(!result.dryRun);
      if (!result.dryRun) {
        qc.invalidateQueries({ queryKey: ["account", accountId] });
        qc.invalidateQueries({ queryKey: ["accounts"] });
      }
    },
  });

  const nothingToDo = preview != null && preview.candidates === 0;

  return (
    <div className="aci-backfill">
      <button
        className="btn-secondary"
        onClick={() => { setApplied(false); run.mutate(true); }}
        disabled={run.isPending}
      >
        {run.isPending ? "…" : "Подставить НКД в старые сделки"}
      </button>

      {run.isError && <div className="error">⚠ {(run.error as Error).message}</div>}

      {nothingToDo && (
        <div className="hint">Все сделки по облигациям уже с НКД — дозаполнять нечего.</div>
      )}

      {preview && preview.candidates > 0 && (
        <>
          <div className="hint">
            {applied ? "Заполнено" : "Будет заполнено"}: <b>{preview.filled}</b> из{" "}
            {preview.candidates} сделок без НКД, стоимость этих сделок изменится на{" "}
            <b>{num(preview.costDelta)} ₽</b>.
            {preview.unknown > 0 && (
              <> По {preview.unknown} биржа НКД не назвала — они остались как есть.</>
            )}
          </div>

          {preview.rows.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Дата</th><th>Код</th><th>Название</th><th>Тип</th><th className="num">Кол-во</th>
                    <th className="num">НКД</th><th>Котировка за</th><th className="num">Итого</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.map((r) => (
                    <tr key={r.txId}>
                      <td>{date(r.tradeDate)}</td>
                      <td>{r.secId}</td>
                      <td>{r.secName ?? "—"}</td>
                      <td>{r.side === "BUY" ? "Покупка" : "Продажа"}</td>
                      <td className="num">{num(r.quantity, 0)}</td>
                      <td className="num">{num(r.aci)}</td>
                      <td>{date(r.quotedOn)}</td>
                      <td className="num">{num(r.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!applied && preview.filled > 0 && (
            <button
              className="btn-primary"
              onClick={() => run.mutate(false)}
              disabled={run.isPending}
            >
              Применить к {preview.filled} сделкам
            </button>
          )}
          {applied && <div className="hint">Готово — суммы на странице пересчитаны.</div>}
        </>
      )}
    </div>
  );
}
