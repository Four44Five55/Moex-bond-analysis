import { useEffect, useRef } from "react";
import { createChart, ColorType, LineStyle, type IChartApi } from "lightweight-charts";
import { compact } from "../lib/format";

export interface LineSeriesSpec {
  label: string;
  color: string;
  /** Пунктир — для опорных линий (например, вложенного капитала). */
  dashed?: boolean;
  /** Точки ряда; date — ISO yyyy-MM-dd, lightweight-charts принимает такую строку как есть. */
  data: Array<{ date: string; value: number }>;
}

/**
 * Многорядный линейный график с легендой. Только отображение: ряды приходят
 * посчитанными с бэкенда. Переиспользуется везде, где нужна динамика во времени.
 */
export function LineChart({ series, height = 320 }: { series: LineSeriesSpec[]; height?: number }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#90a0bd",
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: "rgba(43,56,79,0.4)" },
        horzLines: { color: "rgba(43,56,79,0.4)" },
      },
      rightPriceScale: { borderColor: "#2b384f" },
      timeScale: { borderColor: "#2b384f", timeVisible: false },
      localization: { priceFormatter: (v: number) => compact(v) },
      height,
      autoSize: true,
    });
    chartRef.current = chart;

    for (const spec of series) {
      const line = chart.addLineSeries({
        color: spec.color,
        lineWidth: 2,
        lineStyle: spec.dashed ? LineStyle.Dashed : LineStyle.Solid,
        priceLineVisible: false,
        lastValueVisible: false,
      });
      line.setData(spec.data.map((p) => ({ time: p.date, value: p.value })));
    }

    chart.timeScale().fitContent();

    return () => {
      chart.remove();
      chartRef.current = null;
    };
  }, [series, height]);

  return (
    <div>
      <ul className="chart-legend">
        {series.map((s) => (
          <li key={s.label}>
            <span
              className="swatch"
              style={{ background: s.dashed ? "transparent" : s.color, outline: `2px solid ${s.color}` }}
            />
            {s.label}
          </li>
        ))}
      </ul>
      <div className="chart" style={{ height }} ref={containerRef} />
    </div>
  );
}
