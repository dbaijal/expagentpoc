# Migration Strategy — Content Pages

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Migration strategy for content pages — all site levels

| | Environment |
|---|---|
| **Source** | AEM On-Premise instance (AEM 6.4) — existing production environment |
| **Target** | AEM Author instance on AEM as a Cloud Service — with Edge Delivery Services as the delivery layer |

---

## 1. TFS Content Hierarchy

TFS operates a four-level content hierarchy built on AEM Multi-Site Manager (MSM). Content flows top-down through this hierarchy via MSM rollout and live copy relationships.

```
Global English  (global/en)
        │
        │  MSM Rollout
        ▼
Regional English  (europe/en, north-america/en, ipac/en, greater-china/en etc.)
        │
        │  MSM Rollout
        ▼
Country English  (en-us, en-uk, en-br, en-de, en-fr, en-cn, en-kr, en-jp etc.)
        │
        │  MSM Live Copy
        ▼
Translated Country Language  (de-de, fr-fr, pt-br, zh-cn, ko-kr, ja-jp etc.)
```

**Key characteristics of this hierarchy:**

| Level | Customer-Facing | Role |
|-------|----------------|------|
| Global English | No | Master content source — origin of the entire inheritance chain |
| Regional English | No | Inheritance intermediary between Global and Country sites |
| Country English | Yes | Primary customer-facing site per market |
| Translated Country Language | Yes | Localized version of Country English |

**Additional structural note:**
- **UK English (en-uk) acts as a Western Europe super-hub** — en-fr, en-de, en-es inherit from en-uk rather than directly from the Europe Regional EN node. This creates an additional inheritance layer within the Country English level that must be accounted for in the migration sequence.

---

## 2. Why JCR Content Cannot Be Migrated via Package

This migration is a **replatforming** — not a lift-and-shift. The content structure in source AEM 6.4 and target AEMaaCS are fundamentally incompatible.

| Source (AEM 6.4 On-Prem) | Target (AEMaaCS + EDS) |
|--------------------------|------------------------|
| Custom component resource types (`tfsite/components/*`, `lifetech/components/*`, `formcommons/components/*`) | EDS block types (`core/franklin/components/block/v1/block` etc.) |
| Custom AEM templates | EDS page templates — different structure |
| parsys / responsivegrid node hierarchy with nested component nodes | Flat EDS block structure |
| Component-specific JCR properties | EDS block properties |
| MSM metadata tied to source component structure and paths | MSM re-established fresh in target AEMaaCS |

Moving a JCR content package from source to target would result in pages with resource types that do not exist in the target environment — nothing would render and MSM relationships would reference invalid paths. Content must therefore be extracted, transformed to EDS block format, and created fresh in the target AEM authoring instance. MSM inheritance relationships must be established fresh in AEMaaCS through rollout — they are not carried over from source.

---

## 3. Migration Approach — Wave Strategy

The migration follows the inheritance hierarchy top-down. Global English must be migrated first as it is the origin of the entire inheritance chain. Each subsequent level is established through MSM rollout in the target, followed by migration of only the pages that differ from what they would inherit.

### 3.1 Wave Overview

| Wave | Scope | Approach |
|------|-------|----------|
| Wave 0 | Forms XF and Site XF migration | JCR extraction, transform, content package — prerequisite for all page migration |
| Wave 1 | Global English — all active pages | Migrate via EMA / scraping; must be made accessible |
| Wave 2 | Regional English — delta pages only | Rollout from Global first; identify and migrate delta pages |
| Wave 3 | Country English — delta pages only | Rollout from Regional first; identify and migrate delta pages |
| Wave 4 | Translated locales — cancelled inheritance pages only | Rollout from Country EN first; identify and migrate translated delta pages |

### 3.2 Critical Prerequisite — Global and Regional Content Must Be Accessible

Global English and Regional English nodes are **not customer-facing** and are not accessible via public URLs. The Experience Modernization Agent (EMA) tool, which is the primary mechanism for content migration and transformation to EDS blocks, requires access to rendered HTML to function.

**Global English and Regional English content must be made accessible before Wave 1 and Wave 2 migration can begin.** This is a hard prerequisite — without it, EMA cannot process these pages.

