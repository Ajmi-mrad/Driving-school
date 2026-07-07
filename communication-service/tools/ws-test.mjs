// Manual WebSocket/STOMP end-to-end test for communication-service.
// Zero dependency: uses Node's built-in global WebSocket + fetch (Node 21+).
// Run:  node tools/ws-test.mjs
//       MON_USER=monitor CLI_USER=client PASS=Pass123! node tools/ws-test.mjs
//
// Flow: get tokens (monitor, client) -> open conversation via REST ->
//       connect both over STOMP -> client sends -> assert monitor receives.

const KC_TOKEN = 'http://localhost:8080/realms/auto-ecole/protocol/openid-connect/token';
const API      = 'http://localhost:8084';
const WS_URL   = 'ws://localhost:8084/ws/websocket'; // raw WebSocket transport (plain STOMP)
const PASS     = process.env.PASS     || 'Pass123!';
const MON_USER = process.env.MON_USER || 'monitor';
const CLI_USER = process.env.CLI_USER || 'client';

// ---------- helpers ----------
const log = (who, ...a) => console.log(`[${who}]`, ...a);

async function getToken(username) {
  const body = new URLSearchParams({
    grant_type: 'password', client_id: 'auto-ecole-frontend', username, password: PASS,
  });
  const r = await fetch(KC_TOKEN, { method: 'POST', body });
  if (!r.ok) throw new Error(`token for ${username} failed: ${r.status} ${await r.text()}`);
  return (await r.json()).access_token;
}

const subOf = (jwt) => JSON.parse(Buffer.from(jwt.split('.')[1], 'base64url')).sub;

function stompFrame(command, headers = {}, body = '') {
  let f = command + '\n';
  for (const [k, v] of Object.entries(headers)) f += `${k}:${v}\n`;
  return f + '\n' + body + '\0';
}

function parseStomp(raw) {
  const nul = raw.indexOf('\0');
  const text = nul >= 0 ? raw.slice(0, nul) : raw;
  const sep = text.indexOf('\n\n');
  const head = text.slice(0, sep < 0 ? text.length : sep);
  const body = sep < 0 ? '' : text.slice(sep + 2);
  const lines = head.split('\n');
  const command = lines.shift();
  const headers = {};
  for (const l of lines) { const i = l.indexOf(':'); if (i > 0) headers[l.slice(0, i)] = l.slice(i + 1); }
  return { command, headers, body };
}

// One authenticated raw-STOMP-over-WebSocket session (/ws/websocket = no SockJS framing).
function connect(name, token, onMessage) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    const send = (frame) => ws.send(frame);        // raw STOMP frame, sent as-is
    const session = { ws, send, name };

    ws.onerror = (e) => reject(new Error(`${name} ws error: ${e.message || e}`));
    ws.onclose = (e) => log(name, `socket closed (${e.code})`);

    ws.onopen = () => send(stompFrame('CONNECT', {  // raw transport: CONNECT immediately on open
      'accept-version': '1.2', 'heart-beat': '0,0', Authorization: `Bearer ${token}`,
    }));

    ws.onmessage = (evt) => {
      const data = typeof evt.data === 'string' ? evt.data : evt.data.toString();
      if (data === '\n' || data === '') return;     // STOMP heartbeat
      for (const raw of data.split('\0')) {         // a frame may carry >1 STOMP frame
        if (!raw.trim()) continue;
        const f = parseStomp(raw);
        if (f.command === 'CONNECTED') { log(name, 'STOMP connected'); resolve(session); }
        else if (f.command === 'MESSAGE') onMessage(f);
        else if (f.command === 'ERROR') { log(name, 'STOMP ERROR:', f.headers.message, f.body); reject(new Error(`${name} STOMP ERROR: ${f.headers.message}`)); }
      }
    };
  });
}

const subscribe = (s, id, dest) => s.send(stompFrame('SUBSCRIBE', { id, destination: dest }));

// ---------- main ----------
const received = { monitor: null };

(async () => {
  log('setup', 'fetching tokens...');
  const monTok = await getToken(MON_USER);
  const cliTok = await getToken(CLI_USER);
  const monSub = subOf(monTok), cliSub = subOf(cliTok);
  log('setup', 'monitor sub =', monSub);
  log('setup', 'client  sub =', cliSub);

  log('setup', 'opening conversation via REST...');
  const cr = await fetch(`${API}/api/conversations`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${monTok}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ counterpartId: cliSub }),
  });
  if (!cr.ok) throw new Error(`create conversation failed: ${cr.status} ${await cr.text()}`);
  const conv = await cr.json();
  log('setup', 'conversationId =', conv.id);

  // monitor connects first and listens
  const monitor = await connect('monitor', monTok, (f) => {
    log('monitor', 'MESSAGE on', f.headers.destination, '->', f.body);
    if (f.headers.destination?.includes('/queue/messages')) received.monitor = JSON.parse(f.body);
  });
  subscribe(monitor, 'sub-msg', '/user/queue/messages');
  subscribe(monitor, 'sub-presence', '/topic/presence');

  // client connects (its CONNECT should trigger a presence event to monitor)
  const client = await connect('client', cliTok, (f) =>
    log('client', 'MESSAGE on', f.headers.destination, '->', f.body));
  subscribe(client, 'sub-msg', '/user/queue/messages');

  await new Promise((r) => setTimeout(r, 400)); // let subscriptions settle

  log('client', 'sending message...');
  client.send(stompFrame('SEND',
    { destination: '/app/chat.send', 'content-type': 'application/json' },
    JSON.stringify({ conversationId: conv.id, content: 'Bonjour depuis client 👋' })));

  await new Promise((r) => setTimeout(r, 1000)); // wait for delivery

  // verify via REST (monitor is the recipient -> 1 unread, 1 message in history)
  const uc = await (await fetch(`${API}/api/conversations/unread-count`,
    { headers: { Authorization: `Bearer ${monTok}` } })).json();
  const hist = await (await fetch(`${API}/api/conversations/${conv.id}/messages`,
    { headers: { Authorization: `Bearer ${monTok}` } })).json();

  console.log('\n================ RESULT ================');
  console.log('monitor received via WS :', received.monitor ? 'YES' : 'NO', received.monitor?.content ?? '');
  console.log('unread-count (monitor)  :', uc.count);
  console.log('history size            :', hist.totalElements ?? hist.numberOfElements);
  console.log('=======================================');

  monitor.ws.close(); client.ws.close();
  process.exit(received.monitor ? 0 : 1);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
