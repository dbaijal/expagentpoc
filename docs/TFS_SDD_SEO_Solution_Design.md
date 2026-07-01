# SEO — Solution Design

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## 1. Introduction

This document defines the SEO solution design for the TFS content websites built on Adobe Edge Delivery Services (EDS), with **AEM as the authoring source** and EDS as the delivery layer. It serves as the reference architecture for the development, content authoring, and SEO teams to align on how SEO requirements are met across the platform.

**Purpose:** to document the **technical approach, configuration patterns, authoring guidelines, and implementation mechanism** for each SEO capability — i.e. *how* each SEO requirement is delivered on EDS, not only that it is supported.

**Scope:**

- Page metadata — Title, Description, Open Graph
- Canonical tags
- XML sitemaps
- Image optimisation
- Structured data (JSON-LD)
- Favicon
- noindex meta tag capability
- robots.txt support

> Detailed field-level mapping, the structured-data inventory, and the locale/hreflang model are finalised during implementation once the final block design, template inventory, and content model are confirmed. This document describes the mechanism and configuration approach; it does not guarantee specific search rankings or outcomes, which depend on content and external factors outside the platform.

---

## 2. Page Metadata — Title, Description & Open Graph

### 2.1 Native EDS Metadata Mappings

EDS natively maps metadata properties to `<head>` tags, with built-in fallbacks when a value is not provided:

| Metadata Property | Rendered HTML Tags | Fallback |
|---|---|---|
| `title` | `<title>`, `og:title`, `twitter:title` | First `<h1>` on the page |
| `title:suffix` | Appended to `<title>` with a space | — |
| `description` | `<meta name="description">`, `og:description`, `twitter:description` | First paragraph ≥ 10 words |
| `image` | `og:image`, `og:image:secure_url`, `twitter:image` | First page image → `/default-meta-image.png` |
| `canonical` | `<link rel="canonical">`, `og:url`, `twitter:url` | Auto-generated from the production host |

These mappings are provided by the EDS pipeline; no custom code is required for the standard tags. The fallbacks mean a page always emits sensible metadata even where an author leaves a field empty.

### 2.2 How Metadata Is Authored

Metadata is set at two levels:

- **Per-page metadata** — authored as **page metadata** in AEM (the page-metadata model, set in Page Properties in the Universal Editor). EDS renders these values as the corresponding `<head>` tags for that page.
- **Bulk metadata** — managed in a **`metadata` sheet (`metadata.xlsx`)** at the site root. Each row's **URL column** targets pages by pattern (the wildcard `*` may be used as prefix/suffix, e.g. `/us/en/**`); each subsequent column is a metadata property (e.g. `robots`, `title:suffix`, `og:type`). The sheet is evaluated **top-to-bottom**, so site-wide entries (`**`) are placed before more specific ones. Property names are lower-cased in the HTML.
- **Precedence:** **page-level metadata takes precedence over bulk metadata.** (The full hierarchy is: page-level → folder-mapped → bulk metadata sheet, in configured order.)
- The metadata sheet must be **previewed and published** for changes to take effect.

This gives site-wide/section defaults via the bulk sheet, with per-page override where authors provide specific values.

### 2.3 Title Suffix (Brand Consistency)

A global **`title:suffix`** (for example `| Thermo Fisher Scientific`) is applied via the bulk metadata sheet and auto-appended to the page `<title>`. Authors set only the page-specific title; the brand suffix is applied consistently without per-page effort.

Example output: `Environmental Analysis Solutions | Thermo Fisher Scientific`

### 2.4 Migration of Metadata

At migration, the mandatory SEO fields — `title`, `description`, `og:*`, `canonical`, `robots` (incl. `noindex`) — are carried over as the page's metadata so migrated pages emit equivalent `<head>` tags. (See the Metadata migration strategy for extraction detail.)

