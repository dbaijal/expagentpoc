# Brightcove Video Integration — EDS Solution Design

## 1. Overview

This document describes the approach for integrating Brightcove video players into AEM Edge Delivery Services (EDS) with Universal Editor (xWalk), migrating from the current AEM 6.4 Brightcove video component. It covers two block types — Video (single video) and Video Playlist — with environment-agnostic authoring via a centralized config spreadsheet.

---

## 2. Current State (AEM 6.4)

### Components

| Component | Purpose |
|-----------|---------|
| **Video Component** | Embeds a single Brightcove video with default player |
| **Video Playlist Component** | Embeds a Brightcove playlist with configurable player layout |

### How It Works Today

```
Author opens Video component dialog
  → Selects Account from dropdown (CBD, PGH, FEI, etc.)
  → Enters Video ID
  → Player is auto-selected as "Default" for single video
  → AEM reads account config from /etc/brightcovetools/
  → Resolves Account ID + Player ID
  → Renders Brightcove <video-js> embed with correct IDs
```

### Configuration Storage

Account and player configurations are stored in AEM at `/etc/brightcovetools/`:

```
/etc/brightcovetools/
├── cbd-video-players/
│   ├── default-player       (accountId: 3663210762001, playerId: abc123)
│   ├── playlist-bottom-rail  (accountId: 3663210762001, playerId: def456)
│   └── playlist-right-rail   (accountId: 3663210762001, playerId: ghi789)
├── pgh-video-players/
│   ├── default-player       (accountId: 665001591001, playerId: pqr012)
│   ├── playlist-bottom-rail  (accountId: 665001591001, playerId: 9wemoeX81)
│   └── playlist-right-rail   (accountId: 665001591001, playerId: vwx345)
└── fei-video-players/
    ├── default-player       (accountId: 887654321001, playerId: bcd678)
    ├── playlist-bottom-rail  (accountId: 887654321001, playerId: hij901)
    └── playlist-right-rail   (accountId: 887654321001, playerId: nop234)
```

### Key Characteristics

| Characteristic | Details |
|---------------|---------|
| **Multiple Accounts** | CBD, PGH, FEI (and potentially more) — each represents a business unit |
| **3 Player Types** | Default Player, Playlist with bottom rail, Playlist with right rail |
| **Environment-specific IDs** | Account IDs and Player IDs differ across dev, stage, and prod |
| **Auto-player selection** | Video component always uses "Default" player; Playlist component allows author to choose |
| **Brightcove JS** | Loaded dynamically using Account ID + Player ID in script URL |

### Brightcove Embed Requirements

To load a Brightcove player, three values are needed:

```html
<video-js
  data-account="{ACCOUNT_ID}"
  data-player="{PLAYER_ID}"
  data-video-id="{VIDEO_ID}"
  data-embed="default">
</video-js>
<script src="https://players.brightcove.net/{ACCOUNT_ID}/{PLAYER_ID}_default/index.min.js"></script>
```

---

## 3. The Core Challenge

```
component-models.json = STATIC (committed to git repo, same across all environments)
Account IDs / Player IDs = DYNAMIC (different values per environment: dev, stage, prod)
```

**Account IDs and Player IDs cannot be hardcoded in the component model** because they change across environments. Authors should not deal with cryptic IDs — they should work with friendly names.

---

## 4. EDS Approach: Config Spreadsheet + Friendly Names

### Architecture Decision

Authors select **friendly names** (account name + player type) in the Universal Editor dialog. The block JavaScript resolves these names to actual IDs at runtime by fetching a **centralized config spreadsheet**.

```
+-----------------------+       +------------------------------+       +------------------+
| Universal Editor      |       | Config Spreadsheet           |       | Brightcove CDN   |
| (Author selects       | ----> | /content/config/brightcove   | ----> | Players JS       |
|  "CBD" + "default")   |       | Resolves: CBD + default      |       | Loaded with      |
|                       |       | → AccountID: 3663210762001   |       | correct IDs      |
|                       |       | → PlayerID: abc123def        |       |                  |
+-----------------------+       +------------------------------+       +------------------+
```

