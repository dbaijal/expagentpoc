# EDS Migration Strategy — Overview

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

| | Environment |
|---|---|
| **Source** | AEM On-Premise instance (AEM 6.4) — existing production environment |
| **Target** | AEM Author instance on AEM as a Cloud Service — with Edge Delivery Services as the delivery layer |

---

## 1. Overview

This page provides an overview of the migration strategy for moving TFS from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS), where **AEM remains the authoring source** and EDS is the delivery layer. Detailed strategies for each content type are captured in their respective sub-pages (Section 6).

Migration is primarily **scrape-based**: the migration tooling analyses each page on the live reference site, identifies the UI elements in the rendered output, and translates them into EDS blocks with their content. **The existing CMS is not modified during migration and remains fully operational throughout** — the new environment is built in parallel.

---

## 2. Migration Method — Scraping Supplemented by JCR

While scraping the live site is the primary method, **the whole site cannot be migrated through live-site scraping alone.** The rendered HTML of a live page does not always contain everything required to migrate it accurately, and some content is not publicly available to scrape at all. Migration therefore combines scraping with **JCR lookup/extraction** where needed:

- **Experience Fragment references on pages** — the rendered page shows the resolved fragment content, but **not** the XF source path. A JCR lookup is required to identify which XF a page references so it can be mapped to the corresponding EDS fragment.
- **Forms** — the live HTML does not carry the complete form configuration (field definitions, action/endpoint references, division mapping). Form data is read from the JCR.
- **Reference components** — the live HTML signals their presence but not the source path; a JCR lookup resolves it.
- **Non-public content** — Experience Fragments, Content Syndication Pods, and the non-public Global/Regional nodes have no public live URL to scrape, and are migrated through a JCR-based, script-driven approach.

In summary: migration is **scrape-based where the live HTML is sufficient, supplemented by JCR lookup where it is not, and fully JCR-based for non-public content.** This is reflected in the per-content-type strategies (Section 6).

It is also important to note that this is a **replatforming, not a lift-and-shift**: source AEM component resource types and templates do not exist in the target (which uses EDS blocks), so content is **transformed** into the EDS model and created fresh — it is **not** a JCR content-package move.

---

## 3. Inheritance and Migration Sequence (MSM)

TFS is a single large site operating an **AEM Multi-Site Manager (MSM)** model across approximately 45 locales, with content flowing top-down:

```
Global English  →  Regional English  →  Country English  →  Translated Country Language
```

Because the target re-establishes MSM inheritance fresh (it is not carried over from source), migration follows the inheritance hierarchy **top-down**:

- **Global English is migrated first**, as it is the origin of the entire inheritance chain.
- **Regional** and then **Country** levels are established by **MSM rollout** from their parent in the target, which creates the live-copy structure and inheritance relationships.
- At each level, only the **delta pages** — those where inheritance has been broken and local content exists — are migrated on top of the rolled-out structure. Pages that simply inherit do not need individual migration; their content is served via inheritance.
- Delta migration is **inheritance-aware**: overrides are applied only where local content differs from the inherited content, and inheritance is preserved everywhere else.

This sequencing means the validation effort invested at the Global level directly reduces the scope of every subsequent level, since only deltas are migrated thereafter. Full detail is in the Content Pages migration strategy.

---

## 4. Go-Live and Rollback

Migrated content is published to its **Edge Delivery (EDS) URLs**, where it can be **verified in production conditions ahead of go-live** — before any live traffic is routed to it. Go-live and rollback are handled at the **CDN routing layer**: go-live routes live traffic to the verified EDS content, and rollback reverts that routing so traffic is served as before. The existing environment is retained until go-live is confirmed stable.

The detailed sequencing and grouping of go-live (and the associated rollback approach) are confirmed as part of **joint go-live planning between TFS and Adobe**.

---

## 5. Validation

Each content area is validated after migration before sign-off — for example, rendered content/parity checks for pages, and completeness/reconciliation checks for content extracted via JCR. **TFS sign-off** is obtained at each stage. Validation specifics are described within each content-type strategy.

---

## 6. Detailed Migration Strategies

Each content type has a dedicated strategy page covering its detailed approach:

- **Content Pages** — scrape + JCR lookup; top-down wave migration with inheritance-aware delta handling.
- **Experience Fragments** — JCR-based migration to a centralized fragment library; best-match resolution preserved.
- **Forms** — JCR read for complete form configuration.
- **Reference Components** — flattened into host pages by default; only genuinely reused content promoted to fragments.
- **Content Syndication Pods** — JCR-discovery + public render fetch; delivered via the `aem-embed` Web Component.
- **Metadata** — extracted from page `<head>`; per-page values as page properties, site-wide values via bulk metadata.
- **Redirects** — consolidated from existing sources into the EDS redirects mechanism (simple) and CDN (pattern-based).
- **Assets** — covered in the dedicated asset migration strategy.

---

## 7. Assumptions

1. AEM remains the authoring source; EDS is the delivery layer.
2. The existing CMS remains operational throughout migration and is not modified.
3. Non-public content (Experience Fragments, Content Syndication Pods, Global/Regional nodes) is migrated via a JCR-based approach, as it cannot be scraped from public URLs.
4. MSM inheritance is re-established in the target through rollout; only delta (override) pages are migrated per level.
5. Go-live and rollback are handled at the CDN routing layer, with sequencing confirmed during joint go-live planning.
