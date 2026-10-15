// Small helpers shared by the pages: HTTP calls, live updates, toasts, storage.

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

/** Calls the server API; returns the parsed JSON (or null), throws ApiError on failure. */
export async function api(method, path, body, headers = {}) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: { "Content-Type": "application/json", ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError("Can't reach the BugsWorld server. Check your network connection.", 0, null);
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }
  if (!response.ok) {
    const message = data && data.error ? data.error : `Request failed (${response.status})`;
    throw new ApiError(message, response.status, data);
  }
  return data;
}

/**
 * Follows the live game over a WebSocket, reconnecting automatically.
 * onFrame(game) gets every frame; onConnection(true/false) reports the link.
 */
export function followLiveGame(onFrame, onConnection = () => {}) {
  let delay = 500;
  function connect() {
    const scheme = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${scheme}://${location.host}/ws/game`);
    ws.onopen = () => { delay = 500; onConnection(true); };
    ws.onmessage = event => onFrame(JSON.parse(event.data));
    ws.onclose = () => {
      onConnection(false);
      setTimeout(connect, delay);
      delay = Math.min(delay * 2, 5000);
    };
  }
  connect();
}

/** Shows a short message in the corner of the page. */
export function toast(message, kind = "") {
  let box = document.querySelector(".toasts");
  if (!box) {
    box = document.createElement("div");
    box.className = "toasts";
    box.setAttribute("role", "status");
    document.body.append(box);
  }
  const item = document.createElement("div");
  item.className = `toast ${kind}`;
  item.textContent = message;
  box.append(item);
  setTimeout(() => item.remove(), kind === "err" ? 7000 : 3500);
}

/** localStorage that never throws (private windows, blocked storage). */
export const store = {
  get(key, fallback = null) {
    try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
  },
  set(key, value) {
    try { localStorage.setItem(key, value); } catch { /* storage unavailable */ }
  },
  remove(key) {
    try { localStorage.removeItem(key); } catch { /* storage unavailable */ }
  },
};

/** Creates an element: el("button", {className: "primary", onclick}, "Text", child, ...). */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "dataset") Object.assign(node.dataset, value);
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  for (const child of children) {
    if (child !== null && child !== undefined && child !== false) node.append(child);
  }
  return node;
}

/** "2 min ago" style text for an ISO time. */
export function timeAgo(iso) {
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 45) return "just now";
  if (seconds < 90) return "1 min ago";
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}
