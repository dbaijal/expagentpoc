# Product List Block — Architecture & Flow Diagrams

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                              AUTHOR TIME                                    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Universal Editor                                                    │    │
│  │  Author configures:                                                  │    │
│  │  • SKU List: "SKU001, SKU002, SKU003, SKU004"                       │    │
│  │  • Columns: Size, Price, Quantity, Add-to-Cart                      │    │
│  └──────────────────────────────────┬──────────────────────────────────┘    │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  AEM Cloud (xWalk)                                                   │    │
│  │  Publishes page with Product List block containing authored config   │    │
│  └──────────────────────────────────┬──────────────────────────────────┘    │
│                                     │                                       │
└─────────────────────────────────────┼───────────────────────────────────────┘
                                      │ Publish
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                              RUNTIME                                        │
│                                                                             │
│  ┌────────────┐    ┌──────────────────┐    ┌────────────────────────────┐  │
│  │            │    │                  │    │                            │  │
│  │  Browser   │───►│  CDN + Edge      │───►│  Product Microservice      │  │
│  │  (User)    │◄───│  Worker          │◄───│  (TFS-Owned)              │  │
│  │            │    │                  │    │                            │  │
│  └────────────┘    └──────────────────┘    └──────────────┬─────────────┘  │
│                                                           │                 │
│                                                           ▼                 │
│                                            ┌──────────────────────────────┐ │
│                                            │  Backend APIs (TFS-Owned)    │ │
│                                            │  • Catalog API               │ │
│                                            │  • Pricing API               │ │
│                                            │  • UserType Service          │ │
│                                            └──────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Request Flow — Anonymous User

```
                    ANONYMOUS USER REQUEST
                    ━━━━━━━━━━━━━━━━━━━━━

┌──────────┐         ┌─────────────────────────────────────────┐
│          │  GET    │                                         │
│  Browser │────────►│  Customer CDN (Akamai)                  │
│          │         │                                         │
└──────────┘         └───────────────────┬─────────────────────┘
                                         │
                                         │ Forward to origin
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  EDS CDN + Edge Worker                   │
                     │                                         │
                     │  1. Receive page request                 │
                     │  2. Fetch page HTML from EDS origin      │
                     │  3. Parse HTML — find Product List block │
                     │  4. Extract: SKUs, columns, country      │
                     │                                         │
                     │  5. CHECK EDGE CACHE:                    │
                     │     Key = country + SKU hash             │
                     │     ┌─────────────────────────┐         │
                     │     │ Cache HIT?              │         │
                     │     │ YES → use cached HTML   │         │
                     │     │ NO  → call microservice │         │
                     │     └────────────┬────────────┘         │
                     │                  │ (cache MISS)          │
                     └──────────────────┼──────────────────────┘
                                        │
                      API Key header     │
                      X-API-Key: ***     │
                                        ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Product Microservice (TFS)              │
                     │                                         │
                     │  1. Validate API Key                     │
                     │  2. Resolve country/locale               │
                     │  3. Generate OAuth token                 │
                     │  4. Call Catalog API (product details)   │──► Catalog API
                     │  5. Call Pricing API (anonymous pricing) │──► Pricing API
                     │     (parallel calls)                     │
                     │  6. Merge responses                      │
                     │  7. Return normalized JSON               │
                     │                                         │
                     │  Response time: < 500ms (p95 target)     │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                                         │ JSON response
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Edge Worker (continues)                 │
                     │                                         │
                     │  6. Receive JSON from microservice       │
                     │  7. Render product HTML table            │
                     │     (SKU, Name, Size, Price, Qty, Cart)  │
                     │  8. Inject HTML into page response       │
                     │  9. Store in edge cache (TTL: 5-15 min)  │
                     │  10. Return complete page to CDN         │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                                         │ Complete HTML page
                                         │ (with product table)
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Browser receives page                   │
                     │                                         │
                     │  • Product table in markup (SEO ✓)       │
                     │  • Block JS activates:                   │
                     │    - Add-to-Cart button handlers         │
                     │    - Quantity validation                  │
                     │    - Analytics: product impression push  │
                     │                                         │
                     └─────────────────────────────────────────┘
```

---

## 3. Request Flow — Logged-In User (Personalized Pricing)

