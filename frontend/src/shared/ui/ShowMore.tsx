/**
 * Переключатель «показать все / свернуть» под длинной таблицей.
 * Ничего не решает про сами данные: какие строки показывать в свёрнутом виде — дело вызывающего,
 * потому что «ближайшие» у списка будущих выплат и у графика до погашения означают разное.
 */
export function ShowMore({
  total,
  limit,
  expanded,
  onToggle,
  collapseLabel,
}: {
  total: number;
  limit: number;
  expanded: boolean;
  onToggle: () => void;
  /** Текст сворачивающей кнопки, если «Свернуть до N» недостаточно точно. */
  collapseLabel?: string;
}) {
  if (total <= limit) {
    return null;
  }
  return (
    <button className="btn-secondary table-more" onClick={onToggle}>
      {expanded ? (collapseLabel ?? `Свернуть до ${limit}`) : `Показать все ${total}`}
    </button>
  );
}
