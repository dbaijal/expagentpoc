# Video (Brightcove) Integration — EDS Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 0.1
**Date:** March 2026
**Status:** Draft — Pending Discovery Call Confirmation

---

## 1. Executive Summary

This document defines the approach for migrating the Thermo Fisher Scientific (TFS) video experience from AEM 6.x to AEM Edge Delivery Services (EDS) with Universal Editor (xWalk). TFS uses **Brightcove Video Cloud** as its video hosting platform with custom AEM components wrapping the Brightcove player. The EDS migration preserves the same Brightcove integration while simplifying the authoring model.

**Key Findings:**
- TFS uses Brightcove Video Cloud — no custom video player exists
- Two AEM components exist: `cmp-p-videoplayer` (single) and `cmp-p-videoplaylist` (playlist)
- Each playlist layout (right rail, bottom rail, etc.) uses a **different Brightcove Player ID** — player IDs cannot be hardcoded
- Four display modes exist: Inline, Button, Link, Teaser
- Brightcove's playlist plugin handles sidebar rendering, but TFS has custom jQuery code for description injection, height sync, and share button positioning

**Recommendation:** Two EDS blocks (`video` + `video-playlist`) with config-driven player ID resolution. Shared modal utility for non-inline display modes.

---

## 2. Current State Analysis

### 2.1 Brightcove Account & Player Configuration

**Discovered from live site inspection and AEM instance:**

| Configuration | Value | Source |
|--------------|-------|--------|
| **Brightcove Account ID** | `3663210762001` | `data-account` on player element |
| **Player — Single/Default** | `08UsfMRkC` | digital-solutions.html |
| **Player — Playlist Right Rail** | `my5Il4M9K` | JCR: `/etc/brightcovetools/cbd-video-players/cbd-playlist-with-right-rail` |
| **Player — Playlist Bottom Rail** | `AzszCMDYe3` | JCR: `/etc/brightcovetools/cbd-video-players/cbd-playlist-with-bottom-rail` |
| **Player — Playlist Left Rail** | **⚠️ UNKNOWN — confirm in discovery** | Not found in inspection |
| **sling:resourceType** | `brightcove/components/page/brightcoveplayer` | JCR node properties |

> **CRITICAL:** Each playlist layout has a DIFFERENT Brightcove Player ID because the playlist UI position (right/bottom/left) is configured inside the Brightcove player itself in Brightcove Studio. This is NOT just a CSS change — the player JS bundle includes the playlist rendering behavior for that specific layout.

### 2.2 AEM Components

TFS has **two separate AEM components** for video:

```
COMPONENT 1: cmp-p-videoplayer (Single Video)
  AEM Dialog Fields:
    ├── Account:         Dropdown (3663210762001)
    ├── Video:           Dropdown/Picker (Brightcove video ID)
    └── Call to Action:  Dropdown (Inline / Button / Link / Teaser)

  JCR Properties:
    ├── account:    3663210762001
    ├── videoId:    6293625135001
    ├── playerID:   08UsfMRkC (default single player)
    └── cta:        inline | button | link | teaser


COMPONENT 2: cmp-p-videoplaylist (Video Playlist)
  AEM Dialog Fields:
    ├── Account:         Dropdown (3663210762001)
    ├── Playlist:        Dropdown/Picker (Brightcove playlist ID)
    ├── Player:          Dropdown:
    │     - CBD - Default Player
    │     - CBD - Playlist with right rail
    │     - CBD - Playlist with bottom rail
    └── Call to Action:  Dropdown (Inline / Button / Link / Teaser)

  JCR Properties:
    ├── account:       3663210762001
    ├── playlistId:    1726843331032229875
    ├── playerID:      my5Il4M9K (varies by layout selection)
    ├── playlistalign: right-rail | bottom-rail | left-rail
    └── cta:           inline | button | link | teaser
```

### 2.3 Display Modes (Call to Action)

Both components support 4 display modes:

| Mode | Behavior | Author Configures |
|------|----------|-------------------|
| **Inline** | Video/playlist player renders directly on page. Click to play in place. | Video/Playlist ID only |
| **Button** | CTA button displayed. Click opens video in a modal overlay. | Video/Playlist ID + Button text |
| **Link** | Text link displayed. Click opens video in a modal overlay. | Video/Playlist ID + Link text |
| **Teaser** | Card with thumbnail, title, description. Click opens video in modal. | Video/Playlist ID + Title + Description + (optional) Thumbnail |

### 2.4 Playlist Layout Options

When component is `cmp-p-videoplaylist`, author selects layout:

| Layout | Player ID | Visual |
|--------|-----------|--------|
| **Right Rail** | `my5Il4M9K` | Player left, video list sidebar right (vertical scroll) |
| **Bottom Rail** | `AzszCMDYe3` | Player top, video list below (horizontal scroll) |
| **Left Rail** | **⚠️ TBD** | Video list sidebar left, player right |
| **Default** | **⚠️ TBD** | Brightcove default layout |

