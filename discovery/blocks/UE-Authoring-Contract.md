# Universal Editor Authoring Contract — Reference Guide

**Document Version:** 1.0
**Date:** April 28, 2026
**Project:** AEM 6.4 to AEM as a Cloud Service + EDS (xWalk)
**Purpose:** Implementation-grade reference for UE authoring contracts across all blocks

---

## Table of Contents

1. Global Conventions (Apply to All Blocks)
2. Block Authoring Patterns
3. Block Inventory — Authoring Specification
4. Block-Specific Notes
5. Representative Block Walkthroughs (JCR to UE Mapping)
6. Nested Authoring Strategy
7. Governance — Allowed Blocks by Context
8. Style System Replacement Model

---

## 1. Global Conventions (Apply to All Blocks)

### 1.1 Resource Type Conventions

All blocks in xWalk use a standard set of Franklin resource types. The `name` property in the block template differentiates them.

| Component Type | Resource Type | Used For |
|---|---|---|
| Section | `core/franklin/components/section/v1/section` | Page sections (content containers) |
| Block | `core/franklin/components/block/v1/block` | All blocks (Hero, Cards, Tabs, etc.) |
| Block Item | `core/franklin/components/block/v1/block/item` | Child items inside container blocks |
| Columns | `core/franklin/components/columns/v1/columns` | Columns layout block |
| Text | `core/franklin/components/text/v1/text` | Default content -- text |
| Title | `core/franklin/components/title/v1/title` | Default content -- heading |
| Image | `core/franklin/components/image/v1/image` | Default content -- image |
| Button | `core/franklin/components/button/v1/button` | Default content -- button/link |

### 1.2 UE Connection

Universal Editor connects to AEM via a meta tag in the page head:

```html
<meta name="urn:adobe:aue:system:aemconnection" content="aem:https://author-pXX-eYYY.adobeaemcloud.com">
```

This tells UE where to read/write content. All editable content on the page is backed by JCR nodes at the mapped content path.

### 1.3 Field Types Available in UE

| Field Component | UE Behavior | Use For | Author Interaction |
|---|---|---|---|
| `text` | Single-line text input | Titles, labels, URLs, IDs | Types text |
| `richtext` | Inline rich text editor with formatting toolbar | Descriptions, body text, content with links | Types with formatting options (bold, italic, links, lists) |
| `reference` | Opens AEM DAM asset picker | Images, videos, documents | Clicks field -> browses DAM -> selects asset |
| `aem-content` | Opens AEM content/page path picker | Fragment paths, internal page links | Clicks field -> browses content tree -> selects page |
| `select` | Dropdown with predefined options | Style variants, heading levels, button types | Clicks dropdown -> picks one option |
| `multiselect` | Multi-choice dropdown | Tags, multiple categories | Clicks dropdown -> picks multiple options |
| `boolean` | Toggle switch | Open in new tab, show/hide options | Toggles on/off |
| `number` | Numeric input | Counts, limits | Types number |

### 1.4 Field Collapsing Rules

Fields ending with these suffixes are **automatically collapsed** into their parent field's HTML attributes. Do NOT create separate model rows or field hints for collapsed fields.

| Suffix | Collapsed Into | Example |
|---|---|---|
| `Alt` | `alt` attribute on image | `imageAlt` -> `<img alt="...">` |
| `Title` | `title` attribute on link | `linkTitle` -> `<a title="...">` |
| `Text` | Text content of link | `linkText` -> `<a>text</a>` |
| `Type` | CSS class or type attribute | `linkType` -> `<a class="primary">` |
| `MimeType` | MIME type attribute | `fileMimeType` -> type attribute |

**Rule:** When you see `image` and `imageAlt` in a model, it produces ONE cell with `<img src="..." alt="...">` -- NOT two separate cells.

### 1.5 Field Grouping Rules

Fields with the same prefix (separated by underscore) are grouped into the **same cell**:

```json
{ "name": "cta_label" }
{ "name": "cta_href" }
```

Results in one cell: `<!-- field:cta_label -->Label<!-- field:cta_href -->URL`

### 1.6 Special Field Exclusions

| Field | Rule |
|---|---|
| `classes` | SKIP entirely -- do not create a row. Applied as CSS class on block wrapper automatically. |
| Fields with `component: "tabs"` | SKIP -- do not create a row. Used for UE panel organization only. |

### 1.7 Shared Field Partials

These field groups are defined once in `models/partials/` and reused across multiple blocks via the JSON spread operator:

```json
{ "...": "../../models/partials/_cta-fields.json#/fields" }
```

| Partial File | Fields Included | Used By |
|---|---|---|
| `_cta-fields.json` | ctaLabel (text), ctaHref (text), ctaExternal (boolean), ctaIcon (select), ctaType (select) | Hero, Teaser, Banner, any block with a CTA |
| `_button-types.json` | Options: primary, secondary, outline, cta-arrow, video, download | Button block, ctaType field in CTA partial |
| `_button-icons.json` | Options: arrow, download, play, external-link | Button block, ctaIcon field in CTA partial |

**Benefit:** When a block uses a CTA partial, the author sees the same CTA fields and dropdown options regardless of which block they are editing. Changes to the partial update all blocks automatically.

### 1.8 Governance Model (component-filters.json)

Governance controls what blocks authors can add and where.

**Page level:**

