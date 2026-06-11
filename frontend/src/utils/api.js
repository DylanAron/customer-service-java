const API_BASE = '';

export async function request(url, options = {}) {
  const token = localStorage.getItem('token');
  const headers = { ...options.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  const res = await fetch(`${API_BASE}${url}`, { ...options, headers });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export function connectWebSocket(url, onMessage, onOpen, onClose) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  const wsUrl = `${protocol}//${host}${url}`;
  let ws = null;
  let reconnectTimer = null;
  let shouldReconnect = true;

  function connect() {
    if (!shouldReconnect) return;
    ws = new WebSocket(wsUrl);
    ws.onopen = () => { console.log('WS connected'); onOpen?.(); };
    ws.onmessage = (e) => {
      try { onMessage?.(JSON.parse(e.data)); }
      catch { onMessage?.({ type: 'raw', content: e.data }); }
    };
    ws.onclose = () => {
      console.log('WS disconnected, reconnecting in 3s...');
      onClose?.();
      if (shouldReconnect) {
        reconnectTimer = setTimeout(connect, 3000);
      }
    };
    ws.onerror = () => { ws.close(); };
  }

  connect();

  return {
    close() {
      shouldReconnect = false;
      clearTimeout(reconnectTimer);
      ws?.close();
    },
    send(data) { if (ws?.readyState === WebSocket.OPEN) ws.send(data); },
  };
}