> Reference: [Bulk Metadata — aem.live](https://www.aem.live/docs/bulk-metadata)

---

## 3. Canonical Tags

- EDS **auto-generates the canonical** from the production host (`cdn.prod.host`) as a **self-referencing** canonical — no author action required for the standard case.
- Authors **override** only for non-standard cases (e.g. a variant/filtered URL pointing to a base page) by setting the `canonical` value in the page Metadata.
- The canonical also feeds `og:url` / `twitter:url` (Section 2.1). Canonical values reflect the page's own target URL rather than a value copied from the old site.

---

## 4. URL Structure

The existing public URL structure — `/{country}/{language}/...` (e.g. `/us/en/...`, `/de/de/...`) — is **preserved** in the migration.

- Existing indexed URLs and inbound links remain valid, which **reduces SEO risk at cutover** (no mass URL change, no loss of accumulated ranking signals for existing paths).
- URLs remain **clean and descriptive**, with no query parameters used for content addressing.
- Because URLs are preserved, redirect migration is largely a like-for-like carry-over rather than a re-pointing exercise (Section 5).

---

## 5. Redirects

Redirects are handled through the EDS delivery layer, split by type:

- **Simple 1:1 redirects** — managed in the EDS **redirects mechanism** (a `redirects` sheet published to `redirects.json`), with `Source` and `Destination` columns, serving **301** redirects. Redirects take precedence over content at the same path.
- **Pattern / wildcard redirects** — handled at the **CDN (Akamai)**, as the EDS redirects sheet is 1:1 and does not support wildcard patterns.
- Existing redirects (page-level, vanity paths, dispatcher/CDN rules) are consolidated, de-duplicated, and loaded into the appropriate mechanism.

Validation ensures every destination resolves (no redirect-to-404) and no redirect loops/chains remain. (See the Redirects migration strategy for detail.)

---

## 6. XML Sitemaps

Sitemaps are configured via **`helix-sitemap.yaml`** at the project root. For a multi-locale site (~45 locales), the `languages` configuration is used to produce **per-locale sitemaps** with hreflang, referenced by a **sitemap index**.

Example configuration pattern:

```yaml
sitemaps:
  tfs:
    languages:
      en-us:
        source: /us/en/query-index.json
        destination: /us/en/sitemap.xml
        hreflang: en-us
      de-de:
        source: /de/de/query-index.json
        destination: /de/de/sitemap.xml
        hreflang: de-de
        alternate: /de/de/{path}
      # ... one entry per locale
```

- A **`sitemap-index.xml`** file in the project references all per-locale sitemaps; it is referenced in `robots.txt` and submitted to Google Search Console and Bing Webmaster Tools.
- **noindex exclusion:** when using a manually configured index and sitemap, the **`helix-query.yaml` index definition must include the `robots` property** so that pages with `robots: noindex` are **automatically excluded from all sitemaps**. This ties noindex and sitemap exclusion together through the indexing stage — a page marked noindex is omitted from generation, not just from crawler indexing.
- Sitemaps update as content is published; coverage and exclusions are validated before launch.

> Reference: [Sitemap — aem.live](https://www.aem.live/developer/sitemap)

---

## 7. Image Optimisation

Images are selected from AEM and delivered via the EDS media pipeline. EDS serves **optimised images via CDN transformation** — the pipeline renders responsive `<picture>`/`srcset` markup and transforms images at the edge (format, width, and compression), for example:

```
./media_{hash}.jpg?width=750&format=webp&optimize=medium
```

- **Modern formats and compression** (e.g. WebP) and **responsive sizing** are applied automatically at the edge, supporting Core Web Vitals.
- **Alt text** is an authoring field with guidance so images carry descriptive alternative text.
- **Descriptive file names** are recommended via authoring guidance.

---

## 8. Structured Data (JSON-LD)

Structured data is implemented as **JSON-LD**, generated by different mechanisms depending on where the source data lives:

- **Authored content components (text/content in HTML)** — the **block's JavaScript decorator** (`blocks/<name>/<name>.js`) reads the rendered DOM and generates the JSON-LD from the block's content (for example, Breadcrumb → `BreadcrumbList`, FAQ/Accordion → `FAQPage`). The `<script type="application/ld+json">` is injected into the page **client-side by the block decorator JS** during decoration.
- **Data-driven components (e.g. Product List, Video)** — where the schema depends on data not fully present in the authored HTML, the structured data is **injected into the HTML via an Edge Worker** at the delivery layer. This places the JSON-LD in the server-delivered HTML, so it is present for crawlers **without relying on client-side JavaScript generation** — the more robust route where the data supports it.
- **Global structured data** (e.g. `Organization`, `WebSite` + `SearchAction`) — emitted from the global head (`head.html`) so it is present in the initial HTML site-wide.

> **Note on placement.** Block-generated JSON-LD is added **client-side by the block's JS decorator**; major search engines execute JavaScript and read it, but in-HTML structured data (global head or Edge Worker) is inherently more robust. The mechanism per schema type is confirmed during implementation.

**Authoring structured data via the Metadata block.** Where structured data (or a value feeding it) should be authored per page, authors can use the **`json-ld` property in the page Metadata block** — EDS renders a `json-ld` metadata value into a `<script type="application/ld+json">` tag in the `<head>`. This lets specific pages carry authored structured data directly, without a code change.

**Candidate schema by component (initial inventory — confirmed during implementation):**

| Component | Schema Type | Generation |
|---|---|---|
| Breadcrumb | `BreadcrumbList` | Block decorator reads DOM |
| FAQ / Accordion (FAQ variant) | `FAQPage` | Block decorator reads DOM |
| Video / Video playlist | `VideoObject` | Edge Worker injects into HTML |
| Product List | `Product` / `ItemList` | Edge Worker injects into HTML |
| Organization (global) | `Organization` | Global head — site-wide |
| Search | `WebSite` + `SearchAction` | Global head — sitelinks search box |

Structured data is validated using standard tooling (Google Rich Results Test / Schema Markup Validator) during implementation and pre-launch.

---

## 9. Favicon

The favicon is configured in the **global head (`head.html`)** so it is applied site-wide for browser tab and bookmark identification.

---

## 10. noindex Meta Tag

- Authors add **`robots: noindex`** to the page Metadata; EDS renders it as `<meta name="robots" content="noindex">`.
- Because the sitemap configuration indexes the `robots` property (Section 4), pages marked `noindex` are **automatically excluded from the sitemaps** as well — a single, consistent control.

---

## 11. robots.txt

- `robots.txt` is served at the delivery layer and configured through the **site configuration**.
- It is **environment-aware**: non-production/preview environments disallow crawling, while production allows it.
- It references the **sitemap index** (`sitemap-index.xml`) so crawlers can discover the per-locale sitemaps.

---

## 12. Rendering Approach and Crawlability (No JS Dependency for Content)

A core SEO strength of EDS is how content is rendered and delivered, ensuring crawlers receive complete content without depending on JavaScript.

- **Content is in the server-delivered HTML.** EDS serves the page's content — text, headings, links, and image markup — as **semantic HTML in the initial response**. A crawler (or a JS-disabled client) receives the core content and navigation directly, without executing any script.
- **JavaScript is progressive enhancement, not rendering.** Block decorator JS enhances the already-present markup (layout, interactivity, behaviour); it does **not** generate the core page content. The content exists in the DOM before decoration runs.
- **The practice that assures this** — following EDS best practice, blocks are authored so their **content originates in the document/markup** and the decorator only *transforms* existing DOM, rather than fetching or constructing primary content in JS. Content that must be crawlable is therefore never behind a client-side fetch or render.
- **Structured data placement follows the same principle** (Section 8): where robustness matters (data-driven schema), JSON-LD is placed in the HTML via the Edge Worker rather than generated only client-side.
- **Fast, edge-delivered HTML** supports crawl efficiency and Core Web Vitals; HTTPS is enforced at the edge.

> Stated accurately: **core content and links are crawlable in the initial HTML without JavaScript; JavaScript provides progressive enhancement.** This is a design practice enforced through block design, not an automatic guarantee for content that a developer might place behind a client-side fetch — such patterns are avoided for crawlable content.

---

## 13. Ownership and Guardrails

| Area | Owner |
|---|---|
| `helix-sitemap.yaml`, `helix-query.yaml`, `head.html`, robots.txt config, block decorators / Edge Workers for structured data, image pipeline | Adobe (development) |
| Per-page metadata (title, description, og, canonical override, robots/noindex), alt text | TFS Authors |
| Bulk metadata sheet (`title:suffix`, site/section defaults) | TFS + Adobe |
| SEO strategy, KPI monitoring, Search Console / Webmaster tools | TFS SEO / Content team |

Authoring guardrails (native fallbacks, `index, follow` default, self-referencing canonical default, required alt text, length guidance for title/description) reduce author error and keep SEO signals consistent.

---

## 14. Open Items

| Item | Owner | Status |
|---|---|---|
| Confirm in-scope metadata properties and bulk-sheet defaults per section | TFS + Adobe | Open |
| Confirm structured-data inventory and per-component generation (block vs Edge Worker) | TFS + Adobe | Open |
| Confirm hreflang / locale mapping for `helix-sitemap.yaml` (with MSM/translation design) | TFS + Adobe | Open |
| Confirm robots.txt rules per environment | TFS + Adobe | Open |
