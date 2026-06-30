# SEO — Solution Design

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## 1. Overview

This document defines the SEO solution design for the TFS migration to AEM as a Cloud Service with Edge Delivery Services (EDS), where **AEM is the authoring source** and EDS is the delivery layer.

The intent is to **preserve the SEO foundations of the existing site** and apply EDS SEO best practices. Critical SEO signals — page titles, meta descriptions, canonical URLs, robots directives, Open Graph tags, hreflang coverage, sitemaps, robots.txt, redirects, crawlable semantic HTML, and applicable schema.org structured data — are carried forward.

> **Note on scope and commitments.** This document describes the **approach and platform capabilities** based on EDS best practices. Detailed field-level mapping, template-specific implementation, the structured-data inventory, and the localization model are **finalised during implementation** once the final block design, template inventory, and content model are confirmed. Targets and parity are stated as design intent, not as guarantees of specific search outcomes (which depend on factors outside the platform, such as content and external ranking signals).

---

## 2. Crawlability and Indexation

### 2.1 Rendering — Crawlable Content

EDS delivers **semantic HTML in the initial server response** — page text, links, headings, and image markup are present in the delivered HTML and **do not require JavaScript execution to be crawled**. JavaScript is used for **progressive enhancement** (block decoration and interactivity), not for rendering the core content. This makes the primary content and navigation crawlable by search engines without a JS-rendering dependency.

> This is described as "critical content present in the initial HTML, crawlable without JS; JavaScript provides progressive enhancement" — not as "zero JavaScript on the page."

### 2.2 URL Structure

The existing public URL structure (`/{country}/{language}/...`) is **preserved** in the migration. URLs remain clean, descriptive, and free of query parameters for content addressing. Preserving the URL structure reduces SEO risk at cutover (existing indexed URLs and inbound links remain valid).

### 2.3 Canonical Tags

- Pages are **self-referencing canonical by default** — EDS can derive the canonical from the page's own URL.
- An **override** is available where a page should point its canonical elsewhere (e.g. filtered/variant URLs pointing to a base page).
- Canonical values reflect the page's **own target URL**; source/host values are not carried over verbatim from the old site.

### 2.4 robots.txt

- Served at the edge and **environment-aware**: non-production/preview environments disallow crawling; production allows it.
- References the sitemap index (Section 2.5).

### 2.5 XML Sitemaps

Following EDS sitemap best practice, a **tiered sitemap architecture** is used, suited to the multi-locale (≈45 locales) site:

- **Per-section / per-locale sitemaps** generated from the published content.
- A **sitemap index** referencing the individual sitemaps, referenced in robots.txt and submitted to search engines.
- **noindex pages are automatically excluded from sitemaps** — by indexing the `robots` property in the query configuration, any page marked `robots: noindex` is omitted from sitemap generation. This keeps noindex and sitemap exclusion consistent through a single mechanism.
- Sitemap coverage and exclusions are validated before launch and update as content is published.

### 2.6 Redirects

Redirects are handled per the **Redirects migration strategy**: simple 1:1 redirects via the EDS redirects mechanism (301), and pattern/wildcard redirects at the CDN (Akamai). The existing URL structure is preserved, so redirect migration is a like-for-like carry-over. (See the Redirects strategy for detail.)

---

## 3. On-Page SEO Controls (Metadata, Authoring, Guardrails)

### 3.1 Where Metadata Is Managed

In the AEM-as-authoring-source model, metadata is managed at two levels:

- **Per-page metadata** — authored as **page properties** (the page-metadata model, set in the Page Properties of the Universal Editor). EDS emits these as the corresponding `<head>` tags.
- **Bulk / site-wide metadata** — managed in a **bulk metadata sheet** at the site root, applied by URL pattern; used for defaults that apply across sections.
- **Precedence:** page-level metadata **always overrides** bulk metadata.

### 3.2 Metadata Mapping and Fallbacks (EDS Native Behaviour)

EDS provides native mapping of metadata properties to head tags, with built-in fallbacks:

