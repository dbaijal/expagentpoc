# XF → EDS (Crosswalk) Fragment Migration — Option 2 / Solution A (Cross-Host Fetch)

## 1. Overview

This document describes **Option 2, Solution A** for migrating Thermo Fisher's Experience Fragments (XF) and their variations to AEM Edge Delivery Services (EDS / Crosswalk).

In on-prem AEM, each XF is a **single fragment containing many variations** (Global: Master + country / region / language, e.g. `en-ca`, `en-europe`, `de-de`, `zh-cn`). On a page the author references the XF by its **root path**, and the XF component performs **server-side best-match resolution** at render time:

```
page locale (e.g. en-us)
   → exact locale variation (en-us)?         use it
   → else region variation (en-north-america)? use it
   → else Global: Master                       fallback
```

**Solution A** preserves this behavior in EDS by keeping the **master in a `global` site** and each **regional / country variation in its own site hierarchy**, then using a **custom block that fetches fragments cross-host via absolute EDS URLs** with a country→region→host lookup map.

---

## 2. Content Layout (Option 2)

| Variation | Location (EDS site) |
|---|---|
| Global: Master | `global` site (e.g. `/content/.../global/.../<fragment>`) |
| Regional (e.g. EMEA, North America) | the regional site's hierarchy |
| Country / locale (e.g. `en-ca`, `en-us`, `de-de`) | that country/locale site's own hierarchy |

- Each public **country/locale** is a **separate EDS site** (own `contentBusId` + `public.paths` scope), all fronted on one public domain (`www.thermofisher.com`).
- **`global` and the regional nodes are separate EDS sites too** — they have their **own EDS delivery URLs** (`main--global--<owner>.aem.{page|live}`) but are **NOT mapped to a public CDN domain** (they are internal/fallback content).

---

## 3. Why a Naive Migration Returns 404

A host serves a path only if **BOTH** gates pass, and **both are per-site**:

1. **Scope gate** — the path is within that site's `public.paths.includes`.
2. **Content-bus gate** — a published copy exists in that site's `contentBusId`.

A country page (e.g. `en-ca` host) referencing the master under `global` fails **both**:

```
GET https://main--tfs-ca-en--owner.aem.page/content/.../global/.../demo.plain.html
  Gate 1: /global/... not in en-ca site's includes      → 404
  Gate 2: not published in en-ca site's content bus      → 404
```

Adding `global` to the country site's `includes` does **not** fix it (Gate 2 still fails — the fragment is in the global site's bus, not the country's). Previewing/publishing every fragment into every country bus is **not feasible** at thousands of fragments × ~40 sites.

---

## 4. Solution A — Cross-Host Fetch via Absolute EDS URL

### 4.1 Key insight

Relative vs absolute URL only changes **which host** the request goes to. The 404 happened because the country host was asked for content it does not own. **Fetching the absolute URL of the host that DOES own and publish the fragment (the `global` / regional site) returns 200** — provided that owner host has the fragment in scope, published to its bus, and CORS enabled.

> The `includes` requirement does **not disappear** — it **moves to the owner host**. The `global` site must have its fragment paths in **its own** `public.paths`, with fragments published to **its own** bus. No `includes` change is needed on the country sites.

### 4.2 Components

1. **Custom block — "TFS Form Fragment"**
   - Authored with the fragment **root path** (not a specific variation), preserving the XF authoring experience.

2. **Country → Region → Host lookup map** (config)
   ```json
   {
     "en-us": { "region": "north-america", "host": "<env-aware global/regional EDS host>" },
     "en-ca": { "region": "north-america", "host": "<env-aware global/regional EDS host>" },
     "de-de": { "region": "europe",        "host": "<env-aware global/regional EDS host>" }
   }
   ```
   The same map drives both the **fallback chain** (which region a country rolls up to) and the **absolute host** for cross-site fetches.