> **Action required:** TFS and Adobe to jointly determine how Global English and Regional English publish content can be made accessible for the duration of migration — whether via VPN access to the AEM publish instance, a staging URL, or another mechanism. This must be confirmed with TFS infrastructure and validated with the Adobe EMA team before migration planning is finalized.

Country English and Translated Locale sites are publicly accessible via live site URLs and do not have this constraint.

---

## 4. Detailed Wave Approach

### Wave 1 — Global English Migration

Global English is the master content source and must be fully migrated before any other wave begins. Regional, Country, and Translated sites all trace their inheritance to this single source. Until Global English exists in the target AEMaaCS environment, there is no base for MSM rollout.

**Migration flow:**

```
Global English publish content made accessible
              │
              ▼
TFS provides confirmed list of active master pages
under /content/lifetech/global/en
(No JCR query can reliably determine active vs stale pages — TFS content owners confirm)
              │
              ▼
EMA tool processes each page:
  Reads rendered HTML from accessible publish URL
  Identifies AEM components and their content
  Transforms to EDS block structure
  Creates page in target AEM author instance
              │
              ▼
XF references on migrated pages resolved and replaced with
corresponding target fragment paths
(via JCR lookup + XF-to-Fragment path mapping table from Wave 0 — see Section 6)
              │
              ▼
Parity validation — all Global EN pages
              │
              ▼
TFS sign-off on Global EN migration
              │
              ▼
MSM blueprint configured in AEMaaCS with Global EN as source
```

**Advantage of validating Global EN upfront:**
Once Global English pages are validated and signed off by TFS, those pages serve as the inherited base for all regional, country, and locale sites. The volume of pages requiring individual migration and parity checks in subsequent waves is only the delta — the pages that differ from what Global EN provides. This means the validation effort invested in Wave 1 directly reduces the scope of all subsequent waves.

### Wave 2 — Regional English Migration

Regional English hubs (europe/en, north-america/en, ipac/en, greater-china/en etc.) are inheritance intermediaries — the majority of their pages cleanly inherit from Global English. Only pages that have been locally modified or created at the regional level require migration.

**Step 1 — Rollout first:**
MSM rollout is triggered from Global English to each Regional English hub in AEMaaCS. This creates live copy pages in the target Regional English nodes, with MSM relationships established. Rollout must complete before delta migration begins — delta pages are migrated on top of the rollout-established structure.

**Step 2 — Delta identification script:**
Adobe builds a JCR script that runs against each regional hub path in the source AEM. For each page found, the script checks for the following conditions:

| Condition | JCR Signal | Migration Action |
|-----------|-----------|-----------------|
| Page-level inheritance cancelled | `cq:LiveSyncCancelled` mixin present on `jcr:content` | Page has fully regional-specific content — migrate as delta |
| Block/component-level inheritance cancelled | `cq:isCancelled = true` on one or more component nodes under `jcr:content` | Page has partially regional-specific content — migrate as delta |
| New component added on page | Component node present under `jcr:content` with no corresponding `cq:LiveRelationship` on that component node | Additional content added at regional level on top of the inherited page — migrate as delta |
| Page created locally at regional level | No `cq:LiveRelationship` mixin on `jcr:content` | Brand new regional page not sourced from Global — migrate as delta |

Any page meeting one or more of these conditions is a delta page for that regional hub. The script outputs a flat list of delta page paths. This list is a **first-pass identification** of candidate pages; the precise block-level content to override is determined during delta migration (Step 4).

**Step 3 — TFS review gate:**
The delta list is reviewed by TFS content owners before migration runs. The script identifies all technical delta pages but cannot determine business relevance — stale, deprecated, or retirement-candidate pages may appear in the list. Only TFS content owners can make that call. The approved list drives migration.

**Step 4 — Delta migration (inheritance-aware):**
Approved delta pages are migrated on top of the rollout-established live copy structure in the target, **without disconnecting them from the inheritance chain**. The migration must apply overrides surgically — preserving inheritance for everything that has not changed, and cancelling inheritance only where local content exists.

To achieve this, the migration compares two versions of the page **in the target (EDS-block) format**:

```
Target rolled-out page   →  the content the page would inherit (from Global EN rollout)
        vs
Migrated source page     →  the actual regional page content (transformed to EDS blocks)
```

The script compares these block by block and acts as follows:

| Comparison result | Action |
|---|---|
| Block content is **the same** as the inherited version | Leave inheriting — do not override, do not modify |
| Block content is **changed** | Cancel inheritance on that block and write the updated content |
| Block is **new** (present in source, not in inherited version) | Add the new block; existing inherited blocks remain untouched |

This way, MSM relationships established by rollout are preserved, inheritance remains intact for unchanged content, and only genuinely overridden or added content breaks inheritance. Overwriting the full page or its MSM metadata is avoided — which would otherwise turn live copy pages into standalone orphan pages disconnected from the inheritance chain.

> **What the migration logic looks for (conceptual):** the inherited (rolled-out) page content vs the actual source page content, compared per block — to determine which blocks are unchanged (keep inheriting), which are overridden (cancel inheritance + write), and which are newly added. The detailed scripting is an implementation-phase concern; the strategy point is that delta migration is **inheritance-aware** and override-only.

**Step 5 — Parity validation:**
Parity check is run for all migrated delta pages — source path vs target path. Since the non-delta pages are served from Global EN via MSM rollout, parity validation is only needed for delta pages — a significantly smaller set than the full regional site. In addition, MSM inheritance integrity is verified for delta pages (see Section 5).

### Wave 3 — Country English Migration

Country English sites sit one level below Regional English. The same approach applies — rollout first from Regional English to establish live copies in target, then identify and migrate delta pages.

**Important — UK English as Western Europe super-hub:**
en-uk must be rolled out from Europe Regional EN before en-fr, en-de, and en-es can be rolled out from en-uk. The rollout sequence within Country English must respect this intermediate blueprint relationship.

**Rollout sequence for Western Europe:**
```
Europe Regional EN
      │ rollout
      ▼
en-uk (UK English)
      │ rollout
      ▼
en-fr, en-de, en-es
```

The same delta identification script, TFS review gate, inheritance-aware delta migration, and parity validation steps from Wave 2 apply at Country English level.

### Wave 4 — Translated Locales Migration

Translated locales (de-de, fr-fr, pt-br, zh-cn, ko-kr, ja-jp etc.) are MSM live copies of their respective Country English sources. Translation is selective in TFS — not all pages are translated. Pages that have not been translated continue to serve Country English content via live copy.

**Step 1 — Rollout from Country EN:**
MSM rollout from each Country English site to its corresponding translated locale creates live copy pages in the target. These live copy pages serve Country English content for all pages that have not been translated — preserving the existing selective translation behaviour.

**Step 2 — Delta identification — translated pages only:**
The delta script identifies pages in the translated locale where inheritance has been cancelled — meaning actual translated content exists on the page. Only these pages need to be migrated. Pages where inheritance is intact (not yet translated) remain as live copies serving Country EN content — no migration needed.

This is different from a full-locale migration — only the translated subset requires migration, consistent with TFS's existing selective translation model.

**Step 3 — Inheritance-aware delta migration, TFS review, parity validation:**
Same approach as Waves 2 and 3. Translated delta pages are migrated from the translated locale's publicly accessible live site URLs, applied on top of the rolled-out live copy structure using the same inheritance-aware (override-only) comparison. Parity check is run against migrated translated pages only.

---

## 5. Validation — Parity Check After Each Wave

After each migration wave, validation is performed between the source and target for all migrated pages.

**Rendered content parity:**
A parity check compares source and target page output using internal Adobe QA tooling — source and target page paths are provided and the tool compares rendered output, flagging differences for review. This significantly reduces manual validation effort compared to page-by-page review.

> **Parity is content/structural, not pixel-identical.** EDS delivers different markup/DOM/CSS than AEM 6.4, so a naive rendered diff will produce false positives. Parity validation confirms that the **same content, components, links, and assets** are present and correct — not that the DOM or pixels are identical.

