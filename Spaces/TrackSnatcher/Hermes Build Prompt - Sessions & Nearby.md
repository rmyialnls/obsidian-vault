---
tags: [tracksnatcher, hermes, grok, build-prompt]
project: TrackSnatcher (HermesListen)
modules: [shared-sessions, nearby-discovery]
---

# TrackSnatcher APK: Hermes build prompt (DJ Sessions + Nearby "Napster Mode")

> Paste everything from **PROMPT FOR HERMES** down into the Hermes agent. The prompt comes first, then the reference code: the backend, then the Kotlin + Jetpack Compose code for the APK.

---

## PROMPT FOR HERMES

### Role
You are the lead Android + backend developer for **TrackSnatcher (HermesListen)**. TrackSnatcher is an **Android APK written in Kotlin + Jetpack Compose**. The existing system already does:
- Live audio identification (mic capture → AudD API → MusicBrainz enrichment)
- Automated downloading (`/download -MP3`) and YouTube playlist appends through Node-RED flows

You are adding two modules to the APK:
1. **Shared Session / Event DJ Mode.**
   - The host starts a session (Road Trip, Campfire, Wedding Recital) and the app shows a QR code and a 6-digit PIN.
   - Guests scan the QR with the app (or with the phone camera, which opens the APK through the `tracksnatcher://join?s=TOKEN` deep link) or type the PIN, then add tracks to a shared live queue.
   - Every track shows who added it. Tapping that person opens their profile: YouTube/Spotify links and their Snatched Library.
   - Snatches are tracked as lineage ("Snatched by B from A").
2. **Nearby Discovery ("Camp / Bar Napster Mode").**
   - Users who opt in see other TrackSnatcher users close by: Bluetooth LE for people in the same room, plus a ~150 m server-side area match.
   - They can browse a nearby user's public Snatched Library and one-tap **Snatch** a track. Snatching copies it to their library and triggers `/download -MP3` and/or the YouTube playlist append.

A small Node backend (included below) holds the shared state, so phones can see each other's sessions, queues and libraries. It runs on a server, or in Termux on the host's phone for offline hotspot sessions. **The APK talks to it over REST + WebSocket.** Grok is called only from the backend, so the xAI key never ships inside the APK.

### Brains: Grok (xAI)
**Grok is the reasoning engine for every AI decision in these modules.** Use the xAI API (OpenAI-compatible) at `https://api.x.ai/v1/chat/completions` with `XAI_API_KEY` from the backend environment. The model goes in `XAI_MODEL` (default `grok-4`). Do not hard-code any other LLM provider.

Grok is responsible for:
1. **Intent parsing.** Turn chat commands ("queue some Zeppelin", "snatch what Jake just added", "/download -MP3 that last one") into a strict JSON action.
2. **Metadata cleanup.** Normalize messy guest searches and AudD results into `{artist, title}` before the MusicBrainz lookup.
3. **Session DJ.** Given the queue, the session vibe and the contributors, suggest the next tracks and a fair play order so one guest can't take over the queue.
4. **Vibe check.** Flag tracks that clash with the session vibe. The host decides; Grok never auto-removes anything.

Rules for Grok calls:
- Always request JSON and validate it.
- On a parse failure or timeout, fall back to deterministic behaviour: round-robin queue order, raw query to MusicBrainz. The app must keep working with Grok offline.
- Never send Grok location, device files, auth tokens or emails. Send only track metadata, display names and the vibe string.
- Use a 12 s timeout and cache identical normalization prompts for 10 minutes.

### UI: keep the current breathing background and colors
All new screens (Host Session, Join Session / Live Queue, Contributor Profile, Nearby) **keep the current breathing background and colors exactly as they are on the existing TrackSnatcher screens.**
- Each new screen is wrapped in the app's **existing** breathing background composable, through the `background` parameter of `socialGraph()`.
- Colors come from the app's existing `MaterialTheme`.
- Don't change, recolor or recreate the background or the theme.

### Deliverables
1. Add the `com.tracksnatcher.social` Kotlin package below to the APK. Rename the package to match the app's own package, and wire `socialGraph()` into the existing `NavHost`.
2. Add the Gradle dependencies, the manifest permissions and the deep link below.
3. Deploy the backend below. Reuse the existing AudD, MusicBrainz and Node-RED modules where they already exist instead of duplicating them.
4. Add entry points in the existing UI:
   - "Start session" → `SocialRoutes.HOST`
   - "Join session" → `SocialRoutes.join()`
   - "Nearby" → `SocialRoutes.NEARBY`
   - After an AudD identify, an "Add to session?" action that calls `POST /api/sessions/:id/queue` with `{identified: {...}}`.
5. Work through the roadmap below in order and check each item off in the PR description.

### Architecture overview
```
┌─────────────────────┐   QR / PIN / deep link   ┌──────────────────────────────────────────┐
│ Host phone (APK)    │─────────────────────────▶│  TrackSnatcher backend (Node, Express)   │
│ Compose + OkHttp    │◀────────── WS ──────────▶│  ├─ REST /api/*                          │
└─────────────────────┘                          │  ├─ WS   /ws  (session rooms)            │
┌─────────────────────┐   scan QR (ZXing)        │  ├─ SQLite (better-sqlite3)              │
│ Guest phones (APK)  │─────────────────────────▶│  ├─ grok.js ──────▶ xAI Grok API         │
│                     │◀────────── WS ──────────▶│  ├─ resolver.js ──▶ MusicBrainz          │
└─────────────────────┘                          │  ├─ pipeline.js ──▶ Node-RED             │
┌─────────────────────┐  BLE ephemeral ids       │  │      ├─ /download -MP3 flow           │
│ Nearby phones (APK) │◀──── (phone to phone) ──▶│  │      └─ YouTube playlist append flow  │
│                     │── geohash beacon ───────▶│  └─ mdns.js (LAN advertise for offline)  │
└─────────────────────┘                          └──────────────────────────────────────────┘
```
- **Session flow:**
  1. The host app calls `POST /api/sessions` and draws the returned `join_url` (`tracksnatcher://join?s=TOKEN`) as a QR code with ZXing.
  2. A guest scans it (in-app scanner, or the phone camera, which opens the deep link) and the app calls `POST /api/sessions/join`, which returns a guest token.
  3. The app opens the WebSocket for live queue updates.
  4. When a guest adds a track: Grok normalizes the query, MusicBrainz resolves it, the row is inserted with `added_by`, the WS broadcasts `queue.updated`, and Node-RED appends it to the host's YouTube playlist.
