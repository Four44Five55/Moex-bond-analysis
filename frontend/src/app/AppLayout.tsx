import { NavLink, Outlet } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { instrumentsApi } from "../shared/api/endpoints";
import { GlobalSearch } from "../features/instruments/GlobalSearch";

const navItems = [
  { to: "/portfolio", label: "Портфель" },
  { to: "/dashboard", label: "Дашборд" },
  { to: "/bonds", label: "Облигации" },
  { to: "/shares", label: "Акции" },
  { to: "/watchlist", label: "Избранное" },
  { to: "/compare", label: "Сравнение" },
];

export function AppLayout() {
  const queryClient = useQueryClient();
  const refresh = useMutation({
    mutationFn: instrumentsApi.refresh,
    onSuccess: () => queryClient.invalidateQueries(),
  });

  return (
    <div className="layout">
      <header className="topbar">
        <div className="brand">
          <span className="logo">◆</span> Инвестиционный блокнот
        </div>
        <nav className="nav">
          {navItems.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="topbar-right">
          <GlobalSearch />
          <button className="btn-primary" onClick={() => refresh.mutate()} disabled={refresh.isPending}>
            {refresh.isPending ? "Обновление…" : "Обновить с MOEX"}
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
