# Migration Strategy — Reference Components

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Migration strategy for AEM Reference Components (`foundation/components/reference`)

| | Environment |
|---|---|
| **Source** | AEM On-Premise instance (AEM 6.4) — existing production environment |
| **Target** | AEM Author instance on AEM as a Cloud Service — with Edge Delivery Services as the delivery layer |

---

## Overview

This document defines the migration strategy for **AEM Reference Components** (`foundation/components/reference`) from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS).

Reference Components are AEM's content-reuse mechanism — a page embeds a specific component that physically lives on a dedicated reference-components source page, rather than duplicating the content across pages. In the target environment, content reuse is achieved through **EDS fragments**. The migration converts each referenced component into an EDS fragment in the target AEM authoring instance, and replaces the reference on every consuming page with a **fragment block**.

Reference Component handling runs **as part of page migration** — when a page containing reference components is migrated, its references are resolved and converted as described below.

---

## 1. What Are Reference Components

### The Pattern in AEM

TFS uses dedicated **reference-components pages** in AEM to store reusable content — primarily raw HTML embeds, scripts, and structured content blocks that need to appear consistently across multiple pages. Instead of duplicating this content on every page, pages embed it using the `foundation/components/reference` component, which points to the specific component node on the source page by its **JCR path**.

```
reference-components/
  └── gds/          ← source page holding reusable components
  └── (others...)   ← other source pages per use case

Consuming page A
  └── MainParsys
       ├── reference_ad2b  [foundation/components/reference]
       │     path → .../reference-components/gds/.../ltrawhtml_519f
       └── reference_fa59  [foundation/components/reference]
             path → .../reference-components/gds/.../ltrawhtml_def9

Consuming page B
  └── MainParsys
       └── reference_ad2b  [foundation/components/reference]
             path → .../reference-components/gds/.../ltrawhtml_519f
             (same component, same source — single source of truth)
```

One component in the source page is shared across as many consuming pages as needed. A change to the source updates everywhere it is referenced.

> **Locale note:** Each locale has its own reference-components page, and a page references the component on its **same-locale** reference-components page. The locale of the source reference path therefore aligns with the locale of the consuming page — this is what makes the fragment-path derivation in Section 4 produce the correct per-locale fragment.

### How It Renders on Live Pages

On the live site, the AEM render engine outputs a wrapper div with the CSS class `reference parbase section` around the referenced component's rendered output. This class is the identifiable signal in scraped HTML that a reference component is present on the page.

```
<div class="reference parbase section">
  <div class="cq-dd-paragraph">
    <div class="ltrawhtml_def9 LTRawHTML">
      <!-- referenced component content rendered here -->
    </div>
  </div>
</div>
```

The **inner class** (`ltrawhtml_def9`) reflects the JCR node name of the referenced component on the source page. This is useful for reliably pairing a detected reference with its source (see Section 5).

---

## 2. What Changes in the Target

In AEM On-Prem, the reference component is a **pointer** — it resolves at render time to a component on another page. Content reuse in the target environment is done through **EDS fragments** — standalone content that any page includes via a **fragment block**.

The migration replaces every AEM reference component with an **EDS fragment block**. The referenced content becomes an **EDS fragment in the target AEM authoring instance**, published to EDS. Single source of truth is fully preserved — the fragment is authored once and included on as many pages as needed, exactly as today.

This is a **content transformation**, not a node move: the source `foundation/components/reference` pointer and the referenced component's AEM resource types do not exist in the target; the referenced content is transformed into the target EDS block structure and created fresh.

---

## 3. Detection — How the Migration Identifies Reference Components

**Step 1 — HTML detection**
During page migration, the scraped page HTML is scanned for the CSS class `reference parbase section`. If found, the page contains one or more reference components.

**Step 2 — JCR lookup**
The rendered HTML confirms a reference component is present but does **not** contain the source path it points to. A JCR query is performed on that page to retrieve the `path` property of each `foundation/components/reference` node. This `path` is the JCR address of the actual component being referenced — the source of the EDS fragment.

