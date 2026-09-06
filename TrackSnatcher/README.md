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

## Status

Scaffold: architecture, both OAuth managers, and the Listening → 4-button picker are in
place and demoable on dummy data. Next: point `RECOGNITION_BASE_URL` at the live backend and
flip the repository binding to `StreamingPlaylistRepository`.
