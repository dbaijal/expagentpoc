# CTA Block — Technical Details & UE Authoring Contract

**Block Name:** CTA (Call-To-Action / Button)
**Maps To:** Button Component (AEM 6.4)
**Pattern:** Flattened Block (Simple — all fields on one block, no child items)
**Dual Purpose:** Standalone block + reusable field partial for other blocks

---

## 1. Overview and Design Rationale

### 1.1 Why a Dedicated CTA Block (Not OOTB Button Component)

The OOTB Button component (`core/franklin/components/button/v1/button`) provides basic fields: `href`, `text`, `title`, `type`. This does not meet the client's requirements for:

- **Icon support** — author must select from predefined icons (Arrow, Document, Download, Print, etc.)
- **Extended style variants** — Primary, Outline, Link (with additional variants during implementation)
- **Open in new tab** — explicit boolean toggle (not available on OOTB button)
- **Consistent CTA styling** — centrally managed across 88k+ pages via shared partials

The CTA block replaces the OOTB Button entirely. The OOTB Button component will **not** be used on this project.

### 1.2 Dual Purpose Architecture

The CTA serves two purposes from a single field definition source:

| Context | How It's Used | What Gets Created |
|---|---|---|
| **Standalone CTA** | Author drops CTA block directly on the page | Block: `blocks/cta/_cta.json` — full block definition + model |
| **CTA inside other blocks** | Hero, Cards, Testimonial, etc. embed CTA fields | Partial: `models/partials/_cta-fields.json` — field group spread into parent block model |

Both the standalone block and the partial use the **same field definitions and same option partials**. This ensures:

- No duplication of field definitions
- Consistent authoring experience regardless of where the CTA appears
- Change an option in one partial → all blocks using CTA get the update

---

## 2. UE Authoring Contract

### 2.1 AEM Resource (Standalone Block)

| Property | Value |
|---|---|
| Resource Type | `core/franklin/components/block/v1/block` |
| Block Name | `CTA` |
| Model ID | `cta` |
| Block Type | Simple (flattened — leaf block, no children) |
| JCR Path | `/content/<project>/<path>/jcr:content/root/<section>/cta` |

### 2.2 Editable Properties in Universal Editor

**When author clicks the standalone CTA block:**

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `label` | Button Text | text | Yes — Required | Label displayed on the CTA |
| `href` | URL | text | Yes — Required | Target link URL or content path |
| `external` | Open in New Tab | boolean (toggle) | Yes — Optional | Default: false |
| `icon` | Icon | select | Yes — Optional | Options from `_button-icons.json` partial. Default: none. |
| `type` | Style | select | Yes — Optional | Options from `_button-types.json` partial. Default: "primary". |

**No `classes` field on the CTA block** — the `type` field controls the visual style directly. There is no separate variant/classes mechanism because `type` IS the variant.

### 2.3 Container vs Leaf Fields

The CTA block is a **leaf block (flattened)**. All fields are on the block itself. No child items, no container behavior, no repeatable groups.

### 2.4 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| CTA link | `href` (text) | Author types URL or content path manually |

No DAM references, no fragments, no nested items. The CTA is a simple text + link component.

### 2.5 Block Registration — Component Definition

```json
{
  "title": "CTA",
  "id": "cta",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block",
        "template": {
          "name": "CTA",
          "model": "cta"
        }
      }
    }
  }
}
```

---

## 3. Component Model Definition (Field-Level)

### 3.1 CTA Block Model (Standalone Usage)

| Field | Component | Label | Required | Default | Validation | Authored / Derived |
|---|---|---|---|---|---|---|
| `label` | text | Button Text | Yes | -- | Must not be empty | Authored |
| `href` | text | URL | Yes | -- | Valid URL or content path | Authored |
| `external` | boolean | Open in New Tab | No | false | -- | Authored |
| `icon` | select | Icon | No | "" (none) | Options from `_button-icons.json` | Authored |
| `type` | select | Style | No | "primary" | Options from `_button-types.json` | Authored |

### 3.2 Style Options (`_button-types.json`)