```
                    LOGGED-IN USER REQUEST
                    ━━━━━━━━━━━━━━━━━━━━━

┌──────────┐         ┌─────────────────────────────────────────┐
│          │  GET    │                                         │
│  Browser │────────►│  Customer CDN (Akamai)                  │
│ (cookie) │         │  Detects auth cookie → NO edge cache    │
│          │         │                                         │
└──────────┘         └───────────────────┬─────────────────────┘
                                         │
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  EDS CDN + Edge Worker                   │
                     │                                         │
                     │  1. Receive page request                 │
                     │  2. Detect auth cookie → SKIP CACHE      │
                     │  3. Fetch page HTML from EDS origin      │
                     │  4. Extract SKUs, columns, country       │
                     │  5. Forward user context (cookie/token)  │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                      API Key + User      │
                      context forwarded   │
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Product Microservice (TFS)              │
                     │                                         │
                     │  1. Validate API Key                     │
                     │  2. Resolve UserType (from cookie/token) │
                     │  3. Generate OAuth token                 │
                     │  4. Call Catalog API                     │──► Catalog API
                     │  5. Call Pricing API                     │──► Pricing API
                     │     (user-specific / contract pricing)   │
                     │  6. Merge responses                      │
                     │  7. Return JSON (personalized pricing)   │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                                         │ JSON (user-specific)
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Edge Worker (continues)                 │
                     │                                         │
                     │  6. Render product HTML with user pricing│
                     │  7. Inject into page                     │
                     │  8. DO NOT CACHE (personalized data)     │
                     │  9. Return page                          │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Browser receives page                   │
                     │  (with user-specific pricing in markup)  │
                     │                                         │
                     └─────────────────────────────────────────┘
```

---

## 4. Fallback Flow — Microservice Timeout

```
                    FALLBACK SCENARIO (Microservice Timeout)
                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌──────────┐         ┌─────────────────────────────────────────┐
│          │  GET    │                                         │
│  Browser │────────►│  CDN + Edge Worker                       │
│          │         │                                         │
└──────────┘         │  1. Parse page, find Product List block │
                     │  2. Call microservice...                 │
                     │                                         │
                     │     ┌─────────────────────────────┐     │
                     │     │  ⏱️ TIMEOUT (>2 seconds)     │     │
                     │     │  Microservice did not respond │     │
                     │     └─────────────────────────────┘     │
                     │                                         │
                     │  3. FALLBACK: Return page WITHOUT       │
                     │     product data in the table            │
                     │     (empty product-list block with       │
                     │      loading placeholder + SKU config)   │
                     │                                         │
                     └───────────────────┬─────────────────────┘
                                         │
                                         │ Page with empty product table
                                         │ (contains SKU config in data attrs)
                                         ▼
                     ┌─────────────────────────────────────────┐
                     │                                         │
                     │  Browser — Block JS takes over           │
                     │                                         │
                     │  1. Block JS detects empty product table │
                     │  2. Reads SKU config from data attributes│
                     │  3. Calls microservice directly          │─────────┐
                     │     (client-side fetch)                  │         │
                     │  4. On success: renders product table    │         │
                     │  5. On failure: shows error message      │         │
                     │     "Products temporarily unavailable"   │         │
                     │                                         │         │
                     └─────────────────────────────────────────┘         │
                                                                         │
                                                                         ▼
                                                  ┌──────────────────────────┐
                                                  │ Product Microservice     │
                                                  │ (may now be responsive)  │
                                                  └──────────────────────────┘

    NOTE: SEO/LLM crawlability is degraded in fallback mode because
    product data is not in the initial HTML markup. Edge rendering
    is the primary path; client-side is the safety net.
```

---

## 5. Add-to-Cart Flow (Client-Side — Always)

