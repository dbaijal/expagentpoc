# Text and Image Block (Cards) — Technical Details & UE Authoring Contract

**Block Name:** Cards
**Maps To:** Text and Image Component (AEM 6.4) — used with Layout Container for multi-column grid
**Pattern:** Parent-Child (Container block with repeatable card items)

---

## 1. UE Authoring Contract

### 1.1 AEM Resource

| Property | Value |
|---|---|
| Resource Type (Parent) | `core/franklin/components/block/v1/block` |
| Resource Type (Child Item) | `core/franklin/components/block/v1/block/item` |
| Block Name | `Cards` |
| Model ID (Parent) | `cards` |
| Model ID (Child) | `card` |
| Filter | `cards` -> allows only `card` children |
| Block Type | Container (parent-child with repeatable items) |
| JCR Path (Parent) | `/content/<project>/<path>/jcr:content/root/<section>/cards` |
| JCR Path (Child) | `/content/<project>/<path>/jcr:content/root/<section>/cards/item_N` |

### 1.2 Editable Properties in Universal Editor

**Parent block (Cards container):**

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `classes` | Style Options | multiselect | Yes | Layout + visual variants — combinable. See Section 4. |
| `backgroundColor` | Background Color | select | Yes | Predefined options: white, light-grey, blue, dark. Applies to entire cards block instance. |

**Child item (Card):**

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `image` | Image | reference (DAM picker) | Yes — Required | Card image |
| `imageAlt` | Image Alt Text | text | Yes — Conditional | Required if image is set. Collapsed into `image` element. |
| `title` | Title | text | No | Card heading (rendered as H2) |
| `subtitle` | Subtitle | text | No | Secondary text below title (rendered as H3) |
| `description` | Description | richtext | No | Supporting body copy. Supports: bold, italic, links, lists. |
| `ctaLabel` | CTA Text | text | No | From CTA partial |
| `ctaHref` | CTA URL | text | No | From CTA partial |
| `ctaExternal` | CTA Open in New Tab | boolean | No | From CTA partial |
| `ctaType` | CTA Style | select | No | Options: primary, outline. From CTA partial. |
| `ctaIcon` | CTA Icon | select | No | Options: arrow, document, download, print, etc. From CTA partial. |

### 1.3 Container vs Leaf Fields

The Cards block is a **container block (parent-child)**:

- **Parent (Cards):** Container with layout/style variants and background color. Governs the grid layout and visual treatment for all cards. Controls which children are allowed via filter.
- **Children (Card items):** Each card is a repeatable item with image, title, subtitle, description, and CTA. Author adds/removes/reorders cards freely.

### 1.4 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| Card image | `image` (reference) | Opens DAM asset picker — author browses and selects |
| CTA link | `ctaHref` (text) | Author types URL or content path |
| Inline links in description | Within `description` richtext | Author adds links via richtext editor |

**No fragments or nested blocks are used by the Cards block.** Each card is self-contained with its own fields.

### 1.5 Block Registration — Component Definition

**Parent (Cards):**

```json
{
  "title": "Cards",
  "id": "cards",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block",
        "template": {
          "name": "Cards",
          "model": "cards",
          "filter": "cards"
        }
      }
    }
  }
}
```

**Child (Card):**

```json
{
  "title": "Card",
  "id": "card",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block/item",
        "template": {
          "name": "Card",
          "model": "card"
        }
      }
    }
  }
}
```

### 1.6 Key Architectural Change from AEM 6.4

In AEM 6.4, this component was used inside a **Layout Container (responsive grid)** — authors resized the parsys to 3 or 4 columns and dropped the Text-Image component in each column.

In EDS, there is **no layout container or responsive grid**. The Cards block **owns its own grid layout**. The `classes` variant (e.g., `4-col`, `img-left`, `list`) tells the block CSS how to render the layout. Authors no longer need to set up a grid first — they select the layout variant on the block, and CSS handles the rest.