```
main -> allows: section only
(All content must be inside a section)
```

**Section level:**

```
section -> allows: [all registered block IDs + default content types]
(Authors can add any block from the palette inside a section)
```

**Container block level (per block):**

```
cards -> allows: card only
accordion -> allows: accordion-item only
tabs -> allows: tabs-item only
table -> allows: table-row only
form-container -> allows: form-config, form-input, form-options, form-textarea,
                          form-hidden, form-upload, form-button, form-label, form-fragment
```

**How this compares to AEM 6.4:**

| AEM 6.4 | xWalk EDS |
|---|---|
| Template policies configured per template by admin in Template Editor | Filters defined globally in `component-filters.json` by developers |
| Different templates can allow different components | All templates share the same filter set (global) |
| Admin can change policies without deployment | Filter changes require code deployment via GitHub |

**To restrict blocks per template:** Use different `component-definition.json` entries with template-specific IDs, or use separate site configurations.

### 1.9 Fragment and Reference Editing

| Pattern | UE Behavior |
|---|---|
| **Fragment block** | Author picks a path via `aem-content` picker -> content renders as read-only preview on the consuming page. To EDIT the fragment content, author must navigate to the fragment page in UE. Fragment is NOT editable inline from the consuming page. |
| **DAM asset reference** | `reference` field opens AEM asset picker. Author browses DAM, selects image/video. Selected asset path stored as field value. Asset is referenced, not copied. |
| **Internal page link** | `aem-content` field opens content tree picker. Author browses pages, selects target. Link path stored as field value. |
| **External URL** | `text` field -- author types URL manually. No picker. |

### 1.10 Section Metadata

Sections can have a `classes` field (via the section model) that applies CSS classes for visual variations:

```json
{
  "id": "section",
  "fields": [
    {
      "component": "select",
      "name": "classes",
      "label": "Style",
      "options": [
        { "name": "Default", "value": "" },
        { "name": "Light Grey", "value": "light-grey" },
        { "name": "Dark", "value": "dark" },
        { "name": "Highlight", "value": "highlight" }
      ]
    }
  ]
}
```

Author selects style from dropdown -> section renders with CSS class -> CSS targets `.section.dark { ... }`. This is the xWalk equivalent of the AEM 6.4 Style System at section level.

### 1.11 Block Variants (classes Property)

Blocks can have visual variants via the `classes` property in the component definition template:

```json
{
  "title": "Cards (Profile)",
  "id": "cards-profile",
  "template": { "name": "Cards", "classes": "profile", "filter": "cards" }
}
```

This appears as a separate block option in UE's block palette. The `classes` value is applied as a CSS class on the block wrapper: `<div class="cards profile">`.

**Variants vs Style field:** Use variants when the block appears as a distinct option in the palette. Use a `classes` select field when the author should choose the style after adding the block.

---

## 2. Block Authoring Patterns

### Pattern A: Simple Block

**Applies to:** Hero, Quote, Embed, Video, Search, Modal, Fragment, Button, Form

**Structure:** One column, each model field = one row in the block table.

**Authoring experience:**
1. Author adds block from palette (e.g., "Hero")
2. Block appears in page
3. Author clicks block -> properties panel shows all fields
4. Each field corresponds to one property in the panel
5. No child items -- block is a single unit

**JCR structure:**
```
/content/.../page/jcr:content/root/section_1/hero
  ├── sling:resourceType = core/franklin/components/block/v1/block
  ├── name = "Hero"
  ├── model = "hero"
  ├── image = "/content/dam/hero.jpg"
  ├── imageAlt = "Lab scientist"
  ├── title = "Keep Seeking"
  └── description = "<p>The lab is not a destination...</p>"
```

**Rendered HTML (block table):**
```
| Hero |
|------|
| <picture><img src="hero.jpg" alt="Lab scientist"></picture> |
| <h2>Keep Seeking</h2><p>The lab is not a destination...</p> |
```

**Row calculation:** Count unique field names (excluding collapsed suffixes). Fields with same prefix (underscore-separated) group into one row.

---

### Pattern B: Container Block

**Applies to:** Cards, Accordion, Tabs, Table

**Structure:** Parent block with child items. Each child = one row. Child fields = columns in that row.

**Authoring experience:**
1. Author adds container block from palette (e.g., "Cards")
2. Empty container appears in page
3. Author clicks "+" inside container -> adds child item (e.g., "Card")
4. Each child item has its own properties panel
5. Child items can be reordered via drag-and-drop in UE
6. Child items can be deleted individually
7. Only allowed child types can be added (governed by filter in component-filters.json)

**JCR structure:**
```
/content/.../page/jcr:content/root/section_1/cards
  ├── sling:resourceType = core/franklin/components/block/v1/block
  ├── name = "Cards"
  ├── filter = "cards"
  ├── item_1/
  │   ├── sling:resourceType = core/franklin/components/block/v1/block/item
  │   ├── name = "Card"
  │   ├── model = "card"
  │   ├── image = "/content/dam/card1.jpg"
  │   └── text = "<p>Card 1 text</p>"
  ├── item_2/
  │   ├── image = "/content/dam/card2.jpg"
  │   └── text = "<p>Card 2 text</p>"
  └── item_3/
      ├── image = "/content/dam/card3.jpg"
      └── text = "<p>Card 3 text</p>"
```