| Option | Value | Visual |
|---|---|---|
| Primary | `primary` | Solid filled button (brand color background, white text) |
| Outline | `outline` | Border-only button (transparent background, brand color border + text) |
| Link | `link` | Text link style with optional icon (no background, no border) |

**Note from PDF:** ~~Secondary~~ and ~~Video~~ are struck through — removed from options. Additional variants will be added during implementation as needed.

### 3.3 Icon Options (`_button-icons.json`)

| Option | Value | Visual |
|---|---|---|
| None | `""` | No icon |
| Arrow | `arrow` | Right-pointing arrow (blue — per PDF: use the blue play button to the left, not the red classic one) |
| Document | `document` | Document icon |
| Download | `download` | Download icon |
| Print | `print` | Print icon |

Additional icons will be added during implementation as encountered across the site.

**Design note from PDF:** "The red play button (right arrow) is from classic. The blue play button to the left of the text is what we should use moving forward." All icons must use the current design system — no classic-era icons.

---

## 4. Partial Architecture — Reuse Across Blocks

### 4.1 Partial File Structure

```
models/partials/
├── _cta-fields.json          ← complete CTA field group (5 fields)
├── _dual-cta-fields.json     ← two CTA groups for blocks needing primary + secondary
├── _button-types.json        ← type options (Primary, Outline, Link)
└── _button-icons.json        ← icon options (Arrow, Document, Download, Print)
```

### 4.2 `_cta-fields.json` — Single CTA Field Group

This partial defines the **complete set of CTA fields** that any block can embed. When spread into a block model, authors see these fields as a group in the properties panel.

**Fields included:**

| Field | Component | Label |
|---|---|---|
| `ctaLabel` | text | CTA Text |
| `ctaHref` | text | CTA URL |
| `ctaExternal` | boolean | CTA Open in New Tab |
| `ctaIcon` | select | CTA Icon (spreads `_button-icons.json#/options`) |
| `ctaType` | select | CTA Style (spreads `_button-types.json#/options`) |

**How a block consumes it:**

```json
// Inside blocks/testimonial/_testimonial.json → model fields:
{
  "id": "testimonial",
  "fields": [
    { "component": "richtext", "name": "quote", "label": "Quote" },
    { "component": "richtext", "name": "attribution", "label": "Attribution" },
    { "component": "reference", "name": "authorImage", "label": "Author Image" },
    { "...": "../../models/partials/_cta-fields.json#/fields" }
  ]
}
```

One line spreads all 5 CTA fields into the Testimonial block model.

### 4.3 `_dual-cta-fields.json` — Dual CTA Field Groups

For blocks needing two CTAs (e.g., Hero), this partial wraps two instances of the CTA fields with different prefixes:

**Groups included:**

| Group | Prefix | Default Type |
|---|---|---|
| Primary CTA | `primaryCta*` | "primary" |
| Secondary CTA | `secondaryCta*` | "outline" |

**How Hero consumes it:**

```json
// Inside blocks/hero/_hero.json → model fields:
{
  "id": "hero",
  "fields": [
    { "component": "reference", "name": "image", "label": "Background Image" },
    { "component": "text", "name": "subtitle", "label": "Subtitle" },
    { "component": "richtext", "name": "description", "label": "Description" },
    { "...": "../../models/partials/_dual-cta-fields.json#/fields" }
  ]
}
```

One line spreads both primary and secondary CTA field groups into the Hero block model.

### 4.4 How Consuming Blocks Restrict Options

The partials define the **full set** of options. Each consuming block can **override** the type options to restrict what authors can select:

| Block | CTA Count | Type Options Available | Why Restricted |
|---|---|---|---|
| **CTA Block (standalone)** | 1 | Primary, Outline, Link (full list) | All styles available for standalone usage |
| **Hero** | 0, 1, or 2 | Primary, Outline only | Hero CTAs are always prominent buttons — Link style not appropriate |
| **Cards (per card)** | 0 or 1 | Primary, Outline | Same as Hero — card CTAs are buttons |
| **Testimonial** | 0 or 1 | Link only | Testimonial CTAs are always text links ("Read customer story") — not buttons |

