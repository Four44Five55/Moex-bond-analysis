import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { InstrumentFilters } from "../../shared/api/types";
import { SummaryCards } from "./SummaryCards";
import { InstrumentsTable } from "../instruments/InstrumentsTable";

const topBonds: InstrumentFilters = {
  type: "BOND",
  minYield: 10,
  maxDuration: 5,
  minReliability: 7,
  onlyInvestmentGrade: true,
  size: 10,
};

const topShares: InstrumentFilters = { type: "SHARE", size: 10 };

export function DashboardPage() {
  const bonds = useQuery({
    queryKey: ["instruments", topBonds],
    queryFn: () => instrumentsApi.list(topBonds),
  });
  const shares = useQuery({
    queryKey: ["instruments", topShares],
    queryFn: () => instrumentsApi.list(topShares),
  });

  return (
    <div className="page">
      <h1>Дашборд</h1>
      <SummaryCards summary={bonds.data?.summary ?? null} />

      <section className="panel">
        <div className="panel-head">
          <h2>Топ надёжных облигаций с доходностью ≥ 10%</h2>
          <Link to="/bonds" className="link">Все облигации →</Link>
        </div>
        {bonds.data && <InstrumentsTable instruments={bonds.data.content} columnType="BOND" />}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Самые ликвидные акции</h2>
          <Link to="/shares" className="link">Все акции →</Link>
        </div>
        {shares.data && <InstrumentsTable instruments={shares.data.content} columnType="SHARE" />}
      </section>
    </div>
  );
}
