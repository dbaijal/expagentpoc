# Hero Block — Technical Details & UE Authoring Contract

**Block Name:** Hero
**Maps To:** Page Heading Hero Component (AEM 6.4)
**Pattern:** Flattened Block (Simple — all fields on one block, no child items)

---

## 1. UE Authoring Contract

### 1.1 AEM Resource

| Property | Value |
|---|---|
| Resource Type | `core/franklin/components/block/v1/block` |
| Block Name | `Hero` |
| Model ID | `hero` |
| Block Type | Simple (flattened — leaf block, no children) |
| JCR Path | `/content/<project>/<path>/jcr:content/root/<section>/hero` |

### 1.2 Editable Properties in Universal Editor

When an author clicks the Hero block in Universal Editor, the following fields appear in the properties panel:

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `classes` | Style Options (multiselect) | multiselect | Yes | Variant selection — options: dark-background, center-align, foreground-image, gradient-overlay, image-focal-center |
| `image` | Background Image | reference (DAM picker) | Yes | Opens asset picker |
| `imageAlt` | Background Image Alt Text | text | Yes | Collapsed into `image` element |
| `foregroundImage` | Foreground Image | reference (DAM picker) | Yes | Optional — used with `foreground-image` variant |
| `foregroundImageAlt` | Foreground Image Alt Text | text | Yes | Collapsed into `foregroundImage` element |
| `subtitle` | Subtitle | text | Yes | Secondary text below heading |
| `description` | Description | richtext | Yes | Supports bold, italic, links, lists |
| `overlayBreadcrumb` | Show Breadcrumb | boolean (toggle) | Yes | Toggles breadcrumb trail visibility |
| `primaryCtaLabel` | Primary CTA Text | text | Yes | From CTA partial |
| `primaryCtaHref` | Primary CTA URL | text | Yes | From CTA partial |
| `primaryCtaExternal` | Primary CTA New Tab | boolean (toggle) | Yes | From CTA partial |
| `primaryCtaType` | Primary CTA Style | select | Yes | Options: Primary, Outline |
| `secondaryCtaLabel` | Secondary CTA Text | text | Yes | From CTA partial |
| `secondaryCtaHref` | Secondary CTA URL | text | Yes | From CTA partial |
| `secondaryCtaExternal` | Secondary CTA New Tab | boolean (toggle) | Yes | From CTA partial |
| `secondaryCtaType` | Secondary CTA Style | select | Yes | Options: Primary, Outline |
| *(H1 Heading)* | *Not in properties panel* | -- | **No** | **Derived from page title (`jcr:title`) — not authored in block** |
| *(Breadcrumb content)* | *Not in properties panel* | -- | **No** | **Derived from content hierarchy — block only toggles visibility** |

### 1.3 Container vs Leaf Fields

The Hero block is a **leaf block (flattened)**. All fields are on the block itself. There are no child items, no container behavior, and no repeatable groups.

The dual CTA (0, 1, or 2 buttons) is handled by two **fixed CTA field groups** (primary + secondary), not by repeatable child items. This enforces the PDF requirement of "Can add 0, 1, or 2" at the model level — there is no way for an author to add a third CTA.

### 1.4 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| Background image | `image` (reference) | Opens DAM asset picker — author browses and selects |
| Foreground image | `foregroundImage` (reference) | Opens DAM asset picker — same behavior |
| CTA links | `primaryCtaHref`, `secondaryCtaHref` (text) | Author types URL or content path manually |
| H1 heading | Derived from `jcr:title` on page node | **Not editable in block** — author edits in Page Properties |
| Breadcrumb trail | Derived from content hierarchy / navigation | **Not editable** — `overlayBreadcrumb` toggles visibility only |

**No fragments or nested items are used by the Hero block.**

### 1.5 Block Registration — Component Definition

The Hero block is registered in `component-definition.json` (or `blocks/hero/_hero.json`) so that Universal Editor knows it exists and can present it in the block palette:

```json
{
  "title": "Hero",
  "id": "hero",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block",
        "template": {
          "name": "Hero",
          "model": "hero"
        }
      }
    }
  }
}
```

This definition tells UE:
- **What to show in the block palette:** "Hero" (from `title`)
- **What resource type to create in JCR:** `core/franklin/components/block/v1/block`
- **What model to use for the properties panel:** `hero` (links to the model definition in `component-models.json`)
- **What block name to set:** `Hero` (used by EDS for block resolution → `blocks/hero/hero.js` + `blocks/hero/hero.css`)

---

## 2. Component Model Definition (Field-Level)

### 2.1 Field Definitions