### 2.5 How the Architecture Layers Work

```
┌──────────────────────────────────────────────────────────────┐
│  LAYER 1: BRIGHTCOVE VIDEO CLOUD (Their Platform)             │
│                                                               │
│  Brightcove handles:                                         │
│   ✅ Video hosting, encoding, and streaming (HLS adaptive)   │
│   ✅ Player UI (controls, progress bar, fullscreen, PiP)    │
│   ✅ Playback API (JWT-authenticated content URLs)           │
│   ✅ Playlist plugin (sidebar with thumbnails/titles)        │
│   ✅ DRM/EME (encrypted media extensions)                    │
│   ✅ Video poster/thumbnail generation                       │
│   ✅ Player analytics                                        │
│   ✅ Captions/subtitles                                      │
│                                                               │
│  API flow (internal to Brightcove player JS):                │
│   Player JS loads → reads data-video-id attribute            │
│   → Calls: edge.api.brightcove.com/playback/v1/accounts/    │
│            3663210762001/videos/{id}?bcov_auth={JWT}         │
│   → Receives: HLS manifest URL                              │
│   → Starts adaptive streaming                                │
│                                                               │
│  NO custom code calls Brightcove APIs directly.              │
│  The player JS handles ALL API communication internally.     │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  LAYER 2: AEM COMPONENT (Server-Rendered HTML)                │
│                                                               │
│  AEM component renders the HTML structure:                   │
│   <div class="cmp-p-videoplayer">                            │
│     <div data-video="{uniqueId}">                            │
│       <div class="video-container video-single">             │
│         <div class="brightcove-container">                   │
│           <div class="video-js"                              │
│                data-account="3663210762001"                   │
│                data-player="08UsfMRkC"                       │
│                data-video-id="6293625135001"                 │
│                data-embed="default">                         │
│           </div>                                             │
│         </div>                                               │
│       </div>                                                 │
│     </div>                                                   │
│   </div>                                                     │
│                                                               │
│  AEM also renders:                                           │
│   → <script src="players.brightcove.net/{account}/{player}   │
│       _default/index.min.js">                                │
│   → Schema.org VideoObject structured data for SEO           │
│   → Inline <script> for player initialization timing         │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  LAYER 3: CUSTOM TFS JAVASCRIPT (Inline in Page)              │
│                                                               │
│  TFS-written code that augments the Brightcove player:       │
│                                                               │
│  a) checkVideojsLoaded() — polling function                  │
│     → Waits for videojs global to be available               │
│     → Registers dummy 'eme' plugin (DRM workaround)         │
│     → Sets techOrder to ["html5"]                            │
│     → Polls every 500ms, max 10 seconds                     │
│                                                               │
│  b) Playlist description injection                           │
│     → player.playlist().forEach() iterates playlist items    │
│     → Appends <p class="vjs-playlist-description"> to each  │
│     → Brightcove shows title+thumbnail+duration, NOT desc    │
│     → TFS code adds the description text                     │
│                                                               │
│  c) Playlist sidebar height sync (jQuery)                    │
│     → $('#playlist-{id}').css('max-height', ...)             │
│     → Matches sidebar height to player height                │
│     → Handles tab-switching visibility scenarios             │
│                                                               │
│  d) Share button repositioning                               │
│     → Moves share button into video control bar              │
│     → Brightcove places it outside by default                │
│                                                               │
│  e) IE detection                                             │
│     → Shows error message for Internet Explorer users        │
│     → Can be removed in EDS (IE no longer supported)         │
│                                                               │
│  f) Schema.org structured data                               │
│     → VideoObject JSON-LD with name, description, duration   │
│     → Includes Brightcove contentUrl with JWT auth token     │
│     → Thumbnail URL from Brightcove CDN                      │
└──────────────────────────────────────────────────────────────┘
```

### 2.6 HapYak Interactive Video

The digital-solutions page shows `hapyak-player` class on the Brightcove player, indicating HapYak integration:

| Aspect | Detail |
|--------|--------|
| **What is HapYak** | Interactive video overlay platform (now Brightcove Interactivity) |
| **Features** | Clickable hotspots, chapters, CTAs, quizzes overlaid on video |
| **Where found** | `digital-solutions.html` (single video player `08UsfMRkC`) |
| **Configured in** | Brightcove Studio (player-level or video-level) |
| **EDS impact** | If HapYak is configured per-player in Brightcove Studio, it works automatically when the same player ID is used. No custom EDS code needed. |

> **⚠️ Discovery needed:** Confirm if HapYak is actively used and how widely.

### 2.7 Analyzed Pages

