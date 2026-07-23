import { getClientId } from "../lib/clientId";

const BASE = "/api/v1";

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Ошибка ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) message = body.error;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function apiGet<T>(path: string): Promise<T> {
  return fetch(`${BASE}${path}`).then((r) => handle<T>(r));
}

export function apiPost<T>(path: string, body?: unknown, withClient = false): Promise<T> {
  return fetch(`${BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(withClient ? { "X-Client-Id": getClientId() } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  }).then((r) => handle<T>(r));
}

export function apiDelete<T>(path: string, withClient = false): Promise<T> {
  return fetch(`${BASE}${path}`, {
    method: "DELETE",
    headers: withClient ? { "X-Client-Id": getClientId() } : {},
  }).then((r) => handle<T>(r));
}

export function apiGetWithClient<T>(path: string): Promise<T> {
  return fetch(`${BASE}${path}`, {
    headers: { "X-Client-Id": getClientId() },
  }).then((r) => handle<T>(r));
}

export { BASE as API_BASE };
