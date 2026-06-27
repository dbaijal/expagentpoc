# TFS — MSM, Inheritance & Edge Delivery Services Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 1.0
**Date:** June 2026
**Status:** Draft for Review

---

## 1. Purpose

This document describes the proposed content structure and inheritance model for the Thermo Fisher (TFS) website migration to **Adobe Experience Manager as a Cloud Service (AEMaaCS)** with **AEM as the authoring source** and **Edge Delivery Services (EDS)** as the delivery layer (Crosswalk).

The objectives of this design are to:

- preserve the client's current multi-region content operating model;
- support the existing **Global → Regional → Country → Locale** inheritance behavior;
- support the client's **selective translation** use case;
- define the EDS delivery setup (repoless, single site, path mappings, content bus);
- describe how inheritance and overrides are authored;
- clearly distinguish where the solution **aligns with Adobe best practice** and where it is a deliberate **customer-specific exception**, with the corresponding recommendation.

**Primary references:**
- [MSM Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm/best-practices)
- [Multi Site Manager and Translation](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm-and-translation)
- [Translation Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/translation/best-practices)
- [Authoring with AEM for Edge Delivery Services](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/wysiwyg-authoring/authoring)

---

## 2. Executive Summary

TFS's current content model can be reproduced in AEM as a Cloud Service with **AEM as the authoring source** and **Edge Delivery Services as the delivery layer**. With AEM as the authoring source, **the full set of AEM authoring capabilities remains available** — MSM, blueprints and live copies, inheritance, rollouts, workflows, and translation. EDS is the delivery tier and does not replace AEM authoring.

The current model is broadly:

```text
Global English
→ Regional English
→ Country English
→ Locale (language) branches
```

This design is **partially aligned** with Adobe best practice:

- **Global English → Regional English → Country English** is a valid **same-language MSM** pattern and aligns with best practice.
- The **locale (language) layer** is retained as **live copies** (rather than translation/language copies) to satisfy the client's **selective translation** requirement. This is a deliberate **customer-specific exception** to Adobe's standard multilingual model.

The proposed migration design **delivers the model the client has asked for** and is **technically achievable**. Where it deviates from best practice (the locale layer), the deviation, its rationale, and the best-practice recommendation are documented so the decision is explicit (Sections 4–7, 13).

---

## 3. Architecture Overview — Authoring Tier vs Delivery Tier

With AEM as the authoring source, the architecture is split across two tiers:

| Tier | System | Responsibilities |
|---|---|---|
| **Authoring tier** | AEM (as a Cloud Service) | Content authoring; **MSM, blueprints & live copies, inheritance, rollouts, workflows, translation**; page properties and metadata. System of record. |
| **Delivery tier** | Edge Delivery Services (EDS) | Fast delivery of published content; rendering and serving via the content bus and CDN. |

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

**Key takeaway:** EDS does not remove or replace AEM's authoring features. All MSM, inheritance, and translation concerns remain **authoring-time concerns in AEM**. When a page is **published**, it is ingested into the **Edge Delivery content bus** and served by Edge Delivery (preview and live), fronted by the CDN. EDS serves what has been published; it does not participate in MSM/inheritance logic.

**References:**
- [Authoring with AEM for Edge Delivery Services](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/wysiwyg-authoring/authoring)
- [Edge Delivery Services Overview](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/overview)

---

## 4. Adobe Best-Practice Reference Model

Adobe best practice for multilingual AEM Sites uses **MSM and Translation together**, with distinct responsibilities. The core principle, per Adobe's *Multi Site Manager and Translation* guidance, is to **keep MSM use within a single language**:

> "Limit the use of Multi Site Manager to content within one language." MSM should manage "the deployment of translated content from a blueprint (that is, a primary global) to the Live Copies (that is, the local sites), **within the boundaries of a language**." "MSM does not manage the different language versions as such" — translation services handle language conversion.

In other words:

- **MSM / Live Copies** are intended for **same-language reuse** — reusing content across regions/countries that share the **same** language (e.g. one translated German master reused by multiple German-speaking sites).
- **Translation / Language Copies** are intended for **cross-language localization** — producing a different language version of content.

The recommended workflow is therefore: translate the primary site into the required languages, then use MSM to reuse each translated language across the countries/regions that share that language. The defining best-practice constraint is that a **Live Copy relationship should stay within one language**, while moving **between languages** should be a translation/language-copy relationship.

**References:**
- [Translation Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/translation/best-practices)
- [Multi Site Manager and Translation](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm-and-translation)

---

## 5. Customer-Specific Requirement