**MSM inheritance integrity (delta waves):**
For delta pages (Waves 2–4), rendered parity alone is not sufficient — it does not confirm that inheritance is still intact. A structural MSM check verifies that:
- Blocks that were left inheriting still resolve their content from the parent (not silently overridden).
- Only the intended blocks have inheritance cancelled.
- The page remains a connected live copy (not orphaned) — i.e. a future rollout from the parent would still propagate to the unchanged parts of the page.

**Asset rendering:**
Migrated pages are checked to confirm that referenced images and media resolve and display correctly in the target. (Asset migration itself is covered in a separate migration document.)

**Validation scope per wave:**

| Wave | Pages validated | Checks |
|------|----------------|--------|
| Wave 1 — Global EN | All migrated Global EN pages (full set) | Rendered parity, asset rendering |
| Wave 2 — Regional EN | Delta pages only | Rendered parity, MSM inheritance integrity, asset rendering |
| Wave 3 — Country EN | Delta pages only | Rendered parity, MSM inheritance integrity, asset rendering |
| Wave 4 — Translated Locales | Delta (translated) pages only | Rendered parity, MSM inheritance integrity, asset rendering |

**TFS sign-off is required after each wave** before the next wave begins. This gates the rollout step for the next level and ensures the inheritance base is stable and approved before dependent sites are built on top of it.

---

## 6. Migration Pipeline — Parsers and Transformers

The page migration pipeline has a defined structure applicable to all waves where HTML scraping is used. It consists of a sequence of parsers and transformers applied to each page.

```
Source page HTML (from accessible publish URL)
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│  PARSER                                                  │
│  Reads and parses the rendered HTML of the source page   │
│  Identifies all AEM components on the page by their      │
│  rendered HTML structure and CSS classes                 │
│  Produces a structured component inventory per page      │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  TRANSFORMERS  (one per component / block type)          │
│  Each transformer maps one AEM component type to its     │
│  corresponding EDS block type and maps source properties │
│  to target block properties                              │
│                                                          │
│  e.g. AEM text component → EDS text block               │
│       AEM image component → EDS image block              │
│       AEM teaser component → EDS teaser block            │
│  ...                                                     │
│                                                          │
│  SPECIAL HANDLERS — run within the transformer pipeline: │
│                                                          │
│  Form detection handler:                                  │
│    Detects pages containing forms by identifying the     │
│    form container CSS class in the parsed HTML           │
│    (cmp-p-form-container or equivalent marker)           │
│    Flags the page for form migration handling            │
│    Reads form structure from source JCR directly         │
│    (form content is not fully represented in rendered    │
│    HTML — JCR read is required for complete form data)   │
│                                                          │
│  Experience Fragment reference handler:                   │
│    An XF's source path is NOT present in the rendered    │
│    page HTML — only the resolved fragment content is.    │
│    The handler detects the presence of an embedded XF    │
│    by its rendered marker / CSS class, then performs a   │
│    JCR lookup on the source page to obtain the actual    │
│    XF path that the page references. That XF path is     │
│    matched against the XF-to-Fragment path mapping table │
│    (produced during Wave 0 XF migration), and the page   │
│    is authored with the EDS fragment block pointing to   │
│    the corresponding target fragment path.               │
│                                                          │
│  Reference Component handler:                             │
│    Detects Reference Components on the page. A JCR        │
│    lookup identifies the referenced component/content.   │
│    If a corresponding fragment does not already exist    │
│    in the target, the referenced content is migrated to  │
│    a fragment, and the page is authored with the EDS     │
│    fragment block pointing to that target fragment path. │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  OUTPUT                                                  │
│  Transformed page content in EDS block format            │
│  Written to target AEM author instance                   │
│  (as a new page or as an inheritance-aware delta overlay │
│  on a rolled-out live copy page, depending on the wave)  │
└─────────────────────────────────────────────────────────┘
```

A transformer must be written for every AEM component type present across the TFS site. The component inventory from source (produced as part of pre-migration analysis) determines the full set of transformers required. No page can be migrated for a component type that does not have a transformer — untransformed components either render incorrectly or are dropped.

---

## 7. Embedded Content — Forms, Experience Fragments, Reference Components

Forms, Experience Fragments, and Reference Components are not fully represented in the rendered page HTML and require a JCR lookup during page migration. Their dedicated migration (where applicable) is covered in separate documents; this section describes how they are handled **within page migration**.