### Why This Works

| Concern | Solution |
|---------|---------|
| IDs change per environment | Config spreadsheet is environment-specific; same path, different data per env |
| Authors shouldn't see IDs | Authors select from friendly names (CBD, PGH, FEI) |
| Component model must be static | Only friendly names in model — no IDs |
| New accounts may be added | Add rows to config sheet + one option to component model |

---

## 5. Config Spreadsheet

### Path

`/content/config/brightcove`

Each environment (dev, stage, prod) has its own version of this spreadsheet at the same path, containing the correct IDs for that environment.

### Structure

| Account Name | Account ID | Player Name | Player ID | Player Type | Playlist Align |
|-------------|-----------|-------------|-----------|-------------|---------------|
| CBD | 3663210762001 | Default Player | abc123def | default | |
| CBD | 3663210762001 | Playlist with bottom rail | def456ghi | playlist | bottom-rail |
| CBD | 3663210762001 | Playlist with right rail | jkl789mno | playlist | right-rail |
| PGH | 665001591001 | Default Player | pqr012stu | default | |
| PGH | 665001591001 | Playlist with bottom rail | 9wemoeX81 | playlist | bottom-rail |
| PGH | 665001591001 | Playlist with right rail | vwx345yza | playlist | right-rail |
| FEI | 887654321001 | Default Player | bcd678efg | default | |
| FEI | 887654321001 | Playlist with bottom rail | hij901klm | playlist | bottom-rail |
| FEI | 887654321001 | Playlist with right rail | nop234qrs | playlist | right-rail |

### Column Definitions

| Column | Type | Description |
|--------|------|-------------|
| **Account Name** | String | Friendly name of the Brightcove account (e.g., CBD, PGH, FEI) |
| **Account ID** | String | Brightcove account ID (environment-specific) |
| **Player Name** | String | Human-readable player name for reference |
| **Player ID** | String | Brightcove player ID (environment-specific) |
| **Player Type** | String | `default` for single video, `playlist` for playlist players |
| **Playlist Align** | String | Layout variant: `bottom-rail` or `right-rail` (empty for default) |

### Environment Management

```
DEV environment:  /content/config/brightcove → CBD Account ID = 1111111111
STAGE environment: /content/config/brightcove → CBD Account ID = 2222222222
PROD environment: /content/config/brightcove → CBD Account ID = 3663210762001
```

Same spreadsheet path, different content per environment. Authors never see the IDs — they select "CBD" and the correct ID is resolved at runtime.

---

## 6. Block 1: Video (Single Video)

### Purpose

Embeds a single Brightcove video. Always uses the **default player** for the selected account.

### Authoring Experience

The dialog dynamically shows/hides fields based on the Call to Action selection using UE's `condition` property (JsonLogic).

**When CTA = "None" (default):**
```
+---------------------------------------+
| Video Block Properties                |
+---------------------------------------+
| Account:         [CBD          v]     |
| Video ID:        [6293625135001]      |
| Call to Action:  [None         v]     |
| Poster Image:    [Browse...]         |
+---------------------------------------+
```

**When CTA = "Button":**
```
+---------------------------------------+
| Video Block Properties                |
+---------------------------------------+
| Account:         [CBD          v]     |
| Video ID:        [6293625135001]      |
| Call to Action:  [Button       v]     |
| CTA Text:        [Watch Now    ]      |  <-- appears
| CTA Link:        [/contact-us  ]      |  <-- appears
| Poster Image:    [Browse...]         |
+---------------------------------------+
```

**When CTA = "Teaser":**
```
+---------------------------------------+
| Video Block Properties                |
+---------------------------------------+
| Account:         [CBD          v]     |
| Video ID:        [6293625135001]      |
| Call to Action:  [Teaser       v]     |
| Teaser Title:    [Are you ready]      |  <-- appears
| Teaser Desc:     [The Connect..]      |  <-- appears
| Teaser Image:    [Browse...]         |  <-- appears
| Teaser Link:     [/products/...]      |  <-- appears
| Poster Image:    [Browse...]         |
+---------------------------------------+
```

