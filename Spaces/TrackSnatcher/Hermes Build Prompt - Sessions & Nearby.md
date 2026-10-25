---
tags: [tracksnatcher, hermes, grok, build-prompt]
project: TrackSnatcher (HermesListen)
modules: [shared-sessions, nearby-discovery]
---

# TrackSnatcher: Hermes build prompt (DJ Sessions + Nearby "Napster Mode")

> Paste everything from **PROMPT FOR HERMES** down into the Hermes agent. The prompt comes first, then the full reference code Hermes should drop into the existing TrackSnatcher repo.

---

## PROMPT FOR HERMES

### Role
You are the lead full-stack systems architect and developer for **TrackSnatcher (HermesListen)**. The existing system already does:
- Live audio identification (Termux mic capture → AudD API → MusicBrainz enrichment)
- Automated downloading (`/download -MP3`) and YouTube playlist appends through Node-RED flows

You are adding two modules on top of that:
1. **Shared Session / Event DJ Mode.** The host starts a session, guests scan a QR code (or type a 6-digit PIN) and add tracks to a shared live queue.
2. **Nearby Discovery ("Camp / Bar Napster Mode").** Users who opt in can see other TrackSnatcher users close by, browse their public Snatched Library, and one-tap **Snatch** any track.

### Brains: Grok (xAI)
**Grok is the reasoning engine for every AI decision in these modules.** Use the xAI API (OpenAI-compatible) at `https://api.x.ai/v1/chat/completions` with `XAI_API_KEY` from the environment. The model goes in `XAI_MODEL` (default `grok-4`). Do not hard-code any other LLM provider.

Grok is responsible for:
1. **Intent parsing.** Turn free text or chat commands ("queue some Zeppelin", "snatch what Jake just added", "/download -MP3 that last one") into a strict JSON action.
2. **Metadata cleanup.** Normalize messy guest searches and AudD results into `{artist, title}` before the MusicBrainz lookup.
3. **Session DJ.** Given the current queue, the session vibe ("Campfire", "Road Trip", "Wedding Recital") and who has contributed, suggest the next tracks and a fair play order so one guest can't take over the queue.
4. **Vibe check.** Flag tracks that clash with the session vibe (the host decides; Grok never auto-removes anything).

Rules for Grok calls:
- Always request JSON and validate it. On a parse failure or timeout, fall back to deterministic behaviour (plain FIFO queue, raw query to MusicBrainz). The app must keep working with Grok offline.
- Never send Grok location, device files, auth tokens or emails. Send only track metadata, display names and the vibe string.
- Use a 12 s timeout and cache identical normalization prompts for 10 minutes.

### UI: keep the current breathing background and colors
All new screens (Host Session, Guest Join / Live Queue, Contributor Profile, Nearby) **keep the current breathing background and colors exactly as they are on the existing TrackSnatcher screens.** Reuse the existing stylesheet and background as-is. Don't change, recolor or recreate them.

### Deliverables
Produce and commit, in this order:
1. **System architecture**: data flow between the mobile client (Termux/web), the TrackSnatcher Node backend, Grok, MusicBrainz, Node-RED (`/download -MP3`, YouTube playlist append) and Spotify/YouTube profile links.
2. **Data models**: `users` (UserProfiles), `sessions`, `session_members`, `tracks`, `session_tracks`, `library`, `track_snatches` (lineage), `presence`, `friends`.
3. **API contracts**: REST plus WebSocket endpoints for QR pairing, real-time queue updates, profiles, snatching and proximity broadcasts.
4. **Implementation**: integrate the reference code below into the existing repo. Reuse the existing AudD, MusicBrainz and Node-RED modules where they already exist instead of duplicating them.
5. **Roadmap**: ordered tasks (below). Check each one off in the PR description as you finish it.

### Architecture overview
```
┌───────────────┐   QR / PIN    ┌──────────────────────────────────────────┐
│ Host phone    │──────────────▶│  TrackSnatcher backend (Node, Express)   │
│ (Termux/web)  │◀──── WS ─────▶│  ├─ REST /api/*                          │
└───────────────┘               │  ├─ WS   /ws  (session rooms, presence)  │
┌───────────────┐   scan QR     │  ├─ SQLite (better-sqlite3)              │
│ Guest phones  │──────────────▶│  ├─ grok.js ──────▶ xAI Grok API         │
│ (browser)     │◀──── WS ─────▶│  ├─ resolver.js ──▶ MusicBrainz          │
└───────────────┘               │  ├─ pipeline.js ──▶ Node-RED             │
┌───────────────┐  beacon       │  │      ├─ /download -MP3 flow           │
│ Nearby users  │──(geohash)───▶│  │      └─ YouTube playlist append flow  │
│ BLE / mDNS    │──(eph. id)───▶│  └─ mdns.js (LAN advertise for offline)  │
└───────────────┘               └──────────────────────────────────────────┘
```
- **Session flow:** the host runs `POST /api/sessions` and gets back a token, a PIN and a QR. A guest scans the QR, which opens `/join.html?s=TOKEN`, then calls `POST /api/sessions/join` and gets a guest token. The WS subscribes the guest to the room. A guest adds a track: Grok normalizes the query, MusicBrainz resolves it, the track is inserted into `session_tracks` with `added_by`, the WS broadcasts `queue.updated`, and Node-RED appends it to the host's YouTube playlist.
- **Snatch flow:** `POST /api/snatch {track_id, from_user_id, context}` copies the track into the caller's `library`, writes a `track_snatches` lineage row, and triggers Node-RED `/download -MP3` and/or the YouTube append. A `snatch.created` event goes out to the session and to the source user.
- **Proximity flow (hybrid, layered):**
  1. **Server geohash clustering (default, works everywhere).** The client sends lat/lon once per minute. The server converts it to a 7-character geohash (about 150 m), stores only the hash and **throws away the coordinates**. `/api/nearby` matches the same cell plus its 8 neighbours and never returns the geohash itself.
  2. **BLE (native app / Termux:API).** The server issues rotating 16-byte ephemeral IDs that change every 15 min. The device advertises the ephemeral ID, scans for others and resolves the ones it sees through `/api/presence/resolve`. A device MAC or user ID never goes over the air.
  3. **mDNS on the LAN (offline campfire).** The host's backend advertises `_tracksnatcher._tcp` so guests on the same Wi-Fi or hotspot can join with no internet.
- **Privacy:** `ghost` = invisible everywhere and beacons are ignored. `friends` = visible only to mutual friends. `public` = visible to anyone nearby. Only track metadata and streaming URLs are ever shared. No file paths, no coordinates.

### Implementation roadmap (do in order)
1. Add the dependencies and env vars (`XAI_API_KEY`, `XAI_MODEL`, `NODE_RED_URL`, `PUBLIC_URL`, `DB_PATH`).
2. Apply `schema.sql` and add a migration runner (`src/db.js`).
3. Add token auth (`src/auth.js`): register device users, issue guest tokens.
4. Add `src/grok.js` with JSON-mode calls, timeout, cache and fallbacks. Unit-test the fallback path with Grok disabled.
5. Add `src/resolver.js`: Grok normalize → MusicBrainz → upsert `tracks`. Reuse the existing MusicBrainz module if there is one.
6. Add `src/pipeline.js` and wire it to the existing Node-RED `/download -MP3` and YouTube append flows.
7. Sessions REST: create, QR, join by token or PIN, queue add/list/reorder, now-playing, end.
8. WebSocket rooms (`src/realtime.js`): `queue.updated`, `member.joined`, `now_playing`, `snatch.created`, `session.ended`.
9. Profiles plus a public library endpoint, with visibility enforcement.
10. Snatch endpoint plus lineage, including the "Snatched by B from A" label.
11. Grok DJ: `/api/sessions/:id/dj/suggest` and fair-order reorder (host approves).
12. Presence: geohash beacon, `/api/nearby`, BLE ephemeral IDs + resolve, mDNS advertise.
13. Build the host, join/queue, profile and nearby screens on the existing stylesheet, keeping the current breathing background and colors.
14. Hook AudD identify results into session queues (identify → "Add to session?").
15. Hardening: rate limits, input caps, session expiry (12 h), ghost-mode tests.
16. Hook the existing `/download -MP3` chat command into Grok intent parsing.

---

## REFERENCE CODE

Stack: Node 18+ (native `fetch`), Express 4, `ws`, `better-sqlite3`, `qrcode`, `ngeohash`, `bonjour-service`. Runs on a server or directly in Termux (`pkg install nodejs`).

#### `package.json`
```json
{
  "name": "tracksnatcher-social",
  "version": "1.0.0",
  "private": true,
  "main": "src/server.js",
  "scripts": {
    "start": "node src/server.js",
    "dev": "node --watch src/server.js"
  },
  "engines": { "node": ">=18" },
  "dependencies": {
    "better-sqlite3": "^11.3.0",
    "bonjour-service": "^1.2.1",
    "dotenv": "^16.4.5",
    "express": "^4.21.0",
    "express-rate-limit": "^7.4.0",
    "ngeohash": "^0.6.3",
    "qrcode": "^1.5.4",
    "ws": "^8.18.0"
  }
}
```

