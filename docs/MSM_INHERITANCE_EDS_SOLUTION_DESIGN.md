# MSM & Inheritance — EDS Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 0.1
**Date:** June 2026
**Status:** Draft for Review

---

## 1. Executive Summary

This document defines the **Multi-Site Management (MSM) and content inheritance** approach for the Thermo Fisher migration to AEM Edge Delivery Services (EDS) with **AEM as the authoring source** (Crosswalk).

The central principle: with AEM as the authoring source, **the full set of AEM authoring capabilities remains available** — MSM, blueprints and live copies, inheritance, rollouts, workflows, and translation. Edge Delivery is the **delivery tier**; it does not replace AEM authoring. This means TFS's existing global → regional → country → locale content model can be reproduced in AEM using standard MSM, while EDS handles fast, published delivery.

This document covers the tier model, the EDS (repoless) setup, the proposed site structure, the single-site delivery configuration with path mappings, and how inheritance and overrides are authored.

> Activation, rollout cascade, and publication behavior are covered in a separate document (*MSM Activation Cascade — EDS Solution Design*) and are not repeated here. Site delegation is also covered separately.

---

## 2. Authoring Tier vs Delivery Tier

With AEM as the authoring source, the architecture is split across two tiers:

| Tier | System | Responsibilities |
|---|---|---|
| **Authoring tier** | AEM (as a Cloud Service) | Content authoring; **MSM, blueprints & live copies, inheritance, rollouts, workflows, translation**; page properties and metadata |
| **Delivery tier** | Edge Delivery Services (EDS) | Fast delivery of published content; rendering and serving via the content bus and CDN |

```
   ┌─────────────────────────────────────────────┐
   │              AUTHORING TIER — AEM            │
   │                                              │
   │   Authoring · MSM / Live Copies ·            │
   │   Inheritance · Rollouts · Workflows ·       │
   │   Translation                                │
   └───────────────────────┬─────────────────────┘
                            │  publish
                            ▼
   ┌─────────────────────────────────────────────┐
   │           DELIVERY TIER — EDS                │
   │                                              │
   │   Content Bus → Edge Delivery Rendering →    │
   │   CDN  →  end users                          │
   └─────────────────────────────────────────────┘
```

**Key takeaway:** EDS does not remove or replace AEM's authoring features. All MSM and inheritance capabilities used in the current AEM environment continue to function on the authoring tier. EDS is concerned only with delivering the published result.

---

## 3. Where EDS Fits — Publish to Delivery

Content is authored and managed in AEM. When a page is **published**, it is ingested into the **Edge Delivery content bus** and served by Edge Delivery (preview and live), fronted by the CDN.

In other words:
- **Authoring, MSM relationships, inheritance, and rollouts all happen in AEM** (authoring tier).
- **Publishing** moves the resulting content into EDS for delivery.
- EDS serves what has been published; it does not participate in MSM/inheritance logic.

---

## 4. EDS Setup — Repoless Configuration & Admin API

The EDS site is configured using a **repoless setup**: site configuration is managed through the **Edge Delivery Services Admin API** rather than committed into a code repository. A single shared codebase serves the site, while the Admin API defines the content source, path mappings, and other delivery configuration.

This allows a single EDS site definition (content source + path mappings) to deliver the entire multi-locale content tree.