Author selects account by friendly name and enters the Video ID from Brightcove Studio. No player selection needed — default player is used automatically. Call to Action fields appear conditionally based on the CTA type selection.

### Component Definition

```json
{
  "id": "video",
  "title": "Video",
  "description": "Embed a single Brightcove video with optional call to action",
  "group": "Media"
}
```

### Component Model

```json
{
  "id": "video",
  "fields": [
    {
      "component": "select",
      "name": "account",
      "label": "Account",
      "valueType": "string",
      "required": true,
      "options": [
        { "name": "CBD", "value": "cbd" },
        { "name": "PGH", "value": "pgh" },
        { "name": "FEI", "value": "fei" }
      ]
    },
    {
      "component": "text",
      "name": "videoId",
      "label": "Video ID",
      "valueType": "string",
      "required": true,
      "description": "Brightcove Video ID from Brightcove Studio"
    },
    {
      "component": "select",
      "name": "cta",
      "label": "Call to Action",
      "valueType": "string",
      "options": [
        { "name": "None", "value": "" },
        { "name": "Button", "value": "button" },
        { "name": "Teaser", "value": "teaser" }
      ]
    },
    {
      "component": "text",
      "name": "ctaText",
      "label": "Call to Action Text",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "button"] }
    },
    {
      "component": "text",
      "name": "ctaLink",
      "label": "Call to Action Link",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "button"] }
    },
    {
      "component": "text",
      "name": "teaserTitle",
      "label": "Teaser Title",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "teaser"] }
    },
    {
      "component": "text",
      "name": "teaserDescription",
      "label": "Teaser Description",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "teaser"] }
    },
    {
      "component": "reference",
      "name": "teaserImage",
      "label": "Teaser Image",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "teaser"] }
    },
    {
      "component": "text",
      "name": "teaserLink",
      "label": "Teaser Link",
      "valueType": "string",
      "condition": { "===": [{ "var": "cta" }, "teaser"] }
    },
    {
      "component": "reference",
      "name": "image",
      "label": "Poster Image",
      "valueType": "string",
      "description": "Optional poster image shown before video plays"
    }
  ]
}
```

### Conditional Field Behavior

The `condition` property uses **JsonLogic** syntax to dynamically show/hide fields in the Universal Editor properties panel:

| CTA Selection | Condition | Fields Visible |
|--------------|-----------|----------------|
| None (`""`) | No conditions met | Account, Video ID, CTA dropdown, Poster Image |
| Button | `{ "===": [{ "var": "cta" }, "button"] }` | + CTA Text, CTA Link |
| Teaser | `{ "===": [{ "var": "cta" }, "teaser"] }` | + Teaser Title, Teaser Description, Teaser Image, Teaser Link |

This provides the **exact same conditional dialog behavior** as the AEM 6.4 video component, where selecting a Call to Action type reveals the relevant fields.

### Runtime Flow

```
Page loads
  → Video block decorate() runs
  → Reads block config: account = "cbd", videoId = "6293625135001"
  → Fetches /content/config/brightcove.json
  → Finds row: Account Name = "CBD" AND Player Type = "default"
  → Resolves: Account ID = 3663210762001, Player ID = abc123def
  → Creates <video-js> element with resolved IDs
  → Loads Brightcove script: players.brightcove.net/3663210762001/abc123def_default/index.min.js
  → Brightcove player renders
```

### Block JS (blocks/video/video.js)

