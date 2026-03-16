# Header & Footer Integration — EDS Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 0.2
**Date:** March 2026
**Status:** Draft for Review

---

## 1. Executive Summary

This document defines the approach for integrating the Thermo Fisher centralized header/footer microservice into AEM Edge Delivery Services (EDS). The header/footer is owned by a separate team, serves ~100+ country/language variants, and bundles search, sign-in, cart, analytics, and consent management. No authoring of header/footer content is required in EDS — only injection.

**Key finding:** The current AEM production architecture uses Apache SSI as the **primary delivery mechanism** (not a fallback) to achieve cache separation — page content is cached by Dispatcher while header/footer is fetched fresh by Apache on every request. The EDS edge worker approach directly mirrors this pattern.

**Recommendation:** Edge Compute injection (Approach B) is the recommended path, with a client-side fallback (Approach A) for local development and testing.

---

## 2. Current State Analysis

### 2.1 Architecture Overview — How It Actually Works

The current AEM architecture uses a **three-path conditional system**, but in production only one path handles real user traffic:

```
┌─────────────────────────────────────────────────────────────────┐
│              CURRENT AEM 6.x — THREE PATHS                      │
│                                                                  │
│  PATH 1: Author/Preview (c:import → internal gateway)           │
│    Condition: isAuthor || isPreview                              │
│    When: Content authoring & preview environments                │
│    URL: {gateway}/tf/header/{userType}/{lang}/{country}/...     │
│    Result: Pre-localized header HTML, fetched by AEM directly   │
│                                                                  │
│  PATH 2: Publish Direct (c:import → internal gateway)           │
│    Condition: x-forwarded-for EMPTY && !isPreview               │
│    When: Direct access to AEM Publish (bypassing Apache)        │
│    URL: Same as Path 1                                          │
│    Result: Pre-localized header HTML, fetched by AEM directly   │
│    NOTE: This RARELY fires in production (all traffic goes      │
│          through Apache, which adds x-forwarded-for)            │
│                                                                  │
│  PATH 3: Apache SSI — THE PRIMARY PRODUCTION PATH               │
│    Condition: c:otherwise (x-forwarded-for IS PRESENT)          │
│    When: ALL production traffic (100% of real user requests)    │
│    URL: /global-header-footer/header/nojquery                   │
│    Result: SSI directives in HTML, Apache resolves them         │
│                                                                  │
│  Despite being labeled c:otherwise in the JSP code,             │
│  Path 3 handles ALL production traffic because Apache/CDN       │
│  ALWAYS adds x-forwarded-for to the request.                    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 The Role of x-forwarded-for

The `x-forwarded-for` HTTP header is NOT used for locale detection — it tells AEM whether Apache is in front of it:

```
x-forwarded-for PRESENT:
  → Request came through Apache/CDN proxy
  → Apache CAN process SSI directives
  → AEM outputs SSI placeholders (not actual header HTML)
  → Apache resolves SSI → fetches header → injects into page

x-forwarded-for ABSENT:
  → Request is direct to AEM (no Apache in front)
  → Nobody to process SSI directives
  → AEM must use c:import to fetch header itself
  → Because raw SSI tags would be sent to browser (broken)
```

### 2.3 Why SSI Is the Primary Production Path (Cache Separation)

The SSI design achieves a critical architectural goal — **independent caching of page content and header/footer**:

```
┌─────────────────────────────────────────────────────────────────┐
│          WHY SSI FOR PRODUCTION: CACHE SEPARATION               │
│                                                                  │
│  Step 1: AEM Publish renders page with SSI directives           │
│  ┌──────────────────────────────────────────┐                   │
│  │ <div id="globalHeaderInclusion">         │                   │
│  │   <!--#include virtual="$includeUrl" --> │ ← SSI directive   │
│  │ </div>                                   │   NOT actual HTML │
│  │ <main>...page content...</main>          │                   │
│  │ <!--#include footer SSI -->              │                   │
│  └──────────────────────────────────────────┘                   │
│                                                                  │
│  Step 2: Dispatcher CACHES this HTML (with SSI directives)      │
│   → Page cache contains placeholder, NOT header HTML            │
│   → Cache TTL: hours/days (page content rarely changes)         │
│                                                                  │
│  Step 3: On each request, even from Dispatcher cache:           │
│   → Apache processes SSI directives FRESH                       │
│   → Fetches header from: /global-header-footer/header/nojquery  │
│   → Injects FRESH header HTML into cached page                  │
│   → Sends complete HTML to browser                              │
│                                                                  │
│  RESULT:                                                         │
│   ✅ Page content: cached for days (fast, no AEM hit)           │
│   ✅ Header/footer: fresh on every request (or short-cached)    │
│   ✅ Header team deploys update → immediately reflected         │
│   ✅ No page cache invalidation needed for header changes       │
│   ✅ Separation of concerns: page cache ≠ header cache          │
│                                                                  │
│  IF c:import WERE USED INSTEAD:                                  │
│   ❌ AEM would render actual header HTML into the page          │
│   ❌ Dispatcher would cache page WITH header baked in           │
│   ❌ Header update → must flush ALL page caches                 │
│   ❌ Stale header until cache expires or is purged              │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 Complete Production Request Flow