#### `.env.example`
```bash
PORT=8787
PUBLIC_URL=http://192.168.43.1:8787   # what guests' phones can reach (hotspot IP, LAN IP, or domain)
DB_PATH=./data/tracksnatcher.db

# Grok = the brains
XAI_API_KEY=xai-xxxxxxxxxxxxxxxx
XAI_MODEL=grok-4
GROK_ENABLED=true

# Existing Node-RED flows
NODE_RED_URL=http://127.0.0.1:1880
NODE_RED_DOWNLOAD_PATH=/download        # existing /download -MP3 flow
NODE_RED_YT_APPEND_PATH=/youtube/append # existing YouTube playlist flow

MUSICBRAINZ_UA=TrackSnatcher/1.0 ( you@example.com )
MDNS_ENABLED=true
```

#### `src/db/schema.sql`
```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- UserProfiles
CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  display_name  TEXT NOT NULL,
  avatar_url    TEXT,
  youtube_url   TEXT,                -- public YouTube channel/playlist
  spotify_url   TEXT,                -- public Spotify profile/playlist
  yt_playlist_id TEXT,               -- personal "Snatched" playlist for auto-append
  visibility    TEXT NOT NULL DEFAULT 'friends'
                CHECK (visibility IN ('ghost','friends','public')),
  is_guest      INTEGER NOT NULL DEFAULT 0,
  token_hash    TEXT NOT NULL UNIQUE,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS friends (
  user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (user_id, friend_id)
);

-- Canonical tracks (deduped by MBID, then artist+title)
CREATE TABLE IF NOT EXISTS tracks (
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL,
  artist      TEXT NOT NULL,
  album       TEXT,
  mbid        TEXT UNIQUE,
  isrc        TEXT,
  youtube_url TEXT,
  spotify_url TEXT,
  artwork_url TEXT,
  duration_ms INTEGER,
  created_at  INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE UNIQUE INDEX IF NOT EXISTS tracks_artist_title
  ON tracks (lower(artist), lower(title));

-- Sessions
CREATE TABLE IF NOT EXISTS sessions (
  id             TEXT PRIMARY KEY,
  host_id        TEXT NOT NULL REFERENCES users(id),
  title          TEXT NOT NULL,
  vibe           TEXT NOT NULL DEFAULT 'Party', -- Road Trip, Campfire, Wedding Recital...
  join_token     TEXT NOT NULL UNIQUE,          -- in the QR
  join_pin       TEXT NOT NULL,                 -- 6 digits, unique among active sessions
  yt_playlist_id TEXT,                          -- host's shared YouTube playlist
  status         TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','ended')),
  created_at     INTEGER NOT NULL DEFAULT (unixepoch()),
  expires_at     INTEGER NOT NULL,
  ended_at       INTEGER
);
CREATE INDEX IF NOT EXISTS sessions_pin ON sessions (join_pin, status);

CREATE TABLE IF NOT EXISTS session_members (
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role       TEXT NOT NULL DEFAULT 'guest' CHECK (role IN ('host','guest')),
  joined_at  INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (session_id, user_id)
);

-- SessionTracks (the shared queue, with attribution)
CREATE TABLE IF NOT EXISTS session_tracks (
  id         TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  track_id   TEXT NOT NULL REFERENCES tracks(id),
  added_by   TEXT NOT NULL REFERENCES users(id),
  position   REAL NOT NULL,                       -- fractional for cheap reorders
  status     TEXT NOT NULL DEFAULT 'queued'
             CHECK (status IN ('queued','playing','played','skipped')),
  source     TEXT NOT NULL DEFAULT 'search'
             CHECK (source IN ('identify','search','snatch','grok')),
  vibe_flag  TEXT,                                -- Grok note, host decides
  added_at   INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS session_tracks_q ON session_tracks (session_id, status, position);

-- Personal "Snatched Library"
CREATE TABLE IF NOT EXISTS library (
  user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  track_id  TEXT NOT NULL REFERENCES tracks(id),
  source    TEXT NOT NULL DEFAULT 'identify',     -- identify | snatch | session | search
  is_public INTEGER NOT NULL DEFAULT 1,
  added_at  INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (user_id, track_id)
);

-- TrackSnatches (lineage: "Snatched by B from A")
CREATE TABLE IF NOT EXISTS track_snatches (
  id            TEXT PRIMARY KEY,
  track_id      TEXT NOT NULL REFERENCES tracks(id),
  snatched_by   TEXT NOT NULL REFERENCES users(id),
  snatched_from TEXT REFERENCES users(id),        -- NULL = identified from the air
  parent_id     TEXT REFERENCES track_snatches(id), -- chain A -> B -> C
  context       TEXT NOT NULL CHECK (context IN ('session','nearby','profile','identify')),
  session_id    TEXT REFERENCES sessions(id),
  created_at    INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS snatches_track ON track_snatches (track_id, created_at);
CREATE INDEX IF NOT EXISTS snatches_from  ON track_snatches (snatched_from);

-- Presence (no coordinates ever stored)
CREATE TABLE IF NOT EXISTS presence (
  user_id     TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  geohash     TEXT,                 -- precision 7 (~150 m), never returned by API
  ble_eph_id  TEXT UNIQUE,          -- rotating hex id advertised over BLE
  ble_eph_exp INTEGER,
  last_seen   INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS presence_geo ON presence (geohash, last_seen);
```

#### `src/db.js`
```js
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const Database = require('better-sqlite3');

const dbPath = process.env.DB_PATH || './data/tracksnatcher.db';
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

const db = new Database(dbPath);
db.exec(fs.readFileSync(path.join(__dirname, 'db', 'schema.sql'), 'utf8'));

const id = (prefix) => `${prefix}_${crypto.randomBytes(9).toString('base64url')}`;
const now = () => Math.floor(Date.now() / 1000);

module.exports = { db, id, now };
```

#### `src/auth.js`
```js
const crypto = require('crypto');
const { db, id } = require('./db');

const hash = (t) => crypto.createHash('sha256').update(t).digest('hex');

function createUser({ display_name, avatar_url = null, is_guest = 0, visibility }) {
  const token = crypto.randomBytes(24).toString('base64url');
  const user = {
    id: id('usr'),
    display_name: String(display_name || 'Guest').slice(0, 40),
    avatar_url,
    is_guest: is_guest ? 1 : 0,
    visibility: visibility || 'friends', // guests are kept off Nearby by is_guest
  };
  db.prepare(`INSERT INTO users (id, display_name, avatar_url, is_guest, visibility, token_hash)
              VALUES (@id, @display_name, @avatar_url, @is_guest, @visibility, @token_hash)`)
    .run({ ...user, token_hash: hash(token) });
  return { user, token };
}

function userFromToken(token) {
  if (!token) return null;
  return db.prepare('SELECT * FROM users WHERE token_hash = ?').get(hash(token)) || null;
}

function requireAuth(req, res, next) {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  const user = userFromToken(token);
  if (!user) return res.status(401).json({ error: 'unauthorized' });
  req.user = user;
  next();
}

module.exports = { createUser, userFromToken, requireAuth };
```