```javascript
import { resolveConfig } from '../../scripts/brightcove-config.js';

export default async function decorate(block) {
  // Read authored properties from block table
  const rows = [...block.children];
  const account = rows[0]?.children[1]?.textContent?.trim();
  const videoId = rows[1]?.children[1]?.textContent?.trim();
  const ctaType = rows[2]?.children[1]?.textContent?.trim() || '';
  const posterImg = block.querySelector('img');

  if (!account || !videoId) {
    block.textContent = '';
    return;
  }

  // Resolve account name to actual IDs
  const config = await resolveConfig(account, 'default');
  if (!config) {
    block.textContent = 'Video configuration error. Check Brightcove config.';
    return;
  }

  // Extract CTA-specific fields based on type
  let ctaData = {};
  if (ctaType === 'button') {
    ctaData = {
      text: rows[3]?.children[1]?.textContent?.trim() || '',
      link: rows[4]?.children[1]?.querySelector('a')?.href
        || rows[4]?.children[1]?.textContent?.trim() || '',
    };
  } else if (ctaType === 'teaser') {
    ctaData = {
      title: rows[3]?.children[1]?.textContent?.trim() || '',
      description: rows[4]?.children[1]?.textContent?.trim() || '',
      image: rows[5]?.querySelector('img'),
      link: rows[6]?.children[1]?.querySelector('a')?.href
        || rows[6]?.children[1]?.textContent?.trim() || '',
    };
  }

  // Build player markup
  block.textContent = '';

  const wrapper = document.createElement('div');
  wrapper.className = 'video-wrapper';

  const videoEl = document.createElement('video-js');
  videoEl.setAttribute('data-account', config.accountId);
  videoEl.setAttribute('data-player', config.playerId);
  videoEl.setAttribute('data-video-id', videoId);
  videoEl.setAttribute('data-embed', 'default');
  videoEl.setAttribute('controls', '');
  videoEl.classList.add('vjs-fluid');

  if (posterImg) {
    videoEl.setAttribute('poster', posterImg.src);
  }

  wrapper.append(videoEl);

  // Render Call to Action based on type
  if (ctaType === 'button' && ctaData.text) {
    const ctaWrapper = document.createElement('div');
    ctaWrapper.className = 'video-cta video-cta-button';
    const link = document.createElement('a');
    link.href = ctaData.link;
    link.className = 'button primary';
    link.textContent = ctaData.text;
    ctaWrapper.append(link);
    wrapper.append(ctaWrapper);
  } else if (ctaType === 'teaser') {
    const teaserWrapper = document.createElement('div');
    teaserWrapper.className = 'video-cta video-cta-teaser';

    if (ctaData.image) {
      const imgWrapper = document.createElement('div');
      imgWrapper.className = 'teaser-image';
      imgWrapper.append(ctaData.image);
      teaserWrapper.append(imgWrapper);
    }

    const teaserContent = document.createElement('div');
    teaserContent.className = 'teaser-content';

    if (ctaData.title) {
      const h3 = document.createElement('h3');
      h3.textContent = ctaData.title;
      teaserContent.append(h3);
    }
    if (ctaData.description) {
      const p = document.createElement('p');
      p.textContent = ctaData.description;
      teaserContent.append(p);
    }
    if (ctaData.link) {
      const link = document.createElement('a');
      link.href = ctaData.link;
      link.className = 'teaser-link';
      link.textContent = ctaData.title || 'Learn More';
      teaserContent.append(link);
    }

    teaserWrapper.append(teaserContent);
    wrapper.append(teaserWrapper);
  }

  block.append(wrapper);

  // Load Brightcove player JS
  const script = document.createElement('script');
  script.src = `https://players.brightcove.net/${config.accountId}/${config.playerId}_default/index.min.js`;
  script.async = true;
  block.append(script);
}
```

---

## 7. Block 2: Video Playlist

### Purpose

Embeds a Brightcove playlist with configurable player layout. Author selects account, playlist ID, and player type (default, bottom rail, or right rail).

### Authoring Experience

```
+---------------------------------------+
| Video Playlist Block Properties       |
+---------------------------------------+
| Account:     [PGH              v]     |
| Playlist ID: [1234567890        ]     |
| Player:      [Playlist with     v]    |
|              [bottom rail       ]     |
| Title:       [Featured Videos   ]     |
+---------------------------------------+
```

### Component Definition

```json
{
  "id": "video-playlist",
  "title": "Video Playlist",
  "description": "Embed a Brightcove video playlist with configurable layout",
  "group": "Media"
}
```

### Component Model

```json
{
  "id": "video-playlist",
  "fields": [
    {
      "component": "select",
      "name": "account",
      "label": "Account",
      "valueType": "string",
      "required": true,
      "options": [
        { "name": "CBD", "value": "cbd" },
        { "name": "PGH", "value": "pgh" },
        { "name": "FEI", "value": "fei" }
      ]
    },
    {
      "component": "text",
      "name": "playlistId",
      "label": "Playlist ID",
      "valueType": "string",
      "required": true,
      "description": "Brightcove Playlist ID from Brightcove Studio"
    },
    {
      "component": "select",
      "name": "playerType",
      "label": "Player",
      "valueType": "string",
      "required": true,
      "options": [
        { "name": "Default Player", "value": "default" },
        { "name": "Playlist with bottom rail", "value": "bottom-rail" },
        { "name": "Playlist with right rail", "value": "right-rail" }
      ]
    },
    {
      "component": "text",
      "name": "title",
      "label": "Title",
      "valueType": "string"
    }
  ]
}
```

### Runtime Flow

```
Page loads
  → Video Playlist block decorate() runs
  → Reads block config: account = "pgh", playlistId = "1234567890", playerType = "bottom-rail"
  → Fetches /content/config/brightcove.json
  → Finds row: Account Name = "PGH" AND Player Type = "playlist" AND Playlist Align = "bottom-rail"
  → Resolves: Account ID = 665001591001, Player ID = 9wemoeX81
  → Creates <video-js> element with data-playlist-id
  → Loads Brightcove script with resolved IDs
  → Brightcove playlist player renders with bottom rail layout
