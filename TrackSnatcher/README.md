# TrackSnatcher — Android Client

Fast, 2-tap ambient song identification and playlist sorter. Point your phone at whatever's
playing, and one tap later the track is appended to a streaming playlist. No custom media
player, no downloads — it identifies ambient audio and appends via each platform's own API
(or a deep link for Amazon Music).

This module is the Kotlin / Jetpack Compose client, scaffolded in clean MVVM.

## The 2-tap flow

```
Widget / launcher / "Hey Google…"        ← trigger
        │
        ▼
  ┌───────────────┐   record 5–7s PCM/WAV        ┌────────────────────┐
  │  Listening…   │ ───────────────────────────▶ │ Recognition backend │
  └───────────────┘                              │ (AcoustID/MusicBrainz)
        │  match (Title, Artist, Track ID)        └────────────────────┘
        ▼
  ┌──────────────────────────────┐
  │  Quick-target grid (3–4 tiles)│   ← tap a pinned playlist
  │  Favorites · Driving · Gym …  │
  └──────────────────────────────┘
        │  POST append (Spotify / YouTube API)
        ▼
   ✓ Added — auto-dismiss
```

When launched from the widget or an Assistant App Action that names a playlist, the match
is appended automatically — no second tap needed.

## Architecture (clean MVVM)

```
ui/            Compose screens + ViewModels (capture flow, manual mode) + theme + nav
  capture/     CaptureViewModel drives CaptureUiState: Idle→Listening→Matched→Adding→Added
  manual/      In-app search + pick-a-playlist add
domain/        Framework-free core
  model/       Track, Playlist, MusicService, AppError
  repository/  RecognitionRepository, PlaylistRepository (interfaces)
  usecase/     IdentifyTrack, GetPinnedPlaylists, AddTrackToPlaylist
data/          Implementations
  audio/       AudioRecorder — mic → 16 kHz mono WAV
  remote/      Retrofit APIs (Recognition, Spotify, YouTube) + DTOs + BearerAuthInterceptor
  recognition/ RecognitionRepositoryImpl
  playlist/    FakePlaylistRepository (dummy data, bound now) · StreamingPlaylistRepository (real)
auth/          OAuth 2.0 PKCE
  OAuthManager (base) · spotify/SpotifyAuthManager · google/GoogleAuthManager
  Pkce, OAuthRedirectBus, EncryptedTokenStore
amazon/        AmazonMusicDeepLink — amzn://music/search/<query> fallback
widget/        CaptureWidget (Glance 1×1) — one-tap capture trigger
shortcuts/     PlaylistShortcutPublisher — pushes top playlists to Assistant dynamically
di/            Hilt modules (Network, Repository, Coroutine)
```

Dependencies flow **ui → domain ← data**; the domain layer depends on nothing Android.
DI is Hilt; networking is Retrofit + OkHttp + kotlinx.serialization; tokens live in
`EncryptedSharedPreferences`.

### Wiring in the real backends

The scaffold binds `FakePlaylistRepository` (dummy data) so the UI runs end-to-end with no
credentials. To go live:

1. Fill in `local.properties` (see `local.properties.example`) with your Spotify + Google
   client IDs, redirect URIs, and the recognition backend URL.
2. In `di/RepositoryModule.kt`, swap the `PlaylistRepository` binding from
   `FakePlaylistRepository` to `StreamingPlaylistRepository`.

## Auth

| Service       | Flow                                   | Scopes |
|---------------|----------------------------------------|--------|
| Spotify       | OAuth 2.0 Authorization Code + PKCE     | `playlist-modify-public`, `playlist-modify-private`, `playlist-read-private` |
| YouTube Music | Google OAuth 2.0 + PKCE (offline)       | `https://www.googleapis.com/auth/youtube` |
| Amazon Music  | Deep-link intent (no API)               | — |

Both OAuth services share `OAuthManager`: it launches a Chrome Custom Tab, catches the
`tracksnatcher://…` redirect via `OAuthRedirectBus`, exchanges the code, persists tokens
encrypted, and silently refreshes on each API call through `BearerAuthInterceptor`.