```
User Browser
    │
    ▼
CDN / WAF (adds x-forwarded-for header)
    │
    ▼
Apache Reverse Proxy
    │
    ├─ Check Dispatcher cache for page
    │   ├─ MISS → forward to AEM Publish → AEM renders page
    │   │         with SSI directives → Dispatcher caches it
    │   └─ HIT → return cached HTML (contains SSI directives)
    │
    ▼
Apache SSI Processing (on EVERY request, cached or not)
    │
    ├─ Sees: <!--#include virtual="/global-header-footer/header/nojquery" -->
    ├─ Makes sub-request: GET /global-header-footer/header/nojquery
    ├─ Receives: ~500KB header HTML (with inline CSS, JS, locale logic)
    ├─ Replaces SSI directive with actual header HTML
    │
    ▼
Complete HTML (page content + fresh header + fresh footer)
    │
    ▼
Browser renders full page
    │
    ▼
Header's inline JS executes:
  → Reads window.location.pathname → /us/en/...
  → Adapts navigation links, language selector, country flag
  → Initializes search, cart, auth widgets
  → Loads analytics (Adobe Launch), consent (TrustArc)
```

### 2.5 Microservice Endpoints

| Endpoint | URL Pattern | Used By | Traffic |
|----------|------------|---------|---------|
| **Public** | `https://www.thermofisher.com/global-header-footer/header/nojquery` | Apache SSI (production) | ~100% of production |
| **Public** | `https://www.thermofisher.com/global-header-footer/footer/nojquery` | Apache SSI (production) | ~100% of production |
| **Internal** | `{gateway}/tf/header/{userType}/{lang}/{country}/noTrustArc.nojquery.shtml` | Author/Preview only | Author environments |

### 2.6 What the Header Microservice Returns

The public endpoint returns a **single generic HTML blob** (~500KB) for ALL locales:

| Component | Size | Description |
|-----------|------|-------------|
| **Inline CSS** | ~200KB | All header styles, minified |
| **HTML** | ~50KB | Navigation, logo, search, cart, auth widgets |
| **Inline JS** | ~150KB | Locale detection, link adaptation, widget logic |
| **Country data** | ~100KB | `ltCountryInfo` JSON (200+ country mappings) |
| **Adobe Launch** | External | `assets.adobedtm.com` analytics tags |
| **TrustArc** | ~50KB | Cookie consent management |

**Critical detail:** ALL localization logic is embedded in the inline JS. The header JS reads `window.location.pathname`, extracts country/language, and adapts all links, text, and widgets client-side. This is why a single generic response works for all locales.

### 2.7 How Locale Works in the Header JS

```
Header's inline JS (embedded in the ~500KB response):

  1. Reads: window.location.pathname
     Example: "/in/en/home/products.html"

  2. Extracts: country = "in", lang = "en"

  3. Applies locale mappings (handles edge cases):
     "uk" → "gb", "ic" → "es", "br"+"pt" → "pt_br"
     "zh" → "zh_hans", "zt" → "zh_cn"

  4. Updates all navigation links: /{country}/{lang}/...
  5. Updates language selector dropdown
  6. Updates country flag display
  7. Updates commerce/cart URLs for locale
  8. Applies locale-specific text from ltCountryInfo JSON
```

**No external system needs to tell the header which locale to use.** The header JS detects it from the page URL.

---