| AEM 6.4 | EDS |
|---|---|
| Layout Container resized to N columns + drop component N times | Cards block with `classes` variant — one block, CSS handles grid |
| Layout controlled by Layout Container (responsive grid) | Layout controlled by block CSS via `classes` variant |
| Component doesn't know about columns | Block knows — variant tells CSS the column count |
| Author action: resize container + drop components | Author action: add cards to one block + select layout variant |

---

## 2. Component Model Definition (Field-Level)

### 2.1 Parent (Cards Container) Field Definitions

| Field | Component | Label | Required | Default | Validation | Authored / Derived |
|---|---|---|---|---|---|---|
| `classes` | multiselect | Style Options | No | "" (default — 3 col, image top, with border) | See variant list in Section 4 | Authored |
| `backgroundColor` | select | Background Color | No | "white" | Predefined options | Authored |

**Background Color options:**

| Option | Value | Visual |
|---|---|---|
| White | `white` | Default — white/transparent background |
| Light Grey | `light-grey` | Light grey background (#f5f5f5) |
| Blue | `blue` | Brand blue background |
| Dark | `dark` | Dark background with light text |

Additional options will be added as encountered during implementation.

### 2.2 Child (Card Item) Field Definitions

| Field | Component | Label | Required | Default | Validation | Authored / Derived | Collapsed Into |
|---|---|---|---|---|---|---|---|
| `image` | reference | Image | Yes | -- | Must be valid DAM path | Authored | -- |
| `imageAlt` | text | Image Alt Text | Conditional (required if image set) | "" | Max 125 chars recommended | Authored | Collapsed into `image` |
| `title` | text | Title | No | "" | -- | Authored | -- |
| `subtitle` | text | Subtitle | No | "" | -- | Authored | -- |
| `description` | richtext | Description | No | "" | Supports: bold, italic, links, lists | Authored | -- |
| `ctaLabel` | text | CTA Text | No | -- | Required if `ctaHref` is set | Authored (CTA partial) | -- |
| `ctaHref` | text | CTA URL | No | -- | Valid URL or content path | Authored (CTA partial) | -- |
| `ctaExternal` | boolean | CTA Open in New Tab | No | false | -- | Authored (CTA partial) | -- |
| `ctaType` | select | CTA Style | No | "primary" | Options: primary, outline | Authored (CTA partial) | -- |
| `ctaIcon` | select | CTA Icon | No | "" (none) | Options: arrow, document, download, print, etc. | Authored (CTA partial) | -- |

### 2.3 Shared Partials

| Partial | How Used in Cards |
|---|---|
| `_cta-fields.json` | Single CTA per card — label, href, external, type, icon fields spread into card model |
| `_button-types.json` | CTA type options restricted to: primary, outline |
| `_button-icons.json` | Full icon options available: arrow, document, download, print, etc. |

### 2.4 Smart Clickable Card Behavior

The PDF states: "When a card contains only an image and a CTA link, the block must wrap the image in the link to make the entire card clickable."

This is handled **automatically in block JS** — no variant selection needed:

```
Block JS logic:
  IF card has image + ctaHref
  AND card has NO title, NO subtitle, NO description
  THEN wrap entire card in <a href="ctaHref"> (card becomes clickable)

  IF author also selects "clickable" variant on parent
  THEN wrap entire card in <a> regardless of whether title/description are present
```

| Scenario | Author Action | Block JS Behavior |
|---|---|---|
| Image + CTA only (no text) | Author fills image + CTA, leaves title/subtitle/description empty | **Auto-clickable** — JS wraps card in link |
| Full card (image + title + description + CTA) with `clickable` variant | Author fills all fields + selects `clickable` on parent | **Force-clickable** — JS wraps entire card in link |
| Full card without `clickable` variant | Author fills all fields, no `clickable` selected | CTA renders as a button inside the card (not full-card clickable) |

---

## 3. Governance — Allowed Block Rules

| Rule | Cards Block |
|---|---|
| **Allowed in sections** | Yes — listed in `component-filters.json` section filter |
| **Allowed on all page types** | Yes — no page-type restriction |
| **Template restriction** | None — available on all templates |
| **Is it a container** | **Yes** — parent-child container |
| **Allowed children** | `card` only (via `cards` filter) |
| **Fragment-only** | **No** — authored directly on the page |
| **Standalone** | **Yes** — author drops it into any section |
| **Min/max items** | No technical limit. At least 1 card needed. |
| **Editable regions** | Style + background on parent; all content fields on each child card |
| **Locked regions** | None — all content is author-controlled |

### Governance in component-filters.json

```json
{
  "id": "cards",
  "components": ["card"]
}
```

Only `card` children can be added inside a cards block. Authors cannot drop other blocks inside cards.

### Relationship to AEM 6.4

| AEM 6.4 | xWalk EDS |
|---|---|
| Text-Image component dropped inside Layout Container (responsive grid) | **Cards block owns its layout** — no container/grid needed |
| Column count controlled by resizing Layout Container parsys | **Column count controlled by `classes` variant** (default=3, 4-col, etc.) |
| Background color via section/container styling | **`backgroundColor` select field on cards parent** |
| Style System per template | `classes` multiselect on cards parent model |

---

## 4. Nested Authoring Strategy

### Classification: Parent-Child (Container Block)

| Pattern | Applies? | Reason |
|---|---|---|
| Flattened block | No | Cards have repeating items — each card is a separate child with its own image, title, description, CTA. |
| **Parent-child structure** | **Yes** | Parent container with repeatable Card children. Author adds/removes/reorders cards. Parent controls layout and styling. |
| Reference-based block | No | Cards are authored directly — no fragment references needed. |

### Authoring Experience

```
AUTHOR VIEW IN UNIVERSAL EDITOR:

┌──────────────────────────────────────────────────────────────────┐
│ CARDS BLOCK                                                      │
│                                                                  │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐                          │
│ │ [image]  │ │ [image]  │ │ [image]  │                          │
│ │ Title    │ │ Title    │ │ Title    │                          │
│ │ Subtitle │ │ Subtitle │ │ Subtitle │                          │
│ │ Desc...  │ │ Desc...  │ │ Desc...  │                          │
│ │ [CTA]    │ │ [CTA]    │ │ [CTA]    │                          │
│ └──────────┘ └──────────┘ └──────────┘                          │
│                                                                  │
│ [+] Add Card                                                     │
│                                                                  │
│ Properties Panel (when Cards container is selected):             │
│ ┌────────────────────────────────────────────────────┐           │
│ │ Style Options: [4-col ✓] [without-border ✓]       │           │
│ │ Background Color: [Light Grey ▼]                   │           │
│ └────────────────────────────────────────────────────┘           │
│                                                                  │
│ Properties Panel (when a Card item is selected):                 │
│ ┌────────────────────────────────────────────────────┐           │
│ │ Image: [📁 Browse DAM]                             │           │
│ │ Image Alt: [Product image         ]                │           │
│ │ Title: [BenchStable Media         ]                │           │
│ │ Subtitle: [Cell Culture Media     ]                │           │
│ │ Description: [Available in our most common...]     │           │
│ │                                                    │           │
│ │ ── CTA ──                                          │           │
│ │ CTA Text: [Explore                ]                │           │
│ │ CTA URL: [/products/benchstable   ]                │           │
│ │ CTA New Tab: [ ] (toggle)                          │           │
│ │ CTA Style: [Primary ▼]                            │           │
│ │ CTA Icon: [Arrow ▼]                               │           │
│ └────────────────────────────────────────────────────┘           │
└──────────────────────────────────────────────────────────────────┘
```

---

## 5. Style Variants — `classes` Multiselect Options

### 5.1 Layout Variants (Mutually Exclusive — Pick One)

These control the grid structure. Only one layout should be selected at a time. Default (3-col, image top) applies when none is selected.

| Variant | `classes` Value | Description | CSS |
|---|---|---|---|
| Default (3 col) | (none — baseline) | 3-column grid, image at top of each card | `.cards ul { grid-template-columns: repeat(3, 1fr); }` |
| 4 Column | `4-col` | 4-column grid | `.cards.4-col ul { grid-template-columns: repeat(4, 1fr); }` |
| Full Width — Image Left | `fullwidth-img-left` | Single column, image left + text right | `.cards.fullwidth-img-left li { display: flex; }` |
| Full Width — Image Right | `fullwidth-img-right` | Single column, image right + text left | `.cards.fullwidth-img-right li { display: flex; flex-direction: row-reverse; }` |
| Image Left (2 Col) | `img-left` | 2-column layout, image left text right per card | `.cards.img-left li { display: flex; }` |
| Image Right (2 Col) | `img-right` | 2-column layout, image right text left per card | `.cards.img-right li { display: flex; flex-direction: row-reverse; }` |
| List | `list` | Stacked list layout, image left text right, full-width cards | `.cards.list li { display: flex; width: 100%; }` |

### 5.2 Visual Treatment Variants (Combinable — Pick Zero or More)

These control visual appearance and can be combined with a layout variant.

| Variant | `classes` Value | Description | CSS |
|---|---|---|---|
| Without Border | `without-border` | No borders or shadows on cards | `.cards.without-border li { border: none; box-shadow: none; }` |
| Clickable | `clickable` | Entire card wrapped in CTA link (even with title/description present) | `.cards.clickable li { cursor: pointer; }` |
| Overlay | `overlay` | Image as full-card background, text + CTA overlaid | `.cards.overlay li { position: relative; }` + absolute positioned text |
| Overlay Light Text | `overlay-light-text` | White text on overlay variant | `.cards.overlay-light-text li { color: #fff; }` |
| Overlay Dark Text | `overlay-dark-text` | Black text on overlay variant | `.cards.overlay-dark-text li { color: #000; }` |
| Feature Card | `feature-card` | Product image in upper right corner, text prominent | `.cards.feature-card .cards-card-image { position: absolute; top: 0; right: 0; }` |
| Card Resource | `card-resource` | Blue background, feature product style | `.cards.card-resource li { background: var(--brand-blue); color: #fff; }` |
| Title Over Image | `title-over-img` | Title text overlaid on the image | `.cards.title-over-img .cards-card-body { position: absolute; bottom: 0; }` |
| Classic Small | `classic-small` | Compact/alternate style | `.cards.classic-small li { padding: 0.5rem; font-size: 0.875rem; }` |
| Classic Gray | `classic-gray` | Gray background with blue text, typically single item | `.cards.classic-gray li { background: #f0f0f0; color: var(--brand-blue); }` |

### 5.3 Valid Combinations (Examples)

| Author Selects | Rendered Class | Visual Result |
|---|---|---|
| (nothing) | `class="cards"` | Default: 3-col grid, image top, bordered |
| `4-col` + `without-border` | `class="cards 4-col without-border"` | 4-column grid, no borders |
| `fullwidth-img-left` | `class="cards fullwidth-img-left"` | Single card per row, image left |
| `overlay` + `overlay-light-text` | `class="cards overlay overlay-light-text"` | Image background, white text overlaid |
| `img-left` + `clickable` | `class="cards img-left clickable"` | 2-col cards, image left, entire card clickable |
| `list` + `without-border` | `class="cards list without-border"` | Stacked list, no borders |
| `4-col` + `feature-card` | `class="cards 4-col feature-card"` | 4-col grid with product image in upper right |

### 5.4 Author Guidance

Layout variants are **mutually exclusive** — selecting both `4-col` and `img-left` would produce conflicting CSS. UE multiselect does not enforce this, so the description text on the field should guide authors:

```json
{
  "component": "multiselect",
  "name": "classes",
  "label": "Style Options",
  "description": "Select ONE layout option (4-col, img-left, etc.) and zero or more visual options (without-border, clickable, overlay, etc.)"
}
```