- **Snatch flow:** `POST /api/snatch {track_id, from_user_id, context}` does four things:
  1. Copies the track into the caller's library.
  2. Writes a lineage row.
  3. Triggers Node-RED `/download -MP3` and/or the YouTube append.
  4. Broadcasts `snatch.created` ("Snatched by B from A").
- **Nearby flow:**
  1. **Bluetooth LE.** The app gets a rotating 16-byte ephemeral id from `/api/presence/ble-token`. It advertises that id as manufacturer data (company id `0xFFFF`, `"TS"` prefix) and scans for other phones' ids. Every 30 s it resolves the heard ids through `/api/presence/resolve`. No user id, name or MAC address goes over the air.
  2. **Area match.** The app sends coarse location every 30 s. The server keeps only a 7-character geohash (~150 m), **throws away the coordinates**, and `/api/presence/nearby` returns users in the same cell and the 8 cells around it.
  3. Results are merged; Bluetooth hits rank first and are labelled "right here".
- **Privacy:** `ghost` = invisible, beacons ignored, BLE stopped. `friends` = visible only to mutual friends. `public` = visible to anyone nearby. Only track metadata and streaming URLs are ever shared. No file paths, no coordinates.

### Implementation roadmap (do in order)
1. Backend: dependencies + env vars (`XAI_API_KEY`, `XAI_MODEL`, `NODE_RED_URL`, `DB_PATH`), schema, token auth.
2. Backend: `grok.js` with JSON-mode calls, timeout, cache and fallbacks. Test the fallback path with Grok disabled.
3. Backend: `resolver.js` (Grok → MusicBrainz) and `pipeline.js` wired to the existing Node-RED flows.
4. Backend: sessions, profiles, snatch + lineage, presence, WebSocket rooms, Grok DJ, mDNS.
5. APK: Gradle deps, manifest permissions + `tracksnatcher://join` deep link, `SOCIAL_BASE_URL` build config.
6. APK: `SocialApi` (OkHttp + kotlinx.serialization) and the `SocialApi.configure()` call.
7. APK: Host and Join screens (QR draw + ZXing scan), live queue over WebSocket, Grok DJ button.
8. APK: Profile screen (Snatched Library + Snatch + YouTube/Spotify links).
9. APK: Nearby screen (permissions, visibility chips, BLE + area discovery).
10. APK: wire `socialGraph()` into the existing NavHost with the **existing breathing background** passed as `background`, then add the entry points.
11. APK: "Add to session?" after an AudD identify; route the existing `/download -MP3` command through `POST /api/agent/command`.
12. Hardening: rate limits, input caps, 12 h session expiry, ghost-mode test on two phones, denied-permission paths.

---

## REFERENCE CODE

### Backend
Stack: Node 18+ (native `fetch`), Express 4, `ws`, `better-sqlite3`, `ngeohash`, `bonjour-service`. Runs on a server or directly in Termux (`pkg install nodejs`).

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
    "ws": "^8.18.0"
  }
}
```

#### `.env.example`
```bash
PORT=8787
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
const { db, id, now } = require('../db');
const { createUser, requireAuth, userFromToken } = require('../auth');
const { resolveTrack } = require('../resolver');
const pipeline = require('../pipeline');
const grok = require('../grok');
const rt = require('../realtime');

const r = express.Router();
const SESSION_TTL = 12 * 3600;

const getSession = (sid) => db.prepare("SELECT * FROM sessions WHERE id = ? AND status = 'active' AND expires_at > ?").get(sid, now());
const isMember = (sid, uid) => db.prepare('SELECT role FROM session_members WHERE session_id = ? AND user_id = ?').get(sid, uid);
const joinUrl = (s) => `tracksnatcher://join?s=${s.join_token}`; // APK deep link; the app draws it as a QR

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
  res.status(201).json({ session: s, join_url: joinUrl(s) });
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
    bonjour.publish({ name: `TrackSnatcher-${process.pid}`, type: 'tracksnatcher', port, txt: { v: '1' } });
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
| POST | `/api/sessions` | auth | Host starts a session → PIN + `join_url` deep link (the app draws the QR) |
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

### Android APK (Kotlin + Jetpack Compose)
Package `com.tracksnatcher.social`; rename it to the app's own package. The screens don't draw any background of their own. `socialGraph()` wraps each one in the app's **existing** breathing background, and all colors come from the app's existing `MaterialTheme`.

#### `app/build.gradle.kts` (add to the existing file)
```kotlin
plugins {
    // add next to the existing Kotlin plugin, same Kotlin version as the app
    id("org.jetbrains.kotlin.plugin.serialization") version "<app's Kotlin version>"
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Address of the TrackSnatcher social backend (LAN/hotspot IP or domain)
        buildConfigField("String", "SOCIAL_BASE_URL", "\"http://192.168.43.1:8787\"")
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.zxing:core:3.5.3")                       // draw the QR
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")       // scan the QR
    implementation("io.coil-kt:coil-compose:2.6.0")                       // artwork + avatars
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")        // skip if already present
}
```

