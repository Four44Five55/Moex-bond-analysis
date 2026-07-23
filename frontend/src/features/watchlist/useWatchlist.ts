import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { watchlistApi } from "../../shared/api/endpoints";
import type { Instrument } from "../../shared/api/types";

const KEY = ["watchlist"];

export function useWatchlist() {
  const qc = useQueryClient();
  const query = useQuery({ queryKey: KEY, queryFn: watchlistApi.list });

  const add = useMutation({
    mutationFn: (secId: string) => watchlistApi.add(secId),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
  const remove = useMutation({
    mutationFn: (secId: string) => watchlistApi.remove(secId),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });

  const items: Instrument[] = query.data ?? [];
  const watched = new Set(items.map((i) => i.secId));

  return {
    items,
    isLoading: query.isLoading,
    isWatched: (secId: string) => watched.has(secId),
    toggle: (secId: string) => (watched.has(secId) ? remove.mutate(secId) : add.mutate(secId)),
  };
}
