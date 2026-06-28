# Migration Strategy — Content Syndication Pods

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Migration strategy for Content Syndication Pods (PFP / PDP product content pods)

| | Environment |
|---|---|
| **Source** | AEM On-Premise instance (AEM 6.4) — existing production environment |
| **Target** | AEM Author instance on AEM as a Cloud Service — with Edge Delivery Services as the delivery layer |

---

## Overview

This document defines the migration strategy for **Content Syndication Pods** from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS).

Content Syndication Pods are HTML fragments authored in AEM and consumed by the TFS commerce / PDP system to display product-specific marketing content on product pages. The migration moves all pod content into the new AEM authoring instance (delivered via EDS), retires the AEM On-Prem servlet-based delivery mechanism, and replaces it with the `aem-embed.js` Web Component.

Pod migration runs **in step with the page migration waves** — when a locale is being migrated, its syndication pods are migrated at the same time.

> The target delivery architecture for content syndication (the `aem-embed` Web Component, the published fragment, Shadow DOM isolation, EDS blocks, the URL convention, and the commerce-side integration) is defined in the **Content Syndication Integration with PDP** solution design. This document covers the **migration** of existing pod content into the target environment.

---

## 1. What Are Content Syndication Pods

### How They Work Today

The TFS commerce system (PDP / product pages) is a separate platform. To display product-specific content on product pages, it calls out to AEM at runtime and injects the response as an HTML fragment directly into the page.

A single product (identified by a SKU) can have multiple pods — each pod is a distinct section of content on the product page.

The content for each pod is authored as a standalone AEM page using a specialized Content Snippet template. On publish, this page renders as a **raw HTML fragment** — no page shell, no navigation, no CSS — just the content HTML that the commerce system injects into the product page.

### How the Commerce System Requests a Pod

The commerce system calls AEM using a structured URL:

```
/{locale}/product-family-syndicated-content.pfpsnippet.html/sku/{SKU}/{POD-NUMBER}
```

The `.pfpsnippet` selector triggers a dedicated AEM servlet (`ProductFamilySyndicationServlet`) which:

1. Parses the SKU and pod number from the URL
2. Constructs the corresponding tag identifier
3. Searches the JCR for a Content Snippet page tagged with that identifier
4. Returns the rendered HTML of that page

This `.pfpsnippet.html` endpoint is **publicly reachable** — it is the production endpoint the commerce system fetches today.

### Where Pods Live in AEM

Content Snippet pod pages exist across the full site hierarchy — at global, regional, and country levels — mirroring the same structure as regular site pages:

```
/content/lifetech/global/en/syndicated-content/snippets/...
/content/lifetech/north-america/en/syndicated-content/snippets/...
/content/lifetech/north-america/en-us/syndicated-content/snippets/...
/content/lifetech/europe/en/syndicated-content/snippets/...
/content/lifetech/europe/de-de/syndicated-content/snippets/...
/content/lifetech/ipac/en/syndicated-content/snippets/...
/content/lifetech/ipac/en-in/syndicated-content/snippets/...
/content/lifetech/japan/ja-jp/syndicated-content/snippets/...
/content/lifetech/greater-china/zh-cn/syndicated-content/snippets/...
```

Each pod page is tagged with a `cq:tag` that identifies which SKU and pod number it serves. The tag namespace is either `pfp:` (Product Family Page) or `pdp:` (Product Detail Page) depending on the type of product page.

---

## 2. What Changes in the Target

### Path Replaces Tags

In AEM On-Prem, the connection between a URL request and a content page is made through **tag taxonomy**. The servlet builds a tag ID, searches the JCR, and finds the right page — regardless of where that page sits in the folder structure.

In the target environment there is **no runtime servlet and no runtime tag search**. Edge Delivery serves published static content at the edge and does not run the Sling/JCR servlet or a runtime tag-query engine. **The path where the content is published IS the identifier.** The commerce system constructs the delivery URL directly from the locale, namespace, SKU, and pod number — no lookup, no tag search.

### Commerce System Uses `aem-embed`

