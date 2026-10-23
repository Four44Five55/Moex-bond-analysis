import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "./AppLayout";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { InstrumentsPage } from "../features/instruments/InstrumentsPage";
import { InstrumentDetailPage } from "../features/instrument/InstrumentDetailPage";
import { ComparePage } from "../features/compare/ComparePage";
import { WatchlistPage } from "../features/watchlist/WatchlistPage";
import { PortfoliosPage } from "../features/portfolio/PortfoliosPage";
import { AccountPage } from "../features/portfolio/AccountPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      // Блокнот открывается на своём портфеле; рыночный дашборд — отдельная вкладка
      { index: true, element: <Navigate to="/portfolio" replace /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "bonds", element: <InstrumentsPage type="BOND" /> },
      { path: "shares", element: <InstrumentsPage type="SHARE" /> },
      { path: "instrument/:secId", element: <InstrumentDetailPage /> },
      { path: "compare", element: <ComparePage /> },
      { path: "watchlist", element: <WatchlistPage /> },
      { path: "portfolio", element: <PortfoliosPage /> },
      { path: "portfolio/:accountId", element: <AccountPage /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