```

### Block JS (blocks/video-playlist/video-playlist.js)

```javascript
import { resolveConfig } from '../../scripts/brightcove-config.js';

export default async function decorate(block) {
  // Read authored properties from block table
  const rows = [...block.children];
  const account = rows[0]?.children[1]?.textContent?.trim();
  const playlistId = rows[1]?.children[1]?.textContent?.trim();
  const playerType = rows[2]?.children[1]?.textContent?.trim() || 'default';
  const title = rows[3]?.children[1]?.textContent?.trim() || '';

  if (!account || !playlistId) {
    block.textContent = '';
    return;
  }

  // Resolve account name + player type to actual IDs
  const config = await resolveConfig(account, playerType);
  if (!config) {
    block.textContent = 'Playlist configuration error. Check Brightcove config.';
    return;
  }

  // Build player markup
  block.textContent = '';

  // Add layout class for CSS styling
  const layout = config.playlistAlign || 'default';
  block.classList.add(`playlist-${layout}`);

  if (title) {
    const h3 = document.createElement('h3');
    h3.className = 'playlist-title';
    h3.textContent = title;
    block.append(h3);
  }

  const wrapper = document.createElement('div');
  wrapper.className = 'playlist-wrapper';

  const videoEl = document.createElement('video-js');
  videoEl.setAttribute('data-account', config.accountId);
  videoEl.setAttribute('data-player', config.playerId);
  videoEl.setAttribute('data-playlist-id', playlistId);
  videoEl.setAttribute('data-embed', 'default');
  videoEl.setAttribute('controls', '');
  videoEl.classList.add('vjs-fluid', 'vjs-playlist');

  wrapper.append(videoEl);
  block.append(wrapper);

  // Load Brightcove player JS
  const script = document.createElement('script');
  script.src = `https://players.brightcove.net/${config.accountId}/${config.playerId}_default/index.min.js`;
  script.async = true;
  block.append(script);
}
```

---

## 8. Shared Config Resolver

### Utility Module (scripts/brightcove-config.js)

Used by both Video and Video Playlist blocks to resolve friendly names to Brightcove IDs.

```javascript
/**
 * Brightcove Configuration Resolver
 *
 * Fetches the centralized Brightcove config spreadsheet and resolves
 * account names + player types to actual Account IDs and Player IDs.
 *
 * Config spreadsheet path: /content/config/brightcove
 * Each environment (dev, stage, prod) has its own spreadsheet content.
 */