#### `app/src/main/AndroidManifest.xml` (add to the existing file)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- BLE: Android 12+ -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <!-- BLE: Android 11 and below -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <!-- usesCleartextTraffic only if the backend is plain http on the LAN/hotspot -->
    <application android:usesCleartextTraffic="true">
        <activity android:name=".MainActivity">
            <!-- QR codes contain tracksnatcher://join?s=TOKEN, so the phone camera opens the app -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="tracksnatcher" android:host="join" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### Wire into the existing `NavHost`
```kotlin
NavHost(navController, startDestination = /* existing */) {
    // ...existing destinations...
    socialGraph(
        navController = navController,
        serverUrl = BuildConfig.SOCIAL_BASE_URL,
        displayName = currentUserName,                                  // the name the user already has in the app
        background = { content -> BreathingBackground { content() } },  // HERMES: the app's EXISTING breathing background composable, unchanged
    )
}
// Entry points from existing screens:
// navController.navigate(SocialRoutes.HOST)    // Start session
// navController.navigate(SocialRoutes.join())  // Join session
// navController.navigate(SocialRoutes.NEARBY)  // Nearby
```

#### `app/src/main/java/com/tracksnatcher/social/data/Models.kt`
```kotlin
package com.tracksnatcher.social.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class User(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    @SerialName("spotify_url") val spotifyUrl: String? = null,
    val visibility: String? = null,
)

@Serializable
data class AuthResponse(val user: User, val token: String)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val vibe: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("join_pin") val joinPin: String? = null,
)

@Serializable
data class CreateSessionResponse(val session: Session, @SerialName("join_url") val joinUrl: String)

@Serializable
data class QueueItem(
    val id: String,
    val status: String,
    @SerialName("vibe_flag") val vibeFlag: String? = null,
    @SerialName("track_id") val trackId: String,
    val title: String,
    val artist: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("added_by") val addedBy: String,
    @SerialName("added_by_name") val addedByName: String,
    @SerialName("added_by_avatar") val addedByAvatar: String? = null,
)

@Serializable
data class JoinResponse(
    val token: String? = null,
    val user: User,
    val session: Session,
    val queue: List<QueueItem> = emptyList(),
)

@Serializable
data class Member(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String,
)

@Serializable
data class SessionDetail(val session: Session, val members: List<Member>, val queue: List<QueueItem>)

@Serializable
data class QueueResponse(val queue: List<QueueItem>)

@Serializable
data class Suggestion(val artist: String? = null, val title: String? = null, val why: String? = null)

@Serializable
data class DjResponse(val order: List<String> = emptyList(), val suggestions: List<Suggestion> = emptyList())

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    @SerialName("spotify_url") val spotifyUrl: String? = null,
    @SerialName("library_count") val libraryCount: Int = 0,
    @SerialName("snatched_from_count") val snatchedFromCount: Int = 0,
    @SerialName("is_friend") val isFriend: Boolean = false,
)

@Serializable
data class LibraryTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("snatched_from_name") val snatchedFromName: String? = null,
    @SerialName("in_my_library") val inMyLibrary: Int = 0, // SQLite EXISTS -> 0/1
)

@Serializable
data class LibraryResponse(val tracks: List<LibraryTrack>)

@Serializable
data class SnatchResponse(val label: String)

@Serializable
data class RecentTrack(
    val id: String,
    val title: String,
    val artist: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
)

@Serializable
data class NearbyUser(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("library_count") val libraryCount: Int = 0,
    val recent: List<RecentTrack> = emptyList(),
)

@Serializable
data class NearbyResponse(val users: List<NearbyUser>)

@Serializable
data class BleToken(
    @SerialName("eph_id") val ephId: String? = null,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val ghost: Boolean = false,
)

@Serializable
data class WsEvent(val type: String, val data: JsonElement? = null)
```