| Page | URL | Video Type | Player ID | Video/Playlist ID |
|------|-----|-----------|-----------|-------------------|
| Digital Solutions | `/us/en/home/digital-solutions.html` | Single | `08UsfMRkC` | `6293625135001` |
| Luminex FLEXMAP 3D | `/us/en/home/life-science/.../luminex-flexmap-3D.html` | Playlist (7 videos) | `hmATFP49Og` | Playlist with right rail |

**Digital Solutions page — Single video:**
- Video: "Are you ready to Connect?" (Thermo Fisher Connect Platform intro)
- Duration: 1:09
- HapYak: YES (interactive overlays enabled)
- Display: Inline
- Also has a "Watch video" link pointing to Brightcove player page

**Luminex FLEXMAP 3D page — Playlist:**
- 7 videos: System Overview, Initialization, Probe Height, Shutdown, Cleaning, Clog Removal, +1
- Layout: Playlist with right rail sidebar
- Display: Inline
- Custom: descriptions injected into sidebar via jQuery

---

## 3. EDS Block Architecture

### 3.1 Two Blocks (Recommended)

```
┌──────────────────────────────────────────────────────────────┐
│  RECOMMENDATION: TWO SEPARATE BLOCKS                          │
│                                                               │
│  BLOCK 1: "video"                                            │
│   → Single video                                             │
│   → Author fields: Video ID, Display Mode, (CTA text)       │
│   → Player ID: auto-resolved from config                     │
│                                                               │
│  BLOCK 2: "video-playlist"                                   │
│   → Playlist of videos                                       │
│   → Author fields: Playlist ID, Layout, Display Mode, (CTA) │
│   → Player ID: auto-resolved from config based on layout     │
│                                                               │
│  SHARED: video-shared.js                                     │
│   → Modal overlay (for button/link/teaser)                   │
│   → Brightcove player loader                                 │
│   → Config reader                                            │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Why Two Blocks Instead of One

| Reason | Detail |
|--------|--------|
| **Player ID resolution differs** | `video`: always same player ID. `video-playlist`: player ID depends on layout selection (right rail vs bottom rail = different player). |
| **Author fields differ** | `video`: just Video ID + CTA. `video-playlist`: Playlist ID + Layout + CTA. One block would show all fields always — UE has no conditional field visibility. |
| **Matches current AEM** | AEM has `cmp-p-videoplayer` + `cmp-p-videoplaylist`. 1:1 mapping simplifies migration import scripts. |
| **Different Brightcove JS bundles** | Single player JS (~200KB, no playlist plugin). Playlist player JS (~250KB, includes playlist plugin). Two blocks: load only what's needed. |
| **Cleaner CSS separation** | `video.css`: player styles only. `video-playlist.css`: player + sidebar + layout variants. |

### 3.3 Config-Driven Player ID Resolution

Player IDs stored in site configuration, NOT hardcoded in block:

```
/configs/video-config.json
{
  "account": "3663210762001",
  "players": {
    "single":            "08UsfMRkC",
    "playlist-right":    "my5Il4M9K",
    "playlist-bottom":   "AzszCMDYe3",
    "playlist-left":     "⚠️ TBD — confirm in discovery",
    "playlist-default":  "⚠️ TBD — confirm in discovery"
  }
}
```

```
Flow:
  Author selects "Playlist with Right Rail"
       ↓
  Block JS reads config: players["playlist-right"]
       ↓
  Resolves: playerID = "my5Il4M9K"
       ↓
  Renders: data-player="my5Il4M9K"
       ↓
  Brightcove player JS loads with right-rail layout baked in

Author NEVER sees or enters a Player ID.
Account ID NEVER shown to author.
```

**Why player IDs can't be hardcoded:**

```
Each Brightcove player is a SEPARATE JS bundle configured in Brightcove Studio:

  Player my5Il4M9K (right rail):
    → Brightcove Studio → Player Settings → Playlist: Enabled
    → Playlist position: Right → Vertical sidebar
    → This is BAKED INTO the player JS bundle
    → URL: players.brightcove.net/3663210762001/my5Il4M9K_default/index.min.js

  Player AzszCMDYe3 (bottom rail):
    → Brightcove Studio → Player Settings → Playlist: Enabled
    → Playlist position: Bottom → Horizontal strip
    → DIFFERENT JS bundle with different rendering behavior
    → URL: players.brightcove.net/3663210762001/AzszCMDYe3_default/index.min.js