```
HTML scan → "reference parbase section" found
                    ↓
JCR query → get path property of each reference node
            e.g. /content/lifetech/north-america/en-us/
                 reference-components/gds/jcr:content/MainParsys/ltrawhtml_519f
```

---

## 4. Fragment Path Derivation

The target fragment path is derived from the AEM `path` property by a deterministic rule — no pre-built mapping table is required.

### Derivation Rule

```
AEM path:
/content/lifetech/{region}/{locale}/reference-components/{name}/jcr:content/MainParsys/{component-node}

Strip /content/lifetech/ prefix
Strip /jcr:content/MainParsys/ middle segment
Normalise underscores to hyphens in component node name
Insert /fragments/ after locale

Target fragment path:
{region}/{locale}/fragments/reference-components/{name}/{component-node}
```

### Examples

| AEM Reference Path | Target Fragment Path |
|---|---|
| `/content/lifetech/north-america/en-us/reference-components/gds/jcr:content/MainParsys/ltrawhtml_519f` | `north-america/en-us/fragments/reference-components/gds/ltrawhtml-519f` |
| `/content/lifetech/north-america/en-us/reference-components/gds/jcr:content/MainParsys/ltrawhtml_def9` | `north-america/en-us/fragments/reference-components/gds/ltrawhtml-def9` |
| `/content/lifetech/global/en/reference-components/gds/jcr:content/MainParsys/ltrawhtml_519f` | `global/en/fragments/reference-components/gds/ltrawhtml-519f` |

Because each locale has its own reference-components page, the locale of the source reference path is the locale of the derived fragment — ensuring the correct locale fragment is used for each site.

---

## 5. Migration Steps

Reference Component handling is performed within the page migration pipeline.

### Step 1 — Scrape Page HTML
Standard page scraping. The full page HTML is fetched.

### Step 2 — Detect Reference Components
Scan the scraped HTML for `class="reference parbase section"`. If found, the page contains one or more reference components.

### Step 3 — Query JCR for Reference Paths
For each reference detected, query the JCR of the page being migrated to retrieve the `path` property of the corresponding `foundation/components/reference` node, giving the full AEM source path.

> **Pairing must be reliable.** Where a page contains multiple references, each detected reference must be matched to the correct source path. The rendered HTML exposes the **inner component node-name class** (e.g. `ltrawhtml_def9`), which corresponds to the source component node name — this is used to pair a detected reference with its JCR `path` reliably, rather than relying on document order alone.

### Step 4 — Derive Fragment Path
Apply the derivation rule (Section 4) to each AEM source path to compute the corresponding target fragment path.

### Step 5 — Create or Reuse Fragment
For each derived fragment path:

```
Does this fragment already exist?
  YES → skip creation, proceed to Step 6
   NO → fetch the referenced component content from JCR at the source path
        → transform to EDS block format
        → create as a new EDS fragment in the target AEM authoring instance
          at the derived path, and publish
        → mark as created (cached for the remainder of the migration run)
```

This ensures the same fragment is never created twice, regardless of how many pages reference it — preserving single source of truth.

### Step 6 — Replace Reference with Fragment Block
In the migrated page, replace each reference (the `reference parbase section` wrapper) with an **EDS fragment block** pointing to the corresponding target fragment path.

### Step 7 — Continue Page Assembly
The page, with reference components replaced by fragment blocks, is assembled as normal and created in the target AEM authoring instance at the mapped page path (as part of standard page migration).

---

## 6. Fragment Placement and MSM

Reference-component fragments are a **content-reuse artifact**, not inheritance content. They are created by the migration at the **derived locale path** for each locale that has them (per the source reference-components page for that locale). They are **not** rolled out via MSM as part of this migration — each locale's reference-component fragments are derived from that locale's own reference-components source page.

> Whether reference-component fragments should additionally participate in MSM inheritance going forward is an authoring-model decision for TFS; it is not required for the migration, which creates each locale's fragments directly from its source.