let configCache = null;

/**
 * Loads the Brightcove config spreadsheet (cached after first load)
 * @returns {Array} Config rows
 */
async function loadConfig() {
  if (configCache) return configCache;

  try {
    const resp = await fetch('/content/config/brightcove.json');
    if (!resp.ok) {
      console.error('Failed to load Brightcove config:', resp.status);
      return [];
    }
    const { data } = await resp.json();
    configCache = data;
    return data;
  } catch (err) {
    console.error('Error loading Brightcove config:', err);
    return [];
  }
}

/**
 * Resolves account name + player type to actual Brightcove IDs
 *
 * @param {string} accountName - Friendly account name (e.g., "cbd", "pgh", "fei")
 * @param {string} playerType - Player type:
 *   - "default" — default single-video player
 *   - "bottom-rail" — playlist with bottom rail layout
 *   - "right-rail" — playlist with right rail layout
 * @returns {Object|null} { accountId, playerId, playlistAlign } or null if not found
 */
export async function resolveConfig(accountName, playerType = 'default') {
  const config = await loadConfig();

  const match = config.find((row) => {
    const nameMatch = row['Account Name'].toLowerCase() === accountName.toLowerCase();

    if (playerType === 'default') {
      return nameMatch && row['Player Type'] === 'default';
    }

    // For playlist players, match on Player Type = "playlist" AND Playlist Align
    return nameMatch
      && row['Player Type'] === 'playlist'
      && row['Playlist Align'] === playerType;
  });

  if (!match) {
    console.error(
      `Brightcove config not found: account="${accountName}", playerType="${playerType}"`,
    );
    return null;
  }

  return {
    accountId: match['Account ID'],
    playerId: match['Player ID'],
    playlistAlign: match['Playlist Align'] || '',
    playerName: match['Player Name'] || '',
  };
}

/**
 * Returns all available account names from the config
 * Useful for validation or dynamic UI
 * @returns {Array<string>} Unique account names
 */
export async function getAccountNames() {
  const config = await loadConfig();
  return [...new Set(config.map((row) => row['Account Name']))];
}

/**
 * Returns all player types available for a given account
 * @param {string} accountName - Friendly account name
 * @returns {Array<Object>} Available player types with labels
 */
export async function getPlayerTypes(accountName) {
  const config = await loadConfig();
  return config
    .filter((row) => row['Account Name'].toLowerCase() === accountName.toLowerCase())
    .map((row) => ({
      type: row['Player Type'] === 'default' ? 'default' : row['Playlist Align'],
      label: row['Player Name'],
    }));
}
```

---

## 9. How Environment Resolution Works

```
                     component-models.json (STATIC — committed to repo)
                     +-------------------------------+
                     | Account: [CBD v] (name only)  |
                     | Video ID: [6293625135001]     |
                     +-------------------------------+
                                   |
                                   | author selects "CBD"
                                   | (same across all envs)
                                   v
              /content/config/brightcove.json (DYNAMIC — per environment)
    +--------------------------------------------------------------------+
    | DEV:   CBD → Account ID: 1111111111001, Player ID: devPlayer123    |
    | STAGE: CBD → Account ID: 2222222222001, Player ID: stagePlayer456  |
    | PROD:  CBD → Account ID: 3663210762001, Player ID: abc123def       |
    +--------------------------------------------------------------------+
                                   |
                                   | block JS resolves at runtime
                                   v
                     Brightcove player loads with correct IDs
                     for the current environment
