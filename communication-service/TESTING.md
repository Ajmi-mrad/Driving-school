# Manual Testing — communication-service

How to run the service locally and exercise both the **REST API** (Swagger) and the
real-time **WebSocket/STOMP chat** (script + interactive page).

> Swagger only covers the REST surface. Sending/receiving chat messages is **WebSocket-only**
> (`/app/chat.send`, `/user/queue/messages`, `/topic/presence`), so the live chat is tested with
> the helper tools in [`tools/`](tools).

## Prerequisites
- Docker + Docker Compose, `jq`
- Node 21+ (built-in `WebSocket`/`fetch`) for `tools/ws-test.mjs`
- Python 3 (to serve `tools/chat.html`)

## 🔗 Links to browse
| URL | What |
|---|---|
| http://localhost:8080/admin | Keycloak Admin Console (`admin` / `admin`) |
| http://localhost:8081/swagger-ui/index.html | **auth-service** Swagger — create users |
| http://localhost:8084/swagger-ui/index.html | **communication-service** Swagger — REST chat API |
| http://localhost:8761 | Eureka discovery dashboard |
| http://localhost:4200 | Interactive chat test page (after you start the server) |

## Test users
| user | role | password |
|---|---|---|
| `monitor` | MONITOR | `Pass123!` |
| `client` | CLIENT | `Pass123!` |
| `owner` | OWNER | `Owner123!` |

---

## Step 1 — Start the auth stack (Keycloak, DBs, discovery, auth-service)
```bash
cd ../auth-service
docker compose up -d --build kc-postgres auth-postgres keycloak discovery-service auth-service
```

## Step 2 — Wait until ready
```bash
until curl -fsS -m 3 http://localhost:8080/realms/auto-ecole/protocol/openid-connect/certs >/dev/null 2>&1; do printf '.'; sleep 3; done; echo " KEYCLOAK READY"
until curl -fsS -m 3 http://localhost:8081/actuator/health >/dev/null 2>&1; do printf '.'; sleep 3; done; echo " AUTH-SERVICE READY"
```

## Step 3 — Build & start communication-service
```bash
cd ../communication-service
docker compose up -d --build communication-service
until curl -fsS -m 3 http://localhost:8084/actuator/health >/dev/null 2>&1; do printf '.'; sleep 3; done; echo " COMM-SERVICE READY"
```

## Step 4 — Allow the Swagger / page origins on the Keycloak client
The Swagger UIs and the chat page call Keycloak's token endpoint from the browser, so their origins
must be on the `auto-ecole-frontend` client's **Web origins**.
> Web origins must be **exact origins** (e.g. `http://localhost:8084`) — a trailing `/*` is only
> valid for *redirect URIs* and breaks CORS ("Failed to fetch").
```bash
KC=http://localhost:8080
ADMIN=$(curl -s -X POST $KC/realms/master/protocol/openid-connect/token -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin | jq -r .access_token)
CID=$(curl -s "$KC/admin/realms/auto-ecole/clients?clientId=auto-ecole-frontend" -H "Authorization: Bearer $ADMIN" | jq -r '.[0].id')
curl -s "$KC/admin/realms/auto-ecole/clients/$CID" -H "Authorization: Bearer $ADMIN" \
 | jq '.webOrigins=(["http://localhost:4200","http://localhost:8081","http://localhost:8084"]) | .redirectUris=((.redirectUris+["http://localhost:8081/*","http://localhost:8084/*","http://localhost:4200/*"])|unique)' > /tmp/fe-client.json
curl -s -o /dev/null -w "client update -> %{http_code}\n" -X PUT "$KC/admin/realms/auto-ecole/clients/$CID" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' --data @/tmp/fe-client.json
```

