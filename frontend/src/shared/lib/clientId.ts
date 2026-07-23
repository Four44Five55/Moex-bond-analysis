const KEY = "moex-client-id";

/** Стабильный анонимный идентификатор клиента для watchlist (хранится в localStorage). */
export function getClientId(): string {
  let id = localStorage.getItem(KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(KEY, id);
  }
  return id;
}
