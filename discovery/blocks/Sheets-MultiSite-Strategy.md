# Multi-Site / Multi-Region Sheet Strategy

**Placement:** Add as Section 9.3 (or new Section 10) in the "Configuration and Data (Sheets) Management in EDS Architecture" document — after Sheet Storage and Organization, before Application in Current Solution.

---

## Context

The solution is a multi-regional, multi-lingual site using:
- AEM MSM (Multi Site Manager) with blueprint/live-copy hierarchy
- Repoless EDS with per-site configurations
- Shared code (one GitHub repo) across all regional sites

Not all sheets have the same regional sensitivity. The strategy for sheet placement in the MSM hierarchy depends on whether the data is locale-specific or globally shared.

---

## Sheet Placement Strategy by Category

| Sheet Category | Placement | MSM Behavior | Rationale |
|---|---|---|---|
| **Standard EDS sheets** (metadata, redirects, placeholders, taxonomy) | **Per-site (per locale)** — in each locale's content root | Authored per live copy. Not inherited from blueprint. | Redirects, placeholders, and metadata are language/region-specific. Each region has its own translated/localized versions. |
| **Block configuration sheets** (e.g., Brightcove account mapping, analytics config) | **Shared globally** — in a global/common content path | Not part of MSM hierarchy. Authored once, consumed by all sites via path mapping. | Technical configuration (account IDs, player IDs, event names) is the same regardless of locale. |
| **Language-neutral business data** (e.g., product SKUs, specifications, comparison tables) | **Shared globally** — in a global data path | Not part of MSM hierarchy. Single source of truth. | Numeric/technical data does not change by language or region. |
| **Localized business data** (e.g., localized labels, regional product availability, regional contacts) | **Per-site (per locale)** — in locale content root | Can be inherited from blueprint and overridden in live copy where localization is needed. | Data is region-specific or translated. |

---

## AEM Content Structure

```
/content/<project>/
│
├── global/                                    ← NOT part of MSM hierarchy
│   ├── configuration/
│   │   └── blocks/
│   │       ├── config-media-brightcove        ← shared (all sites)
│   │       ├── config-analytics-events        ← shared (all sites)
│   │       └── config-forms-rules             ← shared (all sites)
│   └── data/
│       └── tables/
│           ├── product-comparison             ← shared (language-neutral)
│           └── spec-data                      ← shared (language-neutral)
│
├── us/en/                                     ← MSM: Blueprint or Live Copy
│   ├── configuration/
│   │   └── standard/
│   │       ├── metadata                       ← per-site (US English)
│   │       ├── redirects                      ← per-site (US English)
│   │       ├── placeholders                   ← per-site (US English)
│   │       └── taxonomy                       ← per-site (US English)
│   └── data/
│       └── tables/
│           ├── regional-contacts              ← per-site (US-specific)
│           └── localized-labels               ← per-site (English labels)
│
├── fr/fr/                                     ← MSM: Live Copy
│   ├── configuration/
│   │   └── standard/
│   │       ├── metadata                       ← per-site (French)
│   │       ├── redirects                      ← per-site (French)
│   │       ├── placeholders                   ← per-site (French — "Ajouter au panier")
│   │       └── taxonomy                       ← per-site (French)
│   └── data/
│       └── tables/
│           ├── regional-contacts              ← per-site (France-specific)
│           └── localized-labels               ← per-site (French labels)
│
└── de/de/                                     ← MSM: Live Copy
    ├── configuration/
    │   └── standard/
    │       ├── ...                            ← per-site (German)
    └── data/
        └── tables/
            └── ...                            ← per-site (German)
```

---

## How Repoless Sites Access Sheets

Each repoless site's configuration maps both its locale-specific root AND the shared global path:

**Site: tfs-us-en (paths.json / admin config):**

```json
{
  "mappings": [
    "/content/<project>/us/en/:/us/en/",
    "/content/<project>/global/configuration/:/config/",
    "/content/<project>/global/data/:/data/"
  ],
  "includes": [
    "/content/<project>/us/en/",
    "/content/<project>/global/configuration/",
    "/content/<project>/global/data/"
  ]
}
```

**Site: tfs-fr-fr (paths.json / admin config):**

```json
{
  "mappings": [
    "/content/<project>/fr/fr/:/fr/fr/",
    "/content/<project>/global/configuration/:/config/",
    "/content/<project>/global/data/:/data/"
  ],
  "includes": [
    "/content/<project>/fr/fr/",
    "/content/<project>/global/configuration/",
    "/content/<project>/global/data/"
  ]
}
```

**Result for block JS:**