**Rendered HTML (block table):**
```
| Cards |  |
|-------|--|
| <picture><img src="card1.jpg"></picture> | <p>Card 1 text</p> |
| <picture><img src="card2.jpg"></picture> | <p>Card 2 text</p> |
| <picture><img src="card3.jpg"></picture> | <p>Card 3 text</p> |
```

**Row calculation:** Number of rows = number of child items. Columns per row = number of unique field name groups in the child model.

---

### Pattern C: Columns Block

**Applies to:** Columns

**Structure:** Special block type -- defines grid layout. Content inside columns is default content (text, images, buttons), not field-modeled.

**Authoring experience:**
1. Author adds Columns block from palette
2. Specifies number of columns and rows
3. Each cell accepts default content (text, image, button, title) -- direct inline editing
4. No field model on cells -- author types/drops content directly
5. No field hints required (Columns blocks are exempt from hinting rules)

**JCR structure:**
```
/content/.../page/jcr:content/root/section_1/columns
  ├── sling:resourceType = core/franklin/components/columns/v1/columns
  ├── columns = "2"
  └── rows = "1"
```

**Key difference from other blocks:** Columns use `columns/v1/columns` resource type (not `block/v1/block`). Content inside columns is default content, not field-modeled properties.

---

### Pattern D: Form Block

**Applies to:** Form Container + form input child items

**Structure:** Container block with specialized child item types. Each form field type has its own model with field-specific properties.

**Authoring experience:**
1. Author adds "Form Container" from palette
2. Adds form configuration as a child item (Form Config -- action URL, redirect, etc.)
3. Adds form fields as child items (Input Field, Options Field, Text Area, etc.)
4. Each field type has its own properties panel with type-specific options
5. Form fields can be reordered
6. Form Button child item for submit button

**Child item types:**

| Child Type | Model ID | Key Fields |
|---|---|---|
| Form Config | form-config | field_action, config_formid, config_redirect |
| Input Field | form-input | field_name, field_label, config_type (text/email/tel/etc.), validation_required |
| Options Field | form-options | field_name, field_label, config_display (select/radio/checkbox), config_options |
| Text Area | form-textarea | field_name, field_label, config_placeholder, validation_required |
| Hidden Field | form-hidden | field_name, config_value, config_source |
| Upload Field | form-upload | field_name, field_label, config_label, validation_required |
| Form Button | form-button | field_label, config_role (submit/reset) |
| Form Label | form-label | content_text |
| Form Fragment | form-fragment | field_path |

---

## 3. Block Inventory -- Authoring Specification

The table below documents every block's unique authoring contract. For authoring behavior, refer to the applicable pattern in Section 2. For field types, collapsing rules, and governance, refer to Section 1.

### Simple Blocks

| Block | ID | Model ID | Fields (excluding collapsed) | Variants | Partials Used |
|---|---|---|---|---|---|
| Hero | hero | hero | image, title, description, + CTA fields | -- | _cta-fields.json |
| Quote | quote | quote | quote (richtext), author (text) | -- | -- |
| Embed | embed | embed | url (text) | -- | -- |
| Video | video | video | source (reference) | -- | -- |
| Fragment | fragment | fragment | path (aem-content) | -- | -- |
| Modal | modal | modal | content (richtext) | -- | -- |
| Search | search | search | -- (configuration only) | -- | -- |
| Button | button | button | label, href, external, type (select), icon (select) | primary, secondary, outline | _button-types.json, _button-icons.json |
| Form | form | form | action, method | -- | -- |

### Container Blocks

| Block | ID | Child Type | Child Model | Child Fields | Filter | Variants |
|---|---|---|---|---|---|---|
| Cards | cards | Card | card | image, text (richtext) | cards -> card | profile |
| Accordion | accordion | Accordion Item | accordion-item | summary (text), text (richtext) | accordion -> accordion-item | -- |
| Carousel | carousel | Carousel Slide | carousel-item | image, text (richtext) | carousel -> carousel-item | -- |
| Tabs | tabs | Tab | tabs-item | title (text), content_headingType (select) | tabs -> tabs-item | -- |
| Table | table | Row | table-row | column1text | table -> table-row | 2-col, 3-col, 4-col |
| Form Container | form-container | Multiple | See Pattern D | See Pattern D | form-container -> form inputs | -- |

### Special Blocks

| Block | ID | Type | Notes |
|---|---|---|---|
| Columns | columns | Columns (special) | Uses `columns/v1/columns` resource type. No field model on cells. Default content only. |
| Header | header | Auto-loaded | Not added by authors. Loaded automatically from `/nav` content. |
| Footer | footer | Auto-loaded | Not added by authors. Loaded automatically from `/footer` content. |

### Field-Level Definitions for Major Blocks

The tables below provide implementation-grade field definitions including required/optional status, defaults, validation, and whether the field is authored or derived.