TFS has a different operating requirement than the standard Adobe multilingual reference model. The client requires:

- **global English** as the starting source;
- **regional English** as an intermediate inheritance layer;
- **country English** as the operational source for country-level localization;
- **locale branches** that may be only **partially translated**;
- untranslated locale content to **continue inheriting from country English** until localized content is available.

This requirement is driven by the client's need for **selective translation**, partial locale coverage, **English fallback behavior** at the locale level, and country-specific source content rather than purely shared language-master-driven translation.

Because of this, the client's model does not follow the standard "translate the language master first, then roll out same-language locales" pattern in all cases.

---

## 6. Best-Practice Alignment and Deviation

The current TFS model is **partially aligned** with Adobe best practice.

### 6.1 Aligned Areas

The following are aligned with Adobe's MSM guidance, because they are **same-language reuse**:

- **Global English → Regional English** through MSM (live copies).
- **Regional English → Country English** through MSM (live copies).

### 6.2 Customer-Specific Deviation — the Locale Layer

Adobe's guidance is to keep a **Live Copy relationship within a single language**, and to use **Translation / Language Copies** when moving **between languages**. The TFS locale layer deviates from this in a specific way:

- **Best practice:** a Live Copy is used for **same-language** reuse (e.g. German master → German sites). Moving from English to German would be a **translation / language copy**.
- **TFS model:** the locale (language) branches are retained as **live copies that cross languages** — e.g. `de/de` (German) is a live copy of `de/en` (English). This is precisely the point of deviation: a live copy is being used **across** languages rather than **within** one language.

This is done so that **untranslated locale content continues to inherit from country English** and locales are never left with missing content.

Accordingly, the locale layer:

- is **technically achievable** and will be delivered as requested;
- is **not** the default Adobe best-practice multilingual reference model for cross-language branches;
- is retained **intentionally** to satisfy the client's selective-translation operating model.

**References:**
- [Multi Site Manager and Translation](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm-and-translation)
- [Translation Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/translation/best-practices)

---

## 7. Design Decision

The proposed migration design will **preserve the client's current inheritance model** for functional parity:

- the client's **same-language English inheritance** continues through MSM live copies;
- **locale branches remain live copies** of country English, preserving the client's **selective translation and English-fallback** behavior;
- the solution is documented as a **customer-specific migration design**, and the locale layer **should not** be interpreted as Adobe's default recommendation for all multilingual implementations.

If TFS were implementing a **net-new** multilingual solution strictly to Adobe best practice, the locale branches would normally be managed through **Translation / Language Copy** relationships rather than a fallback-oriented inheritance model tied to English (see the recommendation in Section 13).

---

## 8. Proposed Site Structure

The proposed structure follows the AEM country/language hierarchy convention — **country as the container, language nested beneath it** — and is based on the client's current architecture and business requirements.

```text
/content/lifetech/<region>/<country>/<language>
```

### 8.1 Proposed Tree