## Step 5 — Create the test users (monitor + client)
**Option A — admin API (scripted):**
```bash
KC=http://localhost:8080
ADMIN=$(curl -s -X POST $KC/realms/master/protocol/openid-connect/token -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin | jq -r .access_token)

create_user () {  # usage: create_user <username> <ROLE>
  local u=$1 role=$2
  curl -s -o /dev/null -X POST "$KC/admin/realms/auto-ecole/users" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$u\",\"enabled\":true,\"emailVerified\":true,\"email\":\"$u@test.local\",\"firstName\":\"$u\",\"lastName\":\"test\"}"
  local id=$(curl -s "$KC/admin/realms/auto-ecole/users?username=$u&exact=true" -H "Authorization: Bearer $ADMIN" | jq -r '.[0].id')
  curl -s -o /dev/null -X PUT "$KC/admin/realms/auto-ecole/users/$id/reset-password" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
    -d '{"type":"password","value":"Pass123!","temporary":false}'
  local r=$(curl -s "$KC/admin/realms/auto-ecole/roles/$role" -H "Authorization: Bearer $ADMIN")
  curl -s -o /dev/null -X POST "$KC/admin/realms/auto-ecole/users/$id/role-mappings/realm" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "[$r]"
  echo "$u ($role) sub=$id"
}
create_user monitor MONITOR
create_user client  CLIENT
```

**Option B — auth-service Swagger (UI):** open http://localhost:8081/swagger-ui/index.html →
**Authorize** (password flow) as `owner` / `Owner123!` → `POST /api/users`:
```json
{"username":"monitor","email":"monitor@test.local","firstName":"Monitor","lastName":"One","password":"Pass123!","roles":["MONITOR"]}
```
```json
{"username":"client","email":"client@test.local","firstName":"Client","lastName":"One","password":"Pass123!","roles":["CLIENT"]}
```
Copy each response's `keycloakId` (that is the Keycloak `sub`).

## Step 6 — Test the REST API in Swagger
Open http://localhost:8084/swagger-ui/index.html
1. **Authorize** → password flow → `monitor` / `Pass123!` (client_id `auto-ecole-frontend` is prefilled).
2. `POST /api/conversations` body `{"counterpartId":"<client sub>"}` → **201**, note the `id`.
3. `GET /api/conversations` → conversation appears.
4. `GET /api/conversations/unread-count` → `{"count":0}`.
5. `GET /api/conversations/{id}/messages` → empty page.
6. (Negative) re-Authorize as `owner` → `GET /api/conversations` → **403** (only MONITOR/CLIENT).

## Step 7 — Test the live chat (WebSocket/STOMP)

### Option A — automated script
```bash
node tools/ws-test.mjs
# or with explicit users:
MON_USER=monitor CLI_USER=client PASS=Pass123! node tools/ws-test.mjs
```
Expected tail:
```
monitor received via WS : YES Bonjour depuis client 👋
unread-count (monitor)  : 1
history size            : 1
```

### Option B — interactive page (two browser tabs)
```bash
cd tools && python3 -m http.server 4200
```
Open **http://localhost:4200** (use `localhost`, **not** `127.0.0.1` or `file://`) in **two tabs**:
- Tab 1: `monitor` / `Pass123!`, paste a conversationId (from Step 6) → **Connect**
- Tab 2: `client` / `Pass123!`, same conversationId → **Connect**

Type in either tab → the message appears live in **both**; a presence line shows when each connects.

> The page also calls `PATCH /api/conversations/{id}/read` on connect and on each incoming message,
> so opening/viewing a conversation clears its unread count (see Step 8).

## Step 8 — Remaining REST endpoints & edge cases (Swagger)
Authorize as `monitor` / `Pass123!` unless noted.

**Read receipts (`PATCH /{id}/read`) & `unread-count`**
1. `GET /api/conversations/unread-count` → e.g. `{"count":3}` (messages sent *to* you, still unread).
2. `PATCH /api/conversations/{id}/read` → **204**.
3. `GET /api/conversations/unread-count` → `{"count":0}`.
4. `GET /api/conversations/{id}/messages` → the other party's messages now have a `readAt` timestamp.
> "Read" is **explicit**: `GET /{id}/messages` never marks anything read (a GET has no side effects).
> The frontend marks read by calling `PATCH /{id}/read` when the user opens the conversation.