#### Hero Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived | Collapsed Into |
|---|---|---|---|---|---|---|---|
| image | reference | Background Image | Yes | -- | Must be valid DAM path | Authored | -- |
| imageAlt | text | Alt Text | Yes | "" | Max 125 chars recommended | Authored | Collapsed into `image` |
| title | text | Title | Yes | -- | -- | Authored | -- |
| description | richtext | Description | No | "" | -- | Authored | -- |
| ctaLabel | text | CTA Text | No | -- | Required if ctaHref is set | Authored (from partial) | -- |
| ctaHref | text | CTA URL | No | -- | Valid URL or path | Authored (from partial) | -- |
| ctaExternal | boolean | Open in New Tab | No | false | -- | Authored (from partial) | -- |
| ctaType | select | CTA Style | No | "primary" | Options: primary, secondary, outline | Authored (from partial) | -- |
| ctaIcon | select | Icon | No | "" | Options: arrow, download, play, external-link | Authored (from partial) | -- |
| classes | select | Style | No | "" | Options per variant definition | Authored | NOT a row -- applied to block wrapper |

#### Card (Child Item) Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| image | reference | Image | Yes | -- | Must be valid DAM path | Authored |
| imageAlt | text | Alt Text | No | "" | -- | Authored (collapsed into image) |
| text | richtext | Text | Yes | "" | Supports: bold, italic, links, lists | Authored |

#### Accordion Item Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| summary | text | Summary / Title | Yes | "Lorem Ipsum" | -- | Authored |
| text | richtext | Content | Yes | Placeholder text | -- | Authored |

#### Tabs Item Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| title | text | Tab Name | Yes | "Tab Name" | -- | Authored |
| content_headingType | select | Heading Type | No | "h3" | Options: h1-h6 | Authored |

#### Button Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| label | text | Button Text | Yes | -- | -- | Authored |
| href | text | URL | Yes | -- | Valid URL or path | Authored |
| external | boolean | Open in New Tab | No | false | -- | Authored |
| type | select | Style | No | "" (default) | Options: primary, secondary, outline | Authored |
| icon | select | Icon | No | "" (none) | Options: arrow, download, play, external-link | Authored |

#### Fragment Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| path | aem-content | Fragment Path | Yes | -- | Must be valid content path | Authored |

#### Form Input Fields

| Field | Component | Label | Required | Default | Validation | Authored/Derived |
|---|---|---|---|---|---|---|
| field_type | hidden | -- | Yes | "input" | Fixed value | Derived (auto-set) |
| field_name | text | Field Name | Yes | "" | Must be unique within form | Authored |
| field_label | text | Label | Yes | "" | -- | Authored |
| config_type | select | Input Type | No | "text" | Options: text, email, tel, number, password, date | Authored |
| config_placeholder | text | Placeholder | No | "" | -- | Authored |
| validation_required | boolean | Required | No | false | -- | Authored |
| meta_step | text | Step | No | "-" | For multi-step forms | Authored |
| meta_span | text | Column Span | No | "12" | Grid span (1-12) | Authored |

#### Page-Level Metadata Fields (Derived / Not Block-Authored)

These values come from page properties, NOT from block authoring:

| Property | Source | Used By |
|---|---|---|
| title | Page Properties -> jcr:title | `<title>` tag, OG meta, breadcrumbs |
| description | Page Properties -> jcr:description | Meta description, OG description |
| template | Page Properties -> template field | Body CSS class, template-specific styling |
| image | Page Properties -> OG image | Social sharing image |
| canonical | Derived from page path + paths.json | Canonical URL |
| keywords | Page Properties -> keywords | Meta keywords |
| robots (noIndex) | Page Properties -> noIndex | Robots meta tag |

**Rule:** Blocks do NOT author page-level metadata. Page metadata is authored in page properties and rendered in `<head>` via `scripts.js`. Blocks only render body content.

---

## 4. Block-Specific Notes

This section documents authoring behavior that is **unique to specific blocks** and not covered by the general patterns above. Only blocks with notable differences are listed.

### Hero
- Uses CTA field partial -- author sees CTA label, URL, style dropdown, icon dropdown, and new-tab toggle as a field group within the properties panel
- Image field supports DAM picker for background image
- Title and description are separate fields (title is plain text, description is richtext)

### Cards (Profile Variant)
- Profile variant adds `classes: "profile"` to the block template
- Appears as a separate "Cards (Profile)" option in the block palette
- Same card child model (image + text richtext)
- Profile CSS renders circular images with centered text layout
- Social links for profile cards are authored as links within the richtext `text` field

### Fragment
- Author selects fragment path via `aem-content` picker
- Fragment content renders as **read-only preview** on the consuming page
- To edit the fragment content, author must **navigate to the fragment page** in Universal Editor
- Fragment is NOT editable inline from the consuming page

### Tabs
- Each tab item has a `title` field (appears as the tab label in the rendered output)
- `content_headingType` field controls the heading level (h1-h6) used for the tab title
- Tab content is authored within each tab item's properties

### Table (Row Variants)
- Table rows come in variants: 2-column, 3-column, 4-column
- Each variant has a different model with the corresponding number of `columnNtext` fields
- Author picks the row variant when adding a row to the table

### Form Container
- Most complex container block -- accepts 9 different child item types
- Each child type has its own model with specialized fields
- Form Config child item must be added first (defines form action, redirect, etc.)
- Form Fragment child allows including a shared form fragment by path reference
- `meta_step` and `meta_span` fields on form items control multi-step forms and grid layout

---

---

## 5. Representative Block Walkthroughs (JCR to UE Mapping)

This section shows, for 5 representative blocks, exactly how authored JCR content maps to editable regions and properties in Universal Editor. This demonstrates the authoring contract end-to-end: what the author sees, what gets stored, and what gets rendered.