> **Reference:** [AEM Edge Delivery — Admin API](https://www.aem.live/docs/admin.html)

---

## 5. Current TFS MSM Structure

The current Thermo Fisher AEM environment uses a multi-level MSM hierarchy:

```
Global (English)
   └── Regional (English)            e.g. North America, Latin America, Europe, Japan, Greater China, IPAC
          └── Country (English)       e.g. US, Canada, UK, Germany
                 └── Locale (language) e.g. de/de, fr/fr, zh/cn
```

- Content is authored at the **global English** level and cascades down through **regional English**, to **country English**, and then to **country-language** copies.
- Authors can create content at any level and break inheritance where local overrides are required.

---

## 6. Proposed Site Structure

We recommend reproducing the MSM model following **Adobe best practice** for multi-site, multi-language structures:

```
/content/lifetech/<region>/<country>/<language>
```

with the **country as the container** and **language nested beneath it**.

### 6.1 Proposed Tree

```
/content/lifetech
├── global
│   └── en
│
├── north-america
│   ├── en
│   ├── us
│   │   └── en
│   └── ca
│       ├── en
│       └── fr
│
├── latin-america
│   ├── en
│   ├── br
│   │   ├── en
│   │   └── pt
│   ├── mx
│   │   ├── en
│   │   └── es
│   ├── ar
│   │   ├── en
│   │   └── es
│   ├── cl
│   │   ├── en
│   │   └── es
│   └── other
│       └── en
│
├── europe
│   ├── en
│   ├── uk
│   │   └── en
│   ├── fr
│   │   ├── en
│   │   └── fr
│   ├── de
│   │   ├── en
│   │   └── de
│   ├── es
│   │   ├── en
│   │   └── es
│   ├── ru
│   │   ├── en
│   │   └── ru
│   ├── za
│   │   └── en
│   ├── ng
│   │   └── en
│   ├── sa
│   │   └── en
│   ├── tr
│   │   └── en
│   └── other
│       └── en
│
├── greater-china
│   ├── en
│   ├── cn
│   │   ├── en
│   │   └── zh
│   └── hk
│       ├── en
│       └── zh
│
├── japan
│   ├── en
│   └── jp
│       ├── en
│       └── ja
│
└── ipac
    ├── en
    ├── au
    │   └── en
    ├── in
    │   └── en
    ├── nz
    │   └── en
    ├── sg
    │   └── en
    ├── kr
    │   ├── en
    │   └── ko
    ├── tw
    │   ├── en
    │   └── zh
    └── other
        └── en
```

### 6.2 Naming Convention

- **Country container first, language nested beneath it** (e.g. `ca/en`, `ca/fr`).
- ISO country and language codes are used throughout.
- Each region has an English regional node (`<region>/en`) acting as the regional source; an `other/en` node is provided per region where applicable.

### 6.3 MSM Relationships

- **Global English** (`/content/lifetech/global/en`) is the **master / blueprint**.
- **Regional English** (`<region>/en`) nodes are **live copies** of global English.
- **Country English** (`<region>/<country>/en`) nodes are **live copies** of their regional English.
- Authors can create region- or country-specific content at any level and break inheritance for local overrides (see Section 8).

### 6.4 Recommendation — Same-Language Inheritance via MSM, Translation via Language Copies

As an **Adobe best practice**, we recommend:

- **MSM live copies** are used for **same-language inheritance** — i.e. the English chain: global English → regional English → country English. This is where rollout/inheritance of shared English content belongs.
- **Translation to other languages** (e.g. `de/en` → `de/de`, `ca/en` → `ca/fr`, `cn/en` → `cn/zh`) is handled via **language copies** (translation relationships), **not** plain live copies.

This separation keeps same-language structural inheritance distinct from translation, aligns with Adobe's recommended MSM + translation model, and keeps each mechanism doing what it is designed for.

---

## 7. EDS Delivery — Single Site, Path Mappings, Content Bus

### 7.1 Single EDS Site

A **single EDS site** (e.g. `tfssite`) is created in the EDS Admin and serves **all locales**. Individual country/locale delivery URLs are defined through **path mappings** in the site configuration, which translate friendly delivery paths to the underlying content paths.

### 7.2 Path Mappings (example)

```json
"public": {
  "paths": {
    "mappings": [
      "/content/lifetech/:/",
      "/content/lifetech/north-america/us/en/:/us/en/",
      "/content/lifetech/north-america/ca/en/:/ca/en/",
      "/content/lifetech/north-america/ca/fr/:/ca/fr/",
      "/content/lifetech/latin-america/ar/en/:/ar/en/",
      "/content/lifetech/latin-america/br/pt/:/br/pt/",
      "/content/lifetech/europe/de/en/:/de/en/",
      "/content/lifetech/europe/de/de/:/de/de/",
      "/content/lifetech/europe/uk/en/:/uk/en/",
      "/content/lifetech/greater-china/cn/zh/:/cn/zh/",
      "/content/lifetech/japan/jp/ja/:/jp/ja/",
      "/content/lifetech/ipac/kr/ko/:/kr/ko/"
    ],
    "includes": [
      "/content/lifetech/"
    ]
  }
}
```

Each mapping maps an underlying content path to a clean delivery path (e.g. `/content/lifetech/north-america/us/en/` → `/us/en/`).

### 7.3 EDS Delivery URLs

With the mappings above, content is delivered as:

| Delivery URL | Result |
|---|---|
| `<eds-url>.page/us/en` | US **preview** content |
| `<eds-url>.live/us/en/home` | US **live** (published) content |
| `<eds-url>.page/ar/en` | Argentina (English) **preview** content |
| `<eds-url>.live/ar/en` | Argentina (English) **live** content |
| `<eds-url>.page/de/de` | Germany (German) **preview** content |
| `<eds-url>.live/de/de` | Germany (German) **live** content |

(`.page` = preview tier; `.live` = published/live tier.)

### 7.4 Single Content Bus

All locales are served by the **single EDS site** and therefore share **one content bus**. This means content across all locales is delivered within one consistent delivery scope.

---

## 8. Inheritance & Override Authoring

With MSM live copies in place, content rolled down from a blueprint can be **overridden locally** by breaking inheritance. The authoring experience for this differs between the **Universal Editor** and the classic **Page Editor**.

### 8.1 Inheritance — Core Concept

Inheritance links content such that changing the source (blueprint) automatically updates the linked live copy — unless inheritance has been broken for that content, in which case the local override is preserved when the blueprint is rolled out.

### 8.2 Authoring in the Universal Editor

Per Adobe documentation, in the Universal Editor:

- **Editing implicitly cancels inheritance.** When a page is part of MSM (or a Launch) and content is edited in the Universal Editor, the editor **automatically disables inheritance** for the edited content. As soon as a change is made, inheritance is implicitly cancelled, and the modified content is retained when updates are synchronised from the blueprint.
- **Component/block-level inheritance control requires the MSM Extension.** With the **AEM Multi-Site-Management (MSM) Extension** enabled, authors get toolbar controls and visual feedback for inheritance at the component level:
  - **Inheritance Installed icon** — inheritance is active for the selected component; clicking it **breaks** inheritance (editing the component does this automatically as well).
  - **Inheritance Broken icon** — inheritance is disabled; clicking it **reinstates** inheritance (a page reload is required to display the re-inherited content).
- Without the MSM Extension enabled, authors **do not have visual indicators** of which components have inheritance broken vs preserved, and cannot manually manage component-level inheritance.
- The icons appear only when a component is **selected** and the page **derives from a blueprint**.
- **Page-level** inheritance can be reverted via the **Live Copy Overview Console**, the **Launches Console**, or the **Reset** button on the **Live Copy** tab in page properties.

> **Note:** The MSM Extension applies to **pages**, not Content Fragments.

### 8.3 Authoring in the Page Editor

The behaviour and authoring experience for breaking inheritance in the classic **Page Editor** differs from the Universal Editor (e.g. inheritance controls are surfaced differently in the component toolbar / page properties). Teams authoring via the Page Editor should follow the classic MSM inheritance controls rather than the Universal Editor extension model described above.

### 8.4 Granularity Summary

| Level | What it controls | How it is broken / restored |
|---|---|---|
| **Page level** | Whole-page inheritance from the blueprint | Live Copy Overview Console / Launches Console / **Reset** on the Live Copy tab |
| **Component / block level** | Inheritance of an individual component | Universal Editor + MSM Extension (Inheritance Installed / Broken icons); editing a component auto-cancels its inheritance |

### 8.5 References

- [Universal Editor — Inheritance](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/inheritance)
- [Universal Editor — Authoring (Inheritance)](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/authoring#inheritance)

---

## 9. Site Delegation — Secondary Countries

### 9.1 Use Case

Some countries do not have their own content node in the MSM structure but still require a site presence under their own country URL. These **secondary countries** serve the content of a designated **primary country**. For example, Belgium has no dedicated content node and serves the United Kingdom's content — while the Belgium country URL continues to exist and is served to visitors.

The requirement is that the **secondary country URLs must exist and remain in place**, while the content displayed is that of the mapped primary country.

### 9.2 Proposed Approach — Delegation at the CDN

Secondary-country delegation is handled at the **CDN** through edge logic, using a **secondary-to-primary country mapping**.

- A mapping defines, for each secondary country, the primary country whose content it should serve (e.g. Belgium → United Kingdom).
- At request time, the edge logic determines the page's country from the locale. **If the country is a secondary country**, the request is **internally rewritten** to serve the **primary country's content**, while the **secondary country URL is preserved** in the browser. The visitor sees the secondary country's URL but is served the primary country's content.
- **On-page links / hrefs** are rewritten to reflect the **secondary country URL**, so that navigation within the delegated site remains consistent with the secondary country's URL space.

### 9.3 SEO Handling — Canonical to Primary Country

Because this approach serves the same content under more than one country URL, the content is effectively **duplicated** across the primary and its delegated secondary URLs. As in the current implementation, a **canonical reference to the primary country** is set on the delegated pages, so that search engines treat the **primary country URL as the authoritative version**. This handles SEO for the duplicated content.

---

## 10. Summary

| Topic | Approach |
|---|---|
| **Tiers** | AEM = authoring tier (MSM, inheritance, rollouts, workflows, translation); EDS = delivery tier |
| **EDS setup** | Repoless; configured via the EDS Admin API |
| **Site structure** | `/content/lifetech/<region>/<country>/<language>`, country container first, language nested |
| **MSM relationships** | Global English (master) → regional English (live copy) → country English (live copy) |
| **Same-language vs translation** | MSM live copies for same-language inheritance; **language copies** for translation (Adobe best practice) |
| **Delivery** | Single EDS site, path mappings per locale, single content bus |
| **Inheritance/override** | Implicit cancel on edit in Universal Editor; component-level control via MSM Extension; page-level reset via consoles; Page Editor behaviour differs |
| **Site delegation** | Secondary countries serve a mapped primary country's content via CDN edge logic; secondary URL preserved, links rewritten to secondary URL, canonical set to primary country for SEO |
