# Accordion Block — Technical Details & UE Authoring Contract

**Block Name:** Accordion
**Maps To:** FAQ List (v1), FAQ Items, Accordion, Accordion Item (AEM 6.4)
**Pattern:** Parent-Child (Container block with repeatable accordion items)

---

## 1. UE Authoring Contract

### 1.1 AEM Resource

| Property | Value |
|---|---|
| Resource Type (Parent) | `core/franklin/components/block/v1/block` |
| Resource Type (Child Item) | `core/franklin/components/block/v1/block/item` |
| Block Name | `Accordion` |
| Model ID (Child) | `accordion-item` |
| Filter | `accordion` -> allows only `accordion-item` children |
| Block Type | Container (parent-child with repeatable items) |
| JCR Path (Parent) | `/content/<project>/<path>/jcr:content/root/<section>/accordion` |
| JCR Path (Child) | `/content/<project>/<path>/jcr:content/root/<section>/accordion/item_N` |

### 1.2 Editable Properties in Universal Editor

**Parent block (Accordion container):**

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `classes` | Style Variant | select | Yes | Options: default, icon-left, classic-small, classic-gray |

The parent block has no content fields — only the style variant. Content is authored on each child item.

**Child item (Accordion Item):**

| JCR Property | UE Properties Panel | Field Type | Editable? | Notes |
|---|---|---|---|---|
| `summary` | Panel Title | text | Yes — Required | The clickable header for the collapsible panel |
| `text` | Panel Body | richtext | Yes — Optional | For simple content (text, images, tables). Leave empty if using fragment. |
| `fragmentPath` | Rich Content Fragment | aem-content (path picker) | Yes — Optional | For complex content (columns, video, forms, product list). Select a fragment page. Leave empty if Panel Body is filled. |

**Either `text` or `fragmentPath` should be provided** — they are mutually exclusive in practice. Panel Title (`summary`) is always required.

### 1.3 Container vs Leaf Fields

The Accordion is a **container block (parent-child)**:

- **Parent (Accordion):** Container with style variant. No content fields. Governs which children are allowed via filter.
- **Children (Accordion Items):** Each item is a collapsible panel with title + body content (richtext OR fragment reference). Author adds/removes/reorders items freely.

### 1.4 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| Fragment reference | `fragmentPath` (aem-content) | Opens content path picker — author browses and selects a fragment page |
| Inline images in body | Within `text` richtext field | Author inserts images via richtext editor toolbar — DAM picker |
| Inline links in body | Within `text` richtext field | Author adds links via richtext editor |

### 1.5 Block Registration — Component Definition

**Parent (Accordion):**

```json
{
  "title": "Accordion",
  "id": "accordion",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block",
        "template": {
          "name": "Accordion",
          "filter": "accordion"
        }
      }
    }
  }
}
```

**Child (Accordion Item):**

```json
{
  "title": "Accordion Item",
  "id": "accordion-item",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block/item",
        "template": {
          "name": "Accordion Item",
          "model": "accordion-item",
          "summary": "Panel Title",
          "text": ""
        }
      }
    }
  }
}
```

---

## 2. Component Model Definition (Field-Level)

### 2.1 Accordion Item Field Definitions

| Field | Component | Label | Required | Default | Validation | Authored / Derived |
|---|---|---|---|---|---|---|
| `summary` | text | Panel Title | Yes | "" | Must not be empty | Authored |
| `text` | richtext | Panel Body | No | "" | Supports: bold, italic, links, lists, images, tables. Leave empty if using fragment. | Authored |
| `fragmentPath` | aem-content | Rich Content Fragment | No | -- | Must be valid content path if provided. Leave empty if using Panel Body. | Authored |

**Field descriptions in model (author guidance):**

```json
{
  "component": "richtext",
  "name": "text",
  "label": "Panel Body",
  "description": "For simple content (text, images, tables). Leave empty if using Rich Content Fragment below.",
  "value": ""
},
{
  "component": "aem-content",
  "name": "fragmentPath",
  "label": "Rich Content Fragment",
  "description": "For complex content (columns, video, forms, product list). Select a fragment page. Leave empty if Panel Body above is filled."
}
```

### 2.2 Parent-Level Fields

| Field | Component | Label | Required | Default | Notes |
|---|---|---|---|---|---|
| `classes` | select | Style Variant | No | "" (default) | Options: icon-left, classic-small, classic-gray |

### 2.3 Derived Fields

| Property | Source | Why Not in Block Model |
|---|---|---|
| Expand/collapse state | Browser `<details>` element | Runtime UI state — not persisted. Block uses native HTML `<details>/<summary>`. |

### 2.4 Two Usage Patterns — Same Block

The accordion block serves two use cases with the same model:

**Pattern 1: FAQ / Simple Content (text field)**

Author fills `summary` + `text`. Leaves `fragmentPath` empty.

```
Panel Title: "What is Western Blotting?"
Panel Body: "<p>Western blotting is a technique used to detect...</p><img src='diagram.jpg'>"
Fragment: (empty)
```

Suitable for: plain text, text with images, text with basic tables, text with links.

**Pattern 2: Complex Content (fragmentPath field)**

Author fills `summary` + `fragmentPath`. Leaves `text` empty.

```
Panel Title: "Product Specifications"
Panel Body: (empty)
Fragment: "/content/tfs/fragments/product-specs"
```

The fragment page is a regular page authored in Universal Editor containing any blocks (Columns, Video, Product List, Forms, etc.). At delivery time, the accordion block JS fetches the fragment's `.plain.html` and renders it inside the panel.

Suitable for: columns layout, videos, forms, product lists, or any combination of blocks.

**Content type support:**

| Content Inside Panel | Use `text` (richtext) | Use `fragmentPath` |
|---|---|---|
| Rich text | Yes | -- |
| Images | Yes (via richtext toolbar) | -- |
| Basic tables | Yes (via richtext toolbar) | -- |
| Links / CTAs | Yes (via richtext) | -- |
| Columns layout | -- | Yes (fragment page with Columns block) |
| Videos | -- | Yes (fragment page with Video block) |
| Forms | -- | Yes (fragment page with Form block) |
| Product List | -- | Yes (fragment page with Product List block) |
| Mixed complex content | -- | Yes (fragment page with multiple blocks) |

### 2.5 Block JS Rendering Logic

```
For each accordion item:
  1. Read summary → render as <summary> (clickable header)
  2. Check fragmentPath:
     - If fragmentPath is set → fetch fragment .plain.html → render inside panel body
       → decorate and load blocks inside fragment (so Video, Columns, etc. work)
     - If fragmentPath is empty → render text (richtext) as panel body
  3. If both are empty → render empty panel
  4. If both are filled → fragmentPath takes priority
```

---

## 3. Governance — Allowed Block Rules

| Rule | Accordion Block |
|---|---|
| **Allowed in sections** | Yes — listed in `component-filters.json` section filter |
| **Allowed on all page types** | Yes — no page-type restriction |
| **Template restriction** | None — available on all templates |
| **Is it a container** | **Yes** — parent-child container |
| **Allowed children** | `accordion-item` only (via `accordion` filter) |
| **Fragment-only** | **No** — authored directly on the page |
| **Standalone** | **Yes** — author drops it into any section |
| **Min/max items** | No technical limit. Must have at least 1 item to be meaningful. |
| **Editable regions** | Style variant on parent; title + body/fragment on each child item |
| **Locked regions** | None — all content is author-controlled |

### Governance in component-filters.json

```json
{
  "id": "accordion",
  "components": ["accordion-item"]
}
```

Only `accordion-item` children can be added inside an accordion. Authors cannot drop other blocks directly inside the accordion. Complex content is handled via the fragment reference on individual panels.

### Relationship to AEM 6.4

| AEM 6.4 | xWalk EDS |
|---|---|
| Nested components inside accordion items (parsys per item) | **Fragment reference pattern** — complex content authored in fragment pages, referenced via `fragmentPath` field |
| FAQ List with FAQ Items (v1) | Same structure — accordion parent with accordion-item children |
| Style System options per template | `classes` select on accordion parent model |

---

## 4. Nested Authoring Strategy

### Classification: Parent-Child (Container Block)

| Pattern | Applies? | Reason |
|---|---|---|
| Flattened block | No | Accordion has repeating items — each panel is a separate child. |
| **Parent-child structure** | **Yes** | Parent container with repeatable Accordion Item children. Each panel has its own title + body/fragment. |
| Reference-based block | No | The accordion itself is not a reference. Individual panels may reference fragments via `fragmentPath`. |

### Why Fragment Reference for Complex Content (Not Nested Blocks)

EDS does not support nested blocks — you cannot drop a Columns block, Video block, or Form block inside an accordion item. Accordion items are `block/v1/block/item` (leaf items), not sections that accept blocks.

The fragment reference approach solves this:
- Author creates complex content as a fragment page with full block support in UE
- Accordion item references the fragment via `fragmentPath` (content picker)
- Accordion block JS fetches and renders the fragment inline at delivery time
- Fragment content is independently editable (update fragment → all referencing accordions get the update)
- Simple FAQ panels don't need fragments — just use the richtext body

### Fragment Page Naming Convention (Recommendation)

```
/content/<project>/fragments/accordion/
├── product-specs-antibodies
├── product-specs-pcr
├── video-panel-western-blot
└── form-panel-request-quote
```