### 5.1 Hero (Simple Block — Flattened Fields)

```
WHAT THE AUTHOR SEES IN UE:
┌─────────────────────────────────────────────────┐
│ Properties Panel (right side)                   │
│                                                 │
│ Background Image: [📁 Browse DAM]               │  ← reference field → opens asset picker
│ Alt Text: [Lab scientist working        ]       │  ← text field → collapsed into image
│ Title: [Keep Seeking                    ]       │  ← text field
│ Description: [The lab is not a dest...  ]       │  ← richtext field → inline editor
│                                                 │
│ ── Call to Action ──                            │  ← CTA field group (from partial)
│ CTA Text: [Learn more                  ]       │  ← text field
│ CTA URL: [/products/overview           ]       │  ← text field
│ Open in New Tab: [ ] (toggle)                   │  ← boolean field
│ CTA Style: [Primary ▼]                         │  ← select dropdown
│ Icon: [Arrow ▼]                                │  ← select dropdown
└─────────────────────────────────────────────────┘

WHAT GETS STORED IN JCR:
/content/tfs/us/en/home/jcr:content/root/section_1/hero
├── sling:resourceType = "core/franklin/components/block/v1/block"
├── name = "Hero"
├── model = "hero"
├── image = "/content/dam/tfs/hero-lab.jpg"         ← from reference picker
├── imageAlt = "Lab scientist working"              ← collapsed into image element
├── title = "Keep Seeking"                          ← plain text
├── description = "<p>The lab is not a dest...</p>" ← richtext HTML
├── ctaLabel = "Learn more"                         ← from CTA partial
├── ctaHref = "/products/overview"                  ← from CTA partial
├── ctaExternal = false                             ← from CTA partial
├── ctaType = "primary"                             ← from CTA partial
└── ctaIcon = "arrow"                               ← from CTA partial

WHAT GETS RENDERED (EDS block table → HTML):
<div class="hero">
  <div>
    <div>
      <picture><img src="/content/dam/tfs/hero-lab.jpg" alt="Lab scientist working"></picture>
    </div>
    <div>
      <h2>Keep Seeking</h2>
      <p>The lab is not a destination. It's the starting line.</p>
      <p><strong><a href="/products/overview">Learn more</a></strong></p>
    </div>
  </div>
</div>

EDITABLE REGIONS IN UE:
├── Image area → click opens properties panel → image field
├── Title text → inline editable (or via properties panel)
├── Description text → inline editable (richtext)
└── CTA → configured via properties panel (not inline)
```

### 5.2 Cards (Container Block — Parent/Child)

```
WHAT THE AUTHOR SEES IN UE:
┌─────────────────────────────────────────────────┐
│ Page Canvas                                     │
│                                                 │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐            │
│ │ Card 1  │ │ Card 2  │ │ [+]     │            │  ← "+" adds new card
│ │ [image] │ │ [image] │ │ Add Card│            │
│ │ [text]  │ │ [text]  │ │         │            │
│ └─────────┘ └─────────┘ └─────────┘            │
│                                                 │
│ Author clicks Card 1 → Properties Panel:        │
│ ┌───────────────────────────────────────┐       │
│ │ Image: [📁 Browse DAM]               │       │
│ │ Alt Text: [Antibody image     ]      │       │
│ │ Text: [Browse our antibody... ]      │       │  ← richtext
│ └───────────────────────────────────────┘       │
└─────────────────────────────────────────────────┘

WHAT GETS STORED IN JCR:
/content/.../jcr:content/root/section_1/cards
├── sling:resourceType = "core/franklin/components/block/v1/block"
├── name = "Cards"
├── filter = "cards"                               ← governance: only "card" children allowed
├── item_1/
│   ├── sling:resourceType = "core/franklin/components/block/v1/block/item"
│   ├── name = "Card"
│   ├── model = "card"
│   ├── image = "/content/dam/tfs/antibody.jpg"
│   ├── imageAlt = "Antibody image"
│   └── text = "<h3>Antibodies</h3><p>Browse our antibody catalog...</p>"
├── item_2/
│   ├── sling:resourceType = "core/franklin/components/block/v1/block/item"
│   ├── image = "/content/dam/tfs/pcr.jpg"
│   └── text = "<h3>PCR</h3><p>Real-time PCR solutions...</p>"
└── item_3/
    ├── ...

WHAT GETS RENDERED:
<div class="cards">
  <div>
    <div><picture><img src="antibody.jpg" alt="Antibody image"></picture></div>
    <div><h3>Antibodies</h3><p>Browse our antibody catalog...</p></div>
  </div>
  <div>
    <div><picture><img src="pcr.jpg"></picture></div>
    <div><h3>PCR</h3><p>Real-time PCR solutions...</p></div>
  </div>
</div>

EDITABLE REGIONS:
├── Cards container → click shows "+" to add new card
├── Each card → click opens that card's properties panel
├── Card image → reference field in properties panel
├── Card text → richtext field in properties panel
├── Drag handle on cards → reorder
└── Delete button on each card → remove individual card
```

### 5.3 Accordion (Container Block — Expandable Items)

