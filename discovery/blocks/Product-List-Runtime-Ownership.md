# Product List Block — Runtime Ownership & Integration Boundaries

---

## 1. Ownership Matrix

| Component | Owner | Responsibilities |
|---|---|---|
| **Product List Block (EDS)** | Adobe / Implementation Team | Block JS/CSS, UE authoring model, client-side rendering (fallback), quantity validation, analytics event push, add-to-cart UX |
| **Edge Worker** | Adobe / Implementation Team | Extract SKU/column config from page HTML, call Product Microservice, render product HTML table, inject into response, manage cache, handle timeout/fallback |
| **Product Microservice** | TFS | OAuth token generation, UserType resolution, Catalog API calls, Pricing API calls, response merging, normalized JSON response, SLA ownership |
| **Catalog API** | TFS (existing backend) | Product data — names, sizes, descriptions, images |
| **Pricing API** | TFS (existing backend) | User/region-specific pricing, availability, price access types |
| **Mini-Cart Service** | TFS (existing backend) | Cart operations, stock validation, order management |
| **CDN / Edge Compute Platform** | TFS (infrastructure) + Adobe (configuration) | Platform selection, edge worker deployment, secret management, cache rules |

---

## 2. Integration Boundaries

```
┌───────────────────────────────────────────────────────────────┐
│  ADOBE / IMPLEMENTATION TEAM                                   │
│                                                               │
│  EDS Block (JS/CSS)                                            │
│  • Reads authored config (SKUs, columns)                      │
│  • Client-side: quantity validation, add-to-cart, analytics   │
│  • Fallback: client-side fetch if edge rendering fails        │
│                                                               │
│  Edge Worker                                                   │
│  • Extracts block config from page HTML                       │
│  • Calls Product Microservice                                 │
│  • Renders product HTML from JSON response                    │
│  • Manages edge cache (anonymous users)                       │
│  • Handles timeout/fallback                                   │
└────────────────────────────┬──────────────────────────────────┘
                             │
                  INTEGRATION CONTRACT
                  (defined and agreed jointly)
                             │
┌────────────────────────────▼──────────────────────────────────┐
│  TFS-OWNED                                                     │
│                                                               │
│  Product Microservice                                          │
│  • Validates API Key                                          │
│  • Resolves UserType                                          │
│  • Orchestrates Catalog API + Pricing API (parallel)          │
│  • Merges and normalizes response                             │
│  • Returns JSON within SLA                                    │
│                                                               │
│  Backend APIs (existing — no change)                           │
│  • Catalog API                                                │
│  • Pricing API                                                │
│  • UserType Service                                           │
│  • Mini-Cart Service                                          │
└───────────────────────────────────────────────────────────────┘
```

---

## 3. Integration Contract

The integration contract between the Edge Worker and the Product Microservice will be defined and agreed jointly during implementation.

| Aspect | Specification |
|---|---|
| **Request** | SKU list + locale/country + user context (cookie/token if authenticated) |
| **Response** | Normalized JSON — array of product objects with name, SKU, size, price, availability, price access type |
| **Authentication** | API Key in request header (`X-API-Key`) |
| **SLA Target** | < 500ms (p95 response time) for typical request (5–20 SKUs) |
| **Timeout** | 2 seconds at the Edge Worker — if microservice does not respond within 2s, fallback is triggered |
| **Protocol** | HTTPS (TLS) |
| **Method** | POST (SKU list in request body) or GET (SKUs as query parameter) — to be agreed |

---

## 4. Authentication & Security Model

| Integration Point | Auth Mechanism | Secret Management | Who Provides |
|---|---|---|---|
| **Edge Worker → Product Microservice** | API Key in request header | Stored as CDN edge secret (not in code, not in page HTML) | TFS provides key; Adobe configures in CDN |
| **Product Microservice → Catalog/Pricing APIs** | OAuth client credentials (existing pattern) | TFS manages internally | TFS |
| **Browser → Mini-Cart Service** | Session cookie (existing pattern — no change) | Existing | TFS |

**Security Constraints:**

- No API keys or secrets exposed in client-side JavaScript or page HTML
- Edge Worker secrets stored in CDN platform's secret manager
- Microservice must validate API key on every request — reject requests without valid key
- Microservice must not expose raw backend API responses — normalize and sanitize before returning
- User context (cookie/token) forwarded securely from edge to microservice — not logged or stored

---

## 5. Cache Strategy