#### `app/src/main/java/com/tracksnatcher/social/data/SocialApi.kt`: REST + WebSocket client
```kotlin
package com.tracksnatcher.social.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Talks to the TrackSnatcher social backend (REST + WebSocket). One instance per app. */
class SocialApi private constructor(context: Context, private val baseUrl: String) {

    class ApiException(val code: Int, message: String) : Exception(message)

    private val prefs = context.getSharedPreferences("tracksnatcher_social", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // Grok DJ calls can take a few seconds
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    val json = Json { ignoreUnknownKeys = true }

    var token: String?
        get() = prefs.getString("token", null)
        private set(value) = prefs.edit().putString("token", value).apply()

    private suspend fun raw(method: String, path: String, body: JsonObject? = null): String = withContext(Dispatchers.IO) {
        val requestBody = when {
            body != null -> body.toString().toRequestBody(jsonType)
            method == "GET" || method == "DELETE" -> null
            else -> "{}".toRequestBody(jsonType)
        }
        val request = Request.Builder()
            .url("$baseUrl/api$path")
            .method(method, requestBody)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw ApiException(res.code, error ?: "HTTP ${res.code}")
            }
            text
        }
    }

    private suspend inline fun <reified T> call(method: String, path: String, body: JsonObject? = null): T =
        json.decodeFromString(raw(method, path, body))

    // ---- users / profiles ----
    /** Registers this device once, using the name the user already has in the app. */
    suspend fun ensureUser(displayName: String): String {
        token?.let { return it }
        val out: AuthResponse = call("POST", "/users", buildJsonObject { put("display_name", displayName) })
        token = out.token
        return out.token
    }

    suspend fun me(): User = call("GET", "/users/me")
    suspend fun setVisibility(visibility: String): User =
        call("PATCH", "/users/me", buildJsonObject { put("visibility", visibility) })
    suspend fun profile(userId: String): Profile = call("GET", "/users/$userId")
    suspend fun library(userId: String): LibraryResponse = call("GET", "/users/$userId/library")

    // ---- sessions ----
    suspend fun createSession(title: String, vibe: String, playlistId: String?): CreateSessionResponse =
        call("POST", "/sessions", buildJsonObject {
            put("title", title); put("vibe", vibe); put("yt_playlist_id", playlistId)
        })

    suspend fun joinSession(qrToken: String?, pin: String?, displayName: String): JoinResponse {
        val out: JoinResponse = call("POST", "/sessions/join", buildJsonObject {
            put("token", qrToken); put("pin", pin); put("display_name", displayName)
        })
        out.token?.let { token = it } // new guest account
        return out
    }

    suspend fun session(sessionId: String): SessionDetail = call("GET", "/sessions/$sessionId")
    suspend fun addToQueue(sessionId: String, query: String) {
        raw("POST", "/sessions/$sessionId/queue", buildJsonObject { put("query", query) })
    }
    suspend fun setOrder(sessionId: String, order: List<String>) {
        raw("PUT", "/sessions/$sessionId/queue/order", buildJsonObject { putJsonArray("order") { order.forEach { add(it) } } })
    }
    suspend fun nowPlaying(sessionId: String, sessionTrackId: String) {
        raw("POST", "/sessions/$sessionId/now-playing", buildJsonObject { put("session_track_id", sessionTrackId) })
    }
    suspend fun djSuggest(sessionId: String): DjResponse = call("POST", "/sessions/$sessionId/dj/suggest")
    suspend fun endSession(sessionId: String) { raw("POST", "/sessions/$sessionId/end") }

    // ---- snatch ----
    suspend fun snatch(trackId: String, fromUserId: String?, context: String, sessionId: String?): SnatchResponse =
        call("POST", "/snatch", buildJsonObject {
            put("track_id", trackId); put("from_user_id", fromUserId); put("context", context); put("session_id", sessionId)
        })

    // ---- nearby ----
    suspend fun beacon(lat: Double, lon: Double) {
        raw("POST", "/presence/beacon", buildJsonObject { put("lat", lat); put("lon", lon) })
    }
    suspend fun nearby(): NearbyResponse = call("GET", "/presence/nearby")
    suspend fun bleToken(): BleToken = call("POST", "/presence/ble-token")
    suspend fun resolveBle(ephIds: List<String>): NearbyResponse =
        call("POST", "/presence/resolve", buildJsonObject { putJsonArray("eph_ids") { ephIds.forEach { add(it) } } })
    suspend fun goDark() { raw("DELETE", "/presence") }

    /** Live session events. Reconnects with backoff until the collector is cancelled. */
    fun events(sessionId: String): Flow<WsEvent> = callbackFlow {
        val url = "$baseUrl/ws?token=${URLEncoder.encode(token.orEmpty(), "UTF-8")}&session=$sessionId"
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<WsEvent>(text) }.onSuccess { trySend(it) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@callbackFlow.close(t)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@callbackFlow.close()
            }
        })
        awaitClose { socket.close(1000, null) }
    }.retryWhen { _, attempt ->
        delay(minOf(15_000L, 1_000L shl attempt.toInt().coerceAtMost(4)))
        true
    }

    companion object {
        @Volatile private var instance: SocialApi? = null
        @Volatile private var baseUrl: String = ""

        /** Call once (socialGraph does it) with the backend address, e.g. BuildConfig.SOCIAL_BASE_URL. */
        fun configure(serverUrl: String) { baseUrl = serverUrl.trimEnd('/') }

        fun get(context: Context): SocialApi = instance ?: synchronized(this) {
            instance ?: SocialApi(context.applicationContext, baseUrl).also { instance = it }
        }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/SessionViewModel.kt`