| Metadata Property | Rendered HTML Tags | Fallback Behaviour |
|---|---|---|
| `title` | `<title>`, `og:title`, `twitter:title` | First `<h1>` on the page if not set |
| `description` | `<meta name="description">`, `og:description`, `twitter:description` | First substantial paragraph if not set |
| `image` | `og:image`, `twitter:image` | First page image / a default image if not set |
| `canonical` | `<link rel="canonical">`, `og:url` | Auto-generated from the page's own URL if not set |
| `robots` | `<meta name="robots">` | Defaults to `index, follow` |

This native behaviour reduces the amount of custom logic required and provides sensible defaults where a field is left empty.

### 3.3 Title Suffix (Brand Consistency)

A global **title suffix** (e.g. `| Thermo Fisher Scientific`) can be applied via the bulk metadata sheet and appended automatically to page titles. Authors set the page-specific title; the brand suffix is applied consistently without per-page effort. This is native EDS behaviour.

### 3.4 Governed Defaults by Page Type

Default title/description patterns can be defined **per page type** in the bulk metadata sheet (e.g. a consistent description template for a given template), with **page-level values overriding** the default where authors provide specific content. This gives consistent baseline metadata while allowing per-page control.

### 3.5 Authoring Guardrails

- **Length guidance** — recommended limits surfaced to authors (e.g. title ≈60 characters, description ≈160 characters).
- **Required fields** — alt text and core metadata encouraged/required via authoring guidance.
- **Defaults** — `index, follow` default; self-referencing canonical default.
- **Derived vs authored** — URL-/locale-driven values (canonical, og:url, hreflang) are derived rather than hand-authored, avoiding stale or inconsistent values.

---

## 4. Structured Data (schema.org / JSON-LD)

### 4.1 Approach

Structured data is implemented as **JSON-LD**. Two implementation routes are used, chosen per schema type:

- **Site-wide structured data** (e.g. `Organization`, `WebSite` + `SearchAction`) — emitted from the global head, present in the initial HTML.
- **Page/block-level structured data** (e.g. `BreadcrumbList`, `FAQPage`, `VideoObject`, `ItemList`) — generated from block content.

> **Best-practice note — placement of JSON-LD.** Where the data is available up front, structured data is preferably emitted in the **initial HTML / head**, which is the most robust for all crawlers. Where the data only exists after block decoration, it is generated by block JavaScript; this is supported by major search engines that execute JavaScript, but is inherently less robust than in-HTML structured data. The placement choice per schema type is confirmed during implementation.

### 4.2 Candidate Schema by Block Pattern

| Block Pattern | Schema Type | Generation |
|---|---|---|
| Breadcrumb | `BreadcrumbList` | From navigation hierarchy |
| Video / Video playlist | `VideoObject` | From video embed metadata |
| FAQ / Accordion (FAQ variant) | `FAQPage` | From question/answer content |
| Cards / Carousel / Collection | `ItemList` | From item content |
| Organization (global) | `Organization` | Global head — site-wide |
| Search | `WebSite` + `SearchAction` | Global head — enables sitelinks search box |

This is an **initial candidate inventory**; the confirmed set, per-page-type mapping, and placement (head vs block) are finalised during implementation. (A dedicated structured-data / JSON-LD design covers the detail.)

### 4.3 Validation

Structured data is validated using standard tooling (e.g. Google Rich Results Test / Schema Markup Validator) as part of implementation and pre-launch checks.

---

## 5. Content Strategy and Internal Linking

The platform provides the capabilities that support good on-page SEO and internal linking; content strategy itself remains a **TFS editorial responsibility**, supported by these platform guardrails:

- **Semantic heading hierarchy** (H1 → H2 → H3) supported by block design.
- **Internal linking** — contextual in-content links, breadcrumbs, and related-content blocks.
- **No orphan pages** — pages reachable via navigation/internal links.
- **Reasonable click depth** — important pages reachable within a few clicks of the homepage.
- **Crawlable links** — navigation and links present in the server-delivered HTML.

