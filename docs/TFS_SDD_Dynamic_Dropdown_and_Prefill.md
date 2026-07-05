# Dynamic Dropdowns & Prefill

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services (Universal Editor / xWalk)

> **Companion to** the Form Authoring solution design. This page covers two runtime form behaviours: **dynamic dropdowns** (including cascading Country → State options) and **prefill** (populating fields from user profile and other sources). Both are **client-side runtime behaviours** in EDS — there is no server-side rendering of form fields.

---

# Part 1 — Dynamic Dropdowns

## 1. Current State (AEM 6.4)

- TFS maintains lookup data (Country, State, Sales Region, and similar reference data) in **Content Fragments (CF)**, which are the source of truth and support structured authoring and localization.
- Dynamic dropdowns are driven by a **CF Root Path** configured per component in the dialog. A custom **Sling servlet reads the CF folder server-side**, applies filters (e.g. **region** and **locale** variation), and **pre-renders all `<option>` tags into the page HTML** at request time.
- **No client-side call** is made in the current model — options arrive fully rendered in the HTML.
- Dropdown values support a **value format** distinction (e.g. **ISO** vs **Siebel**).
- The AEM publish host, CF paths, and GraphQL schema are internal server concerns — never visible to the browser.

## 2. EDS Approach

In EDS there is **no server-side rendering** of form fields — the form block builds the form HTML in the browser. Consequently:

- Dropdown data must be obtained at runtime via **HTTP/JSON calls** (or static JSON assets).
- **Content Fragments remain the source of truth** for shared, structured, localized reference data.
- The form block fetches the options as JSON at runtime and populates the `<select>` elements.

Two delivery patterns are available (Section 3). **Language/locale** is derived from the page (URL path) and passed to the query; where no CF variation exists for the requested language, the response falls back to the **master** CF variation.

## 3. Delivery Options (GraphQL)

### 3.1 Option 1 — Direct GraphQL / CF API from the browser

The form block JS calls the **AEM publish GraphQL / CF Delivery API directly** from the browser.

- GraphQL on publish is configured for **read-only anonymous** access; **CORS** is configured to allow EDS origins.
- On load (or when needed), the block calls a GraphQL/CF endpoint (e.g. a persisted query for countries), receives JSON, maps results to `{ label, value }`, and populates the `<select>`.

| Pros | Cons |
|---|---|
| Simpler architecture — no extra backend layer | AEM publish host + GraphQL endpoint visible in browser network calls |
| Fewer components to deploy/monitor | GraphQL query/schema visible in dev tools; CORS on AEM publish must be carefully managed |

### 3.2 Option 2 — App Builder wrapper (recommended)

The form block calls a **server-side App Builder action** (Adobe I/O Runtime), which calls the AEM publish GraphQL / CF API **server-to-server**, normalizes the data, and returns a stable, front-end-friendly JSON payload.

- The block calls a simple options endpoint (e.g. `type=countries&locale=en`); the action runs the appropriate query (Countries, StatesByCountry, etc.) and returns a clean `{ label, value }` array.

| Pros | Cons |
|---|---|
| Browser never sees the AEM publish host or GraphQL query/schema | Additional component (App Builder) to build, deploy, operate |
| Central place to normalize data and apply locale/business logic | Slightly more deployment-pipeline complexity |
| CF model/query changes are isolated from the front end | |

### 3.3 Comparison

| Aspect | Direct GraphQL from browser | App Builder wrapper (recommended) |
|---|---|---|
| AEM publish host | Visible in browser requests | Hidden (only App Builder sees it) |
| GraphQL query/schema | Visible in network/debug tools | Hidden; front end sees a simple options API |
| CORS on AEM | Required for EDS domains | Required only for App Builder ↔ AEM |
| Data normalization | In browser JS | In App Builder (front end receives `{label,value}`) |
| Security surface | AEM publish exposed to all users | AEM publish exposed only to App Builder |
| Change impact | CF/schema changes affect JS | CF/schema changes mostly isolated in App Builder |