```kotlin
package com.tracksnatcher.social

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracksnatcher.social.data.QueueItem
import com.tracksnatcher.social.data.QueueResponse
import com.tracksnatcher.social.data.Session
import com.tracksnatcher.social.data.SocialApi
import com.tracksnatcher.social.data.WsEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class SessionUiState(
    val session: Session? = null,
    val joinUrl: String? = null,
    val isHost: Boolean = false,
    val myId: String? = null,
    val queue: List<QueueItem> = emptyList(),
    val memberCount: Int = 0,
    val djText: String? = null,
    val pendingOrder: List<String>? = null,
    val busy: Boolean = false,
    val ended: Boolean = false,
)

/** Shared by the host and guest screens: one live session, its queue and its WebSocket. */
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val api = SocialApi.get(app)
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts
    private var events: Job? = null

    private val sessionId get() = _state.value.session?.id

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _toasts.tryEmit(e.message ?: "Something went wrong")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    fun startSession(displayName: String, title: String, vibe: String, playlistId: String) = launchSafe {
        api.ensureUser(displayName)
        val out = api.createSession(title.ifBlank { "Session" }, vibe.ifBlank { "Party" }, playlistId.ifBlank { null })
        _state.value = SessionUiState(session = out.session, joinUrl = out.joinUrl, isHost = true, myId = out.session.hostId)
        listen(out.session.id)
        refresh()
    }

    fun join(qrToken: String?, pin: String, displayName: String) = launchSafe {
        val out = api.joinSession(qrToken, if (qrToken == null) pin.trim() else null, displayName.ifBlank { "Guest" })
        _state.value = SessionUiState(session = out.session, myId = out.user.id, queue = out.queue)
        listen(out.session.id)
    }

    fun add(query: String) = launchSafe {
        val sid = sessionId ?: return@launchSafe
        if (query.isBlank()) return@launchSafe
        api.addToQueue(sid, query.trim())
        if (!_state.value.isHost) _toasts.tryEmit("Queued!")
    }

    fun next() = launchSafe {
        val sid = sessionId ?: return@launchSafe
        val upNext = _state.value.queue.firstOrNull { it.status == "queued" } ?: return@launchSafe
        api.nowPlaying(sid, upNext.id)
    }

    /** Grok DJ: fair order + suggestions. The host still has to tap "Apply". */
    fun askDj() = launchSafe {
        val sid = sessionId ?: return@launchSafe
        _state.update { it.copy(djText = "Grok is listening to the room…") }
        val out = api.djSuggest(sid)
        val text = out.suggestions.joinToString("\n") { "• ${it.artist.orEmpty()} – ${it.title.orEmpty()}  ${it.why.orEmpty()}" }
        _state.update { it.copy(djText = text.ifBlank { "Fair order ready." }, pendingOrder = out.order) }
    }

    fun applyDjOrder() = launchSafe {
        val sid = sessionId ?: return@launchSafe
        val order = _state.value.pendingOrder ?: return@launchSafe
        api.setOrder(sid, order)
        _state.update { it.copy(pendingOrder = null, djText = null) }
    }

    fun snatch(item: QueueItem) = launchSafe {
        val out = api.snatch(item.trackId, item.addedBy, "session", sessionId)
        _toasts.tryEmit(out.label)
    }

    fun end() = launchSafe {
        val sid = sessionId ?: return@launchSafe
        api.endSession(sid)
        events?.cancel()
        _state.value = SessionUiState()
    }

    private suspend fun refresh() {
        val sid = sessionId ?: return
        val detail = api.session(sid)
        _state.update { it.copy(queue = detail.queue, memberCount = detail.members.size) }
    }

    private fun listen(sid: String) {
        events?.cancel()
        events = viewModelScope.launch {
            api.events(sid).collect { handle(it) }
        }
    }

    private suspend fun handle(event: WsEvent) {
        when (event.type) {
            "queue.updated" -> event.data?.let { data ->
                val queue = api.json.decodeFromJsonElement(QueueResponse.serializer(), data).queue
                _state.update { it.copy(queue = queue) }
            }
            "member.joined" -> {
                event.data.str("display_name")?.let { _toasts.tryEmit("$it joined") }
                runCatching { refresh() }
            }
            "snatch.created" -> event.data.str("label")?.let { _toasts.tryEmit(it) }
            "session.ended" -> {
                _toasts.tryEmit("Session ended")
                _state.update { it.copy(ended = true) }
                events?.cancel()
            }
        }
    }

    private fun JsonElement?.str(key: String) = (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
}
```

#### `app/src/main/java/com/tracksnatcher/social/NearbyViewModel.kt`
```kotlin
package com.tracksnatcher.social

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tracksnatcher.social.ble.BleProximity
import com.tracksnatcher.social.data.NearbyUser
import com.tracksnatcher.social.data.SocialApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class NearbyUiState(
    val ready: Boolean = false,
    val visibility: String = "friends",
    val users: List<NearbyUser> = emptyList(),
    val inBluetoothRange: Set<String> = emptySet(),
    val status: String? = "Scanning…",
    val locationAllowed: Boolean = false,
    val bluetoothAllowed: Boolean = false,
)

class NearbyViewModel(app: Application) : AndroidViewModel(app) {
    private val api = SocialApi.get(app)
    private val ble = BleProximity(app)
    private val location = LocationServices.getFusedLocationProviderClient(app)
    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()
    private var loop: Job? = null

    fun init(displayName: String) = viewModelScope.launch {
        try {
            api.ensureUser(displayName)
            val me = api.me()
            _state.update { it.copy(ready = true, visibility = me.visibility ?: "friends") }
            maybeStart()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = e.message) }
        }
    }

    fun onPermissions(locationAllowed: Boolean, bluetoothAllowed: Boolean) {
        _state.update { it.copy(locationAllowed = locationAllowed, bluetoothAllowed = bluetoothAllowed) }
        maybeStart()
    }

    fun setVisibility(visibility: String) = viewModelScope.launch {
        try {
            val me = api.setVisibility(visibility)
            _state.update { it.copy(visibility = me.visibility ?: visibility) }
            if (visibility == "ghost") goDark() else maybeStart()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = e.message) }
        }
    }

    private fun maybeStart() {
        val s = _state.value
        if (!s.ready || s.visibility == "ghost") return
        if (!s.locationAllowed && !s.bluetoothAllowed) {
            _state.update { it.copy(status = "Allow location or Bluetooth to find people nearby. Only a ~150 m area code is kept.") }
            return
        }
        startLoop()
    }

    @SuppressLint("MissingPermission") // checked via locationAllowed
    private fun startLoop() {
        loop?.cancel()
        loop = viewModelScope.launch {
            var bleExpires = 0L
            while (isActive) {
                try {
                    val s = _state.value
                    if (s.bluetoothAllowed && System.currentTimeMillis() / 1000 >= bleExpires - 30) {
                        val t = api.bleToken()
                        if (!t.ghost && t.ephId != null && ble.start(t.ephId)) bleExpires = t.expiresAt
                    }
                    if (s.locationAllowed) {
                        location.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                            ?.let { api.beacon(it.latitude, it.longitude) }
                    }
                    val heard = if (s.bluetoothAllowed) ble.recentIds() else emptyList()
                    val close = if (heard.isNotEmpty()) api.resolveBle(heard).users else emptyList()
                    val byArea = api.nearby().users
                    val merged = (close + byArea).distinctBy { it.id } // Bluetooth hits first: physically closest
                    _state.update {
                        it.copy(users = merged, inBluetoothRange = close.map { u -> u.id }.toSet(),
                            status = if (merged.isEmpty()) "Nobody nearby yet." else null)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(status = e.message) }
                }
                delay(30_000)
            }
        }
    }

    private suspend fun goDark() {
        loop?.cancel()
        ble.stop()
        api.goDark()
        _state.update { it.copy(users = emptyList(), inBluetoothRange = emptySet(), status = "Ghost mode: you're invisible and not scanning.") }
    }

    override fun onCleared() {
        ble.stop()
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ble/BleProximity.kt`: Bluetooth LE discovery
```kotlin
package com.tracksnatcher.social.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Broadcasts this device's rotating ephemeral id (from /api/presence/ble-token) and collects the ids
 * of other TrackSnatcher phones in Bluetooth range. No user id, name or MAC goes over the air.
 * Payload: manufacturer data, company id 0xFFFF, "TS" magic + 16-byte ephemeral id (fits a legacy 31-byte ad).
 */
class BleProximity(context: Context) {

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val heard = ConcurrentHashMap<String, Long>()
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission") // caller requests BLUETOOTH_SCAN/ADVERTISE (31+) or FINE_LOCATION (<=30)
    fun start(ephIdHex: String): Boolean {
        stop()
        val bt = adapter ?: return false
        if (!bt.isEnabled) return false
        return try {
            bt.bluetoothLeAdvertiser?.let { advertiser ->
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                    .setConnectable(false)
                    .build()
                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(COMPANY_ID, MAGIC + hexToBytes(ephIdHex))
                    .build()
                val cb = object : AdvertiseCallback() {}
                advertiser.startAdvertising(settings, data, cb)
                advertiseCallback = cb
            }
            bt.bluetoothLeScanner?.let { scanner ->
                val filter = ScanFilter.Builder().setManufacturerData(COMPANY_ID, MAGIC, byteArrayOf(-1, -1)).build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
                val cb = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val bytes = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
                        if (bytes.size >= 18) heard[bytesToHex(bytes.copyOfRange(2, 18))] = SystemClock.elapsedRealtime()
                    }
                }
                scanner.startScan(listOf(filter), settings, cb)
                scanCallback = cb
            }
            true
        } catch (e: SecurityException) {
            false // permission revoked; geohash discovery still works
        }
    }

    /** Ephemeral ids heard in the last 2 minutes. */
    fun recentIds(): List<String> {
        val cutoff = SystemClock.elapsedRealtime() - 120_000
        heard.entries.removeIf { it.value < cutoff }
        return heard.keys.take(50)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
            scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) { // Bluetooth turned off
        }
        advertiseCallback = null
        scanCallback = null
        heard.clear()
    }

    private companion object {
        const val COMPANY_ID = 0xFFFF
        val MAGIC = byteArrayOf('T'.code.toByte(), 'S'.code.toByte())

        fun hexToBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        fun bytesToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ui/Components.kt`
