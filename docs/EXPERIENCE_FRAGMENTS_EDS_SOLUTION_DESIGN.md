# Experience Fragments — EDS Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 0.1
**Date:** June 2026
**Status:** Draft for Review

---

## 1. Executive Summary

This document defines the approach for migrating Thermo Fisher's Experience Fragments (XF) to AEM Edge Delivery Services (EDS) with AEM as the authoring source (Crosswalk).

In the current AEM environment, Experience Fragments hold a master variation plus multiple country / region / language variations, and a custom component performs **best-match resolution** at render time — selecting the most specific matching variation for the visitor's locale, with a defined fallback chain ending at the global master.

Edge Delivery has no Experience Fragment concept; XFs are migrated to **EDS fragments**. The best-match behavior is **fully preserved for all fragments**. What changes is the mechanism: server-side Java traversing the MSM hierarchy is replaced by client-side JavaScript that derives the locale from the page URL and resolves the correct variation. The outcome is identical — the most specific matching variation is served, falling back to master.

Key points:
- Fragments are **centralized** in a dedicated fragment library, mirroring how XFs are organized today.
- Best-match resolution applies to **all fragments** (both fragment types described in Section 2).
- Two migration options are presented for how variations are structured; the choice is an **authoring/maintenance** decision, as runtime behavior is identical for both.

---

## 2. Current State — TFS Experience Fragments

### 2.1 The XF Model

- An Experience Fragment consists of a **master variation** plus multiple **variations** (country, region, and language specific).
- A page references only the **XF shell / base path**. A custom container component resolves *which* variation to display at render time, based on the page's locale.
- **Resolution order (best-match):** most specific first, falling back to least specific, ending at master:
  1. exact `language-country` (e.g. `en-us`, `de-de`, `ko-kr`)
  2. `language-region` (e.g. `en-north-america`, `en-europe`, `en-ipac`)
  3. `language` only
  4. `master` (global fallback — always present)
- **Translation awareness:** a variation is only used if its translation is ready (status APPROVED or COMPLETE); if not ready, it is skipped and resolution falls back to the next candidate.

### 2.2 Two Types of Experience Fragments

| Type | Description | Naming / Structure |
|---|---|---|
| **Form Fragments** | Localized form fieldsets (e.g. marketing opt-in, address forms). Include an additional **division** dimension (e.g. CMD, GCMS, Eloqua) mapping to different backend endpoints. | ISO-style variation naming (`en-us`, `de-de`, `en-europe`, `master`); division encoded in folder structure. |
| **Site / Marketing Fragments** | Reusable marketing/content fragments referenced across pages. | Organized by region folders; covered separately in Section 8. |

---

## 3. EDS / Crosswalk — How Fragments Work

- Edge Delivery has **no Experience Fragment concept**. XFs are migrated to **EDS fragments** (content authored in AEM, delivered via EDS).
- A fragment is included on a page by fetching its rendered body via the **`.plain.html`** endpoint and injecting it into the page.
- **Locale is in the URL.** In EDS, each locale is a distinct page/URL (e.g. `/us/en/...` vs `/de/de/...`). The locale is parsed directly from the URL path — replacing the MSM hierarchy traversal used in AEM. The business outcome (correct locale identified) is identical.

**Assumption:** A single EDS site serves all locales via path mappings, so all locales and the fragment library share **one content bus**. Fragments therefore resolve **same-origin** — no cross-site fetch, no CORS, and no edge worker is required for resolution. (See Section 10.)

---

## 4. Proposed Approach — Centralized Fragment Library

All fragments are migrated to a **centralized fragment library** rather than distributed into the region/country page hierarchy.

### 4.1 Why Centralized

- **Reusability:** fragments are reusable assets referenced by many pages; they are not page content belonging to any single country branch.
- **Single resolution boundary:** with all fragments and pages in one site / one content bus, best-match resolution and master fallback occur within one scope — fetches are same-origin, avoiding the cross-site/content-bus resolution problems that arise when content is split.
- **Lookup feasibility:** centralization makes a single fragment **index** possible (used by Option 1, Section 6), enabling existence checks without trial-and-error fetching.
- **Faithful to source:** XFs are already organized today as centralized fragment libraries, separate from the site content tree. Centralizing preserves that model.
- **Avoids inheritance overhead:** representing variations through an MSM live-copy hierarchy would require building and maintaining inheritance relationships that mirror the resolution order, which is error-prone and pushes structural complexity onto authors. Keeping fallback logic in code, and variations as flat content, is simpler to author and maintain.

