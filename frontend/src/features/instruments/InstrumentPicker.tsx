import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";
import type { Instrument } from "../../shared/api/types";

interface Props {
  value: string;
  onChange: (secId: string) => void;
  /** Вызывается при выборе бумаги из списка — удобно подставить цену/тип. */
  onPick?: (instrument: Instrument) => void;
  placeholder?: string;
  required?: boolean;
}

/**
 * Поле выбора инструмента с поиском по коду/названию/ISIN и выпадающим списком.
 * Та же логика, что в глобальном поиске, но результат возвращается в форму.
 */
export function InstrumentPicker({
  value,
  onChange,
  onPick,
  placeholder = "Код или название…",
  required,
}: Props) {
  const [term, setTerm] = useState(value);
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Синхронизация с внешним value (сброс формы, предзаполнение).
  useEffect(() => {
    setTerm(value);
  }, [value]);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(term.trim()), 250);
    return () => clearTimeout(id);
  }, [term]);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  const { data } = useQuery({
    queryKey: ["search", debounced],
    queryFn: () => instrumentsApi.search(debounced),
    enabled: open && debounced.length >= 2,
  });
  const results = data?.content ?? [];

  return (
    <div className="picker" ref={boxRef}>
      <input
        value={term}
        placeholder={placeholder}
        autoComplete="off"
        required={required}
        onChange={(e) => {
          setTerm(e.target.value);
          onChange(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
      />
      {open && results.length > 0 && (
        <ul className="search-results">
          {results.map((r) => (
            <li
              key={r.secId}
              onClick={() => {
                setTerm(r.secId);
                onChange(r.secId);
                onPick?.(r);
                setOpen(false);
              }}
            >
              <span className={`tag tag-${r.type.toLowerCase()}`}>
                {r.type === "BOND" ? "Обл" : "Акц"}
              </span>
              <span className="search-name">{r.shortName ?? r.secId}</span>
              <span className="search-sec">{r.secId}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