#### `src/grok.js`: the brains
```js
// All AI reasoning goes through Grok (xAI). Every function has a deterministic fallback.
const XAI_URL = 'https://api.x.ai/v1/chat/completions';
const MODEL = process.env.XAI_MODEL || 'grok-4';
const ENABLED = process.env.GROK_ENABLED !== 'false' && !!process.env.XAI_API_KEY;
const TIMEOUT_MS = 12000;

const cache = new Map(); // key -> { value, exp }
const CACHE_TTL = 10 * 60 * 1000;

async function askJSON(system, user, { cacheKey } = {}) {
  if (!ENABLED) return null;
  if (cacheKey) {
    const hit = cache.get(cacheKey);
    if (hit && hit.exp > Date.now()) return hit.value;
  }
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(XAI_URL, {
      method: 'POST',
      signal: ctrl.signal,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${process.env.XAI_API_KEY}`,
      },
      body: JSON.stringify({
        model: MODEL,
        temperature: 0.2,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: `${system}\nRespond with a single JSON object only.` },
          { role: 'user', content: typeof user === 'string' ? user : JSON.stringify(user) },
        ],
      }),
    });
    if (!res.ok) throw new Error(`xAI ${res.status}`);
    const data = await res.json();
    const text = data.choices?.[0]?.message?.content || '';
    const value = JSON.parse(text.replace(/^```(?:json)?|```$/g, '').trim());
    if (cacheKey) cache.set(cacheKey, { value, exp: Date.now() + CACHE_TTL });
    return value;
  } catch (err) {
    console.warn('[grok] fallback:', err.message);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// 1) Free text -> {artist, title}
async function normalizeQuery(raw) {
  const q = String(raw).slice(0, 200);
  const out = await askJSON(
    'You clean up music search text. Extract the most likely song. ' +
      'Return {"artist": string|null, "title": string|null, "confidence": 0..1}. ' +
      'Fix typos, drop words like "lyrics", "official video", "remastered".',
    q,
    { cacheKey: `norm:${q.toLowerCase()}` }
  );
  if (out && (out.title || out.artist)) return { artist: out.artist || null, title: out.title || null };
  const m = q.split(/\s+-\s+|\s+by\s+/i); // fallback: "Artist - Title" / "Title by Artist"
  return m.length === 2 ? { artist: m[0].trim(), title: m[1].trim() } : { artist: null, title: q };
}

// 2) Chat command -> action
const ACTIONS = ['queue', 'snatch', 'download_mp3', 'search', 'suggest', 'none'];
async function parseIntent(text, context = {}) {
  const out = await askJSON(
    'You route commands for TrackSnatcher, a music identify/snatch app. ' +
      `Return {"action": one of ${JSON.stringify(ACTIONS)}, "query": string|null, ` +
      '"target": "last_identified"|"last_queued"|"now_playing"|null, "from_user": string|null}. ' +
      '"/download -MP3" means download_mp3. "snatch what X added" means snatch with from_user X.',
    { text: String(text).slice(0, 300), recent_contributors: context.contributors || [] }
  );
  if (out && ACTIONS.includes(out.action)) return out;
  if (/^\/download\b.*-mp3/i.test(text)) return { action: 'download_mp3', target: 'last_identified' };
  return { action: 'queue', query: text };
}

// 3) Session DJ: suggestions + fair order
async function djSuggest({ vibe, queue, played }) {
  const out = await askJSON(
    'You are the DJ brain for a shared group queue. Given the vibe, the queued tracks ' +
      '(with added_by) and recent plays, return ' +
      '{"order": [session_track_id...], "suggestions": [{"artist","title","why"}], ' +
      '"flags": [{"session_track_id","note"}]}. ' +
      'Order: round-robin across contributors so nobody dominates, smooth energy/tempo ' +
      'transitions, keep the host\'s tracks no later than 3 slots back. Max 5 suggestions. ' +
      'Flags only for tracks that clearly clash with the vibe; never remove anything.',
    { vibe, queue, played: played.slice(-10) }
  );
  if (out && Array.isArray(out.order)) return out;
  return { order: roundRobin(queue), suggestions: [], flags: [] };
}

function roundRobin(queue) {
  const by = new Map();
  for (const t of queue) {
    if (!by.has(t.added_by)) by.set(t.added_by, []);
    by.get(t.added_by).push(t.id);
  }
  const lanes = [...by.values()];
  const order = [];
  while (lanes.some((l) => l.length)) for (const l of lanes) if (l.length) order.push(l.shift());
  return order;
}

module.exports = { normalizeQuery, parseIntent, djSuggest, roundRobin, enabled: ENABLED };
```

#### `src/resolver.js`
```js
// Query -> Grok normalize -> MusicBrainz -> canonical track row.
// If the repo already has a MusicBrainz module, call it from lookupMB() instead.
const { db, id } = require('./db');
const grok = require('./grok');

const UA = process.env.MUSICBRAINZ_UA || 'TrackSnatcher/1.0 ( contact@example.com )';
let lastMB = 0; // MusicBrainz asks for <= 1 req/s

async function lookupMB({ artist, title }) {
  const wait = Math.max(0, lastMB + 1100 - Date.now());
  if (wait) await new Promise((r) => setTimeout(r, wait));
  lastMB = Date.now();
  const q = [title && `recording:"${title}"`, artist && `artist:"${artist}"`].filter(Boolean).join(' AND ');
  const url = `https://musicbrainz.org/ws/2/recording?fmt=json&limit=1&query=${encodeURIComponent(q)}`;
  try {
    const res = await fetch(url, { headers: { 'User-Agent': UA } });
    if (!res.ok) return null;
    const rec = (await res.json()).recordings?.[0];
    if (!rec || rec.score < 70) return null;
    return {
      mbid: rec.id,
      title: rec.title,
      artist: rec['artist-credit']?.map((a) => a.name + (a.joinphrase || '')).join('') || artist,
      album: rec.releases?.[0]?.title || null,
      isrc: rec.isrcs?.[0] || null,
      duration_ms: rec.length || null,
    };
  } catch {
    return null;
  }
}

function upsertTrack(t) {
  const existing =
    (t.mbid && db.prepare('SELECT * FROM tracks WHERE mbid = ?').get(t.mbid)) ||
    db.prepare('SELECT * FROM tracks WHERE lower(artist) = lower(?) AND lower(title) = lower(?)')
      .get(t.artist, t.title);
  if (existing) {
    db.prepare(`UPDATE tracks SET youtube_url = COALESCE(youtube_url, ?), spotify_url = COALESCE(spotify_url, ?),
                artwork_url = COALESCE(artwork_url, ?) WHERE id = ?`)
      .run(t.youtube_url || null, t.spotify_url || null, t.artwork_url || null, existing.id);
    return db.prepare('SELECT * FROM tracks WHERE id = ?').get(existing.id);
  }
  const row = {
    id: id('trk'), title: t.title, artist: t.artist, album: t.album || null, mbid: t.mbid || null,
    isrc: t.isrc || null, youtube_url: t.youtube_url || null, spotify_url: t.spotify_url || null,
    artwork_url: t.artwork_url || null, duration_ms: t.duration_ms || null,
  };
  db.prepare(`INSERT INTO tracks (id,title,artist,album,mbid,isrc,youtube_url,spotify_url,artwork_url,duration_ms)
              VALUES (@id,@title,@artist,@album,@mbid,@isrc,@youtube_url,@spotify_url,@artwork_url,@duration_ms)`)
    .run(row);
  return row;
}

// input: { query } OR a structured AudD result { artist, title, ... }
async function resolveTrack(input) {
  const guess = input.query ? await grok.normalizeQuery(input.query) : input;
  const mb = await lookupMB(guess);
  const merged = { ...input, ...guess, ...(mb || {}) };
  if (!merged.title) throw new Error('could not resolve track');
  merged.artist = merged.artist || 'Unknown Artist';
  return upsertTrack(merged);
}

module.exports = { resolveTrack, upsertTrack };
```

#### `src/pipeline.js`: hooks into the existing Node-RED flows
```js
const BASE = process.env.NODE_RED_URL || 'http://127.0.0.1:1880';
const DL = process.env.NODE_RED_DOWNLOAD_PATH || '/download';
const YT = process.env.NODE_RED_YT_APPEND_PATH || '/youtube/append';

async function post(path, body) {
  try {
    const res = await fetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return { ok: res.ok, status: res.status };
  } catch (err) {
    console.warn('[node-red]', path, err.message);
    return { ok: false, error: err.message };
  }
}

// Same payload the existing `/download -MP3` command sends.
const downloadMp3 = (track, user) =>
  post(DL, { format: 'mp3', artist: track.artist, title: track.title,
             youtube_url: track.youtube_url, mbid: track.mbid, requested_by: user.id });

const appendToPlaylist = (playlistId, track) =>
  playlistId
    ? post(YT, { playlist_id: playlistId, artist: track.artist, title: track.title, youtube_url: track.youtube_url })
    : Promise.resolve({ ok: false, skipped: true });

module.exports = { downloadMp3, appendToPlaylist };
```

#### `src/realtime.js`: WebSocket rooms
```js
const { WebSocketServer } = require('ws');
const { URL } = require('url');
const { db } = require('./db');
const { userFromToken } = require('./auth');

const rooms = new Map();   // sessionId -> Set<ws>
const userSockets = new Map(); // userId -> Set<ws>

function attach(server) {
  const wss = new WebSocketServer({ server, path: '/ws' });
  wss.on('connection', (ws, req) => {
    const url = new URL(req.url, 'http://x');
    const user = userFromToken(url.searchParams.get('token'));
    if (!user) return ws.close(4401, 'unauthorized');
    ws.user = user;
    add(userSockets, user.id, ws);

    const sessionId = url.searchParams.get('session');
    if (sessionId) {
      const member = db.prepare('SELECT 1 FROM session_members WHERE session_id = ? AND user_id = ?')
        .get(sessionId, user.id);
      if (!member) return ws.close(4403, 'not a member');
      ws.sessionId = sessionId;
      add(rooms, sessionId, ws);
    }

    ws.isAlive = true;
    ws.on('pong', () => (ws.isAlive = true));
    ws.on('close', () => {
      remove(userSockets, user.id, ws);
      if (ws.sessionId) remove(rooms, ws.sessionId, ws);
    });
  });

  setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) { ws.terminate(); continue; }
      ws.isAlive = false;
      ws.ping();
    }
  }, 30000).unref();
}

function add(map, key, ws) { if (!map.has(key)) map.set(key, new Set()); map.get(key).add(ws); }
function remove(map, key, ws) { map.get(key)?.delete(ws); if (map.get(key)?.size === 0) map.delete(key); }

function send(set, type, data) {
  if (!set) return;
  const msg = JSON.stringify({ type, data, ts: Date.now() });
  for (const ws of set) if (ws.readyState === 1) ws.send(msg);
}

const toSession = (sessionId, type, data) => send(rooms.get(sessionId), type, data);
const toUser = (userId, type, data) => send(userSockets.get(userId), type, data);

module.exports = { attach, toSession, toUser };
```

#### `src/routes/sessions.js`
```js
const express = require('express');
const crypto = require('crypto');
const QRCode = require('qrcode');
const { db, id, now } = require('../db');
const { createUser, requireAuth, userFromToken } = require('../auth');
const { resolveTrack } = require('../resolver');
const pipeline = require('../pipeline');
const grok = require('../grok');
const rt = require('../realtime');

const r = express.Router();
const PUBLIC_URL = () => process.env.PUBLIC_URL || `http://localhost:${process.env.PORT || 8787}`;
const SESSION_TTL = 12 * 3600;

const getSession = (sid) => db.prepare("SELECT * FROM sessions WHERE id = ? AND status = 'active' AND expires_at > ?").get(sid, now());
const isMember = (sid, uid) => db.prepare('SELECT role FROM session_members WHERE session_id = ? AND user_id = ?').get(sid, uid);
const joinUrl = (s) => `${PUBLIC_URL()}/join.html?s=${s.join_token}`;