## 3. EDS Integration — Mirroring the SSI Pattern

In EDS there is no AEM Publish, no Apache, no Dispatcher, and no SSI processing. Pages are served as static HTML from the EDS CDN. We need to replicate the same **cache separation pattern** that Apache SSI provides.

### 3.1 How EDS Maps to Current Architecture

```
CURRENT AEM                         EDS EQUIVALENT
───────────                         ──────────────
Dispatcher page cache          →    CDN page cache
Apache SSI processing          →    Edge Worker stitching
Apache sub-request for header  →    Edge Worker fetch for header
Cache separation (page ≠ hdr)  →    Same: page cached at CDN,
                                     header cached separately at edge

The edge worker IS the EDS equivalent of Apache SSI.
```

### 3.2 Approach A: Client-Side Fetch (Local Dev / Fallback)

```
┌─────────────────────────────────────────────────────────────────┐
│              APPROACH A: CLIENT-SIDE FETCH                       │
│                                                                  │
│  Browser requests page from EDS CDN                             │
│       ↓                                                          │
│  EDS returns HTML with empty <header> and <footer>              │
│       ↓                                                          │
│  Browser starts rendering page content (LCP)                    │
│       ↓                                                          │
│  header.js / footer.js execute:                                 │
│   1. fetch() header HTML from public endpoint                   │
│   2. Inject into <header> / <footer> element                    │
│   3. Re-activate inline <script> tags                           │
│   4. Header's own JS handles locale adaptation automatically    │
│       ↓                                                          │
│  Header/Footer visible to user                                  │
│  (CLS occurs as content shifts down)                            │
└─────────────────────────────────────────────────────────────────┘
```

**EDS header.js is a tiny loader (~20 lines) — it only fetches and injects. All locale logic, search, cart, auth, analytics, and consent run from the header's own inline JS.**

```js
// blocks/header/header.js — complete implementation
const HEADER_ENDPOINT = 'https://www.thermofisher.com/global-header-footer/header/nojquery';

function activateScripts(container) {
  container.querySelectorAll('script').forEach((oldScript) => {
    const newScript = document.createElement('script');
    [...oldScript.attributes].forEach((attr) => newScript.setAttribute(attr.name, attr.value));
    newScript.textContent = oldScript.textContent;
    oldScript.parentNode.replaceChild(newScript, oldScript);
  });
}

export default async function decorate(block) {
  // If edge worker already injected content, just activate scripts
  if (block.querySelector('.main-header-container')) {
    activateScripts(block);
    return;
  }
  // Client-side fallback: fetch and inject
  block.innerHTML = '<div class="header-placeholder"></div>';
  try {
    const resp = await fetch(HEADER_ENDPOINT, { credentials: 'include' });
    if (!resp.ok) throw new Error(`Header fetch failed: ${resp.status}`);
    block.innerHTML = await resp.text();
    activateScripts(block);
  } catch (err) {
    console.error('Header load error:', err);
    // Fallback: minimal navigation from nav.html if available
  }
}
```

**Pros:**
- Simple to implement — just JS in the block
- No edge infrastructure required
- Works immediately for local development
- Easy to debug (browser DevTools)

**Cons:**
- **CLS** — page renders, then header pushes content down
- **LCP impact** — +200-500ms before header is visible
- **CORS** — cross-origin fetch needs CORS headers on microservice
- **SEO** — search engines may not see header content
- **Flash of unstyled content** — page loads without header, then it appears

### 3.3 Approach B: Edge Worker Injection (Recommended for Production)

```
┌─────────────────────────────────────────────────────────────────┐
│     APPROACH B: EDGE WORKER — mirrors Apache SSI pattern        │
│                                                                  │
│  Browser requests page from EDS CDN                             │
│       ↓                                                          │
│  Edge Worker intercepts response:                               │
│   1. Fetch EDS page HTML (from CDN cache or origin)             │
│   2. Fetch header HTML from public endpoint (from edge cache)   │
│   3. Fetch footer HTML from public endpoint (from edge cache)   │
│   4. Stitch: inject header + footer into page HTML              │
│   5. Return complete HTML to browser                            │
│       ↓                                                          │
│  Browser receives FULL page with header + content + footer      │
│  Header's inline JS runs naturally (no activateScripts needed)  │
│  Header JS adapts locale from URL — zero EDS involvement        │
│  No CLS, no extra fetch, SEO-friendly                           │
└─────────────────────────────────────────────────────────────────┘
```

