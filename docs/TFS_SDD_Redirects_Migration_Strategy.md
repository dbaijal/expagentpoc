# Migration Strategy — Redirects

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Migration strategy for TFS redirects

## Overview

This document defines the migration strategy for TFS redirects from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS). It covers how redirects work in EDS, the types of redirects that exist in AEM today, how they are migrated, and who owns what in the process.

The TFS public URL structure is **preserved** in the migration, so redirects are migrated as a like-for-like carry-over of existing rules — re-homed into the appropriate target mechanism (the EDS redirects sheet or the CDN), rather than re-pointed to new URLs.

**EDS Official Reference:** https://www.aem.live/docs/redirects

---

## 1. How Redirects Work in EDS

In EDS, simple redirects are managed through a **redirects spreadsheet** maintained as content and published through the Edge Delivery pipeline. There are no server-side redirect OSGi services and no Dispatcher rules for these redirects — all simple 1:1 redirects are managed in one place and published like any other content.

### Spreadsheet Format

The redirects data has exactly two columns:

| Source | Destination |
|---|---|
| `/old-page-path` | `/new-page-path` |
| `/another-old-path` | `https://external-site.com/page` |

- **Source** — relative path from the domain root (e.g. `/us/en/old-page`).
- **Destination** — a relative path for internal redirects, or a fully qualified URL for external redirects.
- The redirects data is published (as the JSON the pipeline reads) and is referenced by the project's redirects configuration. The exact sheet/configuration is set per the EDS redirects documentation.

### Key Behaviour

- The redirects mechanism is designed primarily for **permanent (HTTP 301)** redirects. The redirect type can be specified where a non-permanent (302) redirect is intentionally required.
- Redirects **take precedence over existing content** — if a redirect is configured for a path, it is served even if a published page exists at that path.
- Redirects can be **previewed on the `.page` environment** before being published live.
- **No code or configuration deployment is required** — the redirects data is authored in AEM (Universal Editor) and published through EDS.
- The redirects spreadsheet is the **single source of truth** for all simple redirects in EDS.

### Wildcard and Pattern-Based Redirects

Pattern-based and wildcard redirects are **not supported** by the EDS redirects spreadsheet. These are handled at the **CDN level (Akamai for TFS)**. CDN wildcard syntax captures a URL segment (e.g. `/old-path/*` → `/new-path/$1`) — something the 1:1 spreadsheet cannot express.

---

## 2. Types of Redirects in AEM Today

TFS currently manages redirects through three mechanisms in AEM:

### 2.1 Page-Level Redirects (Page Properties)

Authors configure a redirect directly on a page via the **Advanced tab** of page properties (`cq:redirectTarget`). Accessing that page's URL redirects the visitor to the configured destination.

### 2.2 Vanity Paths

Authors assign short or alternative URLs to a page via page properties (`sling:vanityPath`). A visitor accessing the vanity URL is served that page's content.

### 2.3 Dispatcher / CDN Pattern Redirects

Pattern-based and wildcard redirects are configured at the **Dispatcher and CDN (Akamai)** level — outside the AEM content tree. These cover bulk URL patterns, domain-level redirects, and path-based rewrite rules that cannot be expressed as simple 1:1 redirects.

---

## 3. Migration Approach

### Step 1 — TFS Provides Complete Redirect Inventory

TFS compiles and provides the complete list of all active redirects across all three mechanisms. This is a **TFS-owned activity**. The inventory must cover:

- All page-level redirects (`cq:redirectTarget`).
- All vanity paths (`sling:vanityPath`).
- All Dispatcher and CDN pattern-based redirects.

For each redirect, the inventory should capture **source, destination, and redirect type (301/302)**.

### Step 2 — Consolidate and De-duplicate

Because redirects exist across three mechanisms, the inventory is consolidated into a single working set:

- **De-duplicate** — the same source may appear in more than one mechanism; keep one authoritative entry.
- **Resolve conflicts** — where two sources give different destinations for the same source path, the correct destination is confirmed with TFS.
- **Collapse chains** — where a redirect points to a path that itself redirects (A → B → C), collapse to a direct redirect (A → C).
- **Remove loops** — detect and eliminate any circular redirects.

### Step 3 — Categorise Redirects

The consolidated set is split into two categories:

| Category | Criteria | Target in EDS |
|---|---|---|
| **Simple 1:1 redirects** | Single source path → single destination path | EDS redirects spreadsheet |
| **Pattern / wildcard redirects** | URL patterns, wildcards, bulk path rewrites, domain-level rules | Akamai CDN configuration |

Page-level redirects and vanity paths are typically **simple 1:1** and map to the EDS spreadsheet. Dispatcher/CDN pattern rules are **pattern-based** and remain at the CDN.

### Step 4 — Simple Redirects into the EDS Redirects Spreadsheet

Simple 1:1 redirects are formatted into the **Source / Destination** columns of the EDS redirects spreadsheet, authored in AEM and published through EDS. Adobe supports the setup and publishing of the redirects data. Because the public URL structure is preserved, source and destination paths carry over directly.

### Step 5 — Pattern Redirects to Akamai

Wildcard and pattern-based redirects are passed to the TFS Akamai infrastructure team for CDN-level configuration. These are outside the EDS spreadsheet mechanism.

### Step 6 — Validation

After the redirects are published, validation confirms:

- **Every destination resolves** — no redirect points to a non-existent/404 target in the target environment.
- **No loops or unintended chains** remain.
- **Redirect type preserved** — 301/302 behaves as intended.
- **Key paths spot-checked** — high-traffic and SEO-critical redirects are verified to resolve correctly on the `.page` (preview) environment before go-live, and on live after cutover.

---

## 4. Ownership

| Activity | Owner |
|---|---|
| Compile complete redirect inventory (all types, with type 301/302) | **TFS** |
| Confirm which redirects are active and in scope | **TFS** |
| Consolidate, de-duplicate, resolve conflicts, collapse chains | **TFS + Adobe** |
| Categorise redirects (simple vs pattern) | **TFS + Adobe** |
| Set up and publish the redirects spreadsheet in EDS | **Adobe** |
| Configure wildcard / pattern redirects in Akamai | **TFS Infrastructure / Akamai team** |
| Validate redirects (preview and post go-live) | **TFS** |

---

## 5. Open Items

| Item | Owner | Status |
|---|---|---|
| Complete redirect inventory provided (all three mechanisms, with type) | TFS | Open |
| Total volume of redirects (informs whether the sheet needs splitting / stale-redirect pruning) | TFS | Open |
| Akamai team engaged for CDN pattern redirects | TFS Infrastructure | Open |
| Conflict/precedence rule confirmed where the same source appears in multiple mechanisms | TFS + Adobe | Open |