```

### What Authors See (Same Across Environments)

```
Account: [CBD v]       ← friendly name, never changes
Video ID: [6293625135001]  ← Brightcove video ID, same across envs
```

### What the Block Resolves (Different Per Environment)

```
DEV:   https://players.brightcove.net/1111111111001/devPlayer123_default/index.min.js
STAGE: https://players.brightcove.net/2222222222001/stagePlayer456_default/index.min.js
PROD:  https://players.brightcove.net/3663210762001/abc123def_default/index.min.js
```

---

## 10. Adding New Accounts

When a new Brightcove account is added (e.g., "LSG"):

| Step | Action | Who | Where |
|------|--------|-----|-------|
| 1 | Add account/player rows to config spreadsheet | Admin | `/content/config/brightcove` (all envs) |
| 2 | Add `{ "name": "LSG", "value": "lsg" }` to select options | Developer | `component-models.json` (code deploy) |

### Frequency

New Brightcove accounts are rare — they correspond to business units. This is not a frequent operation. The one-time code change to add a select option is acceptable.

### Alternative: UI Extension (If Zero-Code-Change Is Required)

If adding accounts with zero code changes is a hard requirement, a UI Extension can dynamically load account names from the config spreadsheet:

```javascript
// UI Extension: dynamically fetches account names
const resp = await fetch('/content/config/brightcove.json');
const { data } = await resp.json();
const accounts = [...new Set(data.map((r) => r['Account Name']))];
// Renders dynamic dropdown in UE properties panel
```

**Recommendation:** Use the static select approach unless account changes are frequent. The UI Extension adds complexity for minimal gain in this scenario.

---

## 11. Comparison: AEM 6.4 vs EDS

| Aspect | AEM 6.4 | EDS | Impact |
|--------|---------|-----|--------|
| **Config storage** | `/etc/brightcovetools/` in JCR | Spreadsheet at `/content/config/brightcove` | Admin manages spreadsheet instead of JCR nodes |
| **Account selection** | Dropdown populated from JCR config | Static dropdown with friendly names | Same author experience |
| **Player selection** | Dropdown filtered by account (dynamic) | Static dropdown (default / bottom-rail / right-rail) | Slightly different — author selects player type, not specific player |
| **ID resolution** | Server-side Sling Model reads JCR config | Client-side JS fetches config spreadsheet | Same result, different execution model |
| **Environment handling** | JCR config is environment-specific | Spreadsheet content is environment-specific | Same pattern, different storage |
| **Brightcove JS loading** | Server renders `<script>` tag in HTML | Block JS dynamically creates `<script>` tag | No visual difference |
| **Video component** | Account + Video ID (player auto = default) | Account + Video ID (player auto = default) | Identical experience |
| **Playlist component** | Account + Playlist ID + Player dropdown | Account + Playlist ID + Player Type dropdown | Identical experience |
| **Call to Action** | Conditional fields in dialog (Button → CTA text; Teaser → title, desc, image) | `condition` property with JsonLogic in component model | Same conditional behavior, same author experience |
| **Adding new accounts** | Add JCR nodes | Add spreadsheet rows + component model option | Slightly more steps |

---

## 12. Block CSS Reference

### Video Block (blocks/video/video.css)

```css
.video {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}

.video .video-wrapper {
  position: relative;
  width: 100%;
}

.video video-js {
  width: 100%;
  aspect-ratio: 16 / 9;
}

/* Call to Action — Button */
.video .video-cta-button {
  padding: 16px 0;
  text-align: center;
}

.video .video-cta-button .button {
  display: inline-block;
  padding: 12px 24px;
  background-color: var(--link-color);
  color: white;
  text-decoration: none;
  border-radius: 4px;
  font-weight: 600;
}

.video .video-cta-button .button:hover {
  background-color: var(--link-hover-color);
}

/* Call to Action — Teaser */
.video .video-cta-teaser {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--light-color);
  margin-top: 16px;
}

.video .video-cta-teaser .teaser-image img {
  width: 100%;
  height: auto;
  border-radius: 4px;
}

.video .video-cta-teaser .teaser-content h3 {
  margin: 0 0 8px;
  font-size: var(--heading-font-size-s);
}

.video .video-cta-teaser .teaser-content p {
  margin: 0 0 12px;
  color: var(--dark-color);
  font-size: var(--body-font-size-s);
}

.video .video-cta-teaser .teaser-link {
  font-weight: 600;
  color: var(--link-color);
}

