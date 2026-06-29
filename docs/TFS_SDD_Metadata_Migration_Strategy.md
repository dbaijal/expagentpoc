# Migration Strategy — Metadata

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Migration strategy for page metadata

## Overview

This document defines the migration strategy for page metadata from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS). It covers how metadata works in the target environment, what metadata exists on TFS pages today, how it is extracted and migrated, and who owns what.

**EDS Official References:**
- https://www.aem.live/developer/block-collection/metadata
- https://www.aem.live/docs/metadata
- https://www.aem.live/docs/bulk-metadata

---

## 1. How Metadata Works in the Target Environment

Metadata is managed at two levels — **per-page** and **bulk (site/section-wide)**. Both produce the same HTML `<head>` tags when the page is served by EDS; they differ in scope and where they are maintained.

### 1.1 Per-Page Metadata — Authored as Page Properties

In the AEM-as-authoring-source model, per-page metadata is authored as **page properties** in AEM (via the Universal Editor Page Properties), not as a table inside the document body. The set of metadata fields a page can carry — title, description, canonical, Open Graph fields, robots, keywords, and any custom properties — is defined **once** in the page-metadata component model (`component-models.json`). Authors (or the migration) populate the **values** of these fields per page, and EDS emits the corresponding `<head>` tags when the page is served.

There are therefore two distinct concerns:

| Concern | What it is | When/who |
|---|---|---|
| **Field definition** | The metadata fields a page can hold (title, description, canonical, og:*, robots, etc.), defined in the page-metadata model (`component-models.json`) | One-time developer setup, before migration |
| **Per-page values** | The actual value of each field for a given page | Written per page (by the migration, or by authors thereafter) |

> An **authored** metadata value can only be migrated onto a page if a **field for it exists in the page-metadata model**. The set of properties TFS confirms as in-scope must therefore be reflected in the model as a prerequisite to migration. Note that not all metadata is authored — some properties are **derived** by the project rather than stored as page-property fields (see Section 3.3).

### 1.2 Bulk Metadata — Site / Section-Wide Defaults

Bulk metadata is managed through a single **`metadata.xlsx`** spreadsheet at the project root. The first column is a URL (which supports **wildcard patterns** to match whole sections of the site), and subsequent columns are property names. EDS evaluates rows top-to-bottom and the **first matching URL pattern wins**.

Bulk metadata is used for **site-wide or section-wide** values — properties that apply consistently across a path pattern and do not need to be set on every individual page (e.g. a common robots setting for a section, a shared `og:type`, third-party site-verification tags).

### 1.3 Precedence

**Per-page metadata always takes precedence over bulk metadata** when the same property is defined in both places.

---

## 2. What Metadata Exists on TFS Pages Today

TFS pages currently serve metadata as HTML `<head>` tags rendered by AEM. Based on observation of live TFS pages, the following types of metadata are present today:

| Category | Sample Tags Observed |
|---|---|
| Page identity | Page title, meta description |
| SEO | Robots directives, canonical URL |
| Open Graph | `og:title`, `og:description`, `og:image`, `og:type`, `og:locale`, `og:url` |
| Site verification | Third-party site-verification tags |
| Other | `DC.title`, additional custom meta tags |

> **Sample reference:** `https://www.thermofisher.com/in/en/home.html`

This is an observation of what currently exists — **not** a confirmed migration scope. **TFS must confirm which metadata properties are carried forward.** Only confirmed properties are migrated; the corresponding fields must exist in the page-metadata model (Section 1.1).

### 2.1 Property Names

EDS recognises standard property names directly — standard properties (`title`, `description`, `robots`, `canonical`, `image`), Open Graph properties using the `og:` prefix, and project-defined custom properties. **Non-standard / custom tags** (e.g. `DC.title`) must be confirmed and explicitly modelled so they are emitted as intended; they are not produced automatically.

---

## 3. Special Handling — URL-Bearing Metadata

Some metadata values contain a URL and **must not be carried over verbatim** from the scraped source, because they would otherwise reference the old URL/host.

### 3.1 Canonical URL

By default, EDS can derive a page's canonical from its own path. The migration must therefore decide, per TFS's SEO requirement, one of:

- **Let EDS derive the canonical** from the page's own (new) URL — preferred where the canonical should simply be self-referential; or
- **Carry a specific canonical value** where the page intentionally points its canonical elsewhere — in which case the value must be **re-pointed to the correct target URL**, not the old absolute URL/host.

In all cases, the migrated canonical must resolve to the **correct URL in the target**, never a stale source URL.

### 3.2 `og:url` and Other URL-Bearing Tags

`og:url` (and any other URL-bearing metadata) must resolve to the **page's own URL in the target**, not the scraped source URL. These are handled the same way as canonical — derived from the page's own URL rather than copied verbatim.

> The TFS public URL structure is preserved in the migration, which simplifies this — but URL-bearing metadata must still be validated to ensure it reflects the page's own target URL rather than a carried-over absolute source value.

