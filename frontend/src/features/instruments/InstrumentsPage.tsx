import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { InstrumentFilters, InstrumentType } from "../../shared/api/types";
import { InstrumentsTable } from "./InstrumentsTable";
import { SummaryCards } from "../dashboard/SummaryCards";

const PAGE_SIZE = 25;

interface Props {
  type: InstrumentType;
}

const defaultBondFilters = {
  minYield: 7,
  maxDuration: 5,
  minReliability: 1,
  onlyInvestmentGrade: false,
};

export function InstrumentsPage({ type }: Props) {
  const [bond, setBond] = useState(defaultBondFilters);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<string | undefined>(undefined);

  const filters: InstrumentFilters = {
    type,
    page,
    size: PAGE_SIZE,
    sort,
    ...(type === "BOND" ? bond : {}),
  };

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["instruments", filters],
    queryFn: () => instrumentsApi.list(filters),
    placeholderData: keepPreviousData,
  });

  const onSort = (key: string) => {
    setSort((prev) => {
      const [k, d] = (prev ?? "").split(",");
      return k === key && d !== "asc" ? `${key},asc` : `${key},desc`;
    });
    setPage(0);
  };

  const patchBond = (p: Partial<typeof bond>) => {
    setBond((prev) => ({ ...prev, ...p }));
    setPage(0);
  };

  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="page">
      <div className="page-head">
        <h1>{type === "BOND" ? "Облигации" : "Акции"}</h1>
        <a className="btn-secondary" href={instrumentsApi.exportUrl(filters)}>
          Скачать Excel
        </a>
      </div>

      {type === "BOND" && (
        <>
          <SummaryCards summary={data?.summary ?? null} />
          <div className="filters">
            <label>
              Мин. доходность, %
              <input
                type="number"
                step="0.5"
                value={bond.minYield}
                onChange={(e) => patchBond({ minYield: Number(e.target.value) })}
              />
            </label>
            <label>
              Макс. дюрация, лет
              <input
                type="number"
                step="0.5"
                value={bond.maxDuration}
                onChange={(e) => patchBond({ maxDuration: Number(e.target.value) })}
              />
            </label>
            <label>
              Мин. надёжность
              <input
                type="number"
                min="1"
                max="10"
                value={bond.minReliability}
                onChange={(e) => patchBond({ minReliability: Number(e.target.value) })}
              />
            </label>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={bond.onlyInvestmentGrade}
                onChange={(e) => patchBond({ onlyInvestmentGrade: e.target.checked })}
              />
              Только инвестиционный уровень
            </label>
          </div>
        </>
      )}

      {isError && <div className="error">⚠ {(error as Error).message}</div>}
      {isLoading && <div className="loading">Загрузка…</div>}

      {data && (
        <>
          <InstrumentsTable instruments={data.content} columnType={type} sort={sort} onSort={onSort} />
          <div className="pager">
            <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              ← Назад
            </button>
            <span>
              Стр. {page + 1} из {Math.max(1, totalPages)} · всего {total}
            </span>
            <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Вперёд →
            </button>
          </div>
        </>
      )}
    </div>
  );
}