**How it mirrors the current SSI production flow:**

```
CURRENT (Apache SSI)                 EDS (Edge Worker)
────────────────────                 ──────────────────
Dispatcher cache: page               CDN cache: page
  (with SSI placeholders)              (with empty <header>/<footer>)
        ↓                                    ↓
Apache processes SSI on request      Edge Worker stitches on request
        ↓                                    ↓
Apache fetches header from           Edge Worker fetches header from
  public endpoint                      same public endpoint
        ↓                                    ↓
Apache injects header into page      Edge Worker injects into page
        ↓                                    ↓
Complete HTML sent to browser        Complete HTML sent to browser
        ↓                                    ↓
Header JS adapts locale              Header JS adapts locale
  (same JS, same behavior)            (same JS, same behavior)

SAME cache separation benefit:
  Page cache (long TTL) ≠ Header cache (short TTL)
  Header team deploys → only header cache purged
  No page cache invalidation needed
```

**Edge Worker pseudocode:**

```js
// edge-worker.js — conceptual implementation
async function handleRequest(request) {
  const headerUrl = 'https://www.thermofisher.com/global-header-footer/header/nojquery';
  const footerUrl = 'https://www.thermofisher.com/global-header-footer/footer/nojquery';

  // Parallel fetch: page + header + footer
  const [pageResp, headerResp, footerResp] = await Promise.all([
    fetch(request),            // original EDS page (CDN cached)
    fetchCached(headerUrl),    // header (edge cached, TTL: 1 hour)
    fetchCached(footerUrl),    // footer (edge cached, TTL: 1 hour)
  ]);

  // Stitch the response
  const pageHtml = await pageResp.text();
  const headerHtml = await headerResp.text();
  const footerHtml = await footerResp.text();

  const stitched = pageHtml
    .replace('<header></header>', `<header>${headerHtml}</header>`)
    .replace('<footer></footer>', `<footer>${footerHtml}</footer>`);

  return new Response(stitched, {
    headers: { 'Content-Type': 'text/html' },
  });
}
```

**The edge worker does NOT need to:**
- Parse the URL for country/language
- Read cookies for userType
- Construct parameterized URLs
- Handle locale logic of any kind

**It only needs to:**
1. Fetch the generic header/footer from the public endpoint
2. Cache it at the edge
3. Stitch it into the EDS page HTML

**All locale adaptation, search, cart, auth, analytics, and consent are handled by the header's own inline JavaScript — automatically, after the page loads in the browser.**

**Pros:**
- **Zero CLS** — header is in the HTML before browser parses it
- **SEO-friendly** — search engines see complete page
- **No CORS issues** — edge worker fetches server-side
- **Mirrors current SSI pattern** — same cache separation architecture
- **Header's inline JS runs naturally** — no `activateScripts` workaround needed
- **Simple edge worker** — no locale logic, just fetch + stitch

**Cons:**
- Requires edge compute infrastructure setup
- Edge worker latency (~5-20ms) added to TTFB
- Cache invalidation needs coordination with header team
- Header's ~500KB still impacts page weight

---

## 4. Approach Comparison

| Criteria | Approach A (Client-Side) | Approach B (Edge Worker) |
|----------|-------------------------|-------------------------|
| **CLS Score** | Poor (header appears after load) | Excellent (zero shift) |
| **LCP Impact** | +200-500ms (fetch + parse) | +5-20ms (edge stitching) |
| **Lighthouse Performance** | 60-75 | 85-95 |
| **SEO** | Poor (header not in initial HTML) | Excellent (full HTML) |
| **CORS Dependency** | Yes (microservice must allow EDS origin) | No (server-side fetch) |
| **Implementation Effort** | Low (JS only) | Medium (edge infra + worker) |
| **Mirrors Current AEM** | No | Yes (same as Apache SSI production path) |
| **Caching** | Browser only | Edge + browser (layered) |
| **Locale Handling** | Header's inline JS (automatic) | Header's inline JS (automatic) |
| **EDS Code Required** | ~20 line loader | ~20 line loader + edge worker config |

### Recommendation

**Approach B (Edge Worker)** is recommended for production because:

1. It directly mirrors the current Apache SSI production flow
2. Achieves the same cache separation (page cache ≠ header cache)
3. Zero CLS protects Core Web Vitals / Lighthouse scores
4. SEO indexing includes the navigation structure
5. No CORS dependency on the microservice

**Approach A (Client-Side)** is recommended for:
- Local development (`aem up` / localhost)
- Fallback if edge worker encounters issues
- Non-production environments

---

## 5. Locale, UserType, and Edge Worker Responsibilities

### 5.1 What the Edge Worker Does and Does NOT Do

```
EDGE WORKER DOES:                    EDGE WORKER DOES NOT:
─────────────────                    ─────────────────────
✅ Fetch generic header HTML         ❌ Parse URL for country/language
✅ Fetch generic footer HTML         ❌ Read cookies for userType
✅ Cache header/footer at edge       ❌ Construct parameterized URLs
✅ Stitch into EDS page HTML         ❌ Handle locale logic
✅ Return complete HTML              ❌ Handle auth/cart/search
                                     ❌ Manage analytics/consent
```

### 5.2 What the Header's Inline JS Handles (Automatically)

Everything below runs from the header's own JavaScript, embedded in the ~500KB response. EDS writes zero code for any of this:

```
HEADER'S INLINE JS (runs in browser after page load):
  ✅ Locale detection (reads window.location.pathname)
  ✅ Navigation link adaptation (/{country}/{lang}/...)
  ✅ Language selector dropdown
  ✅ Country flag display
  ✅ Commerce/cart URLs for locale
  ✅ Locale-specific text (from embedded ltCountryInfo JSON)
  ✅ Search widget initialization
  ✅ Sign-in / authentication widget
  ✅ Cart widget
  ✅ Adobe Launch analytics
  ✅ TrustArc cookie consent
  ✅ Offer bar / personalization
  ✅ Special locale mappings (uk→gb, br+pt→pt_br, zh→zh_hans)
```

### 5.3 Why No Per-Locale Fetching Is Needed

The public endpoint returns ONE response for ALL locales:

```
/us/en/products → Edge worker fetches generic header → header JS adapts to US/English
/in/en/products → Edge worker fetches SAME header    → header JS adapts to India/English
/de/de/products → Edge worker fetches SAME header    → header JS adapts to Germany/German
/jp/ja/products → Edge worker fetches SAME header    → header JS adapts to Japan/Japanese

Cache: ONE entry serves ALL 200+ country/language combinations
```

**Note:** The internal gateway endpoint (`{gateway}/tf/header/{userType}/{lang}/{country}/...`) returns pre-localized HTML, but it is only accessible on Thermo Fisher's private network. The edge worker cannot reach it. If the header team exposes a public parameterized endpoint in the future, the edge worker could optionally use it for pre-localized responses (see Section 9.2).

### 5.4 UserType Handling

| UserType | How It Works Today | How It Works in EDS |
|----------|-------------------|-------------------|
| `anonymous` | Internal gateway returns anonymous header | Header JS detects no userType cookie, shows sign-in CTA |
| `authenticated` | Internal gateway returns auth header | Header JS reads cookie, shows account menu |
| `b2b_user` | Internal gateway returns B2B header | Header JS reads cookie, shows B2B pricing |

In EDS, all userType personalization is handled client-side by the header's own JS. The edge worker fetches the same generic response regardless of user type.

---

## 6. Caching Strategy

### 6.1 Cache Separation (Mirroring Dispatcher + Apache SSI)

```
┌─────────────────────────────────────────────────────────────────┐
│               CACHE SEPARATION IN EDS                            │
│                                                                  │
│  Layer 1: EDS CDN (page content)                                │
│   ├─ Caches: EDS page HTML (with empty <header>/<footer>)       │
│   ├─ TTL: hours/days (page content rarely changes)              │
│   └─ Purge: On content publish from AEM                         │
│                                                                  │
│  Layer 2: Edge Worker Cache (header/footer)                     │
│   ├─ Key: "header-v1" (single global key — same for all pages) │
│   ├─ TTL: 1 hour                                                │
│   ├─ Stale-while-revalidate: 4 hours                            │
│   └─ Purge: Webhook from header team on deployment              │
│                                                                  │
│  Layer 3: Browser Cache                                         │
│   ├─ Cache-Control on stitched response: max-age=300 (5 min)   │
│   └─ ETag for conditional requests                              │
│                                                                  │
│  Benefits (same as current AEM):                                │
│   ✅ Header team deploys → only header cache purged             │
│   ✅ Page content cached independently → no invalidation needed │
│   ✅ Each layer can be tuned separately                         │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 Cache Performance

```
Cache miss (cold start):
  Edge Worker fetches header from thermofisher.com → ~200ms
  Edge Worker stitches + caches → ~5ms
  Total added latency: ~205ms (first request after cache expires)