function queueFor(sessionId) {
  return db.prepare(`
    SELECT st.id, st.position, st.status, st.source, st.vibe_flag, st.added_at,
           t.id AS track_id, t.title, t.artist, t.album, t.artwork_url, t.youtube_url, t.spotify_url,
           u.id AS added_by, u.display_name AS added_by_name, u.avatar_url AS added_by_avatar
    FROM session_tracks st
    JOIN tracks t ON t.id = st.track_id
    JOIN users u  ON u.id = st.added_by
    WHERE st.session_id = ? AND st.status IN ('queued','playing')
    ORDER BY CASE st.status WHEN 'playing' THEN 0 ELSE 1 END, st.position`).all(sessionId);
}
const broadcastQueue = (sid) => rt.toSession(sid, 'queue.updated', { queue: queueFor(sid) });

function uniquePin() {
  for (let i = 0; i < 20; i++) {
    const pin = String(crypto.randomInt(0, 1e6)).padStart(6, '0');
    if (!db.prepare("SELECT 1 FROM sessions WHERE join_pin = ? AND status = 'active' AND expires_at > ?").get(pin, now())) return pin;
  }
  throw new Error('no free pin');
}

// Host: start session
r.post('/', requireAuth, (req, res) => {
  const { title = 'Session', vibe = 'Party', yt_playlist_id = null } = req.body || {};
  const s = {
    id: id('ses'), host_id: req.user.id, title: String(title).slice(0, 60), vibe: String(vibe).slice(0, 40),
    join_token: crypto.randomBytes(12).toString('base64url'), join_pin: uniquePin(),
    yt_playlist_id, expires_at: now() + SESSION_TTL,
  };
  db.transaction(() => {
    db.prepare(`INSERT INTO sessions (id,host_id,title,vibe,join_token,join_pin,yt_playlist_id,expires_at)
                VALUES (@id,@host_id,@title,@vibe,@join_token,@join_pin,@yt_playlist_id,@expires_at)`).run(s);
    db.prepare("INSERT INTO session_members (session_id,user_id,role) VALUES (?,?,'host')").run(s.id, req.user.id);
  })();
  res.status(201).json({ session: s, join_url: joinUrl(s), qr_url: `/api/sessions/${s.id}/qr.png` });
});

// QR code PNG (members only; the token in it is the invite)
r.get('/:id/qr.png', requireAuth, async (req, res) => {
  const s = getSession(req.params.id);
  if (!s || !isMember(s.id, req.user.id)) return res.sendStatus(404);
  res.type('png').send(await QRCode.toBuffer(joinUrl(s), { width: 512, margin: 1 }));
});

// Guest: join by QR token or PIN. Works with or without an existing account.
r.post('/join', (req, res) => {
  const { token, pin, display_name } = req.body || {};
  const s = token
    ? db.prepare("SELECT * FROM sessions WHERE join_token = ? AND status = 'active' AND expires_at > ?").get(token, now())
    : db.prepare("SELECT * FROM sessions WHERE join_pin = ? AND status = 'active' AND expires_at > ?").get(String(pin || ''), now());
  if (!s) return res.status(404).json({ error: 'session not found or ended' });

  const bearer = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  let user = userFromToken(bearer);
  let newToken = null;
  if (!user) {
    const created = createUser({ display_name: display_name || 'Guest', is_guest: 1 });
    user = db.prepare('SELECT * FROM users WHERE id = ?').get(created.user.id);
    newToken = created.token;
  }
  db.prepare("INSERT OR IGNORE INTO session_members (session_id,user_id,role) VALUES (?,?,'guest')").run(s.id, user.id);
  rt.toSession(s.id, 'member.joined', { id: user.id, display_name: user.display_name, avatar_url: user.avatar_url });
  res.json({
    token: newToken, // null if they were already signed in
    user: { id: user.id, display_name: user.display_name },
    session: { id: s.id, title: s.title, vibe: s.vibe, host_id: s.host_id },
    queue: queueFor(s.id),
  });
});

r.get('/:id', requireAuth, (req, res) => {
  const s = getSession(req.params.id);
  if (!s || !isMember(s.id, req.user.id)) return res.sendStatus(404);
  const members = db.prepare(`SELECT u.id, u.display_name, u.avatar_url, m.role FROM session_members m
                              JOIN users u ON u.id = m.user_id WHERE m.session_id = ?`).all(s.id);
  const { join_token, ...pub } = s;
  res.json({ session: pub, members, queue: queueFor(s.id) });
});

r.get('/:id/queue', requireAuth, (req, res) => {
  if (!isMember(req.params.id, req.user.id)) return res.sendStatus(404);
  res.json({ queue: queueFor(req.params.id) });
});

// Add to queue: { query } | { track_id } | { identified: {artist,title,...} } (from AudD)
r.post('/:id/queue', requireAuth, async (req, res) => {
  const s = getSession(req.params.id);
  if (!s || !isMember(s.id, req.user.id)) return res.sendStatus(404);
  try {
    const { query, track_id, identified } = req.body || {};
    const track = track_id
      ? db.prepare('SELECT * FROM tracks WHERE id = ?').get(track_id)
      : await resolveTrack(identified || { query: String(query || '').slice(0, 200) });
    if (!track) return res.status(404).json({ error: 'track not found' });

    const max = db.prepare('SELECT COALESCE(MAX(position), 0) AS p FROM session_tracks WHERE session_id = ?').get(s.id).p;
    const row = { id: id('stk'), session_id: s.id, track_id: track.id, added_by: req.user.id, position: max + 1,
                  source: identified ? 'identify' : track_id ? 'snatch' : 'search' };
    db.prepare(`INSERT INTO session_tracks (id,session_id,track_id,added_by,position,source)
                VALUES (@id,@session_id,@track_id,@added_by,@position,@source)`).run(row);

    pipeline.appendToPlaylist(s.yt_playlist_id, track); // fire-and-forget into the shared YT playlist
    broadcastQueue(s.id);
    res.status(201).json({ session_track: row, track });
  } catch (err) {
    res.status(422).json({ error: err.message });
  }
});

// Host: reorder { order: [session_track_id...] }
r.put('/:id/queue/order', requireAuth, (req, res) => {
  const s = getSession(req.params.id);
  if (!s || s.host_id !== req.user.id) return res.sendStatus(403);
  const order = Array.isArray(req.body?.order) ? req.body.order : [];
  const upd = db.prepare('UPDATE session_tracks SET position = ? WHERE id = ? AND session_id = ?');
  db.transaction(() => order.forEach((stid, i) => upd.run(i + 1, stid, s.id)))();
  broadcastQueue(s.id);
  res.json({ queue: queueFor(s.id) });
});

// Host: now playing (marks previous as played)
r.post('/:id/now-playing', requireAuth, (req, res) => {
  const s = getSession(req.params.id);
  if (!s || s.host_id !== req.user.id) return res.sendStatus(403);
  const { session_track_id } = req.body || {};
  db.transaction(() => {
    db.prepare("UPDATE session_tracks SET status = 'played' WHERE session_id = ? AND status = 'playing'").run(s.id);
    db.prepare("UPDATE session_tracks SET status = 'playing' WHERE id = ? AND session_id = ?").run(session_track_id, s.id);
  })();
  const playing = queueFor(s.id).find((q) => q.status === 'playing') || null;
  rt.toSession(s.id, 'now_playing', { track: playing });
  broadcastQueue(s.id);
  res.json({ now_playing: playing });
});

// Grok DJ: suggestions + fair order (host applies via PUT /queue/order)
r.post('/:id/dj/suggest', requireAuth, async (req, res) => {
  const s = getSession(req.params.id);
  if (!s || !isMember(s.id, req.user.id)) return res.sendStatus(404);
  const queue = queueFor(s.id).filter((q) => q.status === 'queued')
    .map((q) => ({ id: q.id, artist: q.artist, title: q.title, added_by: q.added_by_name }));
  const played = db.prepare(`SELECT t.artist, t.title FROM session_tracks st JOIN tracks t ON t.id = st.track_id
                             WHERE st.session_id = ? AND st.status = 'played' ORDER BY st.position`).all(s.id);
  const out = await grok.djSuggest({ vibe: s.vibe, queue, played });
  const valid = new Set(queue.map((q) => q.id));
  out.order = (out.order || []).filter((x) => valid.has(x));
  for (const q of queue) if (!out.order.includes(q.id)) out.order.push(q.id);
  const flag = db.prepare('UPDATE session_tracks SET vibe_flag = ? WHERE id = ? AND session_id = ?');
  for (const f of out.flags || []) if (valid.has(f.session_track_id)) flag.run(String(f.note).slice(0, 120), f.session_track_id, s.id);
  res.json({ ...out, grok: grok.enabled });
});

r.post('/:id/end', requireAuth, (req, res) => {
  const s = getSession(req.params.id);
  if (!s || s.host_id !== req.user.id) return res.sendStatus(403);
  db.prepare("UPDATE sessions SET status = 'ended', ended_at = ? WHERE id = ?").run(now(), s.id);
  rt.toSession(s.id, 'session.ended', { id: s.id });
  res.json({ ok: true });
});

module.exports = r;
```

#### `src/routes/users.js`: profiles, libraries, friends
```js
const express = require('express');
const { db } = require('../db');
const { createUser, requireAuth } = require('../auth');

const r = express.Router();
const PROFILE_COLS = 'id, display_name, avatar_url, youtube_url, spotify_url, visibility';
const isUrl = (u) => u == null || /^https:\/\/(www\.)?(youtube\.com|youtu\.be|music\.youtube\.com|open\.spotify\.com)\//.test(u);