---

## 7. Validation

Validation confirms that reference components are **completely and correctly converted** to fragments and that single source of truth is preserved. Validation is performed as part of page migration validation.

| Check | Detail |
|---|---|
| **Completeness / reconciliation** | Every `reference parbase section` detected on a migrated page has a corresponding fragment block pointing to a valid target fragment path. The number of fragment blocks on the migrated page matches the number of references on the source page. |
| **Create-or-reuse correctness** | Each unique referenced component results in exactly **one** fragment; multiple pages referencing the same component all point to the same fragment (no duplicates created). |
| **Content fidelity** | The migrated fragment content matches the referenced source component content — no content lost in transformation. |
| **Single source of truth** | Updating a fragment is reflected on all consuming pages (reuse behavior preserved). |
| **Per-locale correctness** | The fragment referenced by a consuming page resolves to the correct locale fragment (consistent with the locale of the consuming page). |

---

## 8. Before and After

### AEM On-Prem — Current State

```
Consuming page JCR:
└── MainParsys
     ├── reference_ad2b  [sling:resourceType: foundation/components/reference]
     │     path: /content/lifetech/north-america/en-us/
     │           reference-components/gds/jcr:content/MainParsys/ltrawhtml_519f
     └── reference_fa59  [sling:resourceType: foundation/components/reference]
           path: /content/lifetech/north-america/en-us/
                 reference-components/gds/jcr:content/MainParsys/ltrawhtml_def9

Rendered HTML:
<div class="reference parbase section">
  <div class="ltrawhtml_519f LTRawHTML"> ... </div>
</div>
<div class="reference parbase section">
  <div class="ltrawhtml_def9 LTRawHTML"> ... </div>
</div>

Source of truth: reference-components/gds page in AEM Sites
Reuse mechanism: foundation/components/reference pointer resolved at render time
```

### Target — After Migration

```
EDS fragments created in the target AEM authoring instance:
  north-america/en-us/fragments/reference-components/gds/ltrawhtml-519f
  north-america/en-us/fragments/reference-components/gds/ltrawhtml-def9

Consuming page in the target:
  [fragment block]
    path: north-america/en-us/fragments/reference-components/gds/ltrawhtml-519f

  [fragment block]
    path: north-america/en-us/fragments/reference-components/gds/ltrawhtml-def9

Source of truth: EDS fragments
Reuse mechanism: Fragment block — same fragment referenced by any number of pages
```

### What Changes — Summary

| Dimension | AEM On-Prem | Target (AEM Authoring + EDS) |
|---|---|---|
| Content storage | Component node on reference-components source page | EDS fragment in the target AEM authoring instance |
| Reuse mechanism | `foundation/components/reference` pointer by JCR path | Fragment block by fragment path |
| Detection in HTML | `class="reference parbase section"` | Fragment block on the page |
| Source path lookup | JCR `path` property on reference node | Fragment path derived by rule |
| Single source of truth | Yes — one AEM component, many pages | Yes — one fragment, many pages |
| Author update | Edit component on source reference-components page | Edit the fragment |

---

## 9. Assumptions

1. Reference Component handling runs as part of page migration — when a page containing references is migrated, its references are resolved and converted in the same pass.

2. Each locale has its own reference-components page; a consuming page references the component on its same-locale source page. The locale of the source reference path therefore determines the locale of the derived fragment.

3. The reference source path is obtained via JCR lookup — the rendered HTML signals the presence of a reference but does not contain the source path.

4. Where a page has multiple references, pairing of each detected reference to its source path is done reliably using the inner component node-name class exposed in the HTML, not document order alone.

5. The create-or-reuse rule ensures one fragment per unique referenced component, preserving single source of truth across all consuming pages.

6. A transformation is available to convert the referenced component content into the target EDS block format.

7. Reference-component fragments are created at the derived locale path and published; participation in MSM inheritance is a separate authoring-model decision and is not required for the migration.