Cache hit (warm — vast majority of requests):
  Edge Worker reads header from edge cache → ~1ms
  Edge Worker stitches → ~5ms
  Total added latency: ~6ms
```

---

## 7. Performance Optimization Strategy

### 7.1 The Problem: ~500KB Header Payload

| Resource | Size | Lighthouse Impact |
|----------|------|-------------------|
| Inline CSS | ~200KB | Blocks FCP (+200ms) |
| Inline JS | ~150KB | Blocks TBT (+300ms) |
| Adobe Launch | ~80KB (external) | Async but adds TBT |
| TrustArc | ~50KB | Consent banner overhead |
| Country JSON | ~200KB | Memory + parse time |
| **Total** | **~680KB** | **FCP +200ms, TBT +500ms** |

### 7.2 Optimization: CSS/JS Separation (Phase 3)

The header team is already planning to separate analytics/TrustArc JS. The edge worker can further optimize by splitting the response:

```
Edge Worker receives header HTML (~500KB)
  ↓
Splits into:
  ├─ Critical CSS (~30KB) → inline in <head>
  ├─ Non-critical CSS (~170KB) → async <link rel="preload">
  ├─ Critical HTML → injected in <header>
  ├─ Deferred JS → injected with defer/async at </body>
  └─ Country data JSON → lazy-loaded on interaction

Expected improvement:
  Critical path: ~50KB (vs ~500KB)
  Lighthouse Performance: 85-95 (vs 60-75)
```

---

## 8. Implementation Plan

### Phase 1: Client-Side Foundation (Week 1-2)

Build the client-side approach for local development and testing:

```
/blocks/header/
  ├── header.js    ← ~20 line loader (fetch + inject + activateScripts)
  └── header.css   ← Placeholder sizing to minimize CLS

/blocks/footer/
  ├── footer.js    ← ~20 line loader (fetch + inject + activateScripts)
  └── footer.css   ← Placeholder sizing to minimize CLS
```

**Deliverables:**
- Working header/footer in local EDS preview
- Graceful fallback if microservice unreachable
- Automatic detection: if edge worker already injected, skip client fetch

### Phase 2: Edge Worker Integration (Week 3-4)

Deploy edge worker for server-side injection:

1. **Create edge worker** (Cloudflare Workers or Fastly Compute)
2. **Header/footer fetch** — single global URL, no locale params
3. **Edge caching** — 1hr TTL, stale-while-revalidate
4. **HTML stitching** — inject into `<header>` / `<footer>` tags
5. **Cache purge webhook** — for header team deployments
6. **Fallback** — if edge fetch fails, serve page without header (client-side JS picks up)

### Phase 3: Performance Optimization (Week 5-6)

1. **CSS splitting** — critical vs deferred
2. **JS deferral** — analytics and consent loaded async
3. **Country data lazy-load** — `ltCountryInfo` on demand
4. **Font optimization** — subset HelveticaNeue to used weights

### Phase 4: Integration Testing (Week 7-8)

1. Test all country/language variants (header JS adapts correctly)
2. Validate search, sign-in, cart functionality
3. Verify analytics tagging (Adobe Launch)
4. Confirm consent management (TrustArc)
5. Load testing with cached/uncached scenarios
6. Lighthouse benchmarking

---

## 9. Addressing Key Concerns

### 9.1 "Does the Edge Worker Need to Know the Locale?"

**No.** The edge worker fetches a single generic header. The header's own inline JS handles all locale adaptation by reading `window.location.pathname`. The edge worker has no locale awareness.

### 9.2 "What If the Header Team Exposes a Public Parameterized Endpoint?"

If the header team exposes a public URL like:
```
https://www.thermofisher.com/global-header-footer/header/nojquery/{lang}/{country}
```

Then the edge worker could optionally use it:
- Parse URL path → extract country/language
- Fetch pre-localized header (no client-side adaptation needed)
- Cache per locale (~200 entries vs 1)

**Trade-off:** Better UX (no locale flicker) but more complex caching. Only needed if the client-side locale adaptation causes visible issues.

### 9.3 "Who Manages What?"

```
HEADER TEAM:                         EDS TEAM:
────────────                         ─────────
✅ Header/footer microservice        ✅ Edge worker deployment
✅ Navigation content                ✅ header.js / footer.js (loader)
✅ Search, cart, auth widgets        ✅ header.css / footer.css (placeholder)
✅ Analytics (Adobe Launch)          ✅ Edge cache TTL configuration
✅ Consent (TrustArc)               ✅ Cache purge webhook setup
✅ Locale JS logic
✅ Inline CSS
✅ Inline JS
✅ Offers/personalization