```kotlin
package com.tracksnatcher.social.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow

/**
 * Translucent card so the app's existing breathing background shows through.
 * Colors come from the app's current MaterialTheme; nothing here sets its own palette.
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Avatar(url: String?, size: Dp = 32.dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    )
}

@Composable
fun TrackRow(
    title: String,
    artist: String,
    artworkUrl: String?,
    playing: Boolean = false,
    vibeFlag: String? = null,
    addedBy: String? = null,
    snatchedFrom: String? = null,
    onAddedByClick: (() -> Unit)? = null,
    onSnatch: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                (if (playing) "▶ " else "") + title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist + (vibeFlag?.let { " · ⚠ $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (addedBy != null) {
                    AssistChip(onClick = { onAddedByClick?.invoke() }, label = { Text("added by $addedBy", style = MaterialTheme.typography.labelSmall) })
                }
                if (snatchedFrom != null) {
                    AssistChip(onClick = {}, label = { Text("snatched from $snatchedFrom", style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        if (onSnatch != null) Button(onClick = onSnatch) { Text("Snatch") }
    }
}

@Composable
fun ToastEffect(messages: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ui/Qr.kt`
```kotlin
package com.tracksnatcher.social.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Renders the session join link (tracksnatcher://join?s=TOKEN) as a QR bitmap. */
fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

/** Pulls the session token out of a scanned QR (deep link or raw token). */
fun joinTokenFrom(scanned: String): String? =
    if (scanned.startsWith("tracksnatcher://")) Uri.parse(scanned).getQueryParameter("s")
    else scanned.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{16}$")) }
```