**How restriction works:** The consuming block defines its own type field with a reduced options array instead of spreading `_button-types.json`. The icon field can use the full `_button-icons.json` unless restriction is also needed.

### 4.5 Partial vs Standalone — Field Name Mapping

The standalone CTA block uses **unprefixed** field names. Partials inside other blocks use **prefixed** names to avoid conflicts:

| Standalone CTA Block | Single CTA Partial (Cards, Testimonial) | Dual CTA Partial — Primary (Hero) | Dual CTA Partial — Secondary (Hero) |
|---|---|---|---|
| `label` | `ctaLabel` | `primaryCtaLabel` | `secondaryCtaLabel` |
| `href` | `ctaHref` | `primaryCtaHref` | `secondaryCtaHref` |
| `external` | `ctaExternal` | `primaryCtaExternal` | `secondaryCtaExternal` |
| `icon` | `ctaIcon` | `primaryCtaIcon` | `secondaryCtaIcon` |
| `type` | `ctaType` | `primaryCtaType` | `secondaryCtaType` |

Despite different names, they **render identically** — the block JS reads whatever field name is defined and applies the same CTA rendering logic.

### 4.6 Change Once, Update Everywhere

The core benefit of this architecture:

```
CHANGE: Add a new icon option "External Link"

UPDATE: models/partials/_button-icons.json
  → Add { "name": "External Link", "value": "external-link" }

RESULT: 
  ✅ CTA block (standalone) — new icon available
  ✅ Hero block — new icon available in both primary and secondary CTA
  ✅ Cards block — new icon available per card
  ✅ Testimonial block — new icon available
  ✅ Any future block using _cta-fields.json — new icon available
  
  ONE file changed. ALL blocks updated.
```

Same applies for adding a new type option, changing a label, or modifying validation.

---

## 5. Governance — Allowed Block Rules

| Rule | CTA Block |
|---|---|
| **Allowed in sections** | Yes — listed in `component-filters.json` section filter |
| **Allowed on all page types** | Yes — no page-type restriction |
| **Template restriction** | None — available on all templates |
| **Is it a container** | **No** — flattened/leaf block with no child items |
| **Fragment-only** | **No** — authored directly on the page |
| **Standalone** | **Yes** — author drops it into any section |
| **Editable regions** | All fields editable via properties panel |
| **Locked regions** | None |

---

## 6. Nested Authoring Strategy

### Classification: Flattened Block

| Pattern | Applies? | Reason |
|---|---|---|
| **Flattened block** | **Yes** | All fields on one block. No repeating items. Author fills one properties panel. |
| Parent-child structure | No | CTA is a single unit — no child items. |
| Reference-based block | No | CTA is authored directly — not a reference to another page. |

---

## 7. Author View in Universal Editor

### 7.1 Standalone CTA Block

```
Properties Panel (CTA Block):
┌────────────────────────────────────────────────────┐
│ Button Text: [Learn more              ]            │  ← text (required)
│ URL: [/products/antibodies            ]            │  ← text (required)
│ Open in New Tab: [ ] (toggle)                      │  ← boolean
│ Icon: [Arrow ▼]                                   │  ← select
│ Style: [Primary ▼]                                │  ← select
└────────────────────────────────────────────────────┘
```

### 7.2 CTA Inside Hero (Via Dual CTA Partial)

```
Properties Panel (Hero Block — CTA section):
┌────────────────────────────────────────────────────┐
│ ... (Hero image, subtitle, description above) ...  │
│                                                    │
│ ── Primary CTA ──                                  │
│ CTA Text: [Request Quote             ]             │
│ CTA URL: [/contact/request-quote     ]             │
│ CTA New Tab: [ ]                                   │
│ CTA Icon: [Arrow ▼]                              │
│ CTA Style: [Primary ▼]                           │  ← restricted: Primary, Outline only
│                                                    │
│ ── Secondary CTA ──                                │
│ CTA Text: [Learn More                ]             │
│ CTA URL: [/products/overview         ]             │
│ CTA New Tab: [ ]                                   │
│ CTA Icon: [ None ▼]                              │
│ CTA Style: [Outline ▼]                           │  ← restricted: Primary, Outline only
└────────────────────────────────────────────────────┘
```

