export const num = (v: number | null | undefined, digits = 2): string =>
  v == null || !Number.isFinite(v)
    ? "—"
    : v.toLocaleString("ru-RU", { minimumFractionDigits: digits, maximumFractionDigits: digits });

export const pct = (v: number | null | undefined, digits = 2): string =>
  v == null ? "—" : `${num(v, digits)}%`;

export const compact = (v: number | null | undefined): string => {
  if (v == null || !Number.isFinite(v)) return "—";
  return new Intl.NumberFormat("ru-RU", { notation: "compact", maximumFractionDigits: 1 }).format(v);
};

export const date = (iso: string | null | undefined): string => {
  if (!iso) return "—";
  const [y, m, d] = iso.split("-");
  return d ? `${d}.${m}.${y}` : iso;
};

/** Цвет фона для балла надёжности (1..10). */
export const reliabilityColor = (score: number | null | undefined): string => {
  if (score == null) return "#3a465e";
  if (score <= 4) return "#e5484d";
  if (score <= 6) return "#f5a623";
  if (score === 7) return "#e8c400";
  if (score <= 9) return "#5bb862";
  return "#2f9e44";
};

export const changeColor = (v: number | null | undefined): string =>
  v == null ? "inherit" : v > 0 ? "#3fb950" : v < 0 ? "#f85149" : "inherit";