Instead of making HTTP calls to the AEM servlet, the commerce system places an `<aem-embed>` Web Component tag on the product page. This component fetches the published fragment, runs the EDS decoration pipeline, and renders the content inside an isolated Shadow DOM — preventing any CSS or JS conflicts between the pod content and the commerce page. (Defined in the PDP integration solution design.)

### Locale Coverage via MSM Rollout — No Runtime Fallback

In the current system, when the commerce system requested a pod URL, the server-side servlet resolved the locale and performed a tag search, so a request for a locale that lacked its own pod still resolved to content at a resolved base path. This graceful fallback was a property of the **servlet's runtime lookup**.

In the target environment there is **no runtime servlet and no runtime fallback** — the commerce system constructs a direct URL and the Web Component fetches the published fragment at that exact path. A locale that does not have a published pod at its path will **not** silently fall back; the fetch will simply not resolve.

Locale coverage is therefore handled at **authoring time using AEM MSM**, so that every locale that serves a pod has a real, published page at its path. A base locale holds the source content, and AEM MSM rolls it down to each required locale variant — so coverage is guaranteed by the standard AEM MSM rollout workflow TFS already operates, not by a runtime lookup.

---

## 3. The Fundamental Change — Tag to Path

### AEM On-Prem Today

The AEM path of a Content Snippet page has no relationship to the product it serves. The link is purely through `cq:tags`:

```
Page path:  /content/lifetech/ipac/en-in/syndicated-content/snippets/cell-culture/promotions/explore-gibco.html
cq:tags:    pfp:sku/CHROMELEON7/pod_3
```

The page can sit anywhere in the folder tree. The tag is what identifies it.

### Target After Migration

The published delivery path IS the identifier. No tags, no servlet lookup:

```
Delivery path:   ipac/en-in/snippets/pfp/sku/CHROMELEON7/pod-3
```

### Path Derivation Rule