---

## 6. Media Optimization

- **Alt text** — an authoring field with guidance; encouraged/required so images carry descriptive alternative text.
- **Edge image optimization** — EDS optimizes images at the edge (modern formats, compression, and responsive sizing via `srcset`), supporting performance and Core Web Vitals.
- **Descriptive file names** — authoring guidance to use descriptive, keyword-relevant image file names.
- **Image sitemaps** — image entries can be included in sitemap generation where required; the need and scope are confirmed with TFS.

---

## 7. Rendering Approach and Crawl Assurance

- Core content and links are delivered as **semantic HTML in the initial response** and are crawlable **without JavaScript**.
- JavaScript provides **progressive enhancement** (block decoration, interactivity), not core content rendering.
- **HTTPS** is enforced at the edge (HTTP → HTTPS).
- The architecture is **performance-oriented** and designed to support good Core Web Vitals; actual scores depend on final content, images, and third-party scripts and are validated during implementation.

> Performance and Core Web Vitals are stated as **design goals supported by the architecture**, not as guaranteed scores.

---

## 8. SEO Measurement and Tooling

- **Search Console / Webmaster tools** — production property verified (verification handled at the edge / head); sitemaps submitted; coverage, indexation, and enhancements monitored.
- **Performance monitoring** — EDS provides Real User Monitoring (RUM) for Core Web Vitals field data.
- **KPIs (to be agreed with TFS)** — examples: organic traffic, indexed-page count, Core Web Vitals, crawl errors, and rankings for priority terms. Migration-specific monitoring: pre/post indexation parity, redirect health (no redirect-to-404), and 404 monitoring after cutover.

---

## 9. Migration / Launch Strategy (SEO)

### 9.1 Pre-Launch Validation

- **Metadata / `<head>` parity** — migrated pages emit the confirmed metadata (title, description, robots, canonical, OG) correctly.
- **Canonical and hreflang** correctness — resolve to correct target URLs / locale alternates.
- **Sitemap and robots.txt** — coverage, exclusions, and environment-awareness verified.
- **Structured data** — validated with Rich Results tooling.
- **Redirects** — verified to resolve (no redirect-to-404).

### 9.2 Launch and Rollback

Content is verified on its EDS URLs ahead of go-live; go-live and rollback are handled at the **CDN routing layer** (route to EDS; revert if required). Sequencing is confirmed during joint go-live planning. (See the Content Migration Strategy overview.)

### 9.3 Post-Launch

- Crawl and Search Console monitoring for indexation and coverage.
- Redirect and 404 monitoring.
- Ranking/traffic monitoring against the pre-launch baseline.

---

## 10. Supporting Elements

- **404 page** — a custom 404 page is provided via the EDS 404 mechanism.
- **Favicon** — configured in the global head.
- **noindex** — pages can be marked `robots: noindex` per page; such pages are excluded from sitemaps automatically (Section 2.5).

---

## 11. Ownership and Governance

| Area | Owner |
|---|---|
| Derived-metadata logic, sitemap/robots configuration, structured-data generation, blocks | Adobe (development) |
| Per-page metadata, alt text, titles/descriptions within guardrails | TFS Authors |
| SEO strategy, KPI monitoring, Search Console, content strategy | TFS SEO / Content team |
| URL structure, canonical/redirect policy decisions | TFS + Adobe |

Authoring guardrails (defaults, required fields, length guidance, derived values) are provided to reduce author error and maintain consistent SEO signals.

---

## 12. Open Items

| Item | Owner | Status |
|---|---|---|
| Confirm in-scope metadata properties and per-page-type defaults | TFS + Adobe | Open |
| Confirm structured-data inventory, per-page-type mapping, and head-vs-block placement | TFS + Adobe | Open |
| Confirm hreflang / locale mapping (with MSM/translation design) | TFS + Adobe | Open |
| Confirm image-sitemap requirement | TFS | Open |
| Confirm SEO KPIs and measurement tooling | TFS | Open |