| Field | Component | Label | Required | Default | Validation | Authored / Derived | Collapsed Into |
|---|---|---|---|---|---|---|---|
| `image` | reference | Background Image | No | -- | Must be valid DAM path | Authored | -- |
| `imageAlt` | text | Background Image Alt | Conditional (required if image is set) | "" | Max 125 chars recommended (accessibility) | Authored | Collapsed into `image` |
| `foregroundImage` | reference | Foreground Image | No | -- | Must be valid DAM path | Authored | -- |
| `foregroundImageAlt` | text | Foreground Image Alt | Conditional (required if foregroundImage is set) | "" | Max 125 chars recommended | Authored | Collapsed into `foregroundImage` |
| `subtitle` | text | Subtitle | No | "" | -- | Authored | -- |
| `description` | richtext | Description | No | "" | Supports: bold, italic, links, lists | Authored | -- |
| `overlayBreadcrumb` | boolean | Show Breadcrumb | No | false | -- | Authored | -- |
| `primaryCtaLabel` | text | Primary CTA Text | No | -- | Required if `primaryCtaHref` is set | Authored (CTA partial) | -- |
| `primaryCtaHref` | text | Primary CTA URL | No | -- | Valid URL or content path | Authored (CTA partial) | -- |
| `primaryCtaExternal` | boolean | Primary CTA Open in New Tab | No | false | -- | Authored (CTA partial) | -- |
| `primaryCtaType` | select | Primary CTA Style | No | "primary" | Options: primary, outline | Authored (CTA partial) | -- |
| `secondaryCtaLabel` | text | Secondary CTA Text | No | -- | Required if `secondaryCtaHref` is set | Authored (CTA partial) | -- |
| `secondaryCtaHref` | text | Secondary CTA URL | No | -- | Valid URL or content path | Authored (CTA partial) | -- |
| `secondaryCtaExternal` | boolean | Secondary CTA Open in New Tab | No | false | -- | Authored (CTA partial) | -- |
| `secondaryCtaType` | select | Secondary CTA Style | No | "outline" | Options: primary, outline | Authored (CTA partial) | -- |
| `classes` | multiselect | Style Options | No | "" (default variant) | Options: dark-background, center-align, foreground-image, gradient-overlay, image-focal-center | Authored | NOT a table row — applied as CSS class on block wrapper |

### 2.2 Derived Fields (Not in Block Model)

| Property | Source | Used For | Why Not in Block Model |
|---|---|---|---|
| H1 Heading | `jcr:title` from page properties | Rendered as `<h1>` inside hero | Per specification: "The H1 heading is pulled automatically from page metadata — authors do not author it within the block" |
| Breadcrumb content | Content hierarchy / site navigation structure | Rendered as breadcrumb trail when `overlayBreadcrumb = true` | Breadcrumb is structural/navigational. Block only toggles visibility. Content is derived from the page path. |

### 2.3 Dual CTA Pattern (0, 1, or 2 CTAs)

The Hero supports **0, 1, or 2 CTAs**. This is implemented as two fixed CTA field groups, not a repeatable group:

```
Primary CTA:   primaryCtaLabel, primaryCtaHref, primaryCtaExternal, primaryCtaType
Secondary CTA: secondaryCtaLabel, secondaryCtaHref, secondaryCtaExternal, secondaryCtaType
```

**Why fixed groups, not repeatable:**
- The specification explicitly limits CTAs to a maximum of 2
- Fixed field groups enforce this limit at the model level — no way for an author to add a third CTA
- A repeatable group would allow unlimited CTAs, violating the specification
- The primary CTA defaults to "primary" style, secondary defaults to "outline" — different defaults per position

**CTA type options for Hero:** The Hero block restricts CTA types to **Primary and Outline only** — not the full CTA type list available in the standalone CTA block:

| Context | Available CTA Types |
|---|---|
| Standalone CTA Block | Primary, Secondary, Outline, Link, Video, Download (full list) |
| Hero Block CTAs | **Primary, Outline only** |

### 2.4 Shared Partials

| Partial | How Used in Hero |
|---|---|
| `_cta-fields.json` | Referenced via `_dual-cta-fields.json` which wraps two CTA field groups (primary + secondary). CTA field definitions are shared, but Hero restricts type options. |
| `_button-types.json` | **Not used directly** — Hero defines its own reduced type options (primary, outline only). |

---

## 3. Governance — Allowed Block Rules

| Rule | Hero Block |
|---|---|
| **Allowed in sections** | Yes — listed in `component-filters.json` section filter |
| **Allowed on all page types** | Yes — no page-type restriction in current xWalk architecture |
| **Template restriction** | None — available on all templates |
| **Is it a container** | **No** — flattened/leaf block with no child items |
| **Fragment-only** | **No** — authored directly on the page |
| **Form-only** | **No** |
| **Standalone** | **Yes** — author drops it into any section |
| **Typical placement** | First block in the first section of the page (convention, not enforced) |
| **Max instances per page** | No technical limit — convention is one hero per page |
| **Editable regions** | All fields editable via properties panel |
| **Locked regions** | H1 heading (locked to page title, not overridable in block) and breadcrumb content (derived from hierarchy, toggle only) |

### Governance in component-filters.json

The Hero block is included in the section-level filter:

```json
{
  "id": "section",
  "components": [
    "hero",
    "cards",
    "accordion",
    "tabs",
    "columns",
    "..."
  ]
}
```

The Hero block itself is **not** a container — it has no filter entry of its own (unlike Cards which has `"id": "cards", "components": ["card"]`). Authors cannot drop child blocks inside a Hero.

### Relationship to AEM 6.4 Template Policies

| AEM 6.4 Policy | xWalk EDS Equivalent for Hero |
|---|---|
| Hero allowed on specific templates via parsys policy | Hero allowed on all pages via `section` filter (global) |
| Style System options configured per template | `classes` multiselect options defined in `hero` model JSON (global) |
| Max 1 hero per page enforced by policy | **Not enforced** — convention only. xWalk does not support max-instance-per-page constraints. |
| Hero locked in template structure position | **Not locked** — author can place hero in any section. Convention: first block in first section. |

---

## 4. Nested Authoring Strategy

### Classification: Flattened Block

| Pattern | Applies? | Reason |
|---|---|---|
| **Flattened block** | **Yes** | All fields on one block. No repeating items. Dual CTA uses fixed field groups. Author fills one properties panel — done. |
| Parent-child structure | No | No repeating child items. CTA count is fixed (0-2), not dynamic. No add/remove/reorder needed. |
| Reference-based block | No | Block is authored directly on the page. Images are DAM references, but the block itself is not a reference to another page or fragment. |