You CANNOT load one player and change its layout with CSS.
The playlist position is controlled by the Brightcove player JS itself.
If the video team creates a new player variant, just update the config file.
```

---

## 4. Block Details

### 4.1 Block 1: "video" (Single Video)

**Variants (via block class names):**

| Variant | Class | Behavior |
|---------|-------|----------|
| Inline | `video (inline)` | Player renders on page, click to play |
| Button | `video (button)` | CTA button, click opens modal with player |
| Link | `video (link)` | Text link, click opens modal with player |
| Teaser | `video (teaser)` | Card (thumbnail + title + desc), click opens modal |

**Universal Editor Component Model:**

```json
{
  "id": "video",
  "fields": [
    {
      "component": "text",
      "name": "videoId",
      "label": "Brightcove Video ID",
      "description": "Enter the Brightcove video ID (e.g., 6293625135001)",
      "required": true
    },
    {
      "component": "text",
      "name": "buttonText",
      "label": "Button / Link Text",
      "description": "Display text for Button and Link variants (e.g., 'Watch Video')"
    },
    {
      "component": "text",
      "name": "title",
      "label": "Title",
      "description": "Card title for Teaser variant"
    },
    {
      "component": "richtext",
      "name": "description",
      "label": "Description",
      "description": "Card description for Teaser variant"
    },
    {
      "component": "reference",
      "name": "thumbnail",
      "label": "Thumbnail Image",
      "description": "Custom thumbnail for Teaser variant. If empty, auto-fetched from Brightcove."
    }
  ]
}
```

**Rendering per variant:**

```
INLINE:
  ┌─────────────────────────────────┐
  │  ▶ ━━━━━━━━━━━━━━━━━━━━━━━━━━  │
  │        Brightcove Player        │
  │  ▶ ━━━━━━━━━━━━━━━  🔊  ⛶     │
  └─────────────────────────────────┘
  → Player visible, click to play in place

BUTTON:
  ┌─────────────────┐
  │  ▶ Watch Video   │  ← CTA button with authored text
  └─────────────────┘
  → Click opens modal overlay with Brightcove player

LINK:
  ▶ Watch the demo →   ← text link with play icon
  → Click opens modal overlay with Brightcove player

TEASER:
  ┌─────────────────────────────────┐
  │  ┌───────────────────────────┐  │
  │  │        ▶ (play icon)      │  │  ← thumbnail with play overlay
  │  └───────────────────────────┘  │
  │  Product Overview                │  ← authored title
  │  Learn about the FLEXMAP 3D...  │  ← authored description
  └─────────────────────────────────┘
  → Click opens modal overlay with Brightcove player
```

### 4.2 Block 2: "video-playlist"

**Variants (combined display mode + layout):**

| Display | Layout | Classes |
|---------|--------|---------|
| Inline | Right Rail | `video-playlist (inline playlist-right)` |
| Inline | Left Rail | `video-playlist (inline playlist-left)` |
| Inline | Top Rail | `video-playlist (inline playlist-top)` |
| Inline | Bottom Rail | `video-playlist (inline playlist-bottom)` |
| Button | Right Rail | `video-playlist (button playlist-right)` |
| Button | Bottom Rail | `video-playlist (button playlist-bottom)` |
| Link | Right Rail | `video-playlist (link playlist-right)` |
| Teaser | Right Rail | `video-playlist (teaser playlist-right)` |
| ... | ... | (all combinations) |

**Universal Editor Component Model:**

```json
{
  "id": "video-playlist",
  "fields": [
    {
      "component": "text",
      "name": "playlistId",
      "label": "Brightcove Playlist ID",
      "description": "Enter the Brightcove playlist ID",
      "required": true
    },
    {
      "component": "select",
      "name": "layout",
      "label": "Playlist Layout",
      "options": [
        { "name": "Right Rail", "value": "playlist-right" },
        { "name": "Left Rail", "value": "playlist-left" },
        { "name": "Top Rail", "value": "playlist-top" },
        { "name": "Bottom Rail", "value": "playlist-bottom" }
      ]
    },
    {
      "component": "text",
      "name": "buttonText",
      "label": "Button / Link Text",
      "description": "Display text for Button and Link variants"
    },
    {
      "component": "text",
      "name": "title",
      "label": "Title",
      "description": "Card title for Teaser variant"
    },
    {
      "component": "richtext",
      "name": "description",
      "label": "Description",
      "description": "Card description for Teaser variant"
    }
  ]
}
```

**Playlist layout rendering:**

```
PLAYLIST-RIGHT (most common — seen on Luminex page):
┌──────────────────────┬──────────┐
│                      │ ▶ Vid 1  │
│   Brightcove Player  │   desc...│
│                      │ ▶ Vid 2  │
│                      │   desc...│
│   ▶  ━━━━━━  🔊 ⛶  │ ▶ Vid 3  │
└──────────────────────┴──────────┘
  Player ID: my5Il4M9K

PLAYLIST-LEFT:
┌──────────┬──────────────────────┐
│ ▶ Vid 1  │                      │
│   desc...│   Brightcove Player  │
│ ▶ Vid 2  │                      │
│   desc...│                      │
│ ▶ Vid 3  │   ▶  ━━━━━━  🔊 ⛶  │
└──────────┴──────────────────────┘
  Player ID: ⚠️ TBD

