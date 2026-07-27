import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { portfolioApi } from "./api";
import { BarChart, type BarDatum } from "../../shared/charts/BarChart";
import { compact, date, num } from "../../shared/lib/format";
import type { CalendarMonth, IncomeKind } from "./types";

const HORIZONS = [6, 12, 24, 36];

const KIND_LABEL: Record<IncomeKind, string> = {
  COUPON: "Купоны",
  AMORTIZATION: "Амортизация",
  DIVIDEND: "Дивиденды",
  INTEREST: "Проценты по вкладам",
};

/**
 * Категориальная палитра из dataviz-навыка, проверена на тёмном фоне панели.
 * Цвет закреплён за видом выплаты, а не за её местом в столбце: иначе смена горизонта
 * перекрашивала бы то же самое в другое.
 */
const KIND_COLOR: Record<IncomeKind, string> = {
  COUPON: "#3987e5",
  AMORTIZATION: "#d95926",
  DIVIDEND: "#199e70",
  INTEREST: "#c98500",
};

const MONTH_SHORT = new Intl.DateTimeFormat("ru-RU", { month: "short" });
const MONTH_LONG = new Intl.DateTimeFormat("ru-RU", { month: "long", year: "numeric" });

const asDate = (month: string) => new Date(`${month}-01T00:00:00`);
const monthShort = (month: string) => MONTH_SHORT.format(asDate(month)).replace(".", "");
const monthLong = (month: string) => MONTH_LONG.format(asDate(month)).replace(" г.", "");

const toBar = (m: CalendarMonth): BarDatum => ({
  key: m.month,
  label: monthShort(m.month),
  // Год подписываем у января — иначе на горизонте в три года не понять, какой это «май»
  sublabel: m.month.endsWith("-01") ? `’${m.month.slice(2, 4)}` : undefined,
  total: m.gross,
  segments: m.byKind.map((k) => ({
    label: KIND_LABEL[k.kind],
    value: k.gross,
    color: KIND_COLOR[k.kind],
  })),
});

/**
 * Календарь выплат по всему капиталу: столбцы по месяцам, детали выбранного месяца списком.
 * Считает бэкенд (`/portfolio/calendar`), здесь только выбор месяца и форматирование.
 */
export function PaymentCalendarPanel() {
  const [months, setMonths] = useState(12);
  const [selected, setSelected] = useState<string | null>(null);

  const calendar = useQuery({
    queryKey: ["portfolio", "calendar", months],
    queryFn: () => portfolioApi.calendar(months),
  });

  const data = calendar.data;
  const bars = useMemo(() => (data?.months ?? []).map(toBar), [data]);
  const firstPaidMonth = useMemo(
    () => data?.months.find((m) => m.count > 0)?.month ?? null,
    [data]
  );

  // Смена горизонта не должна оставлять выбранным месяц, которого в ответе больше нет
  useEffect(() => {
    setSelected((prev) =>
      prev && data?.months.some((m) => m.month === prev) ? prev : firstPaidMonth
    );
  }, [data, firstPaidMonth]);

  const activeMonth = selected ?? firstPaidMonth;
  const monthPayments = (data?.payments ?? []).filter((p) => p.date.startsWith(`${activeMonth}-`));
  const next = data?.payments[0];

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Календарь выплат</h2>
        <div className="seg">
          {HORIZONS.map((h) => (
            <button key={h} className={h === months ? "on" : ""} onClick={() => setMonths(h)}>
              {h} мес
            </button>
          ))}
        </div>
      </div>

      {calendar.isLoading && <div className="empty">Считаем выплаты…</div>}
      {calendar.isError && <div className="empty">Не удалось загрузить календарь.</div>}

      {data && data.payments.length === 0 && (
        <div className="empty">
          Объявленных выплат на ближайшие {months} мес нет. Дивиденды попадают в календарь только
          после того, как эмитент назначит дату отсечки.
        </div>
      )}

      {data && data.payments.length > 0 && (
        <>
          <div className="metrics">
            <div className="metric">
              <div className="metric-label">Поступит за {months} мес</div>
              <div className="metric-value">{num(data.totalGross)}</div>
            </div>
            <div className="metric">
              <div className="metric-label">Из них доход</div>
              <div className="metric-value">{num(data.incomeGross)}</div>
            </div>
            {data.principalReturn > 0 && (
              <div className="metric">
                <div className="metric-label">Возврат номинала</div>
                <div className="metric-value">{num(data.principalReturn)}</div>
              </div>
            )}
            <div className="metric">
              <div className="metric-label">На руки после налога</div>
              <div className="metric-value">{num(data.totalNet)}</div>
            </div>
            <div className="metric">
              <div className="metric-label">В среднем в месяц</div>
              <div className="metric-value">{num(data.averageMonthlyNet)}</div>
            </div>
            <div className="metric">
              <div className="metric-label">Ближайшая выплата</div>
              <div className="metric-value">
                {next ? `${date(next.date)} · ${num(next.gross)}` : "—"}
              </div>
            </div>
          </div>

          <div className="hint">
            Это прогноз будущих поступлений, а не начисленное: в календарь входят выплаты
            с сегодняшнего дня и дальше, поэтому текущий месяц показывает только то, что ещё
            не выплачено.
            {data.principalReturn > 0 && (
              <> Амортизация — возврат вложенного номинала, а не доход, поэтому она вынесена
              из строки «Из них доход» отдельно.</>
            )}
          </div>

          <ul className="chart-legend">
            {(Object.keys(KIND_LABEL) as IncomeKind[])
              .filter((k) => data.months.some((m) => m.byKind.some((s) => s.kind === k)))
              .map((k) => (
                <li key={k}>
                  <span className="swatch" style={{ background: KIND_COLOR[k] }} />
                  {KIND_LABEL[k]}
                </li>
              ))}
          </ul>

          <BarChart data={bars} selectedKey={activeMonth ?? undefined} onSelect={setSelected} format={compact} />

          <h3 className="bars-detail-title">
            {activeMonth ? monthLong(activeMonth) : "Месяц"} · {monthPayments.length}{" "}
            {monthPayments.length === 1 ? "выплата" : "выплат"}
          </h3>
          {monthPayments.length === 0 && <div className="empty">В этом месяце выплат нет.</div>}
          {monthPayments.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Дата</th><th>Бумага</th><th>Название</th><th>Счёт / вклад</th><th>Тип</th>
                    <th className="num">К выплате</th><th className="num">На руки</th>
                  </tr>
                </thead>
                <tbody>
                  {monthPayments.map((e, i) => (
                    <tr key={i}>
                      <td>{date(e.date)}</td>
                      <td>{e.secId ?? "—"}</td>
                      <td>{e.secName ?? "—"}</td>
                      <td>{e.accountName}</td>
                      <td>{KIND_LABEL[e.kind]}</td>
                      <td className="num">
                        {e.estimate && (
                          <span className="estimate-mark" title="Плавающая ставка: оценка по последней известной">≈</span>
                        )}
                        {num(e.gross)}
                      </td>
                      <td className="num">{num(e.net)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.payments.some((p) => p.estimate) && (
            <div className="hint">
              Знаком <b>≈</b> помечены купоны облигаций с плавающей ставкой: биржа объявляет её
              в начале каждого периода, поэтому дальние выплаты посчитаны по последней известной
              ставке и изменятся вместе с ней.
            </div>
          )}
        </>
      )}
    </section>
  );
}
