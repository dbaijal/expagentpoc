# Product Selection Guide Block — Runtime Ownership & Integration Boundaries

---

## 1. Integration Approach — Decision Pending

The integration approach depends on the **Feature Collection JSON API's accessibility and authentication requirements**, which need to be confirmed by TFS.

### Approach A: Client-Side Fetch (Recommended if API is publicly accessible)

```
Page loads
    → Block JS reads authored featureCollectionId
    → Block JS reads URL query params (filters, paging, view)
    → Block JS determines locale (from URL path)
    → Block JS calls Feature Collection JSON API directly (fetch)
    → Block JS receives JSON response
    → Block JS renders product grid/list, filters, pagination
```

**Use when:** API is publicly accessible from the browser, no server-side credentials required.

### Approach B: Edge Worker Proxy (Required if API needs server-side auth)

```
Page request hits CDN
    → Edge Worker reads block config from page HTML
    → Edge Worker calls Feature Collection API (with server-side credentials)
    → Edge Worker injects rendered HTML into page response
    → Browser receives complete page with product guide
```

**Use when:** API requires API keys, OAuth tokens, or is on an internal network not reachable from the browser.

### Decision Criteria

| Condition | Approach |
|---|---|
| API is publicly accessible, no auth needed | **A: Client-Side Fetch** |
| API needs API key but data is not sensitive | **A: Client-Side Fetch** (public API key acceptable in JS) |
| API requires server-side credentials (OAuth, mTLS, secrets) | **B: Edge Worker Proxy** |
| API is on internal network (not reachable from browser) | **B: Edge Worker Proxy** |
| SEO/LLM crawlability required for selection guide content | **B: Edge Worker Proxy** (content must be in initial HTML) |

### Confirmation Needed from TFS

| Question | Impact on Approach |
|---|---|
| **Is the Feature Collection JSON API publicly accessible from the browser?** | If yes → Approach A. If no → Approach B. |
| **Does the API require authentication (API keys, OAuth, session)?** | If auth needed server-side → Approach B. If no auth or public key → Approach A. |
| **Is the API on the same domain or cross-origin?** | Cross-origin → CORS headers required on the API (TFS configures). |
| **Is there an SEO requirement for this content to be in the initial HTML?** | If yes → Approach B. If no → Approach A. |
| **What is the JSON API endpoint URL pattern?** | Needed to configure the block. |
| **What is the expected JSON response schema?** | Needed to build block rendering logic. |

> **Until these questions are answered, the document assumes Approach A (client-side fetch) as the baseline.** If TFS confirms the API requires server-side auth or is internal-only, the architecture will shift to Approach B (Edge Worker), following the same pattern documented for the Product List block.

---

## 2. Ownership & Integration Boundaries

### Ownership Matrix

| Component | Owner | Responsibility |
|---|---|---|
| **Product Selection Guide Block (EDS)** | Adobe / Implementation Team | Block JS/CSS, rendering (grid/list/filters/pagination/empty/error states), URL parameter handling, locale detection, UX interactions |
| **Feature Collection Service (JSON API)** | TFS | Exposes structured JSON endpoint, business logic, filtering, sorting, pagination logic, product data |
| **JSON API Contract** | Joint (Adobe + TFS) | Request/response schema agreed during implementation |
| **CORS Configuration** (if cross-origin) | TFS | Must configure CORS headers to allow browser requests |
| **API Authentication** (if needed) | TFS provides credentials; Adobe configures (in Edge Worker if Approach B) | Depends on approach decision |

### Integration Boundary