### 3.3 Derived vs Authored Metadata (Recommended Practice)

Not every metadata property needs to be an authored page-property field. As a general best practice, metadata splits into two groups — the exact split is an **implementation decision** confirmed during build and may be adjusted:

| Recommended as **derived** (computed by the project, not authored per page) | Recommended as **authored** (per-page field, set by author / migration) |
|---|---|
| `canonical` — derived from the page's own URL / path by default | `title` |
| `og:url` — derived from the page's own URL | `description` |
| `og:locale` — derived from the page's locale / path | `og:title`, `og:description`, `og:image` (where they differ from defaults) |
| `og:type` — typically a constant or template-driven default | `robots` / `noindex` (where a page overrides the default) |
| Site-verification and other site-wide tags — via bulk metadata / project head | `keywords` and any page-specific custom properties |

**Rationale:**
- **Derived** properties are URL-, locale-, or template-driven — computing them avoids carrying stale source values, keeps them automatically correct as content moves, and removes author burden. Canonical and `og:url` in particular should be derived so they always reflect the page's own target URL (Sections 3.1–3.2).
- **Authored** properties are genuinely page-specific editorial values and are migrated into page properties so authors can manage them.

The precise list of derived vs authored properties is finalised during implementation against the confirmed in-scope set (Section 2) and TFS's SEO requirements.

---

## 4. Migration Approach

### 4.1 Source — Page HTML Scraping

Metadata is extracted directly from the `<head>` section of the scraped page HTML. The rendered HTML served by AEM already contains all metadata tags exactly as they appear to users and search engines today, so the scraped `<head>` is the source of truth. No separate JCR lookup is required for metadata.

This keeps metadata migration consistent with the rest of page migration and ensures what is migrated reflects what is actually served on the live site.

### 4.2 How It Works

When the migration scrapes a page, it reads the `<head>` alongside the page body. For each **confirmed** metadata property, the handling follows the derived-vs-authored split (Section 3.3):

- **Authored, page-specific values** (e.g. title, description, page-specific og/robots) are written into that page's **page properties** (the fields defined in the page-metadata model), so EDS emits them as `<head>` tags.
- **Derived values** (`canonical`, `og:url`, `og:locale`, etc.) are **computed by the project** from the page's own URL / locale / template rather than copied from the scraped source — ensuring they reflect the page's own target URL (Section 3).
- **Site-wide / repeated values** are not written per page — they are handled by bulk metadata (Section 4.3).

The page and its metadata are created in the target AEM authoring instance together — metadata migration is **not** a separate workstream.

### 4.3 Bulk Metadata — Implementation Efficiency

Where the same metadata value applies consistently across many pages — e.g. a common robots setting across a section, a shared `og:type`, or site-verification tags — it is configured in **bulk `metadata.xlsx`** rather than written into every page. Site-verification and other site-wide tags belong here (or in the project head configuration) rather than per page.

Page-level values are migrated per page; bulk metadata handles consistent values at scale. This keeps the migration clean and makes future site-wide changes easier to manage.

---

## 5. Validation

Validation confirms that the migrated pages emit the **confirmed metadata correctly**.

| Check | Detail |
|---|---|
| **`<head>` parity** | The metadata tags emitted by a migrated page match the **confirmed** source set (title, description, robots, og:*, custom). Spot-checked on the `.page` (preview) environment and on live after cutover. |
| **Canonical correctness** | The canonical resolves to the correct target URL (self-referential where intended), with no stale source URL/host carried over. |
| **`og:url` / URL-bearing tags** | Resolve to the page's own target URL. |
| **Custom tags** | Non-standard tags (e.g. `DC.title`) are emitted as intended. |
| **Bulk vs page precedence** | Where a property is set both per page and in bulk, the per-page value wins as expected. |

---

## 6. Ownership

| Activity | Owner |
|---|---|
| Define which metadata properties are in scope for migration | **TFS** |
| Define the confirmed metadata fields in the page-metadata model (`component-models.json`) | **Adobe** |
| Extract metadata from scraped HTML and write per-page values to page properties | **Adobe** |
| Handle URL-bearing metadata (canonical, og:url) per the SEO requirement | **Adobe + TFS** |
| Identify bulk metadata patterns and configure `metadata.xlsx` | **Adobe** |
| Validate metadata on migrated pages (preview and post go-live) | **TFS** |

---

## 7. Open Items

| Item | Owner | Status |
|---|---|---|
| Confirm which metadata properties are in scope for migration | TFS | Open |
| Confirm canonical strategy (EDS-derived vs explicit value) | TFS + Adobe | Open |
| Confirm handling of custom / non-standard tags (e.g. `DC.title`) | TFS + Adobe | Open |
| Identify site-wide metadata suitable for bulk `metadata.xlsx` | Adobe | Open |