```
WHAT THE AUTHOR SEES IN UE:
┌─────────────────────────────────────────────────┐
│ ┌─ What is Western Blotting? ──────── [▼] ────┐ │  ← accordion item, click to expand
│ │ Western blotting is a technique...           │ │
│ └──────────────────────────────────────────────┘ │
│ ┌─ How do I choose an antibody? ──── [▼] ────┐ │
│ │ Consider the target, host species...        │ │
│ └──────────────────────────────────────────────┘ │
│ [+] Add Accordion Item                          │
└─────────────────────────────────────────────────┘

Properties panel for selected item:
┌───────────────────────────────────────┐
│ Summary: [What is Western Blotting?]  │  ← text field (the question)
│ Content: [Western blotting is a... ]  │  ← richtext field (the answer)
└───────────────────────────────────────┘

JCR STORAGE:
/content/.../accordion
├── name = "Accordion"
├── filter = "accordion"
├── item_1/
│   ├── model = "accordion-item"
│   ├── summary = "What is Western Blotting?"
│   └── text = "<p>Western blotting is a technique...</p>"
└── item_2/
    ├── summary = "How do I choose an antibody?"
    └── text = "<p>Consider the target, host species...</p>"

EDITABLE REGIONS:
├── Each accordion item → click opens properties panel
├── Summary → text field (accordion header)
├── Content → richtext field (accordion body)
├── [+] → adds new accordion item
└── Drag/delete per item
```

### 5.4 Fragment (Reference Block)

```
WHAT THE AUTHOR SEES IN UE:
┌─────────────────────────────────────────────────┐
│ ┌──────────────────────────────────────────────┐│
│ │ Fragment: /content/tfs/fragments/promo-banner ││  ← read-only preview
│ │ ┌─────────────────────────────────────┐      ││
│ │ │  Spring Sale — 20% off all kits    │      ││  ← rendered fragment content
│ │ │  [Shop Now]                         │      ││    (NOT editable here)
│ │ └─────────────────────────────────────┘      ││
│ └──────────────────────────────────────────────┘│
│                                                 │
│ Properties panel:                               │
│ ┌───────────────────────────────────────┐       │
│ │ Fragment Path: [📁 /content/tfs/fr...] │      │  ← aem-content picker
│ └───────────────────────────────────────┘       │
└─────────────────────────────────────────────────┘

JCR STORAGE:
/content/.../fragment
├── name = "Fragment"
├── model = "fragment"
└── path = "/content/tfs/fragments/promo-banner"   ← reference to fragment page

WHAT GETS RENDERED:
Block JS fetches /content/tfs/fragments/promo-banner.plain.html at delivery time
and injects the fragment content inline.

EDITABLE REGIONS:
├── Fragment path → editable in properties panel (picker)
├── Fragment content → NOT editable from this page
└── To edit fragment content → navigate to /content/tfs/fragments/promo-banner in UE
```

### 5.5 Form Container (Complex Container — Multiple Child Types)

```
WHAT THE AUTHOR SEES IN UE:
┌─────────────────────────────────────────────────┐
│ Form Container                                  │
│ ┌───────────────────────────────────────┐       │
│ │ [Config] Action: ELOQUA  Redirect: / │       │  ← Form Config child
│ ├───────────────────────────────────────┤       │
│ │ [Input] First Name          Required  │       │  ← Input Field child
│ ├───────────────────────────────────────┤       │
│ │ [Input] Email               Required  │       │  ← Input Field child (type: email)
│ ├───────────────────────────────────────┤       │
│ │ [Options] Country           Select ▼  │       │  ← Options Field child
│ ├───────────────────────────────────────┤       │
│ │ [Button] Submit                       │       │  ← Form Button child
│ └───────────────────────────────────────┘       │
│ [+] Add: Input | Options | TextArea | Button | ..│
└─────────────────────────────────────────────────┘

Properties panel for "First Name" Input Field:
┌───────────────────────────────────────┐
│ Field Name: [firstName              ] │  ← text (required, unique)
│ Label: [First Name                  ] │  ← text (required)
│ Input Type: [text ▼]                 │  ← select (text/email/tel/number)
│ Placeholder: [Enter your name       ] │  ← text (optional)
│ Required: [✓] (toggle)               │  ← boolean
│ Step: [-]                             │  ← text (multi-step form)
│ Column Span: [6]                      │  ← text (grid layout)
└───────────────────────────────────────┘

EDITABLE REGIONS:
├── Form Container → click shows "+" with child type options
├── Each form field → click opens type-specific properties panel
├── Form Config → must be first child, configures action/redirect
├── Child types governed by form-container filter (9 allowed types)
└── Reorder fields via drag-and-drop
```

---

## 6. Nested Authoring Strategy

Every block is classified into one of three target authoring patterns. This ensures consistent implementation across the project.

### Classification Table