No coordination needed for locale changes —
header team updates their microservice, EDS automatically picks it up.
```

### 9.4 Cross-Site Usage (Fischer, etc.)

Same edge worker, different endpoint config per brand:

```
Site Config:
  thermofisher.com → /global-header-footer/header/nojquery
  fishersci.com    → /global-header-footer/header/fisher/nojquery (example)
```

---

## 10. Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Microservice downtime → no header | Low | High | Client-side fallback + edge stale cache (4hr) |
| CORS blocking client-side fetch | Medium | High | Edge worker eliminates CORS (server-side) |
| Header JS conflicts with EDS JS | Medium | Medium | Namespace isolation, load order control |
| ~500KB impacts Lighthouse | High | High | Phase 3 CSS/JS splitting |
| Cache stale after header deploy | Medium | Medium | Webhook-based purge from header team |
| Edge worker adds latency | Low | Low | <6ms with warm cache |
| Locale flicker (client-side adaptation) | Low | Low | Header JS runs fast; optionally use parameterized endpoint |

---

## 11. Summary of Recommendations

| # | Decision | Recommendation |
|---|----------|---------------|
| 1 | **Primary approach** | Edge Worker injection (mirrors current Apache SSI production path) |
| 2 | **Fallback approach** | Client-side fetch (for local dev + edge failure fallback) |
| 3 | **Endpoint** | Public: `/global-header-footer/header/nojquery` (same as current production SSI) |
| 4 | **Locale handling** | Header's inline JS handles it automatically — EDS does nothing |
| 5 | **UserType handling** | Header's inline JS handles it automatically — EDS does nothing |
| 6 | **Cache strategy** | Single global cache key, 1hr TTL, webhook purge (mirrors Dispatcher + SSI separation) |
| 7 | **Authoring** | None — purely code-driven injection |
| 8 | **Analytics/consent** | Runs from injected header's inline JS — no EDS setup needed |
| 9 | **Performance** | Phase 3 CSS/JS splitting to protect Lighthouse |
| 10 | **Cross-site** | Same worker, different endpoint config per brand |

---

## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| **SSI** | Server-Side Include — Apache directive that fetches and injects content at request time |
| **Edge Worker** | Serverless function running at CDN edge (Cloudflare Workers, Fastly Compute) |
| **Dispatcher** | AEM caching layer in front of Publish; caches page HTML with SSI directives |
| **x-forwarded-for** | HTTP header added by proxy/CDN; tells AEM that Apache is in front (use SSI path) |
| **CLS** | Cumulative Layout Shift — measures visual stability (Core Web Vital) |
| **LCP** | Largest Contentful Paint — measures loading performance (Core Web Vital) |
| **TBT** | Total Blocking Time — measures interactivity delay |
| **TTFB** | Time to First Byte — server response time |
| **Commerce Gateway** | Internal Thermo Fisher service that serves parameterized header/footer (author/preview only) |
| **ltCountryInfo** | JSON object embedded in header JS with 200+ country/language mappings for locale adaptation |

## Appendix B: Related Documents

- `HEADER_FOOTER_FLOW_DOCUMENTATION.md` — Current AEM 6.x implementation details
- `HEADER_FOOTER_ARCHITECTURE_DIAGRAMS.md` — Visual architecture diagrams
- Edge worker reference: Cloudflare Workers / Fastly Compute@Edge documentation