PLAYLIST-BOTTOM:
┌─────────────────────────────────┐
│                                 │
│        Brightcove Player        │
│   ▶  ━━━━━━━━━━━━━  🔊  ⛶    │
├─────────────────────────────────┤
│ ▶ Vid 1  │ ▶ Vid 2  │ ▶ Vid 3 │ ← horizontal scroll
└─────────────────────────────────┘
  Player ID: AzszCMDYe3

PLAYLIST-TOP:
┌─────────────────────────────────┐
│ ▶ Vid 1  │ ▶ Vid 2  │ ▶ Vid 3 │ ← horizontal scroll
├─────────────────────────────────┤
│                                 │
│        Brightcove Player        │
│   ▶  ━━━━━━━━━━━━━  🔊  ⛶    │
└─────────────────────────────────┘
  Player ID: ⚠️ TBD
```

### 4.3 Shared Modal Component

Button, Link, and Teaser variants all open video in a modal overlay:

```
┌──────────────────────────────────────────────────────────────┐
│  MODAL OVERLAY (shared between both blocks)                   │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                                                    [✕]  │  │
│  │                                                         │  │
│  │          Brightcove Player (single or playlist)        │  │
│  │                                                         │  │
│  │  ▶  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━  🔊  ⛶             │  │
│  │                                                         │  │
│  │  (if playlist: sidebar based on layout variant)        │  │
│  └────────────────────────────────────────────────────────┘  │
│                         (dark backdrop)                       │
└──────────────────────────────────────────────────────────────┘

Behavior:
  → Backdrop click or ✕ button or ESC key closes modal
  → Brightcove player created LAZILY on modal open (not pre-loaded)
  → Player destroyed on modal close (frees memory, stops playback)
  → Body scroll locked while modal is open
  → Focus trapped inside modal (accessibility)
```

### 4.4 File Structure

```
/blocks/
  ├── video/
  │   ├── video.js              ← Single video block
  │   ├── video.css             ← Single video styles + variant styles
  │   ├── video-shared.js       ← Shared: modal, config reader, player loader
  │   └── video-shared.css      ← Shared: modal styles
  │
  └── video-playlist/
      ├── video-playlist.js     ← Playlist block (imports video-shared)
      └── video-playlist.css    ← Playlist layout styles (right/left/top/bottom)

/configs/ (or metadata sheet):
  └── video-config.json         ← Account ID + player ID mapping
```

---

## 5. Authoring Experience in Universal Editor

### 5.1 Video ID Selection — Phased Approach

| Phase | Approach | Author Experience |
|-------|----------|-------------------|
| **Phase 1 (Migration)** | Text field — author pastes Video/Playlist ID | Functional but requires knowing the ID |
| **Phase 2 (Enablement)** | UI Extension — Brightcove Picker | Author searches/browses videos visually, selects from grid |

**Phase 1 — Simple text field:**

```
┌─────────────────────────────────────┐
│  Video Properties                   │
│                                     │
│  Brightcove Video ID *              │
│  ┌─────────────────────────────┐    │
│  │ 6293625135001               │    │
│  └─────────────────────────────┘    │
│  Enter the Brightcove video ID      │
│                                     │
│  Display Mode                       │
│  ┌─────────────────────────────┐    │
│  │ Inline                  ▾   │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Phase 2 — UI Extension with Brightcove Picker:**

```
┌──────────────────────────────────────────────────┐
│  🔍 Search videos...                              │
│  ┌────────────────────────────────────────────┐   │
│  │ ▶ Connect Platform Intro        │ 1:09    │   │
│  │   ID: 6293625135001             │         │   │
│  ├────────────────────────────────────────────┤   │
│  │ ▶ FLEXMAP 3D Overview           │ 4:01    │   │
│  │   ID: 6299213705001             │         │   │
│  ├────────────────────────────────────────────┤   │
│  │ ▶ FLEXMAP 3D Initialization     │ 7:04    │   │
│  │   ID: 6299215907001             │         │   │
│  └────────────────────────────────────────────┘   │
│                           [ Cancel ] [ Select ]   │
└──────────────────────────────────────────────────┘

Architecture:
  Adobe App Builder App (AIO Runtime)
  ├── /web-src/
  │   ├── ExtensionRegistration.js  → registers custom field
  │   └── BrightcovePicker.js       → React search/browse UI
  └── /actions/
      └── brightcove-proxy.js       → proxies Brightcove CMS API
          → GET /v1/accounts/{id}/videos?q={search}
          → Requires: Brightcove OAuth2 client_credentials
```

### 5.2 What Author NEVER Configures

```
HIDDEN FROM AUTHOR (auto-resolved by block JS):
  ├── Account ID:  Always from video-config.json
  ├── Player ID:   Auto-resolved based on:
  │     video block → config.players.single
  │     video-playlist + right → config.players["playlist-right"]
  │     video-playlist + bottom → config.players["playlist-bottom"]
  └── Embed type:  Always "default"
```