```text
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

### 8.2 Interpretation and Naming Convention

- `/global/en` is the global source.
- `/<region>/en` is the regional English layer.
- `/<region>/<country>/en` is the country English layer.
- `/<region>/<country>/<language>` is the locale (language) branch.
- **Country container first, language nested beneath it** (e.g. `ca/en`, `ca/fr`); ISO country and language codes used throughout.
- Each region has an English regional node (`<region>/en`) acting as the regional source; an `other/en` node is provided per region where applicable.

This hierarchy is recommended because it is clearer than a flat `en-br / pt-br` sibling structure, easier to govern, easier to explain to content authors, and consistent with AEM country/language hierarchy conventions.

---

## 9. MSM Relationships

The proposed inheritance model is:

```text
Global English
→ Regional English
→ Country English
→ Locale (language) branches
```

### 9.1 Intended Relationships

- **Global English** (`/content/lifetech/global/en`) is the **master / blueprint**.
- **Regional English** (`<region>/en`) nodes are **live copies** of Global English.
- **Country English** (`<region>/<country>/en`) nodes are **live copies** of their Regional English.
- **Locale (language) branches** (e.g. `de/de`, `ca/fr`, `cn/zh`) are **live copies** of their Country English source.
- Authors can create region-, country-, or language-specific content at any level and break inheritance for local overrides (Section 11).

### 9.2 Language Nodes as Live Copies — Rationale

At TFS, **translation is selective** — the entire English site is not fully translated for every locale; only specific content is translated, and the remaining content continues to be served in English.

To support this, the **locale (language) nodes are maintained as live copies** of their country-English source (rather than as pure translation/language copies):

- Translated content can be authored/overridden on the language node where translation exists.
- **Untranslated content continues to inherit from the country-English source**, so locales are never left with missing content where translation has not been performed.

The same MSM inheritance and override model (Section 11) therefore applies at the language level. As noted in Section 6.2, this is the **customer-specific deviation** from Adobe's standard cross-language recommendation.

---

## 10. EDS Delivery — Single Site, Path Mappings, Content Bus

### 10.1 Repoless Configuration & Admin API

The EDS site is configured using a **repoless setup**: site configuration is managed through the **Edge Delivery Services Admin API** rather than committed into a code repository. A single shared codebase serves the site, while the Admin API defines the content source, path mappings, and other delivery configuration. This allows a single EDS site definition to deliver the entire multi-locale content tree.

> **Reference:** [AEM Edge Delivery — Admin API](https://www.aem.live/docs/admin.html)

### 10.2 Single EDS Site

A **single EDS site** (e.g. `tfssite`) is created in the EDS Admin and serves **all locales**. Individual country/locale delivery URLs are defined through **path mappings** in the site configuration, which translate friendly delivery paths to the underlying content paths.

### 10.3 Path Mappings (example)

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

### 10.4 EDS Delivery URLs

| Delivery URL | Result |
|---|---|
| `<eds-url>.page/us/en` | US **preview** content |
| `<eds-url>.live/us/en/home` | US **live** (published) content |
| `<eds-url>.page/ar/en` | Argentina (English) **preview** content |
| `<eds-url>.live/ar/en` | Argentina (English) **live** content |
| `<eds-url>.page/de/de` | Germany (German) **preview** content |
| `<eds-url>.live/de/de` | Germany (German) **live** content |

(`.page` = preview tier; `.live` = published/live tier.)

### 10.5 Single Content Bus

All locales are served by the **single EDS site** and therefore share **one content bus**, so content across all locales is delivered within one consistent delivery scope.

---

## 11. Inheritance & Override Authoring

With MSM live copies in place, content rolled down from a blueprint can be **overridden locally** by breaking inheritance. The authoring experience differs between the **Universal Editor** and the classic **Page Editor**.

### 11.1 Inheritance — Core Concept

Inheritance links content such that changing the source (blueprint) automatically updates the linked live copy — unless inheritance has been broken for that content, in which case the local override is preserved when the blueprint is rolled out.

### 11.2 Authoring in the Universal Editor

Per Adobe documentation, in the Universal Editor:

- **Editing implicitly cancels inheritance.** When a page is part of MSM (or a Launch) and content is edited in the Universal Editor, the editor **automatically disables inheritance** for the edited content. As soon as a change is made, inheritance is implicitly cancelled, and the modified content is retained when updates are synchronised from the blueprint.
- **Component/block-level inheritance control requires the MSM Extension.** With the **AEM Multi-Site-Management (MSM) Extension** enabled, authors get toolbar controls and visual feedback at the component level:
  - **Inheritance Installed icon** — inheritance is active for the selected component; clicking it **breaks** inheritance (editing the component does this automatically as well).
  - **Inheritance Broken icon** — inheritance is disabled; clicking it **reinstates** inheritance (a page reload is required to display the re-inherited content).
- Without the MSM Extension enabled, authors **do not have visual indicators** of which components have inheritance broken vs preserved, and cannot manually manage component-level inheritance.
- The icons appear only when a component is **selected** and the page **derives from a blueprint**.
- **Page-level** inheritance can be reverted via the **Live Copy Overview Console**, the **Launches Console**, or the **Reset** button on the **Live Copy** tab in page properties.

> **Note:** The MSM Extension applies to **pages**, not Content Fragments.

### 11.3 Authoring in the Page Editor

The behavior and authoring experience for breaking inheritance in the classic **Page Editor** differs from the Universal Editor (e.g. inheritance controls are surfaced differently in the component toolbar / page properties). Teams authoring via the Page Editor should follow the classic MSM inheritance controls rather than the Universal Editor extension model described above.

### 11.4 Granularity Summary

| Level | What it controls | How it is broken / restored |
|---|---|---|
| **Page level** | Whole-page inheritance from the blueprint | Live Copy Overview Console / Launches Console / **Reset** on the Live Copy tab |
| **Component / block level** | Inheritance of an individual component | Universal Editor + MSM Extension (Inheritance Installed / Broken icons); editing a component auto-cancels its inheritance |

### 11.5 References

- [Universal Editor — Inheritance](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/inheritance)
- [Universal Editor — Authoring (Inheritance)](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/authoring#inheritance)

---

## 12. Site Delegation — Secondary Countries

### 12.1 Use Case

Some countries do not have their own content node in the MSM structure but still require a site presence under their own country URL. These **secondary countries** serve the content of a designated **primary country**. For example, Belgium has no dedicated content node and serves the United Kingdom's content — while the Belgium country URL continues to exist and is served to visitors.

The requirement is that the **secondary country URLs must exist and remain in place**, while the content displayed is that of the mapped primary country.

### 12.2 Proposed Approach — Delegation at the CDN

Secondary-country delegation is handled at the **CDN** through edge logic, using a **secondary-to-primary country mapping**.

- A mapping defines, for each secondary country, the primary country whose content it should serve (e.g. Belgium → United Kingdom).
- At request time, the edge logic determines the page's country from the locale. **If the country is a secondary country**, the request is **internally rewritten** to serve the **primary country's content**, while the **secondary country URL is preserved** in the browser. The visitor sees the secondary country's URL but is served the primary country's content.
- **On-page links / hrefs** are rewritten to reflect the **secondary country URL**, so navigation within the delegated site remains consistent with the secondary country's URL space.

### 12.3 SEO Handling — Canonical to Primary Country

Because this approach serves the same content under more than one country URL, the content is effectively **duplicated** across the primary and its delegated secondary URLs. As in the current implementation, a **canonical reference to the primary country** is set on the delegated pages, so search engines treat the **primary country URL as the authoritative version**. This handles SEO for the duplicated content.

---

## 13. Recommendation

### 13.1 Adobe Best-Practice Recommendation

Adobe's standard recommendation for multilingual sites is to:

- use **MSM / Live Copies** for **same-language reuse**;
- use **Translation / Language Copies** for **cross-language branches**;
- use **language-master-based reuse** where translated languages are shared across multiple countries.

### 13.2 Proposed Recommendation for TFS

For this migration, the recommended implementation is to:

- **preserve** the client's current **Global English → Regional English → Country English → Locale** model for business continuity;
- **normalize** the structure into a clear **country → language** hierarchy (Section 8);
- **document the locale layer as a customer-specific exception** (Sections 6–7);
- **preserve** the client's selective-translation and English-fallback behavior.

As a forward-looking recommendation, if TFS wishes to move closer to Adobe best practice in the future, the **locale (language) branches should be reconsidered for redesign** — managed through **Translation / Language Copy** relationships rather than a fallback-oriented live-copy model tied to English. This is offered as a recommendation for a future phase, not a blocker for the migration.

---

## 14. Risks and Trade-offs

| Area | Observation |
|---|---|
| Best-practice alignment | Locale inheritance behavior differs from Adobe's standard multilingual recommendation (cross-language via translation/language copies). |
| Governance | Requires stronger control over where authors edit, who triggers rollouts, and how locale translation exceptions are handled. |
| Translation operations | The selective-translation model is more specialized than a pure language-copy model. |
| Long-term maintainability | Future simplification may require restructuring the locale layer if TFS wants closer best-practice alignment. |
| Delivery validation | Locale fallback behavior must be validated in the target AEM + EDS publishing model to ensure the expected experience is delivered. |

---

## 15. Conclusion

The TFS current architecture is **technically achievable in AEM as a Cloud Service** with EDS delivery, and can be preserved for migration continuity.

The design is **partially aligned** with Adobe best practice:

- the **English inheritance layers** (Global → Regional → Country) are aligned;
- the **locale (language) layer** is a deliberate **customer-specific exception**, retained to support selective translation and English fallback.

Accordingly, the structure proposed here is presented as a **client-specific migration design** based on the client's current operating model — **not** as Adobe's default multilingual reference architecture. Where it deviates, the deviation and the best-practice recommendation (Section 13) are stated explicitly so the decision is transparent and revisitable in a future phase.

> Activation, rollout cascade, and publication behavior are covered in a separate document (*MSM Activation Cascade — EDS Solution Design*).

---

## 16. References

- [MSM Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm/best-practices)
- [Multi Site Manager and Translation](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/msm-and-translation)
- [Translation Best Practices](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/administering/reusing-content/translation/best-practices)
- [Universal Editor — Inheritance](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/inheritance)
- [Universal Editor — Authoring (Inheritance)](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/authoring#inheritance)
- [Authoring with AEM for Edge Delivery Services](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/wysiwyg-authoring/authoring)
- [Edge Delivery Services Overview](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/overview)
- [Using Edge Delivery Services with AEM](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/edge-delivery/using)
- [AEM Edge Delivery — Admin API](https://www.aem.live/docs/admin.html)
