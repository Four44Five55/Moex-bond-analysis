export interface BarSegment {
  label: string;
  value: number;
  color: string;
}

export interface BarDatum {
  /** Ключ выбора: по нему панель узнаёт, какой столбец нажали. */
  key: string;
  label: string;
  /** Вторая строка подписи — например год у января; рисуется приглушённо. */
  sublabel?: string;
  total: number;
  segments: BarSegment[];
}

const TRACK_PX = 170;

/**
 * Столбчатый график с накоплением по видам внутри столбца. Только отображение:
 * и суммы столбца, и его части приходят посчитанными с бэкенда.
 * <p>
 * Столбцы — кнопки: клик выбирает период, детали которого показывает вызывающая панель.
 */
export function BarChart({
  data,
  selectedKey,
  onSelect,
  format,
}: {
  data: BarDatum[];
  selectedKey?: string;
  onSelect?: (key: string) => void;
  format: (v: number) => string;
}) {
  const max = Math.max(...data.map((d) => d.total), 0);

  return (
    <div className="bars" style={{ ["--bars-track" as string]: `${TRACK_PX}px` }}>
      {data.map((d) => {
        const height = max > 0 ? (d.total / max) * TRACK_PX : 0;
        const title = d.total > 0
          ? `${d.label}: ${format(d.total)}\n`
            + d.segments.map((s) => `${s.label}: ${format(s.value)}`).join("\n")
          : `${d.label}: выплат нет`;

        return (
          <button
            type="button"
            key={d.key}
            className={`bar-col${selectedKey === d.key ? " on" : ""}`}
            onClick={() => onSelect?.(d.key)}
            title={title}
            aria-label={title.replace(/\n/g, "; ")}
            aria-pressed={selectedKey === d.key}
          >
            <span className="bar-value">{d.total > 0 ? format(d.total) : ""}</span>
            <span className="bar-track">
              <span className="bar-stack" style={{ height }}>
                {d.segments.map((s) => (
                  <span
                    key={s.label}
                    className="bar-seg"
                    style={{ height: `${(s.value / d.total) * 100}%`, background: s.color }}
                  />
                ))}
              </span>
            </span>
            <span className="bar-label">
              {d.label}
              {d.sublabel && <em>{d.sublabel}</em>}
            </span>
          </button>
        );
      })}
    </div>
  );
}