---

## 6. Migration Mapping

### 6.1 AEM Component → EDS Block

```
CURRENT AEM                              EDS BLOCK
──────────────                           ─────────

cmp-p-videoplayer (inline)          →    | Video (inline)   |
  videoId="6293625135001"                | 6293625135001    |

cmp-p-videoplayer (button)          →    | Video (button)   |
  videoId="6293625135001"                | 6293625135001    |
  buttonText="Watch Demo"               | Watch Demo       |

cmp-p-videoplayer (link)            →    | Video (link)     |
  videoId="6293625135001"                | 6293625135001    |
  linkText="See the demo →"             | See the demo →   |

cmp-p-videoplayer (teaser)          →    | Video (teaser)            |
  videoId="6293625135001"                | 6293625135001             |
  title="Product Overview"               | Product Overview          |
  desc="Learn about..."                  | Learn about...            |

cmp-p-videoplaylist (right rail)    →    | Video-Playlist (inline playlist-right) |
  playlistId="172684..."                 | 172684...                              |

cmp-p-videoplaylist (bottom rail)   →    | Video-Playlist (inline playlist-bottom)|
  playlistId="172684..."                 | 172684...                              |
  title="Video Gallery"                  | Video Gallery                          |
```

### 6.2 Import Script Detection

During migration, the import script identifies video components by:

```
DETECTION SELECTORS:
  Single video:  .cmp-p-videoplayer
  Playlist:      .cmp-p-videoplaylist

EXTRACT FROM DOM:
  Video ID:      [data-video-id] attribute
  Player ID:     [data-player] attribute
  Account:       [data-account] attribute
  Playlist ID:   [data-playlist-id] or from JCR
  Layout:        .video-single | .video-playlist-right | .video-playlist-left
  CTA mode:      Detect from DOM structure:
                   - Has .video-js visible → inline
                   - Has <button> → button
                   - Has <a> link → link
                   - Has card structure → teaser
  Button text:   CTA element .textContent
  Title:         .video-teaser h3/h2 .textContent
  Description:   .video-teaser p .textContent
```

---

## 7. Custom Code Requirements for EDS

### 7.1 What Brightcove Handles Automatically

```
✅ Video playback (HLS adaptive streaming)
✅ Player controls (play, pause, seek, volume, fullscreen, PiP)
✅ Playlist rendering (sidebar with thumbnails, titles, durations)
✅ Click-to-switch video in playlist
✅ Auto-advance to next video
✅ "Now Playing" / "Up Next" indicators
✅ Poster/thumbnail display
✅ Captions/subtitles
✅ Quality switching
✅ DRM/EME (if configured)
✅ HapYak interactive overlays (if configured per-player)
✅ Brightcove analytics
```

### 7.2 What EDS Block Must Implement

```
PHASE 1 — CORE (required for parity):
  ├── Player initialization with correct data attributes
  ├── Config-driven player ID resolution
  ├── Display mode rendering (inline/button/link/teaser)
  ├── Modal overlay for button/link/teaser
  ├── Schema.org VideoObject structured data (SEO)
  └── Responsive sizing

PHASE 2 — POLISH (cosmetic parity with current site):
  ├── Playlist description injection into sidebar
  │   (Brightcove shows title+thumbnail+duration, NOT description)
  ├── Sidebar height synchronization
  ├── Share button repositioning
  └── Playlist layout CSS for right/left/top/bottom

REMOVED (no longer needed in EDS):
  ├── checkVideojsLoaded() polling — not needed, load script with defer
  ├── EME plugin workaround — Brightcove fixed this in newer players
  └── IE detection — IE no longer supported
```

### 7.3 Estimated Implementation Effort

| Component | Effort | Notes |
|-----------|--------|-------|
| `video` block (all variants) | 2-3 days | JS + CSS for inline/button/link/teaser |
| `video-playlist` block (all variants) | 2-3 days | JS + CSS for all layouts + display modes |
| Shared modal utility | 1 day | Modal overlay with lazy player loading |
| Config setup | 0.5 day | video-config.json with player ID mapping |
| Schema.org structured data | 0.5 day | VideoObject JSON-LD |
| Playlist description injection | 0.5 day | Port from jQuery to vanilla JS |
| UI Extension — Brightcove Picker | 2-3 days | Phase 2 (App Builder + CMS API proxy) |
| **Total Phase 1** | **~7 days** | |
| **Total Phase 2 (picker)** | **~3 days** | |

---

## 8. Implementation Plan

### Phase 1: Core Blocks (Week 1-2)