**Presence (`GET /presence`)** — reflects live WebSocket connections.
1. With nobody connected: `GET /presence?ids=<sub>` → `{"online":[]}`.
2. Connect that user in the chat page (Step 7B), then repeat → `{"online":["<sub>"]}`.

**Error paths**
- **403 wrong role:** Authorize as `owner` / `Owner123!` → `GET /api/conversations` → **403** (only MONITOR/CLIENT).
- **404 unknown id:** `GET /api/conversations/00000000-0000-0000-0000-000000000000/messages` → **404**.
- **400 validation:** `POST /api/conversations` with `{"counterpartId":""}` → **400**.
- **403 not a participant:** a third CLIENT user accessing someone else's conversation → **403**.

> **`sort` gotcha (fixed):** the `Pageable` param on `GET /{id}/messages` is annotated with
> `@ParameterObject`, so Swagger renders `page`, `size`, `sort` as separate fields. Leave `sort`
> empty, or use a real property (`sentAt,desc`, `sentAt`, `readAt`). Passing a non-existent property
> (e.g. the old literal `[]`/`string`) yields a 500 from Spring Data.

## Step 9 — Notifications (in-app)
A generic `Notification` (type `NEW_MESSAGE` | `PAYMENT_DUE`) is persisted, pushed live over
`/user/queue/notifications`, and exposed as a feed. Endpoints under `/api/notifications`:
`GET ""` (feed), `GET /unread-count`, `PATCH /{id}/read`, `PATCH /read-all`,
`POST /payment` (OWNER/SECRETARY only).

**Payment notification (admin → client)** — Swagger as `owner`/`Owner123!`:
```
POST /api/notifications/payment
{ "clientId": "<client sub>", "amount": 150.00, "message": "Solde séances à régler" }
```
→ 201. Then as `client`: `GET /api/notifications` shows a `PAYMENT_DUE` item, `GET /unread-count`
= 1, `PATCH /{id}/read` (or `PATCH /read-all`) → 0.
> Stopgap: this only *sends* a reminder — it does **not** track payments/history (future billing service).

**New-message notification (offline only)** — a `NEW_MESSAGE` notification is created for the
recipient **only when they have no live WS session**. Test: with `client` disconnected, send a
message as `monitor` (e.g. a STOMP client), then `GET /api/notifications` as `client` shows a
`NEW_MESSAGE` whose `referenceId` is the conversationId. While `client` is connected, no notification
is created (the live message already arrives).

**Live push** — `tools/chat.html` also subscribes to `/user/queue/notifications` and logs a 🔔 line,
so notifications appear in real time in the connected tab.

**Security** — `POST /payment` as a non-admin → **403**; `PATCH /{id}/read` on a notification that
isn't yours → no-op (treated as not found for that recipient).

---

## Notes & gotchas
- **Read receipts are explicit** — `PATCH /{id}/read` marks the *other* participant's messages read;
  `GET` endpoints never mutate read state. CORS for the REST API is enabled for
  `communication.frontend-origin` (default `http://localhost:4200`) so the SPA can call it.
- **Token** = Keycloak password grant, public client `auto-ecole-frontend`, realm `auto-ecole`,
  endpoint `http://localhost:8080/realms/auto-ecole/protocol/openid-connect/token`. Tokens expire
  in ~5 min — re-authorize / reconnect if a call starts failing.
- **WebSocket endpoints**:
  - `ws://localhost:8084/ws/websocket` = **raw** transport → send **plain STOMP** frames (used by
    `tools/ws-test.mjs` and `tools/chat.html`).
  - `ws://localhost:8084/ws/{server}/{session}/websocket` = **SockJS** transport → frames are JSON
    arrays of strings, so the STOMP NULL terminator is the typeable `\u0000` (handy for Postman).
- **STOMP auth**: the JWT goes in the `Authorization: Bearer <token>` **STOMP header on the CONNECT
  frame** (validated by `StompAuthChannelInterceptor`), not as an HTTP header.
- **Origins**: the WebSocket handshake only allows `http://localhost:4200`; always use `localhost`
  (not `127.0.0.1`) consistently across Keycloak web origins, the page, and the browser.