| Block | Pattern | Description |
|---|---|---|
| **Hero** | Flattened | All fields on one block — no children. CTA fields from partial are part of the same properties panel. |
| **Quote** | Flattened | Single block with quote text + author name. |
| **Embed** | Flattened | Single URL field. |
| **Video** | Flattened | Single source reference. |
| **Button** | Flattened | Label, URL, style, icon — all on one block. |
| **Modal** | Flattened | Content field only. |
| **Search** | Flattened | Configuration only, no authored content fields. |
| **Cards** | Parent-Child | Container with Card items. Each card = image + text. Author adds/removes/reorders cards. |
| **Cards (Profile)** | Parent-Child | Same as Cards but with `classes: "profile"` variant. |
| **Accordion** | Parent-Child | Container with Accordion Item items. Each item = summary + content. |
| **Carousel** | Parent-Child | Container with Slide items. Each slide = image + text. |
| **Tabs** | Parent-Child | Container with Tab items. Each tab = title + content. |
| **Table** | Parent-Child | Container with Row items. Row variants for 2/3/4 columns. |
| **Form Container** | Parent-Child (multi-type) | Container accepting 9 different child types, each with its own model. |
| **Fragment** | Reference-Based | References another page by path. Content fetched and rendered at delivery. Not editable inline. |
| **Regional Fragment** | Reference-Based + Conditional | References a fragment page + country codes. Block JS shows/hides based on URL-derived country. |
| **Columns** | Special — Default Content | Not field-modeled. Cells contain default content (text, image, button) edited inline. |
| **Header** | Auto-Loaded | Not author-placed. Loaded from `/nav` content automatically. |
| **Footer** | Auto-Loaded | Not author-placed. Loaded from `/footer` content automatically. |

### When to Use Each Pattern

| Pattern | Use When | Author Experience |
|---|---|---|
| **Flattened** | Block has a fixed set of fields, no repeating items | Author fills fields in one properties panel |
| **Parent-Child** | Block has repeating items (cards, tabs, rows, form fields) | Author adds/removes/reorders child items inside a container |
| **Reference-Based** | Block displays content from another page/fragment | Author picks a path — content is read-only on consuming page |
| **Default Content (Columns)** | Layout block where cells contain arbitrary content | Author types/drops content directly — no field model |
| **Auto-Loaded** | Global elements (header, footer) | Not added by authors — loaded automatically |

### Rule: Never Mix Patterns Within a Block

A single block should not combine parent-child with reference-based. If a card needs to reference a fragment, the fragment path should be a field on the card item (flattened within the child), not a nested reference block inside the card.

---

## 7. Governance — Allowed Blocks by Context

### 7.1 Page-Level Governance

```
main → allows: section only
```

All authored content must be inside a section. Authors cannot place blocks directly on the page root.

### 7.2 Section-Level Governance

```
section → allows: text, image, title, button,
                   hero, cards, accordion, carousel, columns, embed,
                   form-container, form, fragment, modal, quote,
                   search, table, tabs, video,
                   [+ any custom blocks added to the project]
```

All blocks are available inside any section. There is no per-template section filter in the current xWalk architecture.

### 7.3 Container Block Governance

| Container | Allowed Children | Restricted To |
|---|---|---|
| cards | card | Only card items — no other blocks or default content |
| accordion | accordion-item | Only accordion items |
| tabs | tabs-item | Only tab items |
| table | table-row (2-col, 3-col, 4-col variants) | Only table row items |
| form-container | form-config, form-input, form-options, form-textarea, form-hidden, form-upload, form-button, form-label, form-fragment | Only form-related items |
| carousel | carousel-item | Only carousel slide items |
| search | (search-specific children if any) | Per search filter |
| video | (video-specific children if any) | Per video filter |

### 7.4 Context-Specific Block Restrictions

| Block | Placement Restriction | Reason |
|---|---|---|
| **Header** | Auto-loaded from `/nav` — NOT in section palette | Global element, authored once separately |
| **Footer** | Auto-loaded from `/footer` — NOT in section palette | Global element, authored once separately |
| **Form Config** | Only inside Form Container | Configuration item, not standalone |
| **Form inputs** (Input, Options, etc.) | Only inside Form Container | Form fields have no meaning outside a form |
| **Card** | Only inside Cards block | Card item depends on Cards container for layout |
| **Accordion Item** | Only inside Accordion block | Same |
| **Tab Item** | Only inside Tabs block | Same |
| **Table Row** | Only inside Table block | Same |

### 7.5 Editable vs Locked Regions

| Region | Editable? | Notes |
|---|---|---|
| Page content (sections + blocks) | Yes — fully editable in UE | Authors add/remove/reorder sections and blocks |
| Header | No — auto-loaded from `/nav` page | Authors edit the `/nav` page separately to change header |
| Footer | No — auto-loaded from `/footer` page | Authors edit the `/footer` page separately |
| Page properties (metadata) | Yes — via page properties panel in UE | Title, description, template, keywords, etc. |
| Block JS/CSS (rendering logic) | No — developer-controlled in GitHub repo | Authors only affect content, not rendering |
| Section metadata (style) | Yes — via section properties panel | Authors can select section style (classes field) |

### 7.6 Comparison to AEM 6.4 Template Policies

| AEM 6.4 Capability | xWalk EDS Equivalent | Difference |
|---|---|---|
| Allowed components per template | `component-filters.json` (global) | Filters are global — no per-template variation. All templates allow the same set of blocks. |
| Allowed components per parsys | Container block filters | Each container defines which children are allowed — similar to parsys policies. |
| Style System per template | `classes` field in component model + block variants | Options are defined in the model JSON, not in template policies. |
| Locked template structure | Header/footer auto-loaded; page root requires sections | Structure is enforced by `main → section` filter, not by locked template regions. |
| Content policies (design dialog) | Component model field definitions | Field options, defaults, and constraints are in the model JSON. |

---

## 8. Style System Replacement Model

