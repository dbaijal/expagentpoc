# Header and Footer — Technical Details & Runtime Boundaries

---

## Summary

The header/footer microservice is owned by a separate TFS team and bundles search, sign-in, cart, analytics (Adobe Launch), and consent management (TrustArc). **No authoring of header/footer content is required in EDS** — only injection at the edge.

---

## 1. Current State (AEM 6.4)

The current production architecture uses **Apache SSI (Server-Side Includes)** to inject header/footer into every page at the web server layer.

**Microservice Endpoints:**

| Endpoint | URL Pattern | Used By |
|---|---|---|
| Public (Header) | `https://www.thermofisher.com/global-header-footer/header/nojquery` | Apache SSI (production) |
| Public (Footer) | `https://www.thermofisher.com/global-header-footer/footer/nojquery` | Apache SSI (production) |
| Internal | `{gateway}/tf/header/{userType}/{lang}/{country}/noTrustArc.nojquery.shtml` | Author/Preview only |

**Response Size (~500KB total):**

| Component | Size |
|---|---|
| Inline CSS | ~200KB |
| HTML | ~50KB |
| Inline JS | ~150KB |
| Country data | ~100KB |
| Adobe Launch | External |
| TrustArc | ~50KB |

---

## 2. EDS Integration — Recommended Approach

**Approach B (Edge Worker Injection) is recommended.** This mirrors the current Apache SSI pattern.

```
Browser requests page from EDS CDN
    ↓
Edge Worker intercepts response:
    1. Fetch EDS page HTML (from CDN cache or origin)
    2. Fetch header HTML from public endpoint (from edge cache)
    3. Fetch footer HTML from public endpoint (from edge cache)
    4. Stitch: inject header + footer into page HTML
    5. Return complete HTML to browser
    ↓
Browser receives FULL page with header + content + footer
No CLS, no extra fetch, SEO-friendly
```

**Why Edge Worker over Client-Side Fetch:**

| Concern | Client-Side Fetch | Edge Worker Injection |
|---|---|---|
| CLS | Page renders, header pushes content down | **Zero CLS** — header in HTML before browser parses |
| SEO | Search engines may not see header | **SEO-friendly** — complete page in response |
| Architecture pattern | New pattern | **Mirrors current SSI** — same cache separation |
| LCP | +200-500ms delay | Minimal (~5-20ms edge latency) |

---

## 3. Runtime Ownership & Integration Boundaries

### 3.1 Ownership Matrix

| Component | Owner | Responsibility |
|---|---|---|
| **Edge Worker (injection logic)** | Adobe / Implementation Team | Fetch header/footer from microservice endpoint, stitch into EDS page HTML, manage edge cache for header/footer fragments |
| **Header/Footer Microservice** | TFS (Header/Footer Team) | HTML generation, all bundled functionality (search, sign-in, cart, analytics, consent, locale adaptation) |
| **Header/Footer Content** | TFS (Header/Footer Team) | Navigation links, search config, language/country data, all visual content |
| **CDN / Edge Compute Platform** | TFS (infrastructure) + Adobe (configuration) | Platform selection, edge worker deployment, cache rules |
| **EDS Page Content** | Adobe / Implementation Team | Page body content delivered by EDS — independent of header/footer |

### 3.2 Integration Boundary

```
┌───────────────────────────────────────────────────────────────┐
│  ADOBE / IMPLEMENTATION TEAM                                   │
│                                                               │
│  Edge Worker                                                   │
│  • Fetches header/footer from public endpoint                 │
│  • Caches header/footer HTML at edge (separate from page)     │
│  • Stitches into EDS page response                            │
│  • Handles timeout/fallback                                   │
│                                                               │
│  EDS Page (blocks, sections, content)                          │
│  • Delivered from EDS CDN (aem.live)                          │
│  • Cached independently — EDS cache works normally            │
│  • Invalidated on publish — no interference from header       │
└────────────────────────────┬──────────────────────────────────┘
                             │
              PUBLIC ENDPOINT (no auth required)
              GET /global-header-footer/header/nojquery
              GET /global-header-footer/footer/nojquery
                             │
┌────────────────────────────▼──────────────────────────────────┐
│  TFS — HEADER/FOOTER TEAM                                      │
│                                                               │
│  Header/Footer Microservice                                    │
│  • Generates HTML blob (~500KB)                               │
│  • Bundles: search, sign-in, cart, analytics, consent         │
│  • Locale adaptation via inline JS (reads cookies)            │
│  • All personalization client-side (CK_ISO_CODE,              │
│    CK_LANG_CODE, identity_uid)                                │
│                                                               │
│  Fully self-contained — no EDS-side logic for header behavior │
└───────────────────────────────────────────────────────────────┘
```