```
                    ADD-TO-CART FLOW
                    ━━━━━━━━━━━━━━━━

┌──────────────────────────────────────────────────────────────┐
│  Browser — Product List Block JS                              │
│                                                              │
│  User clicks "Add to Cart" on a product row                  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Step 1: VALIDATE (client-side — ported from lineItem.js)│   │
│  │  • Is quantity > 0?                                    │    │
│  │  • Is quantity an integer?                             │    │
│  │  • Is quantity within min/max limits?                  │    │
│  │  • If invalid → show error message, STOP              │    │
│  └──────────────────────────────────────┬───────────────┘    │
│                                         │ Valid               │
│                                         ▼                    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Step 2: PUSH ANALYTICS EVENT                          │    │
│  │  • Push "add-to-cart" event to analytics layer        │    │
│  │  • Data: SKU, quantity, price, product name           │    │
│  └──────────────────────────────────────┬───────────────┘    │
│                                         │                    │
│                                         ▼                    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Step 3: CALL MINI-CART SERVICE                        │    │
│  │  • POST to mini-cart endpoint                         │    │
│  │  • Payload: SKU, quantity, sessionId                  │    │
│  │  • Auth: session cookie (existing)                    │    │
│  └──────────────────────────────────────┬───────────────┘    │
│                                         │                    │
│                          ┌──────────────┼──────────────┐     │
│                          │              │              │     │
│                          ▼              ▼              ▼     │
│                       SUCCESS        ERROR         TIMEOUT   │
│                          │              │              │     │
│                          ▼              ▼              ▼     │
│                    Update cart      Show error     Retry 1x  │
│                    indicator        message        then error │
│                    "Item added"     "Unable to     message    │
│                                    add to cart"              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. Ownership & Security Boundaries

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│  ┌───────────────────────────────────────┐                              │
│  │      ADOBE / IMPLEMENTATION TEAM       │                              │
│  │                                       │                              │
│  │  • EDS Block (JS/CSS/Model)           │                              │
│  │  • Edge Worker code                   │                              │
│  │  • Analytics event push               │                              │
│  │  • Quantity validation (client-side)   │                              │
│  │  • Fallback logic                     │                              │
│  │  • CDN cache rules                    │                              │
│  │                                       │                              │
│  └────────────────────┬──────────────────┘                              │
│                       │                                                 │
│          ─────────────┼──────────────────────                           │
│          │    INTEGRATION CONTRACT        │                             │
│          │                                │                             │
│          │  • API Key auth (header)       │                             │
│          │  • Request: SKUs + locale +    │                             │
│          │    user context                │                             │
│          │  • Response: normalized JSON   │                             │
│          │  • SLA: < 500ms p95           │                             │
│          │  • Timeout: 2s (edge side)     │                             │
│          │  • Defined jointly             │                             │
│          │                                │                             │
│          ─────────────┼──────────────────────                           │
│                       │                                                 │
│  ┌────────────────────▼──────────────────┐                              │
│  │      TFS-OWNED                         │                              │
│  │                                       │                              │
│  │  • Product Microservice               │                              │
│  │  • OAuth token management             │                              │
│  │  • Catalog API                        │                              │
│  │  • Pricing API                        │                              │
│  │  • UserType Service                   │                              │
│  │  • Mini-Cart Service                  │                              │
│  │  • Microservice SLA & monitoring      │                              │
│  │                                       │                              │
│  └───────────────────────────────────────┘                              │
│                                                                         │
│  SECURITY:                                                              │
│  • Edge → Microservice: API Key (stored in CDN secret manager)          │
│  • Microservice → Backend APIs: OAuth (TFS internal)                    │
│  • Browser → Mini-Cart: Session cookie (existing, no change)            │
│  • NO secrets in page HTML or client-side JS                            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Cache Layers

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  REQUEST →                                                               │
│                                                                          │
│  ┌─────────────────────┐   ┌──────────────────────┐   ┌──────────────┐  │
│  │  CDN Page Cache      │   │  Edge Worker Cache    │   │ Microservice │  │
│  │                     │   │                      │   │ Cache        │  │
│  │  Full page HTML     │   │  Product HTML        │   │              │  │
│  │                     │   │  fragment            │   │  Catalog     │  │
│  │  Key: URL + country │   │                      │   │  data only   │  │
│  │                     │   │  Key: country +      │   │              │  │
│  │  TTL: per EDS       │   │       SKU hash       │   │  TTL: 1 hour │  │
│  │  config             │   │                      │   │              │  │
│  │                     │   │  TTL: 5-15 min       │   │  ❌ Pricing   │  │
│  │  Anonymous: ✅       │   │                      │   │  NOT cached  │  │
│  │  Logged-in: ❌       │   │  Anonymous: ✅        │   │              │  │
│  │  (bypass)           │   │  Logged-in: ❌        │   │              │  │
│  │                     │   │  (bypass)            │   │              │  │
│  └─────────────────────┘   └──────────────────────┘   └──────────────┘  │
│                                                                          │
│  CACHE RULES:                                                            │
│  • Pricing is NEVER cached at any layer (changes frequently)             │
│  • Catalog data (names, sizes) cached at microservice (1 hour)           │
│  • Logged-in users ALWAYS get live data (all caches bypassed)            │
│  • Anonymous users get edge-cached data (refreshed every 5-15 min)       │
│  • stale-while-revalidate: serve stale if microservice is down           │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```