@media (width < 600px) {
  .video .video-cta-teaser {
    grid-template-columns: 1fr;
  }
}
```

### Video Playlist Block (blocks/video-playlist/video-playlist.css)

```css
.video-playlist {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.video-playlist .playlist-title {
  margin: 0 0 16px;
  font-size: var(--heading-font-size-m);
}

.video-playlist .playlist-wrapper {
  width: 100%;
}

.video-playlist video-js {
  width: 100%;
}

/* Default layout — playlist below video */
.video-playlist.playlist-default .playlist-wrapper {
  display: flex;
  flex-direction: column;
}

/* Bottom rail — playlist strip below video */
.video-playlist.playlist-bottom-rail .playlist-wrapper {
  display: flex;
  flex-direction: column;
}

.video-playlist.playlist-bottom-rail .vjs-playlist {
  display: flex;
  overflow-x: auto;
  gap: 8px;
  padding: 8px 0;
}

/* Right rail — playlist sidebar on the right */
.video-playlist.playlist-right-rail .playlist-wrapper {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 16px;
}

@media (width < 768px) {
  /* Stack right rail below video on mobile */
  .video-playlist.playlist-right-rail .playlist-wrapper {
    grid-template-columns: 1fr;
  }
}
```

---

## 13. Limitations

| Limitation | Detail | Mitigation |
|-----------|--------|------------|
| **Static account dropdown** | Adding new account requires code change to component model | New accounts are rare (business unit level). UI Extension can be built if zero-code-change is required. |
| **No dynamic player filtering by account** | AEM 6.4 filters player dropdown based on selected account. EDS shows all player types for all accounts. | Player types (default, bottom-rail, right-rail) are the same across all accounts. If an account doesn't have a specific player, the config resolution returns null and shows an error — author corrects the selection. |
| **Client-side config resolution** | Config spreadsheet is fetched in the browser (not server-side) | Spreadsheet JSON is edge-cached on CDN (5-20ms). Cached after first load on the page. Imperceptible delay. |
| **Config spreadsheet per environment** | Admin must maintain spreadsheet in each environment | Same pattern as current JCR config in `/etc/brightcovetools/`. Can be scripted for deployment. |

---

## 14. File Structure

```
/workspace/
├── blocks/
│   ├── video/
│   │   ├── video.js              ← Single video block
│   │   └── video.css             ← Video styling
│   └── video-playlist/
│       ├── video-playlist.js     ← Playlist block
│       └── video-playlist.css    ← Playlist styling (default, bottom-rail, right-rail)
├── scripts/
│   └── brightcove-config.js      ← Shared config resolver (resolveConfig, getAccountNames, getPlayerTypes)
├── component-models.json          ← Video + Video Playlist component models
├── component-definition.json      ← Block definitions
└── content/
    └── config/
        └── brightcove             ← Config spreadsheet (Account Name → Account ID + Player ID)
```

---

## 15. Implementation Checklist

- [ ] Create config spreadsheet template at `/content/config/brightcove`
- [ ] Populate config spreadsheet with all account/player mappings for each environment
- [ ] Create `scripts/brightcove-config.js` shared resolver module
- [ ] Add Video block definition and model to `component-definition.json` and `component-models.json`
- [ ] Implement `blocks/video/video.js` with config resolution
- [ ] Implement `blocks/video/video.css`
- [ ] Add Video Playlist block definition and model
- [ ] Implement `blocks/video-playlist/video-playlist.js` with config resolution and player type support
- [ ] Implement `blocks/video-playlist/video-playlist.css` with three layout variants
- [ ] Test single video with each account (CBD, PGH, FEI)
- [ ] Test playlist with all three player types (default, bottom-rail, right-rail)
- [ ] Test config resolution error handling (missing account, missing player type)
- [ ] Verify Brightcove JS loads correctly with resolved IDs
- [ ] Test across environments (dev, stage, prod config spreadsheets)
- [ ] Validate responsive behavior (mobile, tablet, desktop)
- [ ] Performance test — verify config spreadsheet caching works (single fetch per page)