function canView(viewer, target) {
  if (!target) return false;
  if (viewer.id === target.id) return true;
  if (target.visibility === 'public') return true;
  if (target.visibility === 'friends') return areFriends(viewer.id, target.id);
  return false; // ghost
}
function areFriends(a, b) {
  return !!db.prepare(`SELECT 1 FROM friends f1 JOIN friends f2 ON f1.user_id = f2.friend_id AND f1.friend_id = f2.user_id
                       WHERE f1.user_id = ? AND f1.friend_id = ?`).get(a, b);
}
// Session co-members can always see each other's profile cards (needed for attribution taps).
const shareSession = (a, b) => !!db.prepare(`SELECT 1 FROM session_members m1 JOIN session_members m2 ON m1.session_id = m2.session_id
   JOIN sessions s ON s.id = m1.session_id AND s.status = 'active' WHERE m1.user_id = ? AND m2.user_id = ?`).get(a, b);

r.post('/', (req, res) => {
  const { display_name, avatar_url } = req.body || {};
  if (!display_name) return res.status(400).json({ error: 'display_name required' });
  const { user, token } = createUser({ display_name, avatar_url });
  res.status(201).json({ user, token });
});

r.get('/me', requireAuth, (req, res) => {
  res.json(db.prepare(`SELECT ${PROFILE_COLS}, yt_playlist_id, is_guest FROM users WHERE id = ?`).get(req.user.id));
});

r.patch('/me', requireAuth, (req, res) => {
  const b = req.body || {};
  if (!isUrl(b.youtube_url) || !isUrl(b.spotify_url)) return res.status(400).json({ error: 'links must be YouTube or Spotify https URLs' });
  if (b.visibility && !['ghost', 'friends', 'public'].includes(b.visibility)) return res.status(400).json({ error: 'bad visibility' });
  const fields = ['display_name', 'avatar_url', 'youtube_url', 'spotify_url', 'yt_playlist_id', 'visibility'].filter((f) => f in b);
  if (fields.length) {
    db.prepare(`UPDATE users SET ${fields.map((f) => `${f} = @${f}`).join(', ')} WHERE id = @id`)
      .run({ ...Object.fromEntries(fields.map((f) => [f, b[f]])), id: req.user.id });
  }
  if (b.visibility === 'ghost') db.prepare('DELETE FROM presence WHERE user_id = ?').run(req.user.id);
  res.json(db.prepare(`SELECT ${PROFILE_COLS} FROM users WHERE id = ?`).get(req.user.id));
});

r.get('/:id', requireAuth, (req, res) => {
  const target = db.prepare(`SELECT ${PROFILE_COLS} FROM users WHERE id = ?`).get(req.params.id);
  if (!target || !(canView(req.user, target) || shareSession(req.user.id, target.id))) return res.sendStatus(404);
  const stats = db.prepare(`SELECT
      (SELECT COUNT(*) FROM library WHERE user_id = @id AND is_public = 1) AS library_count,
      (SELECT COUNT(*) FROM track_snatches WHERE snatched_from = @id) AS snatched_from_count`).get({ id: target.id });
  const { visibility, ...pub } = target;
  res.json({ ...pub, ...stats, is_friend: areFriends(req.user.id, target.id) });
});

// Napster-style browse of someone's public Snatched Library
r.get('/:id/library', requireAuth, (req, res) => {
  const target = db.prepare('SELECT id, visibility FROM users WHERE id = ?').get(req.params.id);
  if (!target || !(canView(req.user, target) || shareSession(req.user.id, target.id))) return res.sendStatus(404);
  const limit = Math.min(+req.query.limit || 50, 200);
  const offset = +req.query.offset || 0;
  const rows = db.prepare(`
    SELECT t.id, t.title, t.artist, t.album, t.artwork_url, t.youtube_url, t.spotify_url, l.source, l.added_at,
      (SELECT u.display_name FROM track_snatches ts JOIN users u ON u.id = ts.snatched_from
        WHERE ts.track_id = t.id AND ts.snatched_by = l.user_id ORDER BY ts.created_at DESC LIMIT 1) AS snatched_from_name,
      EXISTS(SELECT 1 FROM library mine WHERE mine.user_id = @viewer AND mine.track_id = t.id) AS in_my_library
    FROM library l JOIN tracks t ON t.id = l.track_id
    WHERE l.user_id = @uid AND (l.is_public = 1 OR @uid = @viewer)
    ORDER BY l.added_at DESC LIMIT @limit OFFSET @offset`).all({ uid: target.id, viewer: req.user.id, limit, offset });
  res.json({ tracks: rows, limit, offset });
});

r.post('/:id/friend', requireAuth, (req, res) => {
  if (req.params.id === req.user.id) return res.status(400).json({ error: 'cannot friend yourself' });
  db.prepare('INSERT OR IGNORE INTO friends (user_id, friend_id) VALUES (?, ?)').run(req.user.id, req.params.id);
  res.json({ mutual: areFriends(req.user.id, req.params.id) });
});

module.exports = { router: r, canView };
```

#### `src/routes/snatch.js`: one-tap Snatch + lineage
```js
const express = require('express');
const { db, id } = require('../db');
const { requireAuth } = require('../auth');
const pipeline = require('../pipeline');
const rt = require('../realtime');

const r = express.Router();

// { track_id, from_user_id?, context: 'session'|'nearby'|'profile', session_id?, download?: true, append?: true }
r.post('/', requireAuth, async (req, res) => {
  const { track_id, from_user_id = null, context = 'profile', session_id = null, download = true, append = true } = req.body || {};
  if (!['session', 'nearby', 'profile'].includes(context)) return res.status(400).json({ error: 'bad context' });
  const track = db.prepare('SELECT * FROM tracks WHERE id = ?').get(track_id);
  if (!track) return res.status(404).json({ error: 'track not found' });
  if (from_user_id === req.user.id) return res.status(400).json({ error: 'cannot snatch from yourself' });

  // The source must actually have it (library or session queue) so lineage stays honest.
  if (from_user_id) {
    const has = db.prepare(`SELECT 1 FROM library WHERE user_id = ? AND track_id = ?
                            UNION SELECT 1 FROM session_tracks WHERE added_by = ? AND track_id = ?`)
      .get(from_user_id, track_id, from_user_id, track_id);
    if (!has) return res.status(404).json({ error: 'source user does not have this track' });
  }
  const parent = from_user_id
    ? db.prepare('SELECT id FROM track_snatches WHERE track_id = ? AND snatched_by = ? ORDER BY created_at DESC LIMIT 1').get(track_id, from_user_id)
    : null;

  const snatch = { id: id('sn'), track_id, snatched_by: req.user.id, snatched_from: from_user_id,
                   parent_id: parent?.id || null, context, session_id };
  db.transaction(() => {
    db.prepare(`INSERT INTO track_snatches (id,track_id,snatched_by,snatched_from,parent_id,context,session_id)
                VALUES (@id,@track_id,@snatched_by,@snatched_from,@parent_id,@context,@session_id)`).run(snatch);
    db.prepare("INSERT OR IGNORE INTO library (user_id, track_id, source) VALUES (?, ?, 'snatch')").run(req.user.id, track_id);
  })();

  const jobs = await Promise.all([
    download ? pipeline.downloadMp3(track, req.user) : null,
    append ? pipeline.appendToPlaylist(req.user.yt_playlist_id, track) : null,
  ]);

  const fromName = from_user_id && db.prepare('SELECT display_name FROM users WHERE id = ?').get(from_user_id)?.display_name;
  const event = { snatch, track: { id: track.id, title: track.title, artist: track.artist },
                  label: fromName ? `Snatched by ${req.user.display_name} from ${fromName}` : `Snatched by ${req.user.display_name}` };
  if (session_id) rt.toSession(session_id, 'snatch.created', event);
  if (from_user_id) rt.toUser(from_user_id, 'snatch.created', event);
  res.status(201).json({ ...event, download: jobs[0], playlist: jobs[1] });
});

// Lineage chain for a track: who got it from whom
r.get('/lineage/:trackId', requireAuth, (req, res) => {
  const rows = db.prepare(`
    SELECT ts.id, ts.context, ts.created_at, ts.parent_id,
           b.id AS by_id, b.display_name AS by_name, f.id AS from_id, f.display_name AS from_name
    FROM track_snatches ts JOIN users b ON b.id = ts.snatched_by LEFT JOIN users f ON f.id = ts.snatched_from
    WHERE ts.track_id = ? AND b.visibility != 'ghost' ORDER BY ts.created_at`).all(req.params.trackId);
  res.json({ lineage: rows.map((x) => ({ ...x, label: x.from_name ? `Snatched by ${x.by_name} from ${x.from_name}` : `Snatched by ${x.by_name}` })) });
});

module.exports = r;
```

#### `src/routes/presence.js`: Nearby / Napster Mode
```js
const express = require('express');
const crypto = require('crypto');
const geohash = require('ngeohash');
const { db, now } = require('../db');
const { requireAuth } = require('../auth');
const { canView } = require('./users');

const r = express.Router();
const PRECISION = 7;          // ~150 m cells
const FRESH_SECS = 5 * 60;    // considered "here" if seen in last 5 min
const BLE_ROTATE = 15 * 60;   // ephemeral id lifetime

const nearbyCard = (u) => ({ id: u.id, display_name: u.display_name, avatar_url: u.avatar_url,
  youtube_url: u.youtube_url, spotify_url: u.spotify_url,
  library_count: db.prepare('SELECT COUNT(*) c FROM library WHERE user_id = ? AND is_public = 1').get(u.id).c,
  recent: db.prepare(`SELECT t.id, t.title, t.artist, t.artwork_url FROM library l JOIN tracks t ON t.id = l.track_id
                      WHERE l.user_id = ? AND l.is_public = 1 ORDER BY l.added_at DESC LIMIT 3`).all(u.id) });