#### `app/src/main/java/com/tracksnatcher/social/ui/HostSessionScreen.kt`: DJ Session (host)
```kotlin
package com.tracksnatcher.social.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracksnatcher.social.SessionViewModel

/** Host: start a session, show QR + PIN, run the shared queue, ask Grok to DJ. */
@Composable
fun HostSessionScreen(
    displayName: String,
    onOpenProfile: (userId: String) -> Unit,
    vm: SessionViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    ToastEffect(vm.toasts)
    val session = state.session
    var confirmEnd by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (session == null) {
            item {
                var title by rememberSaveable { mutableStateOf("") }
                var vibe by rememberSaveable { mutableStateOf("") }
                var playlist by rememberSaveable { mutableStateOf("") }
                GlassCard {
                    Text("Start a session", style = MaterialTheme.typography.headlineSmall)
                    Text("Road trip, campfire, party: guests scan to add songs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Session name") }, singleLine = true)
                    OutlinedTextField(vibe, { vibe = it }, Modifier.fillMaxWidth(), label = { Text("Vibe (Campfire, Wedding Recital…)") }, singleLine = true)
                    OutlinedTextField(playlist, { playlist = it }, Modifier.fillMaxWidth(), label = { Text("Shared YouTube playlist ID (optional)") }, singleLine = true)
                    Button(onClick = { vm.startSession(displayName, title, vibe, playlist) }, enabled = !state.busy) { Text("Start session") }
                }
            }
        } else {
            item {
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.headlineSmall)
                            Text(session.vibe, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { confirmEnd = true }) { Text("End") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.joinUrl?.let { url ->
                            val qr = remember(url) { qrBitmap(url).asImageBitmap() }
                            Image(qr, contentDescription = "Join QR code", modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Column {
                            Text("or enter PIN", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.joinPin.orEmpty(), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                            Text("${state.memberCount} in session", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                var query by rememberSaveable { mutableStateOf("") }
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Queue", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::askDj, enabled = !state.busy) { Text("✨ Grok DJ") }
                            Button(onClick = vm::next, enabled = !state.busy) { Text("Next ▶") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Add a song…") }, singleLine = true)
                        Button(onClick = { vm.add(query); query = "" }, enabled = query.isNotBlank()) { Text("Add") }
                    }
                    state.djText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (state.pendingOrder != null) {
                        Button(onClick = vm::applyDjOrder) { Text("Apply Grok's fair play order") }
                    }
                    state.queue.forEach { t ->
                        TrackRow(
                            title = t.title, artist = t.artist, artworkUrl = t.artworkUrl,
                            playing = t.status == "playing", vibeFlag = t.vibeFlag,
                            addedBy = t.addedByName, onAddedByClick = { onOpenProfile(t.addedBy) },
                        )
                    }
                    if (state.queue.isEmpty()) Text("Nothing queued yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End session?") },
            text = { Text("Guests won't be able to add songs anymore.") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.end() }) { Text("End") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ui/JoinSessionScreen.kt`: DJ Session (guest join + live queue)
```kotlin
package com.tracksnatcher.social.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tracksnatcher.social.SessionViewModel

/**
 * Guest: join by scanning the host's QR (or opening the tracksnatcher://join deep link) or typing the PIN,
 * then add songs and snatch anything other people queued.
 */
@Composable
fun JoinSessionScreen(
    deepLinkToken: String?,
    displayName: String,
    onOpenProfile: (userId: String, sessionId: String) -> Unit,
    vm: SessionViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    ToastEffect(vm.toasts)
    var name by rememberSaveable { mutableStateOf(displayName) }
    var pin by rememberSaveable { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { joinTokenFrom(it) }?.let { vm.join(it, "", name) }
    }

    // Opened from the QR deep link: join straight away.
    LaunchedEffect(deepLinkToken) {
        if (deepLinkToken != null && state.session == null) vm.join(deepLinkToken, "", displayName)
    }

    val session = state.session
    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (session == null) {
            item {
                GlassCard {
                    Text("Join the session", style = MaterialTheme.typography.headlineSmall)
                    Text("Scan the host's QR code or type their PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true)
                    OutlinedButton(
                        onClick = { scanner.launch(ScanOptions().setPrompt("Scan the host's QR code").setBeepEnabled(false).setOrientationLocked(false)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("📷 Scan QR code") }
                    OutlinedTextField(
                        pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, Modifier.fillMaxWidth(),
                        label = { Text("or 6-digit PIN") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Button(onClick = { vm.join(null, pin, name) }, enabled = pin.length == 6 && !state.busy) { Text("Join") }
                }
            }
        } else {
            item {
                var query by rememberSaveable { mutableStateOf("") }
                GlassCard {
                    Text(session.title, style = MaterialTheme.typography.headlineSmall)
                    Text(if (state.ended) "Session ended" else session.vibe, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Search a song to queue…") }, singleLine = true)
                        Button(onClick = { vm.add(query); query = "" }, enabled = query.isNotBlank() && !state.ended) { Text("Queue") }
                    }
                }
            }
            item {
                GlassCard {
                    Text("Up next", style = MaterialTheme.typography.titleMedium)
                    state.queue.forEach { t ->
                        TrackRow(
                            title = t.title, artist = t.artist, artworkUrl = t.artworkUrl,
                            playing = t.status == "playing", vibeFlag = t.vibeFlag,
                            addedBy = t.addedByName, onAddedByClick = { onOpenProfile(t.addedBy, session.id) },
                            onSnatch = if (t.addedBy != state.myId) ({ vm.snatch(t) }) else null,
                        )
                    }
                    if (state.queue.isEmpty()) Text("Nothing queued yet. Add the first song!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ui/NearbyScreen.kt`: Nearby "Napster Mode"
```kotlin
package com.tracksnatcher.social.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracksnatcher.social.NearbyViewModel

private val PERMISSIONS = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION) // BLE scanning needs it on Android 11 and below
    }
}.toTypedArray()

/** Camp / Bar "Napster Mode": who's around you and what they've snatched. */
@Composable
fun NearbyScreen(
    displayName: String,
    onOpenProfile: (userId: String) -> Unit,
    vm: NearbyViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val location = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted[Manifest.permission.BLUETOOTH_SCAN] == true && granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true
        } else {
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        vm.onPermissions(location, bluetooth)
    }
    LaunchedEffect(Unit) {
        vm.init(displayName)
        permissions.launch(PERMISSIONS)
    }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassCard {
                Text("Nearby", style = MaterialTheme.typography.headlineSmall)
                Text("See what people around you have snatched. Your exact location is never shared.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ghost" to "👻 Ghost", "friends" to "Friends", "public" to "Public").forEach { (value, label) ->
                        FilterChip(selected = state.visibility == value, onClick = { vm.setVisibility(value) }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            GlassCard {
                state.status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.users.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(u.avatarUrl, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                u.displayName + if (u.id in state.inBluetoothRange) "  · right here" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${u.libraryCount} tracks · " + u.recent.joinToString(", ") { it.title },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(onClick = { onOpenProfile(u.id) }) { Text("Browse") }
                    }
                }
            }
        }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/ui/ProfileScreen.kt`: contributor profile + Snatched Library