| Layer | What's Cached | Cache Key | TTL | Invalidation |
|---|---|---|---|---|
| **Edge Worker (product fragment)** | Rendered product HTML for anonymous users | Country + SKU list hash | 5–15 minutes | TTL expiry (auto-refresh on next request) |
| **Microservice (catalog data)** | Product names, sizes, descriptions (non-pricing) | SKU | 1 hour | TTL expiry |
| **Microservice (pricing)** | **NOT cached** — always fetched live from Pricing API | — | — | Always live |

**Cache Rules:**

- **Anonymous users:** Edge-cached product data with short TTL (5–15 minutes). Price changes reflect within TTL window.
- **Logged-in users:** All caches bypassed. Every request fetches live, personalized pricing from microservice. No edge caching for authenticated requests.
- **Pricing is never cached** at any layer because it changes frequently (special offers, online offers, user-specific pricing).
- **Stale-while-revalidate:** If microservice is temporarily unavailable, serve last cached response (anonymous users only) to avoid complete failure.
- **Final TTL is a business decision** — trade-off between performance (longer TTL) and pricing freshness (shorter TTL). Will be agreed with TFS during implementation.

---

## 6. Fallback Behavior

| Failure Scenario | What Happens | User Impact |
|---|---|---|
| **Microservice timeout (> 2 seconds)** | Edge Worker returns page without product data. Block JS detects empty table, fetches from microservice client-side as fallback. | Slight delay — product table loads after page. SEO data missing for this request only. |
| **Microservice error (5xx)** | Same as timeout — client-side fallback with one retry. | Same as above. If retry fails, block shows "Products temporarily unavailable." |
| **Microservice returns partial data** (some SKUs fail) | Render available products. Show "Unavailable" for failed SKUs. | User sees partial product list with clear indication. |
| **Edge Worker failure** | Page serves without edge processing. Block JS takes over entirely — fetches client-side. | Progressive enhancement — products load client-side. SEO degraded for this request. |
| **CDN cache stale + microservice down** | Serve stale cached response (stale-while-revalidate). | User sees last-known pricing. May be slightly outdated until service recovers. |
| **Add-to-Cart service failure** | Client-side retry (1x). If still fails, show error message. | User sees "Unable to add to cart. Please try again." |

**Design principle:** The product list is never completely blank. Primary path = edge rendering. Fallback = client-side fetch. Last resort = stale cache. Total failure = clear error message.

---

## 7. Support Model

| Scenario | First Response | Escalation |
|---|---|---|
| Block rendering issue (layout, styling, UE authoring) | Adobe Implementation Team | Adobe internal |
| Product data incorrect (wrong name, wrong price, wrong availability) | TFS — Microservice / API team | TFS → Catalog/Pricing API owners |
| Edge Worker failure (timeout handling, cache issue) | Adobe Implementation Team | If root cause is microservice → escalate to TFS |
| Microservice down or slow | TFS — Microservice team | TFS internal |
| Add-to-Cart failure | TFS — Mini-Cart service team | TFS internal |
| Analytics events not firing | Adobe Implementation Team (block JS) | Adobe → Analytics team |
| Cache serving stale data beyond acceptable window | Joint review — CDN config (Adobe) + TTL settings (business decision) | Review TTL jointly |
| Security concern (API key leak, unauthorized access) | Joint — immediate response from both teams | Security incident process |

---

## 8. Monitoring & Alerting

| What's Monitored | Who Monitors | Alert Threshold |
|---|---|---|
| Edge Worker execution (error rate, latency) | Adobe / CDN platform | Error rate > 1% or latency > 2s |
| Product Microservice (response time, availability) | TFS | p95 > 500ms or error rate > 1% or availability < 99.9% |
| Edge cache hit rate | Adobe / CDN platform | Hit rate drops below 70% (indicates config issue) |
| Add-to-Cart success rate | TFS (Mini-Cart service) | Success rate < 95% |
| Client-side fallback activation rate | Adobe (block JS telemetry) | Fallback rate > 5% (indicates edge/microservice issue) |

---

## 9. Open Items for Implementation

| Item | Owner | Status |
|---|---|---|
| CDN edge compute platform selection (Akamai EdgeWorkers / Fastly / Cloudflare) | TFS (infrastructure decision) | Pending |
| Microservice deployment location and network topology | TFS | Pending |
| API contract finalization (request/response schema) | Joint (Adobe + TFS) | To be agreed during implementation |
| Final cache TTL (business decision on pricing freshness vs performance) | TFS (business) | Pending |
| User identification mechanism at edge (cookie forwarding / token) | Joint | Pending |
| TFS microservice for product details — scope overlap with this service | TFS | Pending confirmation |
| API Key provisioning and rotation process | TFS (provides) + Adobe (configures) | During implementation |
| Load testing targets (concurrent requests, SKU list sizes) | Joint | During implementation |