```
┌───────────────────────────────────────────────────────────┐
│  ADOBE / IMPLEMENTATION TEAM                               │
│                                                           │
│  Product Selection Guide Block (JS/CSS)                    │
│  • Reads authored config (featureCollectionId)            │
│  • Reads URL params (filters, paging, view type)          │
│  • Determines locale from URL path                        │
│  • Calls Feature Collection JSON API                      │
│  • Renders: product grid, filters, pagination             │
│  • Handles: empty states, error states, loading states    │
│  • Manages: user interactions (filter change, page nav)   │
└────────────────────────────┬──────────────────────────────┘
                             │
              JSON API CONTRACT (defined jointly)
              • Request: featureCollectionId + locale +
                         filters + page + view
              • Response: structured JSON (products,
                          filter options, pagination metadata)
                             │
┌────────────────────────────▼──────────────────────────────┐
│  TFS-OWNED                                                 │
│                                                           │
│  Feature Collection Service                                │
│  • Accepts featureCollectionId + parameters               │
│  • Applies business logic (filtering, sorting)            │
│  • Returns structured JSON                                │
│  • Manages product data and catalog                       │
│  • Owns API availability and performance                  │
│                                                           │
│  ** Must expose JSON endpoint (not HTML passthrough) **     │
│  This is a TFS deliverable — required for EDS integration  │
└───────────────────────────────────────────────────────────┘
```

---

## 3. Fallback Behavior

| Failure Scenario | Block Behavior | User Impact |
|---|---|---|
| **API returns error (5xx)** | Block renders error state: "Product selection guide is temporarily unavailable. Please try again later." | Clear error message — page is not broken |
| **API timeout (>3 seconds)** | Block shows loading skeleton initially, then error state after timeout | Loading → error. No indefinite spinner. |
| **API returns empty data** | Block renders empty state: "No products match your current filters." with option to reset filters | Informative message with clear action |
| **API returns malformed JSON** | Block catches parse error, renders error state | Graceful degradation |
| **Network failure** | Block detects fetch failure, renders error state | "Unable to load. Check your connection." |
| **CORS blocked** (if cross-origin and not configured) | Fetch fails, block renders error state | Same as network failure from user perspective |

---

## 4. Cache Strategy

Caching for this block is **controlled by the TFS API's HTTP response headers** — not by the EDS block.

| Layer | What's Cached | Controlled By | Notes |
|---|---|---|---|
| **Browser cache** | JSON API responses | TFS (via `Cache-Control` headers on response) | Browser respects standard HTTP caching |
| **CDN** (if API is behind CDN) | JSON API responses | TFS | TFS responsibility |
| **Block JS** | No application-level caching | — | Block fetches fresh on each page load and on filter/page change |
| **EDS page cache** | Page HTML (authored featureCollectionId only) | EDS | Page cached normally; product data fetched dynamically at runtime |

**Key points:**
- Product selection data is dynamic (filters, paging) — not suitable for long-lived caches
- TFS controls freshness via standard HTTP cache headers on their API
- No user-specific data → no per-user cache considerations
- EDS page cache is unaffected — only the authored config (featureCollectionId) is in static HTML

---

## 5. Support Model

| Issue | Who Handles |
|---|---|
| Block rendering issue (grid layout, filter UI, pagination, empty/error states) | Adobe Implementation Team |
| API returning wrong/stale data | TFS (Feature Collection Service team) |
| API down or slow | TFS |
| CORS errors (cross-origin access blocked) | TFS (must configure CORS headers) |
| Filter/sorting behavior incorrect (wrong results) | TFS (API business logic) |
| URL parameter handling (wrong filters applied from URL) | Adobe (block JS) |
| JSON schema mismatch (block expects field X, API returns field Y) | Joint — contract misalignment |

---

## 6. Key Dependency: TFS Must Deliver JSON API

The EDS integration **requires TFS to expose a structured JSON endpoint** from the Feature Collection service. The current HTML passthrough endpoint cannot be used in EDS.

### What Adobe Needs from TFS

| Deliverable | Purpose | Status |
|---|---|---|
| JSON API endpoint URL (or URL pattern) | To configure the block's fetch target | Pending |
| JSON response schema (sample response) | To build the block's rendering logic | Pending |
| API authentication requirements | To determine Approach A vs B | Pending |
| CORS headers configured (if cross-origin) | To allow browser-based fetch calls | Pending |
| API availability for development/testing | To build and test the block | Pending |
| Expected API response time | To set appropriate timeout in block JS | Pending |

> The block implementation cannot begin until the JSON API contract is defined and the endpoint is available. This is a **TFS deliverable**.