| Block fetches | US site resolves to | FR site resolves to | Same or different? |
|---|---|---|---|
| `/config/media-brightcove.json` | `/content/global/configuration/blocks/config-media-brightcove` | Same | **Same data** (shared) |
| `/us/en/placeholders.json` | `/content/us/en/configuration/standard/placeholders` | N/A | Per-site |
| `/fr/fr/placeholders.json` | N/A | `/content/fr/fr/configuration/standard/placeholders` | Per-site |
| `/data/product-comparison.json` | `/content/global/data/tables/product-comparison` | Same | **Same data** (shared) |

Block JS uses **consistent URL patterns** regardless of which site it's on. Shared sheets resolve to the same global source. Per-site sheets resolve to the locale-specific source.

---

## MSM Behavior for Sheets

### Sheets OUTSIDE MSM (Global — Shared)

| Path | MSM Involvement | Inheritance |
|---|---|---|
| `/content/<project>/global/configuration/blocks/*` | **None** — not part of any blueprint or live copy | N/A — authored once, consumed by all |
| `/content/<project>/global/data/tables/*` | **None** | N/A |

These sheets are **authored once** by a central team (architecture/development) and made available to all regional sites via path mapping. They are never rolled out, never inherited, never overridden.

### Sheets INSIDE MSM (Per-Site — Locale-Specific)

| Path | MSM Involvement | Inheritance Strategy |
|---|---|---|
| `/content/<project>/us/en/configuration/standard/metadata` | Blueprint (if US is blueprint) or Live Copy | **Authored independently per locale** — metadata is different per region |
| `/content/<project>/fr/fr/configuration/standard/redirects` | Live Copy | **Authored independently** — each region has its own redirect rules |
| `/content/<project>/us/en/configuration/standard/placeholders` | Blueprint | Can be **inherited by Live Copies** and overridden where translation is needed |
| `/content/<project>/fr/fr/configuration/standard/placeholders` | Live Copy | **Overridden** — French labels replace English labels |

**For standard sheets (metadata, redirects):** Typically authored independently per region. MSM inheritance provides structure but content is usually overridden because regional differences are significant.

**For placeholders:** MSM inheritance CAN be useful — inherit the base set of placeholder keys from blueprint, override values in each live copy with translations. New placeholders added to blueprint can roll out to live copies (keys added, values need translation).

---

## Decision Matrix: Where to Place a New Sheet

```
Is the data the same for all regions?
├── YES → Is it technical/system configuration?
│   ├── YES → /global/configuration/blocks/<sheet-name>
│   │         (shared, outside MSM, one source of truth)
│   └── NO  → /global/data/tables/<sheet-name>
│             (shared business data, language-neutral)
│
└── NO  → Is it translated or region-specific?
    ├── YES → /<locale>/configuration/standard/<sheet-name>
    │         or /<locale>/data/tables/<sheet-name>
    │         (per-site, inside MSM, can inherit/override)
    └── PARTIALLY → Consider:
        - Shared base structure in global (column schema)
        - Per-site values in locale path (localized content)
        - Or: shared sheet with locale column for filtering
```

---

## Governance Rules for Multi-Site Sheets

| Rule | Why |
|---|---|
| **Changes to global shared sheets affect ALL sites simultaneously** | Requires cross-site testing before publish. Review process mandatory. |
| **Per-site sheets are owned by the regional content team** | Each region manages its own redirects, metadata, placeholders |
| **New global sheets require architecture approval** | Adding a shared resource impacts all sites — must be intentional |
| **Per-site sheets can be added by regional teams independently** | No cross-site impact — region owns its own data |
| **Shared sheets must NOT contain locale-specific data** | If a sheet needs localized values, it belongs in the per-site path |
| **Schema changes to shared sheets require version coordination** | If block JS expects column X and the sheet changes, all sites break simultaneously |

---

## Anti-Patterns to Avoid

| Anti-Pattern | Problem | Correct Approach |
|---|---|---|
| Putting Brightcove config in each locale's content root | Same data duplicated 30+ times. One update requires 30 edits. | Put in `/global/configuration/blocks/` — shared, authored once. |
| Putting localized redirects in the global path | All regions share one redirects file — can't have region-specific old URLs | Put in `/<locale>/configuration/standard/redirects` — per-site. |
| Using MSM rollout for shared global sheets | Global sheets don't need inheritance — they're the same everywhere | Keep global sheets OUTSIDE the MSM tree entirely. |
| Overriding a global shared sheet in a live copy | Creates a fork — that site now diverges from global truth | If a site needs different values, the sheet should be per-site (move to locale path). |
| Putting region-specific office phone numbers in a global sheet with locale columns | One massive sheet with all regions' data. Hard to manage. Unnecessary data sent to each site. | Split into per-site sheets: `/<locale>/data/tables/contacts` |