## Android Auto & Voice

- `res/xml/shortcuts.xml` declares an Assistant capability bound to the in-app `CAPTURE`
  action with a pass-through `playlist` parameter.
- `PlaylistShortcutPublisher` pushes the user's top pinned playlist names as dynamic
  shortcuts on startup, so *"Hey Google, ask TrackSnatcher to add to my Country playlist"*
  resolves to a real target.

## Build

```bash
cp local.properties.example local.properties   # then edit
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

> The Gradle wrapper JAR is not committed. Run `gradle wrapper --gradle-version 8.11.1`
> once (or open in Android Studio) to generate `gradlew` + `gradle-wrapper.jar`.

## Monetization & advanced curation (phase 2)

Layered on top of the capture flow, all enforced through one entitlement source of truth.

### Subscriptions & paywall
- `billing/` — `SubscriptionManager` interface with a Play Billing v7 implementation
  (`PlayBillingSubscriptionManager`); swap in RevenueCat behind the interface without
  touching callers.
- `EntitlementStore` (DataStore) is the single gate: tier, rolling 30-day free-cap usage,
  and the auto-vibe toggle. Free = 10 snatches/mo + 1 quick playlist; Pro = unlimited + 4
  quick playlists + smart features. Products: `pro_monthly` ($1.99), `pro_yearly` ($14.99),
  `pro_lifetime` ($19.99 one-time).
- `ui/paywall/PaywallScreen` — benefits, per-plan pricing cards, Restore Purchases.

### Auto-duplicate detection
- `PlaylistRepository.findDuplicate` checks the target playlist (by service track id, then
  title+artist) before appending — implemented for Spotify (`added_at`) and YouTube.
- `ui/capture/DuplicateBottomSheet` intercepts the one-tap flow with *"Already in
  '[Playlist]' (added [date])"* → **Add Anyway** / **Choose Another Playlist**.

### Sonic Memory + social share card
- `location/FusedLocationProvider` fetches coarse location (runtime-permission-gated) and
  reverse-geocodes to "City, Region".
- `SonicMemoryStore` (DataStore) persists `{trackId, timestamp, place, lat/lng, playlist}`.
- `ui/share/MemoryCard` renders the stylized card; `ShareableMemoryCard` captures it to a
  Bitmap via `GraphicsLayer.toImageBitmap()`, writes it through a `FileProvider`, and fires
  the native share sheet (`ACTION_SEND`) for Instagram Stories / social.

### Smart Vibe Match auto-filer
- `GenreSource` pulls artist genres (Spotify artist endpoint; MusicBrainz-ready) — no
  reliance on the deprecated `audio-features` endpoint.
- `VibeMatcher` maps genre keywords → buckets (`country/americana → Country Road`,
  `hip hop/trap/edm → Gym`, `indie/folk/acoustic → Chill`) and resolves the bucket to the
  user's real playlist by name.
- Pro users toggle **Auto-Vibe Match** in `ui/settings/SettingsScreen`; when on, a match is
  filed automatically (still through the duplicate check) with no manual tap.

Gating, duplicate interception, auto-vibe routing, and memory capture are all woven into one
place — `CaptureViewModel` — so every append path (quick-tap, voice/widget target, auto-vibe)
goes through the same checks.

## Sessions & the Napster layer (phase 3)

The differentiator: file your own song first, but also share a room and raid libraries.

- **Sessions (`session/`):** a QR-addressable room with one collaborative playlist (the
  "tape"). Templates (Trip / Party / Wedding / Campfire / Custom) differ only in default
  rules + copy. Host starts → QR + short code (ZXing-generated); guests scan or type the
  code and join. Everyone adds what they want to hear (like karaoke for the room). Host
  controls: auto-add vs approve queue, lock, end. The official streaming playlist survives
  when the session ends. `SessionScreen`, seeded `FakeSessionRepository` (demo road trip).
- **Snatch (`domain/usecase/SnatchTrackUseCase`):** copy a tape/library track onto *your*
  playlist — but only through the already-have gate, and never auto-snatched from a session.
  Enforces the free cap and routes to the paywall when it's hit.
- **Recap (`SessionRecapScreen`):** replay a session another day — ordered tape, who added
  each track, **who snatched it and into which playlist**, time/place; tap a person to open
  their library; "Play this session" opens the official streaming playlist.
- **Raid a library (`people/`, `PersonFolderScreen`):** a person is a *folder*, not a
  profile wall — shareable playlists stacked, multi-select, "Snatch selected to [playlist]",
  already-owned tracks greyed out. The only verbs on a person are look / snatch / open
  Spotify. No DMs, comments, likes, or follower graph.
- **Nearby (`NearbyScreen`):** a list of visible libraries around you (not a map of moving
  people) — like opening computers on Napster.
- **Visibility (Napster default):** you're **visible unless you hide**. `UserPrefsStore`
  defaults `visibleNearby`/`visibleInSession` ON; one **Hide my snatches (ghost mode)**
  toggle in Settings turns you invisible while widget/Auto/personal adds keep working.
  Private playlists never leak — only playlists explicitly marked shareable are exposed;
  the Spotify-profile link stays off until you opt in.
- **Undo:** a wrong add is one tap to reverse (`removeTrackFromPlaylist`) and it **refunds**
  the snatch against the free cap.

## Branding

The launcher icon and Settings header banner are generated from the TrackSnatch logo
(`app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` and `brand_banner.png`), with
`#BD9B51` (the logo's gold) as the adaptive-icon background (`values/colors.xml`). Regenerate
either PNG from a source image and drop it in `drawable-nodpi/` under the same filename to
update the brand without touching any XML.