// Location beacon: coordinates are converted and discarded immediately.
r.post('/beacon', requireAuth, (req, res) => {
  if (req.user.visibility === 'ghost') return res.json({ ok: true, ghost: true });
  const lat = Number(req.body?.lat), lon = Number(req.body?.lon);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    return res.status(400).json({ error: 'bad coords' });
  }
  const cell = geohash.encode(lat, lon, PRECISION);
  db.prepare(`INSERT INTO presence (user_id, geohash, last_seen) VALUES (?, ?, ?)
              ON CONFLICT(user_id) DO UPDATE SET geohash = excluded.geohash, last_seen = excluded.last_seen`)
    .run(req.user.id, cell, now());
  res.json({ ok: true }); // never echo the cell back
});

// Nearby users (same cell + 8 neighbours), filtered by visibility.
r.get('/nearby', requireAuth, (req, res) => {
  const me = db.prepare('SELECT geohash FROM presence WHERE user_id = ?').get(req.user.id);
  if (!me?.geohash) return res.json({ users: [] });
  const cells = [me.geohash, ...geohash.neighbors(me.geohash)];
  const rows = db.prepare(`
    SELECT u.* FROM presence p JOIN users u ON u.id = p.user_id
    WHERE p.geohash IN (${cells.map(() => '?').join(',')}) AND p.last_seen > ? AND u.id != ?
      AND u.visibility != 'ghost' AND u.is_guest = 0 LIMIT 100`).all(...cells, now() - FRESH_SECS, req.user.id);
  res.json({ users: rows.filter((u) => canView(req.user, u)).map(nearbyCard) });
});

// BLE: issue a rotating ephemeral id for the device to advertise.
r.post('/ble-token', requireAuth, (req, res) => {
  if (req.user.visibility === 'ghost') return res.json({ ghost: true });
  const eph = crypto.randomBytes(16).toString('hex');
  const exp = now() + BLE_ROTATE;
  db.prepare(`INSERT INTO presence (user_id, ble_eph_id, ble_eph_exp, last_seen) VALUES (?, ?, ?, ?)
              ON CONFLICT(user_id) DO UPDATE SET ble_eph_id = excluded.ble_eph_id, ble_eph_exp = excluded.ble_eph_exp,
              last_seen = excluded.last_seen`).run(req.user.id, eph, exp, now());
  res.json({ eph_id: eph, expires_at: exp, service_uuid: '7a1c5e00-7ac4-4a7c-9b1e-5ea7c4e10000' });
});

// BLE: resolve ids the device heard over the air.
r.post('/resolve', requireAuth, (req, res) => {
  const ids = (Array.isArray(req.body?.eph_ids) ? req.body.eph_ids : []).slice(0, 50).filter((x) => /^[0-9a-f]{32}$/.test(x));
  if (!ids.length) return res.json({ users: [] });
  const rows = db.prepare(`SELECT u.* FROM presence p JOIN users u ON u.id = p.user_id
    WHERE p.ble_eph_id IN (${ids.map(() => '?').join(',')}) AND p.ble_eph_exp > ? AND u.visibility != 'ghost' AND u.id != ?`)
    .all(...ids, now(), req.user.id);
  res.json({ users: rows.filter((u) => canView(req.user, u)).map(nearbyCard) });
});

r.delete('/', requireAuth, (req, res) => {
  db.prepare('DELETE FROM presence WHERE user_id = ?').run(req.user.id);
  res.json({ ok: true });
});

module.exports = r;
```

#### `src/routes/agent.js`: Grok command entry point (chat / Hermes commands)
```js
const express = require('express');
const { db } = require('../db');
const { requireAuth } = require('../auth');
const grok = require('../grok');
const { resolveTrack } = require('../resolver');
const pipeline = require('../pipeline');

const r = express.Router();

// { text, session_id? }. Grok decides; we execute against the same endpoints' logic.
r.post('/command', requireAuth, async (req, res) => {
  const { text = '', session_id = null } = req.body || {};
  const contributors = session_id
    ? db.prepare(`SELECT DISTINCT u.display_name FROM session_tracks st JOIN users u ON u.id = st.added_by
                  WHERE st.session_id = ?`).all(session_id).map((x) => x.display_name)
    : [];
  const intent = await grok.parseIntent(text, { contributors });

  if (intent.action === 'download_mp3') {
    const last = db.prepare(`SELECT t.* FROM library l JOIN tracks t ON t.id = l.track_id
                             WHERE l.user_id = ? ORDER BY l.added_at DESC LIMIT 1`).get(req.user.id);
    const track = intent.query ? await resolveTrack({ query: intent.query }) : last;
    if (!track) return res.json({ intent, error: 'nothing to download' });
    return res.json({ intent, track, job: await pipeline.downloadMp3(track, req.user) });
  }
  if (intent.action === 'snatch' && intent.from_user && session_id) {
    const row = db.prepare(`SELECT st.track_id, u.id AS from_id FROM session_tracks st JOIN users u ON u.id = st.added_by
                            WHERE st.session_id = ? AND lower(u.display_name) = lower(?) ORDER BY st.added_at DESC LIMIT 1`)
      .get(session_id, intent.from_user);
    // The client confirms, then calls POST /api/snatch with this payload.
    return res.json({ intent, confirm: row ? { track_id: row.track_id, from_user_id: row.from_id, context: 'session', session_id } : null });
  }
  if (intent.action === 'queue' && session_id && intent.query) {
    return res.json({ intent, confirm: { endpoint: `/api/sessions/${session_id}/queue`, body: { query: intent.query } } });
  }
  res.json({ intent });
});

module.exports = r;
```

#### `src/mdns.js`: offline LAN discovery (campfire / hotspot)
```js
let bonjour = null;

function advertise(port) {
  if (process.env.MDNS_ENABLED === 'false') return;
  try {
    const { Bonjour } = require('bonjour-service');
    bonjour = new Bonjour();
    bonjour.publish({ name: `TrackSnatcher-${process.pid}`, type: 'tracksnatcher', port, txt: { v: '1', path: '/join.html' } });
    console.log(`[mdns] advertising _tracksnatcher._tcp on ${port}`);
  } catch (err) {
    console.warn('[mdns] disabled:', err.message); // Termux without multicast is fine
  }
}

const stop = () => bonjour?.unpublishAll(() => bonjour.destroy());
module.exports = { advertise, stop };
```

#### `src/server.js`
```js
require('dotenv').config();
const http = require('http');
const path = require('path');
const express = require('express');
const rateLimit = require('express-rate-limit');
const realtime = require('./realtime');
const mdns = require('./mdns');

const app = express();
app.set('trust proxy', 1);
app.use(express.json({ limit: '32kb' }));
app.use('/api', rateLimit({ windowMs: 60_000, limit: 120, standardHeaders: true, legacyHeaders: false }));
app.use('/api/sessions/join', rateLimit({ windowMs: 60_000, limit: 10 })); // PIN brute-force guard

app.use('/api/users', require('./routes/users').router);
app.use('/api/sessions', require('./routes/sessions'));
app.use('/api/snatch', require('./routes/snatch'));
app.use('/api/presence', require('./routes/presence'));
app.use('/api/agent', require('./routes/agent'));
app.get('/api/health', (_req, res) => res.json({ ok: true, grok: require('./grok').enabled }));

app.use(express.static(path.join(__dirname, '..', 'public')));

const server = http.createServer(app);
realtime.attach(server);

const port = +process.env.PORT || 8787;
server.listen(port, '0.0.0.0', () => {
  console.log(`TrackSnatcher social on :${port}`);
  mdns.advertise(port);
});
process.on('SIGINT', () => { mdns.stop(); process.exit(0); });
```

### API contract summary

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/users` | anyone | Register device user → `{user, token}` |
| GET/PATCH | `/api/users/me` | auth | Profile, YT/Spotify links, `visibility` (`ghost`/`friends`/`public`) |
| GET | `/api/users/:id` | auth | Contributor card (tap on `added_by`) |
| GET | `/api/users/:id/library` | auth | Napster-style public Snatched Library |
| POST | `/api/users/:id/friend` | auth | Friend request (mutual = friends) |
| POST | `/api/sessions` | auth | Host starts a session → token, PIN, QR URL |
| GET | `/api/sessions/:id/qr.png` | member | QR code PNG |
| POST | `/api/sessions/join` | anyone | Join by `{token}` (QR) or `{pin}` → guest token |
| GET | `/api/sessions/:id` | member | Session, members, queue |
| POST | `/api/sessions/:id/queue` | member | Add `{query}` / `{track_id}` / `{identified}` |
| PUT | `/api/sessions/:id/queue/order` | host | Reorder |
| POST | `/api/sessions/:id/now-playing` | host | Mark playing |
| POST | `/api/sessions/:id/dj/suggest` | member | **Grok** fair order + suggestions + vibe flags |
| POST | `/api/sessions/:id/end` | host | End session |
| POST | `/api/snatch` | auth | One-tap Snatch → library + lineage + `/download -MP3` + YT append |
| GET | `/api/snatch/lineage/:trackId` | auth | "Snatched by B from A" chain |
| POST | `/api/presence/beacon` | auth | `{lat, lon}` → geohash (coords discarded) |
| GET | `/api/presence/nearby` | auth | Nearby visible users + 3 recent snatches each |
| POST | `/api/presence/ble-token` | auth | Rotating BLE ephemeral ID |
| POST | `/api/presence/resolve` | auth | Resolve heard BLE IDs → profiles |
| DELETE | `/api/presence` | auth | Go dark immediately |
| POST | `/api/agent/command` | auth | **Grok** intent parsing for chat / `/download -MP3` |