This section maps how current AEM 6.4 authorable style choices (Style System / design dialog selections) translate to EDS block variants, `classes` field options, or section metadata.

### 8.1 How Style System Works in AEM 6.4 vs xWalk EDS

| Aspect | AEM 6.4 Style System | xWalk EDS |
|---|---|---|
| Where styles are defined | Template policies (design dialog) | `select` field named `classes` in component model OR block variants in component definition |
| How author selects | Style tab on component | Properties panel dropdown OR separate block in palette (variant) |
| What happens | CSS class added to component wrapper | CSS class added to block wrapper (identical mechanism) |
| Who manages | Admin configures in Template Editor | Developer defines in JSON model |

### 8.2 Decision: When to Use Variant vs Classes Field

| Scenario | Use Block Variant | Use `classes` Select Field |
|---|---|---|
| Structurally different (different fields or layout) | Yes — separate entry in palette | No |
| Same fields, different visual style | Either approach works | Yes — simpler |
| Author needs to see distinct options in palette | Yes | No |
| Frequently toggled per instance | No — would require removing and re-adding block | Yes — just change dropdown |

### 8.3 Style Mapping Table — Major Component Families

**Note:** The specific current AEM 6.4 styles must be confirmed with the client's style system configuration. The table below provides the recommended mapping pattern for each component family. Actual style names should be populated per the client's existing style inventory.

#### Hero Styles

| AEM 6.4 Style Modifier | EDS Approach | Implementation |
|---|---|---|
| Full-width hero (default) | Default block rendering | No `classes` value needed |
| Hero with dark overlay | `classes` select option: `dark-overlay` | CSS: `.hero.dark-overlay { ... }` |
| Hero with light text | `classes` select option: `light-text` | CSS: `.hero.light-text { color: #fff; }` |
| Hero minimal (no CTA) | `classes` select option: `minimal` OR block variant `hero-minimal` | If fields differ → variant. If same fields, just hidden CTA → classes. |
| Hero with video background | Block variant: `hero-video` (additional video field) | Separate block definition with video reference field |

#### Cards Styles

| AEM 6.4 Style Modifier | EDS Approach | Implementation |
|---|---|---|
| Default grid cards | Default block rendering | No `classes` value |
| Profile cards (circular image, centered) | Block variant: `cards-profile` | Separate definition: `classes: "profile"` |
| Horizontal cards (image left, text right) | `classes` select option: `horizontal` | CSS: `.cards.horizontal { ... }` |
| Cards with border | `classes` select option: `bordered` | CSS: `.cards.bordered li { border: ... }` |
| Cards without image | Same block — author simply doesn't add image | No variant needed — image field is optional |

#### Accordion Styles

| AEM 6.4 Style Modifier | EDS Approach | Implementation |
|---|---|---|
| Default accordion | Default block rendering | No `classes` value |
| FAQ-style (bordered) | `classes` select option: `faq` | CSS: `.accordion.faq { ... }` |
| Compact (reduced padding) | `classes` select option: `compact` | CSS: `.accordion.compact { ... }` |

#### Tabs Styles

| AEM 6.4 Style Modifier | EDS Approach | Implementation |
|---|---|---|
| Default horizontal tabs | Default block rendering | No `classes` value |
| Vertical tabs (side navigation) | `classes` select option: `vertical` | CSS: `.tabs.vertical { ... }` |
| Pill-style tabs | `classes` select option: `pills` | CSS: `.tabs.pills { ... }` |

#### Section Styles

| AEM 6.4 Section Background | EDS Section `classes` Value | Implementation |
|---|---|---|
| White / default | No value (default) | Default section CSS |
| Light grey background | `light-grey` | CSS: `.section.light-grey { background: #f5f5f5; }` |
| Dark background | `dark` | CSS: `.section.dark { background: #1a1a1a; color: #fff; }` |
| Brand color background | `brand` | CSS: `.section.brand { background: var(--brand-primary); }` |
| Full-width (no max-width) | `full-width` | CSS: `.section.full-width { max-width: 100%; }` |

### 8.4 What to Do for Each Block During Implementation

For each block, during implementation:

1. **Inventory existing AEM 6.4 styles** — export all Style System configurations for that component
2. **Classify each style** — structural change (→ variant) or visual-only change (→ `classes` select option)
3. **Add to component model** — either as a new variant definition in `_block.json` or as options in the `classes` select field
4. **Write CSS** — create CSS rules targeting `.block-name.style-class` for each mapped style
5. **Test** — verify the author sees the correct options and the visual output matches the AEM 6.4 original

---

## How to Use This Document

**When adding a new block to the SDD:**

1. Identify the block type (Simple, Container, Columns, Form) -> reference the applicable Pattern from Section 2
2. Add a row to the appropriate table in Section 3 with: ID, model, fields, variants, partials
3. If the block has unique authoring behavior not covered by the pattern, add a note in Section 4
4. For field definitions: reference Section 1.3 (field types), 1.4 (collapsing), 1.5 (grouping), 1.7 (partials)
5. For governance: reference Section 1.8 and add the block's filter to `component-filters.json`

**When reviewing a block's UE contract:**

1. Check Section 3 for the block's fields and type
2. Read the applicable Pattern from Section 2 for authoring experience
3. Check Section 4 for any block-specific notes
4. Check Section 1.7 for shared partials used
5. Check Section 1.8 for governance (what filter applies)
