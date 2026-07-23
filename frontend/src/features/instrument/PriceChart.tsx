import { useEffect, useRef } from "react";
import { createChart, ColorType, type IChartApi, type UTCTimestamp } from "lightweight-charts";
import type { Candle } from "../../shared/api/types";

export function PriceChart({ candles }: { candles: Candle[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#90a0bd",
      },
      grid: {
        vertLines: { color: "rgba(43,56,79,0.4)" },
        horzLines: { color: "rgba(43,56,79,0.4)" },
      },
      rightPriceScale: { borderColor: "#2b384f" },
      timeScale: { borderColor: "#2b384f", timeVisible: false },
      height: 380,
      autoSize: true,
    });
    chartRef.current = chart;

    const candleSeries = chart.addCandlestickSeries({
      upColor: "#3fb950",
      downColor: "#f85149",
      borderVisible: false,
      wickUpColor: "#3fb950",
      wickDownColor: "#f85149",
    });
    candleSeries.setData(
      candles.map((c) => ({
        time: c.time as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      }))
    );

    const volumeSeries = chart.addHistogramSeries({
      priceFormat: { type: "volume" },
      priceScaleId: "vol",
      color: "rgba(76,141,255,0.4)",
    });
    volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });
    volumeSeries.setData(
      candles.map((c) => ({ time: c.time as UTCTimestamp, value: c.volume }))
    );

    chart.timeScale().fitContent();

    return () => {
      chart.remove();
      chartRef.current = null;
    };
  }, [candles]);

  return <div className="chart" ref={containerRef} />;
}