**WebSocket** `GET /ws?token=…&session=…` sends server→client events `{type, data, ts}`:
`queue.updated {queue}` · `member.joined {id, display_name, avatar_url}` · `now_playing {track}` · `snatch.created {snatch, track, label}` · `session.ended {id}`.

---

### Screens
Each screen loads the **existing TrackSnatcher stylesheet first**, so it keeps the current breathing background and colors. `social.css` only adds layout for the new components and inherits every color.

#### `public/css/social.css`
```css
/* Layout only. Colors and the background come from the existing TrackSnatcher stylesheet. */
.wrap { max-width: 560px; margin: 0 auto; padding: 16px; box-sizing: border-box; }
.card { border: 1px solid color-mix(in srgb, currentColor 18%, transparent); border-radius: 18px;
  backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); padding: 16px; margin-bottom: 14px; }
h1 { font-size: 1.6rem; margin: 8px 0 4px; }
.dim { opacity: .72; font-size: .9rem; }
.row { display: flex; gap: 10px; align-items: center; }
.stack > * + * { margin-top: 8px; }
input { width: 100%; box-sizing: border-box; padding: 12px 14px; border-radius: 12px; font: inherit; }
.track { display: flex; gap: 12px; align-items: center; padding: 10px 0;
  border-bottom: 1px solid color-mix(in srgb, currentColor 14%, transparent); }
.track:last-child { border-bottom: 0; }
.track .meta { flex: 1; min-width: 0; }
.track .title { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.track.playing .title::before { content: "▶ "; }
.avatar { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; flex: none; }
.art { width: 44px; height: 44px; border-radius: 8px; object-fit: cover; flex: none; }
.chip { font-size: .75rem; padding: 2px 8px; border-radius: 999px; cursor: pointer; opacity: .8;
  border: 1px solid color-mix(in srgb, currentColor 30%, transparent); }
.selected { outline: 2px solid currentColor; }
.toast { position: fixed; left: 50%; bottom: 20px; transform: translateX(-50%); max-width: calc(100% - 32px); }
.qr { width: 150px; height: 150px; border-radius: 12px; background: #fff; }
.pin { font-size: 2rem; font-weight: 800; letter-spacing: .15em; }
```

#### `public/js/api.js`
```js
window.TS = {
  get token() { try { return localStorage.getItem('ts_token'); } catch { return null; } },
  set token(v) { try { localStorage.setItem('ts_token', v); } catch {} },
  async api(path, opts = {}) {
    const res = await fetch('/api' + path, {
      ...opts,
      headers: { 'Content-Type': 'application/json', ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}) },
      body: opts.body ? JSON.stringify(opts.body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw Object.assign(new Error(data.error || res.statusText), { status: res.status });
    return data;
  },
  socket(sessionId, onEvent) {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    let ws, retry = 1000;
    const open = () => {
      ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(this.token)}${sessionId ? `&session=${sessionId}` : ''}`);
      ws.onmessage = (e) => onEvent(JSON.parse(e.data));
      ws.onopen = () => (retry = 1000);
      ws.onclose = (e) => { if (e.code < 4400) setTimeout(open, (retry = Math.min(retry * 2, 15000))); };
    };
    open();
    return () => ws.close();
  },
  esc: (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])),
  toast(msg) {
    const t = document.createElement('div'); t.className = 'card toast'; t.textContent = msg;
    document.body.append(t); setTimeout(() => t.remove(), 2600);
  },
  trackRow(t, { onSnatch, onProfile } = {}) {
    const el = document.createElement('div');
    el.className = 'track' + (t.status === 'playing' ? ' playing' : '');
    el.innerHTML = `
      ${t.artwork_url ? `<img class="art" src="${TS.esc(t.artwork_url)}" alt="">` : ''}
      <div class="meta">
        <div class="title">${TS.esc(t.title)}</div>
        <div class="dim">${TS.esc(t.artist)}${t.vibe_flag ? ` · ⚠ ${TS.esc(t.vibe_flag)}` : ''}</div>
        ${t.added_by_name ? `<span class="chip" data-profile>added by ${TS.esc(t.added_by_name)}</span>` : ''}
        ${t.snatched_from_name ? `<span class="chip">snatched from ${TS.esc(t.snatched_from_name)}</span>` : ''}
      </div>
      ${onSnatch && !t.in_my_library ? '<button data-snatch>Snatch</button>' : ''}`;
    el.querySelector('[data-snatch]')?.addEventListener('click', () => onSnatch(t));
    el.querySelector('[data-profile]')?.addEventListener('click', () => onProfile?.(t.added_by));
    return el;
  },
};
```

#### `public/host.html`: Host session screen
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Host Session</title>
  <!-- HERMES: existing TrackSnatcher stylesheet (current breathing background + colors). Use its real path. -->
  <link rel="stylesheet" href="/css/app.css">
  <link rel="stylesheet" href="/css/social.css">
</head>
<body>
  <main class="wrap">
    <section class="card" id="setup">
      <h1>Start a session</h1>
      <p class="dim">Road trip, campfire, party: guests scan to add songs.</p>
      <input id="name" placeholder="Your name" style="margin-bottom:8px">
      <input id="title" placeholder="Session name (e.g. Road Trip)" style="margin-bottom:8px">
      <input id="vibe" placeholder="Vibe (Campfire, Wedding Recital…)" style="margin-bottom:8px">
      <input id="pl" placeholder="Shared YouTube playlist ID (optional)" style="margin-bottom:12px">
      <button id="start">Start session</button>
    </section>

    <section class="card" id="live" hidden>
      <div class="row" style="justify-content:space-between">
        <div><h1 id="s-title"></h1><div class="dim" id="s-vibe"></div></div>
        <button id="end">End</button>
      </div>
      <div class="row" style="margin-top:12px;align-items:flex-start">
        <img id="qr" class="qr" alt="Join QR code">
        <div><div class="dim">or enter PIN</div><div id="pin" class="pin"></div>
          <div class="dim" id="members"></div></div>
      </div>
    </section>

    <section class="card" id="queue-card" hidden>
      <div class="row" style="justify-content:space-between">
        <strong>Queue</strong>
        <div class="row"><button id="dj">✨ Grok DJ</button><button id="next">Next ▶</button></div>
      </div>
      <div class="row" style="margin:10px 0"><input id="q" placeholder="Add a song…"><button id="add">Add</button></div>
      <div id="dj-out" class="dim"></div>
      <div id="queue"></div>
    </section>
  </main>

  <script src="/js/api.js"></script>
  <script>
    const $ = (id) => document.getElementById(id);
    let session, queue = [];

    $('start').onclick = async () => {
      if (!TS.token) TS.token = (await TS.api('/users', { method: 'POST', body: { display_name: $('name').value || 'Host' } })).token;
      const out = await TS.api('/sessions', { method: 'POST', body: { title: $('title').value || 'Session', vibe: $('vibe').value || 'Party', yt_playlist_id: $('pl').value || null } });
      session = out.session;
      const qr = await fetch(out.qr_url, { headers: { Authorization: `Bearer ${TS.token}` } });
      $('qr').src = URL.createObjectURL(await qr.blob());
      $('s-title').textContent = session.title; $('s-vibe').textContent = session.vibe; $('pin').textContent = session.join_pin;
      $('setup').hidden = true; $('live').hidden = false; $('queue-card').hidden = false;
      TS.socket(session.id, onEvent);
      refresh();
    };

    function onEvent(m) {
      if (m.type === 'queue.updated') render(m.data.queue);
      if (m.type === 'member.joined') { TS.toast(`${m.data.display_name} joined`); refresh(); }
      if (m.type === 'snatch.created') TS.toast(m.data.label);
    }
    async function refresh() {
      const d = await TS.api(`/sessions/${session.id}`);
      $('members').textContent = `${d.members.length} in session`;
      render(d.queue);
    }
    function render(q) {
      queue = q; $('queue').replaceChildren(...q.map((t) => TS.trackRow(t, { onProfile: (id) => location.href = `/profile.html?u=${id}` })));
    }
    $('add').onclick = async () => { const v = $('q').value.trim(); if (!v) return; $('q').value = '';
      try { await TS.api(`/sessions/${session.id}/queue`, { method: 'POST', body: { query: v } }); } catch (e) { TS.toast(e.message); } };
    $('next').onclick = async () => { const n = queue.find((t) => t.status === 'queued'); if (n) await TS.api(`/sessions/${session.id}/now-playing`, { method: 'POST', body: { session_track_id: n.id } }); };
    $('dj').onclick = async () => {
      $('dj-out').textContent = 'Grok is listening to the room…';
      const d = await TS.api(`/sessions/${session.id}/dj/suggest`, { method: 'POST' });
      $('dj-out').innerHTML = (d.suggestions || []).map((s) => `• ${TS.esc(s.artist)} – ${TS.esc(s.title)} <span class="dim">${TS.esc(s.why || '')}</span>`).join('<br>') || 'Fair order ready.';
      if (confirm('Apply Grok\'s fair play order?')) await TS.api(`/sessions/${session.id}/queue/order`, { method: 'PUT', body: { order: d.order } });
    };
    $('end').onclick = async () => { if (confirm('End session?')) { await TS.api(`/sessions/${session.id}/end`, { method: 'POST' }); location.reload(); } };
  </script>
</body>
</html>
```