### 3.3 Authentication / Security

**No authentication is needed for this integration.**

| Integration Point | Auth? | Reason |
|---|---|---|
| Edge Worker → Header/Footer endpoint | **None** | Public endpoints — same URLs currently used by Apache SSI. No API key, no mTLS. |
| Header JS → cart/auth/search | Handled by header's own JS | TFS responsibility — not Adobe's |
| Header JS → analytics (Adobe Launch) | Handled by header's own JS | TFS responsibility |
| Header JS → consent (TrustArc) | Handled by header's own JS | TFS responsibility |

### 3.4 Cache Strategy

**EDS page cache and header/footer cache are independent.** They do not interfere with each other.

| Cache Layer | What's Cached | TTL | Invalidation |
|---|---|---|---|
| **EDS CDN (aem.live)** | Page HTML only (blocks, sections, content) — WITHOUT header/footer | Per EDS config | Push-based invalidation on publish from AEM |
| **Edge Worker cache** | Header HTML (~500KB) + Footer HTML — from microservice | 5–15 minutes (or match existing Apache SSI cache) | TTL expiry |
| **Customer CDN (Akamai)** | Not recommended to cache stitched response — let Edge Worker handle per-request stitching using fragment caches | Pass-through or very short TTL | — |

**Key points:**
- Header/footer is the **same HTML for all users** — one cached copy serves everyone
- All personalization (locale, cart state, sign-in) happens **client-side** via header's inline JS reading cookies (`CK_ISO_CODE`, `CK_LANG_CODE`, `identity_uid`)
- No per-user edge caching needed for header/footer
- EDS page cache works normally — Edge Worker only adds header/footer around the cached page content
- EDS push-based cache invalidation (on author publish) is not affected by header injection

### 3.5 Fallback Behavior

| Failure Scenario | Fallback | User Impact |
|---|---|---|
| **Header/Footer endpoint down** | Edge Worker returns page without header/footer. EDS block JS falls back to client-side fetch (Approach A). | Header appears after page load — CLS occurs. Functional but degraded. |
| **Header/Footer endpoint slow (>1s)** | Serve from edge cache (stale-while-revalidate). If no cache, fall back to client-side fetch. | Minimal — stale header is acceptable (content changes rarely). |
| **Edge Worker fails entirely** | Page served without edge processing. EDS header/footer blocks do client-side fetch. | Same as Approach A — header loads after page. |
| **Header microservice returns malformed HTML** | Edge Worker validates response (checks for expected markers). If invalid, skip injection → client-side fallback. | Prevents broken HTML from corrupting the page. |

**Design principle:** The page always renders. Header injection is progressive enhancement via Edge Worker. Client-side fetch is the safety net.

### 3.6 Support Model

| Issue | Who Handles | Escalation |
|---|---|---|
| Header/footer not appearing on page | Adobe (Edge Worker — injection failed) | If endpoint is down → TFS Header Team |
| Header content wrong (nav links, search, language) | TFS Header Team | TFS internal |
| Search not working | TFS Header Team | TFS internal |
| Cart / Sign-in not working | TFS Header Team | TFS internal |
| Analytics (Adobe Launch) not firing | TFS Header Team | TFS → Analytics team |
| TrustArc / consent not working | TFS Header Team | TFS → TrustArc team |
| CLS / performance from header injection | Joint — Adobe (edge timing) + TFS (payload size) | Review cache TTL and payload optimization |
| Locale adaptation failing (wrong language/country) | TFS Header Team (owns header JS + cookie logic) | TFS internal |

---

## 4. Assumptions

- All locale adaptation, search, cart, auth, analytics, and consent are handled by the **header's own inline JavaScript** — automatically, after the page loads in the browser.
- Header reads cookies `CK_ISO_CODE`, `CK_LANG_CODE`, `identity_uid` to determine country, language, and user type — adapts itself client-side.
- In EDS, **all userType personalization is handled client-side** by the header's own JS. The Edge Worker fetches the same generic HTML for all users.
- The header/footer microservice is an **existing production service** (currently serving Apache SSI) — no new service needs to be built.

---

## 5. Open Questions

| # | Question | Status |
|---|---|---|
| a | Does Edge Worker call the global `/global-header-footer/header/nojquery` URL, or does it need to pass userType/lang/country in the URL? | Pending confirmation from TFS Header Team |
| b | Confirm that locale-specific link handling (us/en links for us/en site, in/en links for in/en site) is handled by header.js — not by the Edge Worker. | Pending confirmation |
| c | What is the current Apache SSI cache TTL for header/footer? (to align Edge Worker cache TTL) | Pending confirmation from TFS |
| d | Is TFS planning any changes to the header/footer microservice endpoints that would affect this integration? | Pending confirmation |
