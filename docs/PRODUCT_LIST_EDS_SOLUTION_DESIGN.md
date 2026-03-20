# Product List — EDS Solution Design

**Project:** Thermo Fisher Scientific — AEM 6.4 to AEM Cloud + Edge Delivery Services
**Component:** Product List (`cmp-p-productlist`)
**Version:** Draft v0.1
**Date:** March 2026
**Author:** Architecture Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [Challenge: No Server-Side in EDS](#3-challenge-no-server-side-in-eds)
4. [EDS Architecture](#4-eds-architecture)
5. [Layered Architecture](#5-layered-architecture)
6. [Edge Worker — Detailed Design](#6-edge-worker--detailed-design)
7. [Product Microservice — Detailed Design](#7-product-microservice--detailed-design)
8. [Authoring Experience in Universal Editor](#8-authoring-experience-in-universal-editor)
9. [SEO & Schema.org](#9-seo--schemaorg)
10. [Two-Layer Pricing: Anonymous vs Logged-In](#10-two-layer-pricing-anonymous-vs-logged-in)
11. [Multiple Blocks Per Page — Batching Strategy](#11-multiple-blocks-per-page--batching-strategy)
12. [Caching Strategy](#12-caching-strategy)
13. [Add to Cart — Client-Side Interaction](#13-add-to-cart--client-side-interaction)
14. [Microservice Hosting Decision](#14-microservice-hosting-decision)
15. [Request Context Flow: AEM vs EDS](#15-request-context-flow-aem-vs-eds)
16. [Migration Mapping](#16-migration-mapping)
17. [Risk Matrix](#17-risk-matrix)
18. [Open Questions for Discovery](#18-open-questions-for-discovery)
19. [Summary of Decisions](#19-summary-of-decisions)

---

## 1. Executive Summary

The Product List component (`cmp-p-productlist`) displays a table of products with SKU, name, size, pricing, quantity selector, and add-to-cart functionality. In the current AEM 6.4 implementation, the component relies on server-side Sling models to orchestrate three backend API calls (user type, catalog, pricing), merge responses, and render HTML via JSP.

EDS has no server-side rendering layer. The HTML published from Universal Editor is static. To maintain **SEO-friendly server-rendered HTML** and keep **API authentication tokens secure**, this design introduces a **two-layer architecture**:

- **Edge Worker** — Presentation layer. Scans the page for product-list placeholders, calls the product microservice, renders HTML from JSON, and stitches it into the page.
- **Product Microservice** — Data orchestration layer. Manages auth tokens, calls the three existing TFS APIs, merges responses, caches data, and returns clean JSON.

This mirrors the same edge-worker stitching pattern established for the header/footer solution.

---

## 2. Current State Analysis

### 2.1 Component Overview

| Attribute | Value |
|-----------|-------|
| AEM Component | `cmp-p-productlist` |
| Rendering | Sling Model + JSP |
| Data Source | 3 REST APIs (server-side) |
| Authentication | `authTokenProvider` (server-side) |
| Usage | Product category pages, accessories pages |
| Instances Per Page | Up to 3–4 blocks per page |

### 2.2 Current AEM Data Flow

```
Author Dialog (AEM 6.4)
│
│  Authored fields:
│  • SKU list (comma-separated)
│  • Columns to display (price, qty, size, etc.)
│  • CTA type (Add to Cart, Request Quote, None)
│
▼
Sling Model (Server-Side Java)
│
│  Step 1: authTokenProvider.getToken()
│  Step 2: GET /userType?country={country}&lang={lang}
│          Cookie: login-token={token}
│          → Returns: userType (guest, registered, contract)
│
│  Step 3 (parallel):
│  ├─ GET /catalog?skus=AMEP5015,AMEP5016,...
│  │  → Returns: name, size, description per SKU
│  │
│  └─ GET /pricing?skus=...&userType={userType}
│     → Returns: listPrice, specialPrice, quoteOnly per SKU
│
│  Step 4: Merge catalog + pricing → product objects
│
▼
JSP Template
│
│  Iterates product objects → renders HTML table
│  Handles: regular price, discount price, "Request A Quote"
│
▼
Browser receives fully-rendered HTML
```

### 2.3 DOM Structure (Current Production)

Observed on: `thermofisher.com/.../led-light-cubes.html`

```
div.cmp-p-productlist
└── form#productForm_xxx
    └── table.productlist-mobile-collapse.table-bordered.table-list
        ├── thead
        │   └── tr
        │       ├── th "Catalog #"
        │       ├── th "Name"
        │       ├── th "Size"
        │       ├── th "Price"
        │       └── th "Qty"
        └── tbody
            └── tr (per product)
                ├── td → catalog number
                ├── td → product name (linked)
                ├── td → size
                ├── td → price (4 display states)
                └── td → qty input + add-to-cart button
```

### 2.4 Price Display States

| State | Condition | Rendered Output |
|-------|-----------|-----------------|
| **Regular Price** | `specialPrice == null` | `$495.00` |
| **Discount Price** | `specialPrice < listPrice` | `$4,549.65` ~~$4,945.00~~ Save $395.35 (8%) |
| **Request A Quote** | `quoteOnly == true` | "Request A Quote" link |
| **Auth Required** | No pricing returned | "Sign in for pricing" |

### 2.5 Observed Data Points

From the LED Light Cubes page:
- 15 product rows per block
- SKUs: AMEP5015, AMEP5016, AMEP5018, AMEP4950–AMEP4973
- One product (AMEP4967) shows "Request A Quote"
- One product (AMEP4954) shows discount pricing with savings calculation
- Form wraps entire table for add-to-cart POST submission

---

## 3. Challenge: No Server-Side in EDS

| Requirement | Why Client-Side Rendering Fails |
|-------------|-------------------------------|
| **SEO** | Googlebot sees empty skeleton — no product data in HTML source |
| **Auth Tokens** | `authTokenProvider` credentials cannot be exposed in browser JS |
| **3 API Calls** | Cross-origin, authenticated, token-chained — not browser-safe |
| **Price Sensitivity** | Contract pricing must not be cacheable in public CDN responses |

The product list must be **server-rendered** before reaching the browser, just as it is today in AEM 6.4. The rendering layer must move from AEM's Sling model to an alternative server-side execution environment.

---

## 4. EDS Architecture

### 4.1 High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│  UNIVERSAL EDITOR (Authoring)                                │
│  Author configures: SKUs, visible columns, CTA type         │
└──────────────┬──────────────────────────────────────────────┘
               │ published to EDS origin
               ▼
┌─────────────────────────────────────────────────────────────┐
│  EDS ORIGIN (aem.live CDN)                                   │
│  Static HTML with product-list placeholder + authored config │
└──────────────┬──────────────────────────────────────────────┘
               │ edge worker intercepts
               ▼
┌─────────────────────────────────────────────────────────────┐
│  EDGE WORKER (Presentation Layer)                            │
│  1. Scan page for all product-list blocks                    │
│  2. Extract SKUs from all blocks                             │
│  3. Call Product Microservice (ONE call, all SKUs batched)   │
│  4. Render HTML per block using author's column config       │
│  5. Stitch rendered HTML into page                           │
│  6. Return complete page to browser                          │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│  PRODUCT MICROSERVICE (Data Orchestration Layer)             │
│  1. Get auth token (cached)                                  │
│  2. Determine user type from request context                 │
│  3. Call Catalog API + Pricing API (parallel)                │
│  4. Merge and return JSON                                    │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Design Principles

| Principle | Application |
|-----------|-------------|
| **Separation of concerns** | Edge worker = presentation. Microservice = data. Existing APIs = source of truth. |
| **Thin edge worker** | Edge worker does not manage tokens, call external APIs, or contain business logic. |
| **Consistent pattern** | Same edge-worker stitching pattern as header/footer. |
| **JSON over HTML from microservice** | Microservice returns JSON. Edge worker renders HTML. Maximizes cache hit rate. |
| **Batch over individual** | All product-list blocks on a page are batched into one microservice call. |

---

## 5. Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  LAYER 1: EDS Origin                                         │
│  Responsibility: Content storage                             │
│  • Stores authored content (SKUs, column config, CTA)        │
│  • Publishes static HTML with block placeholders             │
│  • Cached by EDS CDN                                         │
│  • Does NOT contain product data                             │
├─────────────────────────────────────────────────────────────┤
│  LAYER 2: Edge Worker                                        │
│  Responsibility: Presentation (HTML rendering + stitching)   │
│  • Scans page for product-list placeholders                  │
│  • Reads author configuration (columns, CTA) from HTML      │
│  • Calls Product Microservice with batched SKUs              │
│  • Renders HTML table from JSON + author config              │
│  • Generates Schema.org JSON-LD structured data              │
│  • Stitches rendered blocks into page                        │
│  • Does NOT manage auth tokens or call TFS APIs directly     │
├─────────────────────────────────────────────────────────────┤
│  LAYER 3: Product Microservice                               │
│  Responsibility: Data orchestration                          │
│  • Manages auth tokens (get, cache, refresh)                 │
│  • Extracts user context (country, lang, login token)        │
│  • Calls 3 existing TFS APIs (userType, catalog, pricing)   │
│  • Merges and normalizes responses                           │
│  • Caches product data (catalog: 1hr, pricing: 15min)       │
│  • Returns clean JSON                                        │
│  • Does NOT render HTML or know about page structure         │
├─────────────────────────────────────────────────────────────┤
│  LAYER 4: Existing TFS APIs (Unchanged)                      │
│  • User Type API                                             │
│  • Catalog API                                               │
│  • Pricing API                                               │
│  • authTokenProvider                                         │
│  • These APIs exist today and require no changes             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Edge Worker — Detailed Design

### 6.1 Responsibilities

The edge worker for product-list follows the same pattern as header/footer stitching. It is a **generic stitching engine** that detects placeholders and replaces them with rendered HTML.

```
Edge Worker handles (today and future):
├── header/footer   → calls Header Service → stitches
├── product-list    → calls Product Service → renders + stitches
├── product-detail  → calls Product Service → renders + stitches  (future)
├── search-results  → calls Search Service  → renders + stitches  (future)
└── ... any future server-rendered component
```

### 6.2 Processing Flow

```
1. Fetch origin HTML from EDS CDN
2. Scan entire page for all product-list blocks
3. For each block, extract:
   - SKUs (from authored content)
   - Columns to display (from authored content)
   - CTA type and label (from authored content)
4. Collect ALL unique SKUs across all blocks on the page
5. Make ONE call to Product Microservice with all SKUs
6. Receive JSON map: { sku → product data }
7. For each block:
   a. Pick relevant SKUs from the JSON map
   b. Render HTML table showing only the author-selected columns
   c. Apply CTA (Add to Cart button, Request Quote link, or none)
   d. Generate Schema.org JSON-LD for SEO
8. Replace each placeholder with rendered HTML
9. Return complete page to browser
```

### 6.3 HTML Rendering Logic

The edge worker renders HTML from JSON using simple string concatenation. No template engine is required.

```javascript
function renderProductTable(products, columns, ctaLabel) {
  const headers = columns
    .map(col => `<th>${COLUMN_LABELS[col]}</th>`)
    .join('');

  const rows = products.map(product => {
    const cells = columns.map(col => {
      if (col === 'price') return `<td>${renderPrice(product)}</td>`;
      return `<td>${product[col] || ''}</td>`;
    }).join('');

    const cta = ctaLabel
      ? `<td><button class="product-add-to-cart" data-sku="${product.sku}">${ctaLabel}</button></td>`
      : '';

    return `<tr data-sku="${product.sku}">${cells}${cta}</tr>`;
  }).join('');

  return `<table class="product-table">
    <thead><tr>${headers}</tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}
```

### 6.4 Price Rendering

```javascript
function renderPrice(product) {
  if (product.quoteOnly) {
    return '<a href="/quote" class="product-request-quote">Request A Quote</a>';
  }
  if (product.specialPrice && product.specialPrice < product.listPrice) {
    const savings = product.listPrice - product.specialPrice;
    const pct = Math.round((savings / product.listPrice) * 100);
    return `<span class="product-price-special">${format(product.specialPrice)}</span>
      <span class="product-price-original">${format(product.listPrice)}</span>
      <span class="product-price-savings">Save ${format(savings)} (${pct}%)</span>`;
  }
  if (product.listPrice) {
    return `<span class="product-price">${format(product.listPrice)}</span>`;
  }
  return '<span class="product-price-auth">Sign in for pricing</span>';
}
```

### 6.5 Why Edge Worker Renders HTML (Not Microservice)

The microservice returns **JSON**, and the edge worker renders **HTML**. This is a deliberate decision:

| Factor | Reasoning |
|--------|-----------|
| **Cache hit rate** | JSON is cacheable per SKU set, regardless of column/CTA config. HTML varies per block configuration, reducing cache reuse. |
| **Author config flexibility** | Author can change visible columns in UE without microservice redeployment. Edge worker reads the new config from the published HTML and renders accordingly. |
| **Reusability** | Same JSON response can serve product-list, product-detail, search results, and any future component. HTML is locked to one specific layout. |
| **Microservice simplicity** | Microservice handles only data aggregation — clean, testable, single responsibility. |

---

## 7. Product Microservice — Detailed Design

### 7.1 API Contract

**Request:**
```
POST /api/products
Headers:
  X-Country: us
  X-Lang: en
  X-Login-Token: {forwarded from browser cookie}
Body:
  { "skus": ["AMEP5015", "AMEP5016", "AMEP5018", ...] }
```

**Response:**
```json
{
  "userType": "anonymous",
  "products": {
    "AMEP5015": {
      "sku": "AMEP5015",
      "name": "EVOS™ LED Light Cube, GFP",
      "catalogNumber": "AMEP5015",
      "size": "Each",
      "listPrice": 495.00,
      "specialPrice": null,
      "quoteOnly": false,
      "currency": "USD",
      "productUrl": "/us/en/home/.../AMEP5015.html"
    },
    "AMEP4954": {
      "sku": "AMEP4954",
      "name": "EVOS™ FL Auto 2 Imaging System",
      "catalogNumber": "AMEP4954",
      "size": "Each",
      "listPrice": 4945.00,
      "specialPrice": 4549.65,
      "quoteOnly": false,
      "currency": "USD",
      "productUrl": "/us/en/home/.../AMEP4954.html"
    },
    "AMEP4967": {
      "sku": "AMEP4967",
      "name": "EVOS™ Onstage Incubator",
      "catalogNumber": "AMEP4967",
      "size": "Each",
      "listPrice": null,
      "specialPrice": null,
      "quoteOnly": true,
      "currency": "USD",
      "productUrl": "/us/en/home/.../AMEP4967.html"
    }
  }
}
```

### 7.2 Internal Processing

```
1. Receive request with SKUs + user context (forwarded by edge worker)
2. Get auth token
   - Check cache → if valid, reuse
   - If expired → call authTokenProvider → cache new token
3. Determine user type
   - Call GET /userType?country={country}&lang={lang}
   - Forward login token from browser cookie
   - Returns: guest | registered | contract
4. Fetch product data (parallel)
   - Call Catalog API with all SKUs → product metadata
   - Call Pricing API with all SKUs + userType → pricing data
5. Merge responses
   - Match catalog and pricing by SKU
   - Build normalized product objects
6. Return JSON response
```

### 7.3 Error Handling

| Scenario | Behavior |
|----------|----------|
| Auth token failure | Return 503 — edge worker renders "Products temporarily unavailable" |
| Catalog API timeout | Return partial data — products without catalog info show SKU only |
| Pricing API timeout | Return products with `listPrice: null` — edge worker renders "Sign in for pricing" |
| Invalid SKU | Omit from response — edge worker skips missing SKUs |
| All APIs fail | Return 503 — edge worker renders fallback message |

---

## 8. Authoring Experience in Universal Editor

### 8.1 Authored Block HTML

```html
<div class="product-list">
  <div>
    <div>AMEP5015, AMEP5016, AMEP5018, AMEP4950, AMEP4951</div>
  </div>
  <div>
    <div>price, qty, size</div>
  </div>
  <div>
    <div>Add to Cart</div>
  </div>
</div>
```

- **Row 1**: Comma-separated SKU list
- **Row 2**: Comma-separated column names to display
- **Row 3**: CTA label (empty = no CTA)

### 8.2 UE Component Model

```json
{
  "id": "product-list",
  "fields": [
    {
      "component": "text",
      "name": "skus",
      "label": "Product SKUs",
      "description": "Comma-separated list of SKUs to display",
      "valueType": "string",
      "required": true
    },
    {
      "component": "multiselect",
      "name": "columns",
      "label": "Visible Columns",
      "valueType": "string",
      "options": [
        { "name": "Catalog #", "value": "catalog" },
        { "name": "Product Name", "value": "name" },
        { "name": "Size", "value": "size" },
        { "name": "Price", "value": "price" },
        { "name": "Quantity", "value": "qty" }
      ]
    },
    {
      "component": "select",
      "name": "ctaType",
      "label": "Call to Action",
      "valueType": "string",
      "options": [
        { "name": "Add to Cart", "value": "Add to Cart" },
        { "name": "Request Quote", "value": "Request A Quote" },
        { "name": "None", "value": "" }
      ]
    }
  ]
}
```

### 8.3 Author Workflow

1. Author drags "Product List" block onto page in Universal Editor
2. Enters comma-separated SKUs (same workflow as current AEM dialog)
3. Selects which columns to display (multi-select checkboxes)
4. Selects CTA type from dropdown
5. Publishes page — static HTML with authored config goes to EDS origin
6. Edge worker + microservice handle all data fetching and rendering at request time

The author **does not** see or interact with product data, pricing, or API details. The experience is identical to the current AEM dialog.

---

## 9. SEO & Schema.org

The edge worker generates Schema.org structured data as part of the rendered HTML:

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "ItemList",
  "numberOfItems": 15,
  "itemListElement": [
    {
      "@type": "Product",
      "position": 1,
      "name": "EVOS™ LED Light Cube, GFP",
      "sku": "AMEP5015",
      "offers": {
        "@type": "Offer",
        "price": "495.00",
        "priceCurrency": "USD",
        "availability": "https://schema.org/InStock"
      }
    }
  ]
}
</script>
```

Because the edge worker renders server-side, this structured data is present in the initial HTML response. Googlebot sees the full product table and Schema.org data without executing JavaScript.

---

## 10. Two-Layer Pricing: Anonymous vs Logged-In

Pricing is user-type-dependent. The architecture handles this with two layers:

### Layer 1: Server-Side (Edge Worker + Microservice)

- Always renders with `userType = anonymous`
- Returns list prices (what Google indexes)
- This HTML is **cacheable** at the edge — same for all anonymous visitors

### Layer 2: Client-Side (Block JS)

- Runs in the browser after page load
- Detects if user is logged in (cookie/session check)
- If logged in, calls a pricing proxy endpoint with the user's auth context
- Replaces anonymous prices with the user's contract/special pricing
- Shows discount badges, savings percentages where applicable

```
Anonymous visitor:
  Server-rendered HTML → list prices → fully cacheable → SEO indexed

Logged-in user:
  Server-rendered HTML → list prices (initial flash)
  Client JS detects login → calls pricing proxy → updates prices in-place
  Result: contract pricing displayed within ~200ms of page load
```

This separation ensures:
- SEO always sees full product data with list prices
- Anonymous users get instant page loads (fully cached)
- Logged-in users see personalized pricing with minimal delay

---

## 11. Multiple Blocks Per Page — Batching Strategy

Pages may contain 3–4 product-list blocks. Without batching, this multiplies API calls. The edge worker batches all blocks into a single microservice call.

### Without Batching (Naive)

```
4 blocks × (1 auth + 1 userType + 1 catalog + 1 pricing) = 16 API calls
Sequential latency: ~3.5 seconds
```

### With Batching

```
Edge worker scans full page → finds 4 product-list blocks
Collects ALL unique SKUs across all blocks (e.g., 60 SKUs)
Makes ONE call to microservice with all 60 SKUs
Microservice makes: 1 auth + 1 userType + 1 catalog + 1 pricing = 4 API calls
Edge worker distributes data back to each block and renders individually

Total API calls: 4 (regardless of block count)
Latency: ~1.1 seconds (same as 1 block)
```

### Batching Flow

```
Page HTML contains:
  Block 1: skus=[A,B,C]   columns=[price, qty]      cta="Add to Cart"
  Block 2: skus=[D,E,F]   columns=[price, size]      cta="Add to Cart"
  Block 3: skus=[A,D,G]   columns=[price, qty, size] cta="Request Quote"
  Block 4: skus=[B,E,H]   columns=[price]            cta=none

Edge worker:
  1. Unique SKUs = [A, B, C, D, E, F, G, H]
  2. ONE call: POST /api/products { skus: [A,B,C,D,E,F,G,H] }
  3. JSON map returned for all 8 SKUs
  4. Block 1: pick A,B,C → render with [price, qty] + "Add to Cart"
     Block 2: pick D,E,F → render with [price, size] + "Add to Cart"
     Block 3: pick A,D,G → render with [price, qty, size] + "Request Quote"
     Block 4: pick B,E,H → render with [price] + no CTA
  5. Stitch all 4 blocks into page
```

**Key benefit**: The microservice returns universal product JSON. Each block applies its own column/CTA configuration independently. Adding more blocks to a page does not increase API calls.

---

## 12. Caching Strategy

### 12.1 Microservice-Level Cache

| Data | Cache TTL | Reasoning |
|------|-----------|-----------|
| **Auth token** | Until expiry (typically 1hr) | Token is reused across all requests |
| **Catalog data** (name, size, description) | 1 hour | Product metadata rarely changes |
| **Pricing data** (anonymous/list prices) | 15 minutes | Prices may update during promotions |
| **User type response** | Not cached | Depends on per-request login state |

### 12.2 Edge Worker-Level Cache (Optional KV Store)

```
Edge Worker KV Cache:
  Key: hash(sorted SKU list)
  Value: JSON response from microservice
  TTL: 5–10 minutes

  Cache HIT:  → return cached JSON (< 10ms)
  Cache MISS: → call microservice → store result → return

  With 5-min TTL and 580 requests/min average:
  API Mesh/Microservice calls reduced to ~12 per hour per SKU set
  Effective cache hit rate: 99%+
```

### 12.3 Cross-Page Cache Reuse

```
Page A loads: SKUs [1, 2, 3, 4, 5] → all cached
Page B loads: SKUs [3, 4, 5, 6, 7]
  SKUs 3, 4, 5 → cache HIT (no API call)
  SKUs 6, 7    → cache MISS → fetch from API

Popular SKUs get cached quickly. Most subsequent page loads
require zero or minimal backend API calls for catalog data.
```

---

## 13. Add to Cart — Client-Side Interaction

Add-to-cart is a user action, not an SEO concern. It remains fully client-side.

### 13.1 Flow

```
User sets quantity → clicks "Add to Cart"
      │
      ▼
Block JS: POST /api/cart/add  (via proxy if auth required)
      │   Body: { sku: "AMEP5015", quantity: 2 }
      │
      ▼
Update mini-cart UI (item count badge, confirmation toast)
```

### 13.2 Block JS Responsibilities (Client-Side Only)

| Feature | Implementation |
|---------|---------------|
| Quantity increment/decrement | Input field event handlers |
| Add to Cart click | POST to cart API (via proxy if needed) |
| Mini-cart update | DOM update after successful add |
| Logged-in price refresh | Fetch personalized pricing, update table |
| Mobile responsive table | CSS-driven collapse (no JS needed) |

---

## 14. Microservice Hosting Decision

### 14.1 Options Evaluated

| Option | Description | Fit for TFS |
|--------|-------------|-------------|
| **AEM Cloud Publish (Sling Servlet)** | Custom Java code on AEM publish tier | Not recommended. AEM is a content platform, not an API gateway. Adds latency (full AEM request pipeline), cost (AEM license per usage), and coupling (AEM downtime affects product API). |
| **Adobe API Mesh** | Adobe-hosted GraphQL gateway | Conditional. Works for simple API aggregation. Risk of rate limits and cold starts under high traffic. Suitable if traffic is low-to-medium and App Builder is licensed. |
| **Adobe I/O Runtime (App Builder)** | Serverless functions (OpenWhisk) | Conditional. Same cold start and rate limit concerns as API Mesh. Originally designed for integrations and event-driven workflows, not for serving live page traffic at scale. |
| **Dedicated lightweight service** | Node.js or Java on TFS's cloud provider (AWS/Azure/GCP) | **Recommended.** Always warm (no cold starts), full control over scaling, in-memory caching, connection pooling, predictable latency, independent of AEM deployments. |

### 14.2 Recommendation

Deploy the Product Microservice as a **dedicated lightweight service** on TFS's existing cloud infrastructure.

**Reasoning:**
- TFS already has cloud infrastructure (AWS, Azure, or GCP)
- Same infrastructure pattern as the header/footer microservice
- Always warm — no cold starts affecting user experience
- In-memory caching for auth tokens and catalog data
- Standard auto-scaling (Kubernetes HPA, Cloud Run, ECS)
- Independent deployment lifecycle — AEM updates do not affect product data
- Standard logging, monitoring, alerting, and debugging

**Deployment options** (choose based on TFS's existing cloud provider):
- **Google Cloud Run** — container-based, scales to zero, pay-per-use
- **AWS ECS / Lambda** — container or serverless, auto-scaling groups
- **Azure Container Apps / Functions** — similar to above

---

## 15. Request Context Flow: AEM vs EDS

A key concern: the current Sling model accesses user identity and locale from the HTTP request. In EDS, the edge worker receives the same browser request and must forward this context to the microservice.

### 15.1 Context Mapping

| Context | AEM Sling Model | Edge Worker | Microservice |
|---------|----------------|-------------|--------------|
| **URL path** (`/us/en/...`) | `request.getRequestURI()` | `new URL(request.url).pathname` | Received via `X-Country` / `X-Lang` headers |
| **Country** | Parsed from URL | Parsed from URL path segment | `X-Country` header |
| **Language** | Parsed from URL | Parsed from URL path segment | `X-Lang` header |
| **Login cookie** | `request.getCookies()` | `request.headers.get('Cookie')` | `X-Login-Token` header |
| **User session** | `resourceResolver.getUserID()` | N/A — no AEM session at edge | Relies on cookie/token, not AEM session |

### 15.2 Edge Worker Forwards Context

```javascript
// Edge worker extracts context from original browser request
const url = new URL(request.url);
const [, country, lang] = url.pathname.split('/');
const cookies = request.headers.get('Cookie');
const loginToken = parseCookie(cookies, 'login-token');

// Forwards to microservice via custom headers
const response = await fetch('https://product-service.tfs.com/api/products', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Country': country,
    'X-Lang': lang,
    'X-Login-Token': loginToken || '',
  },
  body: JSON.stringify({ skus: allSkus }),
});
```

### 15.3 Prerequisite: User Type API Must Accept Token-Based Auth

| Auth Method | Works with Edge Worker? |
|-------------|------------------------|
| Cookie-based (login-token, JWT) | Yes — edge worker forwards the cookie |
| SSO token (SAML, OAuth, Okta) | Yes — edge worker forwards the SSO cookie/token |
| AEM internal session (`JSESSIONID` + AEM repository) | No — no AEM session exists at the edge. The User Type API must be updated to accept token-based auth. |

**Discovery action**: Confirm how the User Type API identifies the user. If it relies on AEM's internal session rather than a cookie/token, it must be updated for EDS compatibility.

---

## 16. Migration Mapping

### 16.1 AEM Dialog → UE Field Mapping

| AEM Dialog Field | UE Component Model Field | Notes |
|-----------------|--------------------------|-------|
| SKU list (text area) | `skus` (text) | Same format — comma-separated |
| Show Price (checkbox) | `columns` (multiselect, includes "price") | Consolidated into column multiselect |
| Show Qty (checkbox) | `columns` (multiselect, includes "qty") | Consolidated into column multiselect |
| Show Size (checkbox) | `columns` (multiselect, includes "size") | Consolidated into column multiselect |
| CTA type (dropdown) | `ctaType` (select) | Same options: Add to Cart, Request Quote, None |

### 16.2 Server-Side Logic Migration

| AEM 6.4 | EDS |
|----------|-----|
| Sling Model Java class | Product Microservice (Node.js or Java) |
| `authTokenProvider.getToken()` | Microservice internal token management |
| `userTypeService.getType(request)` | Microservice calls User Type API with forwarded context |
| `catalogService.getProducts(skus)` | Microservice calls Catalog API |
| `pricingService.getPrices(skus, userType)` | Microservice calls Pricing API |
| JSP template rendering | Edge worker HTML rendering from JSON |
| Dispatcher cache | EDS CDN + edge worker KV cache |

---

## 17. Risk Matrix

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Existing APIs not CORS-enabled / not browser-accessible | High | High | Microservice acts as proxy — APIs are never called from browser. This risk is fully mitigated by the architecture. |
| 2 | User Type API relies on AEM internal session | High | Medium | Confirm in discovery. If so, API must be updated to accept token-based auth. |
| 3 | Edge worker execution time exceeds CDN limits | Medium | Low | Batching reduces to 1 microservice call. Microservice response caching further reduces latency. |
| 4 | Product data API latency impacts page load | High | Medium | Multi-layer caching (microservice: catalog 1hr + pricing 15min, edge worker KV: 5min). Most requests served from cache. |
| 5 | Auth token expiry during high traffic | Medium | Low | Microservice caches token with TTL buffer (refresh 5min before expiry). |
| 6 | Multiple blocks per page causes rendering delay | Medium | Low | Batching: all blocks share one microservice call. 4 blocks = same latency as 1 block. |
| 7 | Contract pricing inadvertently cached in CDN | High | Low | Server-side rendering uses anonymous pricing only. Personalized pricing applied client-side after page load. |
| 8 | Schema.org data missing for "Request A Quote" products | Low | Medium | Schema.org marks these with `availability: OutOfStock` or omits price field. Acceptable for SEO. |

---

## 18. Open Questions for Discovery

### Priority 1: Must Confirm Before Development

| # | Question | Impact |
|---|----------|--------|
| 1 | How does the User Type API identify the user — cookie/token or AEM internal session? | Determines if API works with edge worker context forwarding |
| 2 | Are the Catalog and Pricing APIs accessible outside of AEM's network? | Determines if microservice can call them from cloud infrastructure |
| 3 | What authentication does each API require — API key, OAuth token, or AEM session? | Determines microservice auth implementation |
| 4 | What is the expected traffic volume on product list pages (daily page views)? | Determines caching strategy aggressiveness and microservice scaling |
| 5 | Can the 3 API calls be consolidated into a single endpoint? | Could simplify the microservice significantly |

### Priority 2: Should Confirm Before Launch

| # | Question | Impact |
|---|----------|--------|
| 6 | What is the maximum number of SKUs per product-list block? | Determines API payload size limits |
| 7 | How frequently do catalog and pricing data change? | Determines cache TTLs |
| 8 | Is there a rate limit on the existing APIs? | Determines if microservice needs throttling |
| 9 | What cloud provider does TFS use (AWS, Azure, GCP)? | Determines microservice hosting platform |
| 10 | Is there an existing API gateway or service mesh? | Microservice could deploy behind it |

### Priority 3: Good to Know

| # | Question | Impact |
|---|----------|--------|
| 11 | Are there other components that call the same 3 APIs? (product detail, search) | Microservice could serve multiple block types |
| 12 | What commerce platform handles the cart backend? (Adobe Commerce, custom) | Determines add-to-cart integration approach |
| 13 | Are there regional pricing differences beyond user type? | May need additional context forwarding |

---

## 19. Summary of Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| **Rendering approach** | Server-side via edge worker + microservice | SEO-friendly, auth tokens secure, matches current AEM behavior |
| **Microservice response format** | JSON (not HTML) | Higher cache hit rate, reusable across components, author config changes don't require service redeployment |
| **Block count** | Single `product-list` block | One block handles all column/CTA combinations through author configuration |
| **Multiple blocks per page** | Batched into one microservice call | 4 blocks = same latency as 1 block |
| **Microservice hosting** | Dedicated lightweight service on TFS cloud | Always warm, no cold starts, full scaling control, independent of AEM |
| **Not AEM Cloud Publish** | Excluded | AEM is a content platform, not an API orchestration layer |
| **Not API Mesh / App Builder** | Excluded for live traffic | Cold start risk, rate limits, not designed for high-throughput page serving |
| **Anonymous pricing (server)** | Rendered server-side, cached | SEO-indexable, fast for anonymous visitors |
| **Personalized pricing (client)** | Applied client-side after page load | Secure, user-type-specific, not cached in CDN |
| **Caching** | Multi-layer: microservice (catalog 1hr, pricing 15min) + optional edge KV (5min) | 99%+ cache hit rate, minimal backend API calls |
| **Add to cart** | Client-side JS | User interaction, not SEO concern |
| **Architecture pattern** | Same as header/footer (edge worker stitching) | Consistency, reuse, one pattern for all server-rendered components |

---

*Related Documents:*
- [Header/Footer EDS Solution Design](./HEADER_FOOTER_EDS_SOLUTION_DESIGN.md)
- [Video Brightcove EDS Solution Design](./VIDEO_BRIGHTCOVE_EDS_SOLUTION_DESIGN.md)
