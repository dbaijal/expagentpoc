# TFS Fragment Block — Behavior Specification

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> Companion to the **Experience Fragments — EDS Solution Design**. This document specifies the runtime behavior of the **TFS Fragment block**, the single block used to include Experience-Fragment-derived content (both **form fragments** and **site / marketing fragments**) on a page with best-match variation resolution.

---

## Executive Summary

The current AEM Experience Fragment inclusion components include **best-match variation logic** to serve the correct localized variation to each visitor. This behavior is **fully preserved** in the EDS target architecture.

The mechanism changes — from server-side Java traversing the MSM hierarchy to **client-side JavaScript that reads the page URL and resolves against a published fragment index** — but the outcome is identical: the most specific matching variation is served, with a defined fallback chain ending at the global master.

| Concern | Answer |
|---|---|
| Is best-match logic preserved? | **Yes — fully preserved** |
| Is it replaced? | **The mechanism is replaced (Java → JS + index lookup). The behavior is not.** |
| How is the matching variation found? | **Fragment index lookup** — the block resolves the best-match candidate against a published index and fetches the single matching variation (Section 3). |

---

## 1. Current AEM — Experience Fragment Inclusion

### What The Author Does
The author selects a fragment from a curated dropdown in the component dialog.

### What Happens At Render Time

**Build Locale Candidate List** (MSM live-copy pages):
```
Walk MSM hierarchy upward from current page
→ Build ordered locale list: [lang-country, lang-region, lang]
→ For each locale: check translation status (must be APPROVED or COMPLETE)
→ If incomplete: remove that locale from list
```

**Match and Render:**
Walk the candidate list in order, return the first variation that exists under the fragment; if none match, return `master`. Render the resolved variation with `wcmmode=disabled`.

---

## 2. EDS Target — TFS Fragment Block

### What It Is
A **single block** — the **TFS Fragment block** — placed on a page to include an Experience-Fragment-derived variation. It applies to **both fragment types**:
- **Form fragments** (localized form fieldsets, with a division dimension)
- **Site / marketing fragments**

It replaces the AEM Experience Fragment inclusion components in the EDS page model.

### What The Author Does
The author enters one field — the fragment **base path** into the centralized fragment library.

Standard form fragment:
```
| tfs-fragment                                                    |
| --------------------------------------------------------------- |
| /content/tfs/fragments/form-fragments/standard/marketing-opt-in |
```

Division-specific form fragment:
```
| tfs-fragment                                                        |
| ------------------------------------------------------------------- |
| /content/tfs/fragments/form-fragments/custom/lcd/marketing-opt-in   |
```

Site / marketing fragment:
```
| tfs-fragment                                                    |
| --------------------------------------------------------------- |
| /content/tfs/fragments/site-fragments/<fragment-name>           |
```

No dropdown. No `fragmentType` field. For form fragments, the **division is encoded in the path** — standard fragments use `.../form-fragments/standard/`, division fragments use `.../form-fragments/custom/{division}/`.

### What The Block Does At Runtime
The block JavaScript reads the base path, determines the visitor's locale from the page URL, builds an ordered candidate list, **resolves the first existing candidate against the published fragment index**, and fetches that single matching variation. Full logic is defined in Section 3.

---

## 3. TFS Fragment Block — Execution Flow (Index Lookup)

The block resolves the correct variation using a **dedicated published fragment index** (`fragment-index.json`, produced via `helix-query.yaml`), rather than trial-and-error fetching. This index is **scoped to the fragment library only** — it indexes fragments and their variations, not all site pages — so it stays small and fast to fetch. The block fetches the index **once per page** (cached), resolves the best-match candidate **in memory**, then fetches the **single** matched variation.

```
PAGE LOADS — TFS Fragment block is on the page
│
▼
READ base path from block
basePath = "/content/tfs/fragments/form-fragments/standard/marketing-opt-in"
│
▼
PARSE locale from page URL
URL: /us/en/home/...
→ country = "us"
→ lang    = "en"
│
▼
BUILD ordered candidate list  [most specific → least specific → master]
│
├── Always add:  {lang}-{country}          →  "en-us"
├── If lang ≠ en, also add: en-{country}   →  (e.g. "en-cn" for a zh/cn page)
├── Look up country in region map:
│       us → north-america                 →  "en-north-america"
└── Always add:  "master"                  →  "master"
candidates = ["en-us", "en-north-america", "master"]
│
▼
FETCH the fragment index ONCE (cached for the page)
index = fragment-index.json  (scoped to the fragment library only)
│
▼
RESOLVE in memory — walk candidates in order, pick the FIRST that EXISTS in the index
│   e.g. index has "en-north-america" for this fragment → resolved = "en-north-america"
│   (master always exists → guaranteed resolution)
│
▼
FETCH the single resolved variation → basePath/{resolved}.plain.html
│
▼
RENDER matched variation HTML into block
```

