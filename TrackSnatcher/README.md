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

## Status

Scaffold: architecture, both OAuth managers, the Listening → 4-button picker, and the full
phase-2 monetization/curation surface are in place and demoable on dummy data
(`FakePlaylistRepository`, `FakeGenreSource`). Not yet compiled in CI. Next: point
`RECOGNITION_BASE_URL` at the live backend, flip the repository/genre bindings to the
streaming implementations, and configure the Play Console products.