## UX lessons carried over from SnatchTrack (legacy)

This project is a clean-room rebuild on official Spotify/YouTube/AudD-or-ACRCloud APIs only —
no yt-dlp, no scraping, no raw audio download or transfer pipeline. A few interaction-design
and metadata lessons learned the hard way on an earlier, unrelated prototype were still worth
keeping:

- **Status words, not a progress bar.** The capture flow shows `Listening…` → `Identifying…`
  (`CaptureUiState.Listening` / `.Identifying`) rather than a percentage — a bar that lies
  about progress erodes trust fast.
- **Sticky destination.** The last playlist a track was filed into is remembered
  (`UserPrefs.lastDestinationId`) and leads the quick-target grid as the largest tile, so the
  common case is a single tap instead of a re-pick every time.
- **Never trust the YouTube channel name as the artist.** `YouTubeResultScorer` parses
  "Artist - Title" from the video title first; only a channel ending in `" - Topic"` (YouTube's
  auto-generated artist channel) is trusted as a bare fallback, and a `*VEVO` channel (a label
  brand, not an artist) is never used. It also ranks official/studio uploads above live
  covers, bootlegs, and remixes.
- **MusicBrainz genre lookups use `tags`, not `genres`.** Noted in `GenreSource.kt` for
  whoever wires in MusicBrainz enrichment: MusicBrainz's `includes=["genres"]` comes back
  empty for most artists — query `includes=["tags"]` and fall through recording →
  release-group → artist tags instead.

## Status

Scaffold: architecture, both OAuth managers, the Listening → 4-button picker, the full
phase-2 monetization/curation surface, and the phase-3 session/Napster layer are in place and
demoable on dummy data (`FakePlaylistRepository`, `FakeGenreSource`, `FakeSessionRepository`,
`FakePeopleRepository`). A GitHub Actions workflow (`.github/workflows/tracksnatcher-android.yml`)
builds the debug APK and runs unit tests on every push touching this directory, but the code
has not otherwise been compiled in this environment. Next: point `RECOGNITION_BASE_URL` at the
live backend, flip the repository/genre bindings to the streaming implementations, and
configure the Play Console products.