### 4.2 Library Location and Structure

Fragments live under a dedicated path, parallel to the region content trees:

```
/content/tfs/
    global/ ...            (page content)
    north-america/ ...     (page content)
    latin-america/ ...     (page content)
    europe/ ...            (page content)
    fragments/             ← centralized fragment library
        form-fragments/    (Form Fragments)
        site-fragments/    (Site / Marketing Fragments)
```

Form and Site/Marketing fragments are kept as **separate folders**, as they follow different conventions.

---

## 5. Best-Match Resolution (Applies to All Fragments)

A single block — the **TFS Fragment block** — implements best-match resolution. The author places it on a page and enters the fragment **base path**. At runtime, the block derives the visitor's locale from the page URL, builds an ordered candidate list, and resolves the correct variation.

### 5.1 Candidate Chain

For a page at `/{country}/{lang}/...`, the candidate list is built most-specific → least-specific → master:

1. `{lang}-{country}` — e.g. `de-de`, `en-us` (language + country)
2. if `lang ≠ en`: `en-{country}` — the English version for that country (e.g. `en-cn` for a `zh/cn` page)
3. `{lang|en}-{region}` — region fallback via the locale→region map (e.g. `en-europe`)
4. `master` — final fallback (always present)

The block resolves the **first existing** candidate.

**Worked examples:**

```
German page — URL /de/de/...  → country=de, lang=de
Candidates: de-de → en-de → en-europe → master
Result: de-de absent, en-de absent → en-europe matches → render European English variation

Korean page — URL /kr/ko/...  → country=kr, lang=ko
Candidates: ko-kr → en-kr → en-ipac → master
Result: ko-kr matches → render Korean variation
```

### 5.2 Locale → Region Map

The region candidate requires a **locale → region mapping** (e.g. `us → north-america`, `de → europe`, `kr → ipac`). This is a configuration the block reads to build the region candidate, derived from the existing locale-to-region relationships.

### 5.3 Division Dimension (Form Fragments)

Form fragments carry an additional **division** dimension (e.g. CMD, GCMS, Eloqua), which maps to different backend form endpoints. The division is **encoded in the base path** the author enters, rather than a separate field:

- Standard form fragment: `.../form-fragments/standard/marketing-opt-in`
- Division-specific fragment: `.../form-fragments/custom/{division}/marketing-opt-in`

Best-match resolution then operates on variations under the chosen base path. The author selects the correct base path (standard vs the relevant division).

### 5.4 Readiness — Translation Status → Variation Existence

In AEM, a variation was used only if its translation status was APPROVED or COMPLETE; otherwise it was skipped. In EDS, **publishing a variation is the equivalent of marking it ready**:

- A published, existing variation resolves and is used.
- An unpublished or non-existent variation is treated as **not ready** and skipped, falling back to the next candidate.

Same business outcome: unready content is never shown; the fallback variation is used instead.

### 5.5 No Match / All-Miss Behavior

`master` is always the final candidate and is expected to always exist, so resolution normally cannot fully fail. If no candidate (including master) resolves, the block renders nothing / a placeholder rather than erroring.

### 5.6 Authoring Experience

- The author enters the fragment **base path** in the block (no dropdown).
- Author guidance can be provided via a **path browser in Universal Editor scoped to the fragments root**, so authors select valid base paths easily.

---

## 6. Migration Options for Variations

There are two ways to structure variations when migrating XFs. **Best-match resolution works identically in both** — the difference is how content is authored and how variations are maintained.

### Option 1 — One Fragment per Variation (with Index Lookup)

Each variation is its own fragment under the base path:
```
/content/tfs/fragments/form-fragments/standard/marketing-opt-in/
    master
    en-us
    en-europe
    de-de
    ...
```
- The block builds the candidate chain and uses a **fragment index** (Section 7) to determine which variation exists, then fetches the matched variation.
- **Pros:** clean separation per variation; only existing variations are stored (sparse); fetches only the resolved variation.
- **Cost:** a fragment **index must be created and maintained/published** for lookup.

### Option 2 — One Fragment with Variations as Sections