**Why index lookup (not sequential 404 probing):** because the fragment library is centralized (single site / content bus), a dedicated `fragment-index.json` — scoped to the fragment library only — lists every fragment and the variations that exist for it. Keeping the index scoped to fragments (rather than indexing all site pages) keeps it small and fast. The block checks existence **in the index (in memory)** and issues **one** content fetch for the resolved variation — avoiding trial-and-error requests and 404 noise.

### Example — German Page
```
URL: /de/de/home/...   →   country=de, lang=de
Candidates: de-de → en-de → en-europe → master
Index lookup:
  de-de       → not in index
  en-de       → not in index
  en-europe   → present  ✓  → resolved
Fetch basePath/en-europe.plain.html  →  render European English variation
```

### Example — Korean Page
```
URL: /kr/ko/home/...   →   country=kr, lang=ko
Candidates: ko-kr → en-kr → en-ipac → master
Index lookup:
  ko-kr       → present  ✓  → resolved
Fetch basePath/ko-kr.plain.html  →  render Korean variation
```

---

## 4. What Is Preserved and What Is Simplified

### What Is Preserved

| Behavior | Status |
|---|---|
| Best-match variation selection | Preserved — most specific first, fallback to regional, fallback to master |
| Country-specific variations (en-us, en-ca, zh-cn, de-de, ko-kr …) | Preserved |
| Regional fallback chain (en-north-america, en-europe, en-ipac …) | Preserved |
| Non-English language handling (zh, de, ko, ja, es) | Preserved — tries native language first, then English equivalent, then region |
| Division-specific fragments | Preserved — division encoded in path (`custom/{division}/`) |
| Master as universal final fallback | Preserved |
| Renders nothing when path is wrong | Preserved — placeholder shown |

### What Is Simplified

**1. Locale derivation — MSM hierarchy replaced by URL parsing**
In AEM, locale was derived by walking the MSM live-copy tree upward from the current page. In EDS, each locale is a distinct URL (`/us/en/` ≠ `/de/de/`); the locale is parsed from the URL path. The business outcome — correct locale identified — is identical.

**2. Translation status gate replaced by variation existence (in the index)**
In AEM, a variation was used only if its translation status was APPROVED or COMPLETE. In EDS, **publishing a variation page is the equivalent of marking it ready** — it then appears in the fragment index. A variation that is not published/does not exist is **absent from the index** and is skipped, falling back to the next candidate. Same business outcome: unready content is never shown; the fallback variation is used instead.

**3. Logic runs in JS, not Java**
The best-match algorithm runs in the block's JavaScript in the browser instead of in a Sling Model on the server. The logic is equivalent — build candidate list, resolve first existing match (via the index), fetch it. No behavioral change.

**4. Resolution via index lookup, not trial-and-error fetching**
Instead of fetching candidate URLs one by one and skipping 404s, the block reads the published fragment index once and resolves the best match in memory, then fetches only the matched variation. Same outcome, without redundant requests.

**5. Authoring — path field instead of dropdown**
In AEM, the author selected from a constrained dropdown populated by a datasource servlet. In EDS, the author enters the fragment base path directly; the division is implicit in the path. Author guidance can be provided via a path browser in Universal Editor scoped to the fragments root.

---

## 5. Direct Logic Mapping

| AEM Mechanism | EDS Equivalent |
|---|---|
| `fragmentPath` from author dialog dropdown | `path` field in the TFS Fragment block — author enters base path |
| `fragmentType` STANDARD / CUSTOM hidden field | Encoded in path: `.../form-fragments/standard/` or `.../form-fragments/custom/{division}/` |
| MSM hierarchy walk → ordered locale list | URL path parsing → `[lang]-[country]` + region lookup |
| Translation status gate (APPROVED / COMPLETE) | Variation present in the fragment index (published) — absent = not ready = skip |
| `XFragmentInclusionService.getBestMatchedVariation()` | Ordered candidate list resolved against the fragment **index** (first existing wins) |
| Fallback to `master` | `master` always last in candidate list — always present in the index |
| Render with `wcmmode=disabled` | Fetch resolved variation with `.plain.html` — content only, no editor chrome |

---

## 6. Notes

- **Single index fetch per page:** the fragment index is fetched once and reused for all TFS Fragment block instances on the page.
- **All-miss behavior:** `master` is always present in the index, so resolution normally cannot fully fail; if nothing resolves, the block renders a placeholder rather than erroring.
- **Same-origin:** with a single EDS site / content bus, the index and variation fetches are same-origin (no CORS).
- **Applies to all fragments:** the same block and resolution logic serve both form fragments and site / marketing fragments; only the base path differs.