### 7.3 CTA Inside Cards (Via Single CTA Partial)

```
Properties Panel (Card Item — CTA section):
┌────────────────────────────────────────────────────┐
│ ... (Card image, title, subtitle, desc above) ...  │
│                                                    │
│ ── CTA ──                                          │
│ CTA Text: [Explore                   ]             │
│ CTA URL: [/products/benchstable      ]             │
│ CTA New Tab: [ ]                                   │
│ CTA Icon: [Arrow ▼]                              │
│ CTA Style: [Primary ▼]                           │  ← restricted: Primary, Outline
└────────────────────────────────────────────────────┘
```

### 7.4 CTA Inside Testimonial (Via Single CTA Partial — Link Only)

```
Properties Panel (Testimonial Block — CTA section):
┌────────────────────────────────────────────────────┐
│ ... (Quote, attribution, author image above) ...   │
│                                                    │
│ ── CTA ──                                          │
│ CTA Text: [Read customer story       ]             │
│ CTA URL: [/customer-stories/saito    ]             │
│ CTA New Tab: [ ]                                   │
│ CTA Icon: [Arrow ▼]                              │
│ CTA Style: [Link ▼]                              │  ← restricted: Link only
└────────────────────────────────────────────────────┘
```

---

## 8. Complete Reuse Architecture

### 8.1 File Structure

```
models/partials/
├── _cta-fields.json              ← 5 CTA fields (label, href, external, icon, type)
│                                    Icon options spread from _button-icons.json
│                                    Type options spread from _button-types.json
│
├── _dual-cta-fields.json         ← 2 CTA groups (primary + secondary)
│                                    Each group spreads _cta-fields.json
│
├── _button-types.json            ← Type options: Primary, Outline, Link
│
└── _button-icons.json            ← Icon options: Arrow, Document, Download, Print

blocks/
├── cta/
│   ├── _cta.json                 ← Standalone CTA block definition + model
│   ├── cta.js                    ← Block decoration JS
│   └── cta.css                   ← Block styling (all CTA variants)
│
├── hero/
│   └── _hero.json                ← Spreads _dual-cta-fields.json (primary + secondary)
│                                    Overrides type options to: Primary, Outline only
│
├── cards/
│   └── _cards.json               ← Card item model spreads _cta-fields.json
│                                    Overrides type options to: Primary, Outline
│
└── testimonial/
    └── _testimonial.json         ← Spreads _cta-fields.json
                                     Overrides type options to: Link only
```

### 8.2 Shared CSS

All CTA variants — whether standalone or inside other blocks — use the same CSS classes:

```
styles/buttons.css (loaded globally):

.cta.primary, a.button.primary     → solid filled button
.cta.outline, a.button.outline     → border-only button
.cta.link, a.button.link           → text link style
.cta [data-icon="arrow"]           → arrow icon
.cta [data-icon="document"]        → document icon
.cta [data-icon="download"]        → download icon
.cta [data-icon="print"]           → print icon
```

Block JS in Hero, Cards, Testimonial applies the same CSS classes when rendering the CTA — ensuring visual consistency regardless of where the CTA appears.

### 8.3 Consistency Guarantee

| Aspect | How Consistency Is Maintained |
|---|---|
| **Field definitions** | Single source: `_cta-fields.json` — all blocks spread from it |
| **Type options** | Single source: `_button-types.json` — blocks override only when restricting |
| **Icon options** | Single source: `_button-icons.json` — shared across all CTA contexts |
| **Visual rendering** | Single CSS: `styles/buttons.css` — same classes everywhere |
| **Adding new options** | Update one partial file → all blocks get the update |
| **Author experience** | Same fields, same dropdowns, same behavior — whether standalone or inside Hero/Cards/Testimonial |