A single fragment holds all variations as **sections**, each tagged via **section metadata** indicating the country/language it applies to:
```
/content/tfs/fragments/form-fragments/standard/marketing-opt-in
    ├─ section (master)
    ├─ section (en-us)
    ├─ section (en-europe)
    └─ section (de-de)
```
- The block fetches the single fragment and **selects the section** matching the resolved locale (in-memory).
- **Pros:** no index needed; one known path; selection happens after a single fetch.
- **Cost:** all variations are downloaded to display one; a fragment with many variations grows large.

### Comparison

| Aspect | Option 1 — Fragment per variation | Option 2 — Sections in one fragment |
|---|---|---|
| Best-match preserved | Yes | Yes |
| Index required | **Yes** | No |
| Fetch | Index lookup + one variation fetch | One fragment fetch (all variations) |
| Variation storage | Sparse (only existing) | All variations in one fragment |
| Authoring / maintenance | Maintain individual variation fragments | Maintain variants as sections in one fragment |

### Recommendation

Both options preserve best-match. Based on TFS's existing structure — where variations are maintained as discrete, separately-managed items — **Option 1 aligns well**. However, this is fundamentally an **authoring/maintenance preference**, and authors can decide which model they prefer to work with. If Option 1 is selected, the fragment index (Section 7) is required.

---

## 7. Fragment Index Lookup (for Option 1)

To resolve variations without trial-and-error fetching, the centralized fragment library publishes an **index** listing every fragment and the variations that exist for it.

- The block fetches the index **once** (cached for the page), resolves the best-match candidate against it in memory, and then fetches the single matched variation.
- This avoids fetching variations that do not exist.
- The index is regenerated when fragments are published; new variations become resolvable once the index reflects them.

> **Open items:** index scope/path, and confirming the index refresh occurs on fragment publish (Section 10).

---

## 8. Site / Marketing Fragments — Specifics

Best-match resolution (Section 5) applies to Site / Marketing fragments as it does to Form fragments. This section captures considerations specific to Site / Marketing fragments.

*(To be completed — this section will document the Site / Marketing fragment structure, current organization, naming, and migration/cleanup considerations.)*

---

## 9. What Is Preserved and What Is Simplified

### Preserved (behavior unchanged)

| Behavior | Status |
|---|---|
| Best-match variation selection | Preserved — most specific first, regional fallback, master fallback |
| Country-specific variations (`en-us`, `en-ca`, `zh-cn`, `de-de`, `ko-kr` …) | Preserved |
| Regional fallback chain (`en-north-america`, `en-europe`, `en-ipac` …) | Preserved |
| Non-English language handling (zh, de, ko, ja, es) | Preserved — native language first, then English equivalent, then region |
| Division-specific fragments | Preserved — division encoded in path |
| Master as universal final fallback | Preserved |
| Renders nothing when path is wrong | Preserved — placeholder shown |

### Simplified (mechanism changed, behavior identical)

| AEM Mechanism | EDS Equivalent |
|---|---|
| MSM hierarchy walk → ordered locale list | URL path parsing → `{lang}-{country}` + region lookup |
| Translation status gate (APPROVED / COMPLETE) | Variation existence — unpublished/404 = not ready = skip |
| Best-match resolution in server-side Java (Sling Model) | Best-match resolution in client-side JavaScript (block) |
| Author selects from constrained dropdown | Author enters fragment base path (path browser guidance in UE) |
| Division as hidden `fragmentType` field | Division encoded in path (`standard/` or `custom/{division}/`) |
| Fallback to master | `master` always last in candidate list — always present |

---

## 10. Assumptions and Open Items

**Assumptions:**
1. A **single EDS site** serves all locales via path mappings, so all locales and the fragment library share **one content bus** (fragments resolve same-origin; no CORS / edge worker needed for resolution).

**Open Items:**
1. **Variation migration option** — confirm Option 1 (fragment per variation + index) vs Option 2 (sections) with authors.
2. **Locale → region map** — derived from existing locale-to-region relationships; read by the block to build the region candidate.
3. **Fragment index** (if Option 1) — define scope/path and confirm refresh on publish.
4. **Division list** — confirm the authoritative set of divisions and their base-path encoding.
5. **Site / Marketing fragments** — complete Section 8 (structure, naming, cleanup).