```
Week 1:
  ├── Day 1-2: video block (inline + button + link + teaser)
  ├── Day 3: Shared modal utility
  ├── Day 4-5: video-playlist block (inline + all layouts)

Week 2:
  ├── Day 1-2: video-playlist (button/link/teaser + layouts)
  ├── Day 3: Config setup + Schema.org structured data
  ├── Day 4: Playlist description injection (port from jQuery)
  ├── Day 5: Testing across all combinations
```

### Phase 2: Brightcove Picker (Week 3)

```
  ├── Day 1: App Builder project setup + Brightcove CMS API proxy
  ├── Day 2: React picker UI (search + video grid + selection)
  ├── Day 3: UE Extension registration + integration testing
```

### Phase 3: Migration Import Scripts (Week 4)

```
  ├── Day 1-2: Import script selectors for video/playlist detection
  ├── Day 3: CTA mode detection + field extraction
  ├── Day 4-5: Testing with sample pages from TFS
```

---

## 9. Discovery Call Questions

### 9.1 Account & Player Configuration (MUST CONFIRM)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q1 | Is the Brightcove Account ID (`3663210762001`) the SAME across all TFS sites and country/language variants? Or do different regions/brands use different accounts? | Determines if we need multi-account support in the config |
| Q2 | We found 3 players: Default (`08UsfMRkC`), Playlist Right Rail (`my5Il4M9K`), Playlist Bottom Rail (`AzszCMDYe3`). Are there MORE player IDs? Please provide the **complete list** of player names and IDs. | Directly affects the player ID mapping in config |
| Q3 | Is there a player for **Left Rail**? **Top Rail**? Any other layout variants? | Gap in our analysis — only right and bottom confirmed |
| Q4 | Are these player IDs stable/permanent? Or does the video team update/rotate them periodically? | If they change, config must be externalized and easily updatable |
| Q5 | Who manages the Brightcove players in Brightcove Studio? Central video team or individual content teams? | Determines coordination needed for config updates |

### 9.2 Video Authoring Workflow (MUST CONFIRM)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q6 | How do authors currently select a Video ID in the AEM dialog? Browse/search picker? Paste ID? Select from DAM? | Determines Phase 2 picker requirements |
| Q7 | For playlists — who creates and manages playlists in Brightcove Studio? How does the author get the Playlist ID? | Determines if playlist picker is needed in Phase 2 |
| Q8 | Approximately how many videos are in the Brightcove account? (Dozens, hundreds, thousands?) | Determines if a simple dropdown works or full search picker needed |
| Q9 | The Account field shows as a dropdown in AEM dialog — are there multiple accounts authors can select from? | May need multi-account support |

### 9.3 Display Mode Confirmation (MUST CONFIRM)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q10 | Can you confirm ALL display modes (CTA types)? We found: **Inline, Button, Link, Teaser**. Are there any others? (e.g., Background video, Autoplay hero, Thumbnail-only) | Directly affects block variants |
| Q11 | Can you confirm ALL playlist layout options? We found: **Right Rail, Bottom Rail**. Are there Left Rail, Top Rail, or others? | Affects player ID mapping and CSS variants |
| Q12 | For Teaser mode — does the thumbnail come from Brightcove automatically, author uploads custom image, or both options? | Affects teaser variant implementation |

### 9.4 HapYak / Interactive Video (IMPORTANT)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q13 | Is HapYak (Brightcove Interactivity) actively used? How widely — a few pages or many? | Determines if we need special handling |
| Q14 | Is HapYak configured per-video in Brightcove Studio? Or per-player? Or per-page in AEM? | If per-player, works automatically with same player ID. If per-page, needs custom handling. |
| Q15 | Is HapYak being retained or retired in the migration? | May not need to support at all |

### 9.5 Analytics & Tracking (IMPORTANT)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q16 | Is video analytics tracked via Brightcove's built-in analytics, Adobe Launch custom events, or both? | Custom Adobe Analytics events need replication in EDS block |
| Q17 | Are there custom data layer events fired on video interactions? (play, pause, 25%/50%/75%/100% completion, etc.) | Specific event names and data needed for EDS implementation |

### 9.6 API Access for Phase 2 (PLAN AHEAD)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q18 | Can we get Brightcove CMS API credentials (client_id/client_secret, OAuth2)? | Required for the video picker UI Extension in UE |
| Q19 | Is there an existing internal API or service that wraps Brightcove? (e.g., a TFS video service/proxy) | Could use existing service instead of direct Brightcove API |
| Q20 | Who approves Brightcove API credential access? | Coordination needed with video/security team |

### 9.7 Edge Cases (GOOD TO KNOW)