**Forms:**
When the migration pipeline processes a content page, the form detection handler identifies pages that contain a form by detecting the form container marker in the parsed HTML. When a form is detected, the page is flagged and its form section is read directly from the source JCR — rendered HTML alone does not carry the complete form configuration (field definitions, action endpoints, division mappings). The form content is then transformed using the form component-to-block mapping and the resulting EDS form block is written to the target page.

**Experience Fragments:**
An Experience Fragment renders on a page as its resolved variation content — the **XF source path is never present in the page HTML**. During page migration, the XF reference handler detects that an XF is embedded (by its rendered marker / CSS class), then performs a **JCR lookup on the source page** to obtain the actual XF path the page references. That path is matched against the **XF-to-Fragment path mapping table** (produced during Wave 0 XF migration), and the page is authored with the **EDS fragment block pointing to the corresponding target fragment path**. Because this depends on the mapping table, **Wave 0 XF migration must complete before any page migration begins**.

**Reference Components:**
Reference Components are detected during page migration and resolved via a **JCR lookup** to identify the referenced content. If a corresponding fragment does not already exist in the target, the referenced content is migrated to a fragment and the page is authored with the **EDS fragment block pointing to that target fragment path**. This ensures shared/referenced content is represented consistently as fragments in the target model.

---

## 8. Summary — Migration Sequence

```
Wave 0
  Forms XF and Site XF migration (JCR extraction + transform + content package)
  XF-to-Fragment path mapping table finalized
              │
              ▼
Wave 1
  Global English publish content made accessible (TFS + EMA team to confirm)
  Global EN pages migrated via EMA
  XF / Reference Component references resolved (JCR lookup) and authored as fragment blocks
  Parity check — all Global EN pages
  TFS sign-off
  MSM blueprint configured in AEMaaCS
              │
              ▼
Wave 2
  Rollout: Global EN → Regional EN hubs (creates live copies in target)
  Delta identification script runs on each Regional EN hub
  TFS review of delta list
  Delta pages migrated (inheritance-aware — override-only; MSM metadata preserved)
  Parity check + MSM inheritance integrity — delta pages only
  TFS sign-off
              │
              ▼
Wave 3
  Rollout: Regional EN → Country EN sites
  (en-uk rolled out first; en-fr/de/es rolled out from en-uk)
  Delta identification script runs on each Country EN site
  TFS review of delta list
  Delta pages migrated (inheritance-aware — override-only; MSM metadata preserved)
  Parity check + MSM inheritance integrity — delta pages only
  TFS sign-off
              │
              ▼
Wave 4
  Rollout: Country EN → Translated locales (creates live copies)
  Delta identification script identifies translated pages (cancelled inheritance only)
  Delta pages migrated from live site URLs (inheritance-aware — override-only)
  Parity check + MSM inheritance integrity — translated delta pages only
  TFS sign-off
```

---

## 9. Assumptions

1. Global English and Regional English content will be made accessible to EMA tooling before Wave 1 and Wave 2 migration begin. The mechanism for this is to be confirmed by TFS infrastructure and validated with the Adobe EMA team.

2. TFS content team will provide a confirmed list of active master pages under Global English before Wave 1 begins. No JCR query can reliably determine which pages are active — content owner confirmation is the only reliable source.

3. The delta identification script identifies all technical delta pages. Business relevance decisions (which delta pages to migrate vs retire) rest with TFS content owners through the review gate at each wave.

4. Delta page migration is **inheritance-aware** — overrides are applied only where local content differs from the inherited (rolled-out) content, and inheritance is preserved everywhere else. MSM relationships established by rollout are not overwritten.

5. MSM blueprint and live copy configuration in AEMaaCS is set up as a prerequisite and is a separate configuration activity from content migration.

6. XF migration (Wave 0) is complete and the XF-to-Fragment path mapping table is available before any page migration begins.

7. A transformer is available for every AEM component type present in the TFS site before migration scripts run. The component inventory is produced as part of pre-migration analysis and determines the complete set of transformers required.

8. Asset migration, page metadata migration, and redirects are covered as separate migration tasks/documents and are dependencies of content page migration.
```
