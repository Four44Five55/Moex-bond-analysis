import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { instrumentsApi } from "../../shared/api/endpoints";

export function GlobalSearch() {
  const [term, setTerm] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

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
    enabled: debounced.length >= 2,
  });

  const results = data?.content ?? [];

  return (
    <div className="search" ref={boxRef}>
      <input
        placeholder="Поиск по коду / названию / ISIN…"
        value={term}
        onChange={(e) => {
          setTerm(e.target.value);
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
                navigate(`/instrument/${r.secId}`);
                setOpen(false);
                setTerm("");
              }}
            >
              <span className={`tag tag-${r.type.toLowerCase()}`}>{r.type === "BOND" ? "Обл" : "Акц"}</span>
              <span className="search-name">{r.shortName ?? r.secId}</span>
              <span className="search-sec">{r.secId}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