```kotlin
package com.tracksnatcher.social.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tracksnatcher.social.data.LibraryTrack
import com.tracksnatcher.social.data.Profile
import com.tracksnatcher.social.data.SocialApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Contributor profile + their public Snatched Library (Napster-style browse) with one-tap Snatch. */
@Composable
fun ProfileScreen(userId: String, sessionId: String?) {
    val context = LocalContext.current
    val api = remember { SocialApi.get(context) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var tracks by remember { mutableStateOf<List<LibraryTrack>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        try {
            profile = api.profile(userId)
            tracks = api.library(userId).tracks
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocialApi.ApiException) {
            error = if (e.code == 404) "This profile is private" else e.message
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun snatch(t: LibraryTrack) = scope.launch {
        try {
            val out = api.snatch(t.id, userId, if (sessionId != null) "session" else "profile", sessionId)
            tracks = tracks.map { if (it.id == t.id) it.copy(inMyLibrary = 1) else it }
            Toast.makeText(context, out.label, Toast.LENGTH_SHORT).show()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GlassCard {
                val p = profile
                if (p == null) {
                    Text(error ?: "Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(p.avatarUrl, 56.dp)
                        Column {
                            Text(p.displayName, style = MaterialTheme.typography.headlineSmall)
                            Text("${p.libraryCount} tracks · snatched from ${p.snatchedFromCount}×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.youtubeUrl?.let { url -> Button(onClick = { uriHandler.openUri(url) }) { Text("YouTube") } }
                        p.spotifyUrl?.let { url -> Button(onClick = { uriHandler.openUri(url) }) { Text("Spotify") } }
                    }
                }
            }
        }
        if (profile != null) {
            item {
                GlassCard {
                    Text("Snatched Library", style = MaterialTheme.typography.titleMedium)
                    tracks.forEach { t ->
                        TrackRow(
                            title = t.title, artist = t.artist, artworkUrl = t.artworkUrl,
                            snatchedFrom = t.snatchedFromName,
                            onSnatch = if (t.inMyLibrary == 0) ({ snatch(t) }) else null,
                        )
                    }
                    if (tracks.isEmpty()) Text("No public tracks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

#### `app/src/main/java/com/tracksnatcher/social/SocialNav.kt`: navigation + breathing background hookup
```kotlin
package com.tracksnatcher.social

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.tracksnatcher.social.data.SocialApi
import com.tracksnatcher.social.ui.HostSessionScreen
import com.tracksnatcher.social.ui.JoinSessionScreen
import com.tracksnatcher.social.ui.NearbyScreen
import com.tracksnatcher.social.ui.ProfileScreen

object SocialRoutes {
    const val HOST = "social/host"
    const val JOIN = "social/join?s={s}"
    const val NEARBY = "social/nearby"
    const val PROFILE = "social/profile/{userId}?s={sessionId}"

    fun join() = "social/join"
    fun profile(userId: String, sessionId: String? = null) =
        "social/profile/$userId" + (sessionId?.let { "?s=$it" } ?: "")
}

/**
 * Add inside the app's existing NavHost:
 *
 *   socialGraph(
 *       navController,
 *       serverUrl = BuildConfig.SOCIAL_BASE_URL,
 *       displayName = <the user's current name in the app>,
 *       background = { content -> BreathingBackground { content() } }, // the app's EXISTING breathing background, unchanged
 *   )
 */
fun NavGraphBuilder.socialGraph(
    navController: NavController,
    serverUrl: String,
    displayName: String,
    background: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    SocialApi.configure(serverUrl)

    composable(SocialRoutes.HOST) {
        background {
            HostSessionScreen(displayName, onOpenProfile = { navController.navigate(SocialRoutes.profile(it)) })
        }
    }
    composable(
        SocialRoutes.JOIN,
        arguments = listOf(navArgument("s") { type = NavType.StringType; nullable = true; defaultValue = null }),
        deepLinks = listOf(navDeepLink { uriPattern = "tracksnatcher://join?s={s}" }),
    ) { entry ->
        background {
            JoinSessionScreen(
                deepLinkToken = entry.arguments?.getString("s"),
                displayName = displayName,
                onOpenProfile = { userId, sessionId -> navController.navigate(SocialRoutes.profile(userId, sessionId)) },
            )
        }
    }
    composable(SocialRoutes.NEARBY) {
        background {
            NearbyScreen(displayName, onOpenProfile = { navController.navigate(SocialRoutes.profile(it)) })
        }
    }
    composable(
        SocialRoutes.PROFILE,
        arguments = listOf(
            navArgument("userId") { type = NavType.StringType },
            navArgument("sessionId") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        background {
            ProfileScreen(userId = entry.arguments?.getString("userId").orEmpty(), sessionId = entry.arguments?.getString("sessionId"))
        }
    }
}
```

### Acceptance checklist
- [ ] Host starts a session → QR + PIN show up; a guest scans with the app (or the phone camera opens the deep link) and is in the queue in under 5 s with no signup.
- [ ] Every queue row shows who added it; tapping it opens their profile with YouTube/Spotify links.
- [ ] A guest snatches a host track → everyone in the session sees "Snatched by B from A" and the MP3 job hits Node-RED.
- [ ] Grok DJ returns a fair order; with `GROK_ENABLED=false` it falls back to round-robin and nothing breaks.
- [ ] Two phones in the same room see each other on Nearby with "right here" (Bluetooth); Ghost mode hides you and stops advertising.
- [ ] Denying location or Bluetooth still leaves the other discovery method working.
- [ ] No API response ever contains lat/lon or a geohash, and the xAI key is not in the APK.
- [ ] New screens keep the current breathing background and colors, unchanged.