#### `public/join.html`: Guest join + live queue
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Join Session</title>
  <!-- HERMES: existing TrackSnatcher stylesheet (current breathing background + colors). Use its real path. -->
  <link rel="stylesheet" href="/css/app.css">
  <link rel="stylesheet" href="/css/social.css">
</head>
<body>
  <main class="wrap">
    <section class="card" id="gate">
      <h1>Join the session</h1>
      <p class="dim">Add songs to the shared queue. No account needed.</p>
      <input id="name" placeholder="Your name" style="margin-bottom:8px">
      <input id="pin" inputmode="numeric" maxlength="6" placeholder="6-digit PIN" style="margin-bottom:12px">
      <button id="join">Join</button>
    </section>

    <section class="card" id="room" hidden>
      <h1 id="s-title"></h1><div class="dim" id="s-vibe"></div>
      <div class="row" style="margin-top:12px"><input id="q" placeholder="Search a song to queue…"><button id="add">Queue</button></div>
    </section>
    <section class="card" id="queue-card" hidden><strong>Up next</strong><div id="queue"></div></section>
  </main>

  <script src="/js/api.js"></script>
  <script>
    const $ = (id) => document.getElementById(id);
    const qrToken = new URLSearchParams(location.search).get('s');
    if (qrToken) $('pin').hidden = true;
    let session, me;

    $('join').onclick = async () => {
      try {
        const out = await TS.api('/sessions/join', { method: 'POST', body: { token: qrToken, pin: $('pin').value, display_name: $('name').value || 'Guest' } });
        if (out.token) TS.token = out.token;
        session = out.session; me = out.user;
        $('s-title').textContent = session.title; $('s-vibe').textContent = session.vibe;
        $('gate').hidden = true; $('room').hidden = false; $('queue-card').hidden = false;
        render(out.queue);
        TS.socket(session.id, (m) => {
          if (m.type === 'queue.updated') render(m.data.queue);
          if (m.type === 'snatch.created') TS.toast(m.data.label);
          if (m.type === 'session.ended') TS.toast('Session ended');
        });
      } catch (e) { TS.toast(e.message); }
    };
    function render(q) {
      $('queue').replaceChildren(...q.map((t) => TS.trackRow(t, {
        onProfile: (id) => location.href = `/profile.html?u=${id}&s=${session.id}`,
        onSnatch: t.added_by !== me.id ? snatch : null,
      })));
    }
    async function snatch(t) {
      const d = await TS.api('/snatch', { method: 'POST', body: { track_id: t.track_id, from_user_id: t.added_by, context: 'session', session_id: session.id } });
      TS.toast(d.label);
    }
    $('add').onclick = async () => { const v = $('q').value.trim(); if (!v) return; $('q').value = '';
      try { await TS.api(`/sessions/${session.id}/queue`, { method: 'POST', body: { query: v } }); TS.toast('Queued!'); } catch (e) { TS.toast(e.message); } };
  </script>
</body>
</html>
```

#### `public/profile.html`: Contributor profile + library browse
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Contributor Profile</title>
  <!-- HERMES: existing TrackSnatcher stylesheet (current breathing background + colors). Use its real path. -->
  <link rel="stylesheet" href="/css/app.css">
  <link rel="stylesheet" href="/css/social.css">
</head>
<body>
  <main class="wrap">
    <section class="card">
      <div class="row"><img id="av" class="avatar" style="width:56px;height:56px" alt="">
        <div><h1 id="name"></h1><div class="dim" id="stats"></div></div></div>
      <div class="row" style="margin-top:12px;flex-wrap:wrap" id="links"></div>
    </section>
    <section class="card"><strong>Snatched Library</strong><div id="lib"></div></section>
  </main>
  <script src="/js/api.js"></script>
  <script>
    const p = new URLSearchParams(location.search), uid = p.get('u'), sid = p.get('s');
    (async () => {
      try {
        const u = await TS.api(`/users/${uid}`);
        document.getElementById('name').textContent = u.display_name;
        if (u.avatar_url) document.getElementById('av').src = u.avatar_url;
        document.getElementById('stats').textContent = `${u.library_count} tracks · snatched from ${u.snatched_from_count}×`;
        const links = document.getElementById('links');
        if (u.youtube_url) links.insertAdjacentHTML('beforeend', `<a class="btn" target="_blank" rel="noopener" href="${TS.esc(u.youtube_url)}">YouTube</a>`);
        if (u.spotify_url) links.insertAdjacentHTML('beforeend', `<a class="btn" target="_blank" rel="noopener" href="${TS.esc(u.spotify_url)}">Spotify</a>`);
        const lib = await TS.api(`/users/${uid}/library`);
        document.getElementById('lib').replaceChildren(...lib.tracks.map((t) => TS.trackRow(t, {
          onSnatch: async (tr) => {
            const d = await TS.api('/snatch', { method: 'POST', body: { track_id: tr.id, from_user_id: uid, context: sid ? 'session' : 'profile', session_id: sid } });
            TS.toast(d.label);
          },
        })));
      } catch (e) { TS.toast(e.status === 404 ? 'This profile is private' : e.message); }
    })();
  </script>
</body>
</html>
```

#### `public/nearby.html`: Nearby radar ("Camp / Bar Napster Mode")
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Nearby Snatchers</title>
  <!-- HERMES: existing TrackSnatcher stylesheet (current breathing background + colors). Use its real path. -->
  <link rel="stylesheet" href="/css/app.css">
  <link rel="stylesheet" href="/css/social.css">
</head>
<body>
  <main class="wrap">
    <section class="card">
      <h1>Nearby</h1>
      <p class="dim">See what people around you have snatched. Your exact location is never shared.</p>
      <div class="row" role="radiogroup" aria-label="Visibility">
        <button data-vis="ghost">👻 Ghost</button>
        <button data-vis="friends">Friends</button>
        <button data-vis="public">Public</button>
      </div>
    </section>
    <section class="card"><div id="list" class="dim">Scanning…</div></section>
  </main>
  <script src="/js/api.js"></script>
  <script>
    const list = document.getElementById('list');
    let timer;

    async function setVis(v) {
      const me = await TS.api('/users/me', { method: 'PATCH', body: { visibility: v } });
      document.querySelectorAll('[data-vis]').forEach((b) => b.classList.toggle('selected', b.dataset.vis === me.visibility));
      clearInterval(timer);
      if (v === 'ghost') { await TS.api('/presence', { method: 'DELETE' }); list.textContent = 'Ghost mode: you are invisible and not scanning.'; return; }
      tick(); timer = setInterval(tick, 60000);
    }
    document.querySelectorAll('[data-vis]').forEach((b) => b.onclick = () => setVis(b.dataset.vis));

    function tick() {
      navigator.geolocation.getCurrentPosition(async ({ coords }) => {
        await TS.api('/presence/beacon', { method: 'POST', body: { lat: coords.latitude, lon: coords.longitude } });
        const { users } = await TS.api('/presence/nearby');
        if (!users.length) { list.textContent = 'Nobody nearby yet.'; return; }
        list.replaceChildren(...users.map((u) => {
          const el = document.createElement('div'); el.className = 'track';
          el.innerHTML = `<img class="avatar" src="${TS.esc(u.avatar_url || '')}" alt="">
            <div class="meta"><div class="title">${TS.esc(u.display_name)}</div>
            <div class="dim">${u.library_count} tracks · ${u.recent.map((t) => TS.esc(t.title)).join(', ')}</div></div>
            <button>Browse</button>`;
          el.querySelector('button').onclick = () => location.href = `/profile.html?u=${u.id}`;
          return el;
        }));
      }, () => list.textContent = 'Location permission is needed for Nearby (only a ~150 m area code is kept).', { enableHighAccuracy: false, maximumAge: 60000 });
    }

    (async () => {
      if (!TS.token) TS.token = (await TS.api('/users', { method: 'POST', body: { display_name: prompt('Display name?') || 'Snatcher' } })).token;
      const me = await TS.api('/users/me');
      setVis(me.visibility);
    })();
  </script>
</body>
</html>
```

### BLE layer (native app / Termux:API): integration notes for Hermes
Browsers can't advertise over BLE, so this part lives in the native Android client (or a small Kotlin helper that the Termux client calls):
1. Call `POST /api/presence/ble-token` and get `{eph_id, service_uuid}`. Rotate whenever `expires_at` passes.
2. Advertise `service_uuid` with `eph_id` (16 bytes) as service data. Use `AdvertiseSettings.ADVERTISE_MODE_LOW_POWER`, and send no device name.
3. Scan with a `ScanFilter` on `service_uuid`. Collect the `eph_id`s you hear, de-duplicate them, and every 30 s send them to `POST /api/presence/resolve`.
4. In ghost mode, stop advertising **and** scanning.
5. Merge the BLE results with the `/api/presence/nearby` results by user `id`. BLE hits rank first because they're physically closest.

### Acceptance checklist
- [ ] Host starts a session → QR + PIN show up; a guest scans and is in the queue in under 5 s with no signup.
- [ ] Every queue row shows `added_by`; tapping it opens the profile with YouTube/Spotify links.
- [ ] A guest snatches a host track → everyone in the session sees "Snatched by B from A" and the MP3 job hits Node-RED.
- [ ] Grok DJ returns a fair order; with `GROK_ENABLED=false` it falls back to round-robin and nothing breaks.
- [ ] Ghost mode → absent from `/nearby` and `/resolve`, and beacons are ignored.
- [ ] No API response ever contains lat/lon or a geohash.
- [ ] New screens keep the current breathing background and colors, unchanged.