## 4. Cascading Options (Country → State)

TFS forms include **dependent (cascading) dropdowns**, e.g. Country → State.

- **Current (AEM):** a servlet is called **once on page load**, builds a complete **parent-to-children map** across all countries and their states, and returns it in a **single response**; the JS caches it in memory. Every subsequent user selection is a **pure in-memory lookup** — **no additional network call per selection**.
- **EDS target:** the same pattern is retained. On form build, the block requests the cascading dataset (via the chosen delivery option in Section 3), which returns the **parent → children map** in one call. The block caches it in memory; each parent selection updates the child `<select>` from the cached map — no per-selection network call.
- **Region / format:** the request carries **region** (e.g. Global / NA / EMEA / IPAC / JP / LATAM) and **value format** (ISO / Siebel) so the returned options match the form's configuration.
- **No-match handling:** when a selected parent value has no corresponding children, the child dropdown is **disabled** and a **configurable no-match message** is displayed.

> **Open item:** the complete set of CF sources used for dynamic dropdowns across all TFS forms (beyond Countries, States, Sales Regions) must be confirmed, since option data is resolved from known CF datasets rather than an author-entered CF path.

---

# Part 2 — Prefill

## 5. Overview

Prefill populates form fields for known/logged-in users to reduce manual input. It is a **client-side runtime behaviour**: EDS pages render forms **without** user data, and prefill runs as a **network call after page load**. **No user-specific data** is present in the EDS HTML or CDN cache, and all sensitive logic remains **server-side**.

## 6. Current State (AEM 6.4) — Data Sources

Prefill today draws from **four** data sources, plus a telephone-country default:

| # | Source | Trigger | Fields |
|---|---|---|---|
| 1 | **User profile** (external Consumer / Catalog Detail Service) | `window._lt` user session available | Visible profile fields |
| 2 | **URL query parameters** (visible fields) | Page load | Visible fields (`data-qparam`) |
| 3 | **Cookies / `digitalData` / URL params** (hidden fields) | Page load | Hidden fields (`data-cookiename`, `data-digitaldata`, `data-qparam`) |
| 4 | **JS window objects** (hidden fields) | Page load | Hidden fields (`data-jsparamname`, dot-notation path) |
| — | **Telephone country default** | Server render | Telephone field (from `CK_ISO_CODE` cookie) |

**User-profile source detail (Source 1):**
- The front end **polls `window._lt`** (the ThermoFisher identity layer) for the user session (≈300 ms interval, up to a ~15-second timeout), then reads `userId` / `erpType`.
- It calls an **AEM servlet** (`/bin/servlet/tf/form/userdetails…`), which:
  - Resolves user identity (request params, or a **JWT cookie fallback** — AES-256-CBC decryption of `store_jwt_persistence`, extracting `uId`, `unm`, `erp`),
  - Calls the external **Consumer API** using **OAuth** (client credentials; token cached ~55 minutes),
  - Locates the default account and **enriches** ISO country/state codes with human-readable names via **Content Fragments**,
  - Returns a **flat JSON profile**.
- The front end maps response fields to form fields using **normalized keys and an alias map** (to handle naming mismatches, e.g. `firstname` → `firstName`), sets values, and triggers change events.

## 7. EDS Target Approach

- **Prefill orchestration is consolidated in the form block JS** (the separate current-state handler scripts are not ported — their logic is absorbed into the block).
- Prefill remains a **front-end network call after page load**; EDS introduces **no user-specific rendering** at fetch time.
- **Sources 2, 3, 4 and the telephone default are handled entirely client-side** in the form block at build time:
  - URL query params (`data-qparam`) — visible and hidden fields,
  - Cookies (`data-cookiename`), `window.digitalData` (`data-digitaldata`), URL params — hidden fields,
  - JS window objects (`data-jsparamname`) — hidden fields,
  - **Telephone country default** — the block reads the `CK_ISO_CODE` cookie **client-side** and sets the default country during form build (removing the server-render dependency).