3. **Best-match resolution + fetch (in the block)**
   - Read page locale → build candidate chain `exact locale → region → master`.
   - **Own variation** (lives in the page's own site) → **same-origin/relative** fetch.
   - **Regional / global fallback** (different site) → **absolute** fetch to the owner host.
   - First `200` wins → inject `.plain.html` body into the page.

### 4.3 How `.plain.html` works

EDS serves every page/fragment in a "plain" form — the rendered **body HTML without page chrome** (no `<head>`, nav, footer) — at `<path>.plain.html`. The block fetches it and injects `resp.text()` into the page. This is the standard EDS content-include mechanism (already used by the stock fragment block).

### 4.4 Environment-aware host resolution

The block runs client-side and must select the owner host matching the **current environment tier**, or fragments will render in prod but be blank in Universal Editor.

| Page is on | Fetch fragment from |
|---|---|
| `localhost:3000` | local / preview library host |
| `main--<country>--<owner>.aem.page` (UE / preview) | `main--global--<owner>.aem.page` |
| `www.thermofisher.com` (production) | **global/regional production host — CONFIRM with platform team** |

Recommended implementation: **derive the library host by transforming the current hostname** (same tier `.page`/`.live`, swap the site name to `global`), with an optional `<meta name="fragment-host">` override as an escape hatch.

```js
function getFragmentBase() {
  const override = document.querySelector('meta[name="fragment-host"]')?.content;
  if (override) return override;
  const { hostname, origin } = window.location;
  if (hostname === 'www.thermofisher.com') return origin; // adjust if global has its own prod host
  const m = hostname.match(/--([^-]+)--([^.]+)\.aem\.(page|live)$/);
  if (m) { const [, , owner, tier] = m; return `https://main--global--${owner}.aem.${tier}`; }
  return origin; // localhost
}
```

---

## 5. Requirements / Prerequisites

1. **CORS (mandatory).** The `global` / regional hosts must return `Access-Control-Allow-Origin` for the country origins, in **both** `.aem.page` (UE/preview) and the production host. Without CORS the server returns 200 but the **browser blocks** the response (CORS error, not 404).
2. **Owner-host scope + publish.** `global` / regional fragments must be in **their own** `public.paths` and **published once** to **their own** bus (no per-country republish).
3. **Env-aware host base** (Section 4.4).
4. **Media URL rewriting.** Asset/`media_*` URLs inside the fragment must be rewritten to resolve against the **library origin**, not the current page — otherwise HTML loads but images 404.
5. **All-miss behavior.** Define what happens if even `master` 404s (placeholder / nothing / error log).

---

## 6. Characteristics & Trade-offs

### Pros
- **Publish once** — master/regional fragments published once to their owner site; no fan-out into ~40 country buses.
- **Preserves XF best-match + master fallback** at render time.
- **Works in UE, preview, and prod** (env-aware host + CORS), because EDS owner-host URLs are reachable in all tiers (unlike CDN routing, which exists only in prod).
- **`global`/regional stay non-CDN-public** (no public domain mapping needed).

### Cons / Caveats
- **CORS configuration** required on owner hosts (preview + prod).
- **Cross-origin fetches** — 1 to N requests per block instance (serial fallback). A **manifest/index** can collapse this to a single fetch if needed.
- **Exposure note:** although `global`/regional are not CDN-public, their **EDS URLs and fragment HTML are visible in the browser network tab** (fetch is client-side). Treat "internal" fragments as effectively public — do not store sensitive content assuming it is hidden.
- **Production host must be confirmed** with the platform/infra team (EDS `.aem.live` URL vs an internal/non-public CDN route).
- **Authoring view depends on the block** running in the UE iframe (hence CORS-on-`.aem.page` + env-aware host matter for authoring too).

---

## 7. Open Questions for Engineering / Platform

1. In **production**, what is the exact host for `global`/regional fragments — the EDS `.aem.live` URL, or a non-public internal CDN route?
2. Can **CORS** be configured on the `global`/regional EDS hosts for the country origins in **both** preview (`.aem.page`) and production?
3. Is a **manifest/index** desired to reduce per-instance fetches to one, or is serial fallback acceptable?

---

## 8. Relationship to Other Options

- **Option 1 (Centralized fragment library):** all fragments (master + variations) live together in one dedicated fragment branch served by one owner site. Solution A is effectively a step toward this — `global` becomes the de-facto library. To be documented separately for comparison.
- **Option 2 / Solution B (MSM rollup):** master physically rolls down into each country branch (synced where not overridden) so fragments always resolve **same-origin** with no CORS — at the cost of **duplication/fan-out** and **no runtime best-match** (resolution moves to authoring-time inheritance/override).
