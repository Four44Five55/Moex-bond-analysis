import { useWatchlist } from "./useWatchlist";
import { InstrumentsTable } from "../instruments/InstrumentsTable";

export function WatchlistPage() {
  const { items, isLoading } = useWatchlist();

  if (isLoading) return <div className="loading">Загрузка…</div>;

  const bonds = items.filter((i) => i.type === "BOND");
  const shares = items.filter((i) => i.type === "SHARE");

  return (
    <div className="page">
      <h1>Избранное</h1>
      {items.length === 0 && (
        <div className="empty">
          Список пуст. Нажимайте ★ в таблицах или на странице инструмента, чтобы добавить.
        </div>
      )}
      {bonds.length > 0 && (
        <section className="panel">
          <h2>Облигации</h2>
          <InstrumentsTable instruments={bonds} columnType="BOND" />
        </section>
      )}
      {shares.length > 0 && (
        <section className="panel">
          <h2>Акции</h2>
          <InstrumentsTable instruments={shares} columnType="SHARE" />
        </section>
      )}
    </div>
  );
}