- **`window._lt` polling is unchanged** — it is a ThermoFisher identity layer, not AEM-specific, and will be present on EDS pages.
- The **user-profile source (Source 1)** requires a **server-side backend** (OAuth credentials and JWT decryption must not be exposed client-side). Options are in Section 8.

## 8. Backend Options for the User-Profile Source

The user-profile call needs a server-side component to hold OAuth credentials, perform the JWT-cookie fallback, and enrich country/state names. Three options exist, to be **decided at implementation** on development effort, feasibility, and architectural fit:

### Option 1 — Reuse the existing servlet (migrate to Cloud)
- Migrate the existing user-details servlet logic to **AEM as a Cloud Service**; the front end (or a proxy) calls it.
- **Pros:** minimal change; reuses proven logic completely.
- **Cons:** exposes the AEM host/servlet path (unless proxied); requires cross-domain CORS/auth handling; **reintroduces a runtime delivery-path dependency on AEM/Cloud** (latency, availability).

### Option 2 — Edge Worker / App Builder as a proxy to the servlet
- The front end calls an **Edge Worker / App Builder** action, which forwards to the (Cloud-migrated) servlet and returns the response.
- **Pros:** hides the AEM host from the browser; no re-implementation of logic; good transitional approach.
- **Cons:** still dependent on the servlet; adds a network hop; requires connectivity between the Edge Worker / App Builder and AEM.

### Option 3 — Rebuild the logic in Edge Worker / App Builder (target state)
- The front end calls an **Edge Worker / App Builder** User-Details action that reimplements the behaviour: identity resolution (JWT / params), OAuth call to the Consumer API, and country/state enrichment (using the same reference data as the dropdowns), returning the same flat JSON structure.
- **Pros:** clean, cloud-native; removes the dependency on the AEM servlet; **keeps the call off the AEM delivery path**; easier to evolve independently.
- **Cons:** requires full re-implementation; needs validation for parity with existing behaviour.

> **Architectural note.** Options 1 and 2 keep TFS dependent on the AEM servlet (Option 1 places an AEM/Cloud dependency directly on the delivery path). Option 3 aligns best with the edge-first EDS model. The trade-off is **reuse/effort vs architectural fit**, resolved at implementation.
>
> **Alternative.** If a different middleware or profile service can expose a **secure User-Details endpoint** returning the required JSON, the EDS front end can call it directly, and a new Edge Worker / App Builder action may not be needed.

## 9. EDS Prefill — Data Source Summary

| Source | Trigger | Handler | Fields |
|---|---|---|---|
| User profile (Consumer / Catalog Detail Service) | `window._lt` available | Form block → **Edge Worker / App Builder** (Section 8) | Visible profile fields |
| URL query params | Form build | Form block (`data-qparam`) | Visible + hidden |
| Cookies | Form build | Form block (`data-cookiename`) | Hidden |
| `window.digitalData` | Form build | Form block (`data-digitaldata`) | Hidden |
| JS window objects | Form build | Form block (`data-jsparamname`) | Hidden |
| Telephone country default | Form build | Form block (`CK_ISO_CODE` cookie) | Telephone field |

---

## 10. Open Items & Dependencies

- **CF sources for dropdowns** — confirm the full set of Content Fragment datasets used across TFS forms (beyond Countries, States, Sales Regions), since options resolve from known datasets, not an author-entered CF path.
- **User-profile backend option** — Option 1 / 2 / 3 (Section 8), decided at implementation.
- **Consumer / Catalog Detail Service** — API details, **OAuth credentials**, and **JWT configuration** (`store_jwt_persistence` decryption) to be provided by the **TFS Backend team** prior to implementation.
- **CORS / network reachability** — for the chosen delivery/backend option (browser ↔ AEM/GraphQL, or Edge Worker / App Builder ↔ AEM / Consumer API).

## 11. Related Pages

- Form Authoring in EDS *(block model, action types, authoring-time integrations)*
- Forms — Current State & EDS Migration Overview
- Submission & middleware integration