| # | Question | Why It Matters |
|---|----------|---------------|
| Q21 | Are there pages with MULTIPLE video components? (e.g., inline video + video gallery on same page) | Multiple Brightcove players on same page — test for conflicts |
| Q22 | Is autoplay used anywhere? (Hero background video, muted autoplay) | Needs specific variant + browser autoplay policy handling |
| Q23 | Are there videos with captions/subtitles? Multi-language? Managed in Brightcove or AEM? | Captions typically managed in Brightcove Studio — just confirm |
| Q24 | Is DRM actively enforced on any TFS videos? (We found EME plugin code) | DRM adds complexity to player initialization |
| Q25 | Are there any videos that require authentication/gating? | May need page-level logic before showing player |
| Q26 | Any plans to move away from Brightcove? (To Dynamic Media or another platform?) | Affects investment level in the integration |

### 9.8 Quick Reference Checklist for the Call

```
MUST CONFIRM (blocks implementation):
  □ Q1:  Account ID — same across all sites?
  □ Q2:  Complete list of ALL player IDs
  □ Q3:  Left Rail / Top Rail player IDs?
  □ Q4:  Player IDs stable or can change?
  □ Q10: All display modes confirmed?
  □ Q11: All playlist layouts confirmed?

IMPORTANT (affects Phase 2 / UX):
  □ Q6:  How authors select videos today?
  □ Q8:  How many videos in account?
  □ Q13: HapYak actively used?
  □ Q16: Custom analytics events?
  □ Q18: Brightcove API credentials available?

GOOD TO KNOW (affects scope):
  □ Q22: Autoplay used?
  □ Q23: Captions/subtitles?
  □ Q24: DRM enforced?
  □ Q26: Plans to change video platform?
```

---

## 10. Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Unknown player IDs for left/top rail | Medium | High | Discovery Q3 — get complete list |
| Player IDs change without notice | Low | High | Config-driven mapping, notify EDS team on changes |
| HapYak requires special handling | Medium | Medium | Discovery Q13-Q15 — assess scope |
| Custom analytics events not documented | Medium | Medium | Discovery Q16-Q17 — get event specs |
| Brightcove API credentials unavailable | Medium | Low | Phase 2 only — fall back to text field input |
| Multiple players on same page conflict | Low | Medium | Test with real TFS pages, use player isolation |
| Brightcove player update breaks layout | Low | Medium | Pin player version or test on updates |
| Modal video + playlist sidebar overflow | Medium | Low | Responsive modal design, test all layouts |

---

## 11. Summary of Decisions

| # | Decision | Recommendation |
|---|----------|---------------|
| 1 | **Block architecture** | Two blocks: `video` (single) + `video-playlist` (playlist) |
| 2 | **Player ID handling** | Config-driven mapping (`video-config.json`), never hardcoded |
| 3 | **Account ID** | Stored in config, never shown to author |
| 4 | **Display modes** | 4 variants per block: inline, button, link, teaser |
| 5 | **Playlist layouts** | CSS class variants: right, left, top, bottom — each maps to player ID |
| 6 | **Modal** | Shared utility for button/link/teaser — lazy loads Brightcove player |
| 7 | **Video ID selection (Phase 1)** | Text field — author pastes ID from Brightcove Studio |
| 8 | **Video ID selection (Phase 2)** | UI Extension — Brightcove picker with search/browse |
| 9 | **Custom code** | Description injection + height sync + share button (port from jQuery) |
| 10 | **Schema.org** | VideoObject JSON-LD for SEO (ported from current AEM) |

---

## Appendix A: Brightcove Data Points Discovered

| Data Point | Value | Source |
|-----------|-------|--------|
| Account ID | `3663210762001` | `data-account` on both pages |
| Player (single) | `08UsfMRkC` | digital-solutions.html |
| Player (playlist right) | `my5Il4M9K` | JCR properties |
| Player (playlist bottom) | `AzszCMDYe3` | JCR properties |
| Player (playlist, from live site) | `hmATFP49Og` | luminex-flexmap-3D.html |
| Video ID (sample) | `6293625135001` | "Connect Platform intro" |
| Playlist videos (sample) | 7 videos | FLEXMAP 3D series |
| Player JS CDN | `players.brightcove.net/{account}/{player}_default/index.min.js` | Network inspection |
| Playback API | `edge.api.brightcove.com/playback/v1/accounts/{account}/videos/{id}` | Schema.org data |
| Thumbnail CDN | `cf-images.us-east-1.prod.boltdns.net/v1/jit/{account}/{id}/...` | Schema.org data |
| HapYak | Present on single player (`08UsfMRkC`) | `.hapyak-player` class |
| sling:resourceType | `brightcove/components/page/brightcoveplayer` | JCR |

## Appendix B: Related Documents

- `HEADER_FOOTER_EDS_SOLUTION_DESIGN.md` — Header/Footer integration design
- Brightcove Player API: https://player.support.brightcove.com/
- Brightcove Playlist Plugin: https://player.support.brightcove.com/plugins/playlist-ui-plugin.html
- Brightcove CMS API: https://apis.support.brightcove.com/cms/