The target path is derived from two inputs: the **AEM locale** (extracted from the page's position in the JCR tree) and the **`cq:tag`** value.

```
AEM locale from page path  +  cq:tag value
        ↓                          ↓
   {site-locale-path}      +   snippets/{namespace}/{type}/{SKU}/pod-{N}
        ↓
   Target delivery path
```

| Input | Example |
|---|---|
| AEM page path | `/content/lifetech/ipac/en-in/syndicated-content/...` |
| `cq:tag` | `pfp:sku/CHROMELEON7/pod_3` |
| Derived locale path | `ipac/en-in` |
| Derived target path | `ipac/en-in/snippets/pfp/sku/CHROMELEON7/pod-3` |

The namespace (`pfp` or `pdp`) is preserved in the path so that Product Family Page pods and Product Detail Page pods remain distinguishable.

---

## 4. Target Path Structure

### Full Path Examples

| AEM Locale | `cq:tag` | Target Path |
|---|---|---|
| `global/en` | `pfp:sku/CHROMELEON7/pod_1` | `global/en/snippets/pfp/sku/CHROMELEON7/pod-1` |
| `north-america/en` | `pfp:sku/CHROMELEON7/pod_1` | `north-america/en/snippets/pfp/sku/CHROMELEON7/pod-1` |
| `ipac/en-in` | `pfp:sku/CHROMELEON7/pod_3` | `ipac/en-in/snippets/pfp/sku/CHROMELEON7/pod-3` |
| `europe/de-de` | `pdp:sku/ABC123/pod_1` | `europe/de-de/snippets/pdp/sku/ABC123/pod-1` |
| `japan/ja-jp` | `pfp:fam/CELL-CULTURE/pod_2` | `japan/ja-jp/snippets/pfp/fam/CELL-CULTURE/pod-2` |

---

## 5. Why This Is Not a JCR Package Move

As with all content in this programme, syndication pods cannot be moved as a JCR content package. The source pod pages are built on AEM 6.4 component resource types (the Content Snippet template, the snippet paragraph system, and their child components) which **do not exist in the target environment**. The target stores content as **EDS blocks** with a different node structure and resource types.

Pod migration is therefore a **content transformation** — the pod content is extracted, transformed into the target EDS block structure, and created fresh in the target AEM authoring instance.

---

## 6. Migration Approach

### 6.1 Pods Migrate with Their Locale Wave

Pods are migrated in the same wave as the locale they belong to — when a country or region is being migrated, its syndication pods are migrated as part of that same wave.

```
Wave 1 — global/en migration
  → global/en pages migrated
  → global/en syndication pods migrated

Wave 2 — Regional EN hubs migration
  → north-america/en pages migrated
  → north-america/en syndication pods migrated
  → (same for europe/en, ipac/en, etc.)

Wave 3 — Country EN sites migration
  → north-america/en-us pages migrated
  → north-america/en-us syndication pods migrated
  → (same for all country EN sites)

Wave 4 — Translated locales migration
  → (same for all translated locales)
```

### 6.2 How Pods Are Found

For each locale being migrated, a script queries AEM for all Content Snippet pages under that locale's `syndicated-content/snippets/` tree, using the `lifetech/templates/content_snippet` template as the filter. This produces the complete list of pod pages for that locale.

The JCR query is required because the **list of pods and their `cq:tags` cannot be derived from rendered HTML** — the tag drives the target path, and the JCR is the authoritative source for enumerating pods.

### 6.3 Tag Handling Rules

Once pod pages are found, their `cq:tags` determine the target path:

| Tag situation | Handling |
|---|---|
| Page has one tag | Derive target path from that tag; migrate to that path |
| Page has multiple tags | Migrate to each tag's corresponding target path — same content published at multiple paths |
| Page has no tags | Skip — target path cannot be derived; pod is excluded from migration |

---

## 7. Migration Steps

### Step 1 — Query AEM for All Pod Pages (JCR)

For the locale being migrated, query AEM to find all Content Snippet pages under that locale's `syndicated-content/snippets/` tree using the `lifetech/templates/content_snippet` template. This produces a complete list of pod pages for that locale.

### Step 2 — Extract Tags and Derive Target Paths

For each pod page found, read its `cq:tags` property. Apply the path derivation rule (locale + tag → target path) to produce the target path for each pod. Pages with no tags are excluded; pages with multiple tags produce one target path per tag.

This produces the **pod migration manifest** for that locale — a complete list of source AEM pages mapped to their target paths. The manifest is also the reconciliation baseline used in validation.

### Step 3 — Fetch Pod HTML (public `.pfpsnippet.html`)

For each pod in the manifest, fetch the rendered HTML from the **publicly reachable** `.pfpsnippet.html` endpoint. This renders the Content Snippet page as a clean HTML fragment — the same output the commerce system receives today: no page shell, no navigation, no CSS — pure content HTML ready for transformation.

Because this is the production endpoint the commerce system already consumes, it is publicly accessible and requires no special access for the migration to fetch it.

### Step 4 — Transform to EDS Block Format

The fetched HTML is transformed into the target **EDS block structure**. The content of the pod — headings, paragraphs, images, video, links — is preserved as authored. The transformation converts the AEM component output into the EDS block structure that the target AEM authoring instance stores and EDS renders, conforming to the target block / component model.

### Step 5 — Load into AEM Author and Publish

The transformed pod content is created in the **target AEM authoring instance** at the derived target path and **published to Edge Delivery**. Once published, the pod is available at its delivery path and can be fetched by the `aem-embed.js` Web Component.

Locale coverage is established through **AEM MSM rollout** (Section 2) so that every required locale and pod has a real, published page at its path.

---

## 8. Pilot and Cutover

### Pilot — One SKU End to End First

Before migrating all pods, the pipeline is validated end to end with a single SKU, in coordination with the commerce team. All pods for that one SKU are migrated and published. The commerce team then switches that SKU's pod calls from the AEM servlet URL to `<aem-embed>` tags pointing to the target paths.

If the pilot SKU renders correctly in the commerce page — content, styling, locale resolution — the full migration runs for all remaining pods.

### Full Cutover

After pilot sign-off, all remaining pods are migrated locale by locale within each migration wave. Once all pods for a locale are migrated and published, the commerce team updates their system for that locale to use `<aem-embed>` tags. The AEM On-Prem servlet remains available as a fallback until the commerce team confirms the switch is complete for all locales.

---

## 9. Validation

Validation confirms that pods are **completely and correctly migrated** and are **deliverable** at their target paths. Validation is performed per locale wave, against the pod migration manifest (Step 2) as the reconciliation baseline.

| Check | Detail |
|---|---|
| **Completeness / reconciliation** | Every pod page in the manifest for the locale has a corresponding published pod at its derived target path. Multi-tag pods are present at each derived path. Tagless (skipped) pods are accounted for. |
| **Content fidelity** | The migrated pod content (headings, text, images, video, links) matches the source pod fragment — no content lost in transformation. |
| **Deliverability** | The pod resolves at its target path and renders correctly via `aem-embed` (content displays, styling applied, isolated in Shadow DOM). |
| **Locale coverage** | Every locale expected to serve a given pod has a published pod at its path (MSM rollout completeness) — since there is no runtime fallback, a missing published pod results in a non-resolving fetch. |

**Pilot validation** (one SKU, end to end with the commerce team) gates the full migration. **TFS sign-off** is obtained per wave before proceeding.

---

## 10. Before and After

### AEM On-Prem — Current State

```
Commerce system calls:
  /{locale}/product-family-syndicated-content.pfpsnippet.html/sku/CHROMELEON7/3

AEM servlet receives the call
  → Parses SKU and pod number from URL
  → Builds tag: pfp:sku/CHROMELEON7/pod_3
  → Searches JCR by tag within locale scope
  → Finds tagged Content Snippet page (wherever it sits in folder tree)
  → Renders page via content-snippet.jsp
  → Returns raw HTML fragment to commerce system

Content Snippet page in AEM:
  Path:    /content/lifetech/ipac/en-in/syndicated-content/snippets/cell-culture/promotions/explore-gibco.html
  Tag:     pfp:sku/CHROMELEON7/pod_3
  Content: text, images, links authored via AEM components
```

### Target — After Migration

```
Commerce system places:
  <aem-embed url="/{locale}/snippets/pfp/sku/CHROMELEON7/pod-3"></aem-embed>

aem-embed.js Web Component runs in the browser
  → Reads the url attribute
  → Fetches the published fragment as plain HTML
  → Runs the EDS decoration pipeline
  → Renders content in isolated Shadow DOM — no CSS/JS conflicts with commerce page

Pod content in the target:
  Path:    ipac/en-in/snippets/pfp/sku/CHROMELEON7/pod-3
  Content: same content, authored/stored as EDS blocks
  No tags. No servlet. No JCR search at runtime.
```

### What Changes — Summary

| Dimension | AEM On-Prem | Target (AEM Authoring + EDS) |
|---|---|---|
| Content location | Descriptive folder path, identified by tag | Path IS the identifier — no tags |
| Commerce integration | HTTP call to AEM servlet | `<aem-embed>` Web Component tag |
| Locale resolution | Server-side Java (`MsmMappingConfiguration`) | AEM MSM rollout — published per locale |
| Content lookup | JCR tag search (`TagManager.find()`) | Direct URL resolution — no runtime lookup |
| Fallback | Runtime fallback via servlet | No runtime fallback — coverage via MSM rollout |
| Content format | AEM components (Content Snippet template) | EDS blocks |
| CSS isolation | None — commerce CSS must not conflict | Full Shadow DOM isolation |

---

## 11. Assumptions

1. Pod migration runs in step with the page migration waves — a locale's pods are migrated in the same wave as that locale.

2. The `.pfpsnippet.html` endpoint used to fetch pod HTML is publicly reachable (it is the production endpoint the commerce system consumes today), so no special access is required to fetch pod content for transformation.

3. Pod discovery and tag-to-path derivation are driven by a JCR query against the Content Snippet template; pods without tags are excluded from migration.

4. Locale coverage is guaranteed by AEM MSM rollout so that every required locale pod is published at its path. There is no runtime fallback in the target — a missing published pod does not silently resolve.

5. A transformation is available to convert the Content Snippet pod HTML into the target EDS block format before migration runs.

6. The target delivery mechanism (`aem-embed` Web Component, URL convention, Shadow DOM rendering, commerce-side integration) is defined in the Content Syndication Integration with PDP solution design and is a dependency of this migration.
