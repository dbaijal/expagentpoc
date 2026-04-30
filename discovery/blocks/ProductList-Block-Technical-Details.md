# Product List Block — Technical Details & UE Authoring Contract

**Block Name:** Product List
**Maps To:** Product List Component (AEM 6.4)
**Pattern:** Flattened Block (Simple — all fields on one block, no child items)

> **Note:** Product data (names, sizes, pricing, availability) is NOT authored. It is fetched dynamically at runtime from the Product Microservice based on the SKU list configured by the author. This block defines WHAT to show (SKUs + columns). The Edge Worker + Microservice handle HOW to render it.

---

## 1. UE Authoring Contract

### 1.1 AEM Resource

| Property | Value |
|---|---|
| Resource Type | `core/franklin/components/block/v1/block` |
| Block Name | `Product List` |
| Model ID | `product-list` |
| Block Type | Simple (flattened — no child items) |
| JCR Path | `/content/<project>/<path>/jcr:content/root/<section>/product-list` |

### 1.2 Editable Properties in Universal Editor

| JCR Property | UE Properties Panel | Field Type | Required | Default | Notes |
|---|---|---|---|---|---|
| `skuList` | SKU List | text (textarea) | Yes | "" | Comma-separated SKU IDs (e.g., "16096040,15596018,A33251,17909") |
| `showSize` | Size | boolean | No | true | Show/hide Size column |
| `showPrice` | List Price | boolean | No | true | Show/hide Price column |
| `showQuantity` | Quantity | boolean | No | true | Show/hide Quantity input column |
| `showAddToCart` | Add to Cart | boolean | No | true | Show/hide Add to Cart button column |
| `showPdpLink` | PDP UI | boolean | No | false | Show/hide link to Product Detail Page |

### 1.3 Author View in Universal Editor

```
Properties Panel (Product List block):
┌────────────────────────────────────────────────────────┐
│                                                        │
│ SKU List:                                              │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 16096040,15596018,10296028,A33251,15593049,17909,  │ │
│ │ A39110,A35378,A25602,AM2696,AM2684,EP0042          │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ ── Table Columns ──                                    │
│ Size:         [✓] (toggle)                             │
│ List Price:   [✓] (toggle)                             │
│ Quantity:     [✓] (toggle)                             │
│ Add to Cart:  [✓] (toggle)                             │
│ PDP UI:       [ ] (toggle)                             │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### 1.4 Container vs Leaf Fields

The Product List is a **leaf block (flattened)**. All fields are on the block itself. No child items.

The product rows (individual products) are NOT authored — they are dynamically rendered at runtime based on the SKU list. The block is purely a **configuration block** — it tells the system what to fetch and which columns to display.

### 1.5 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| SKU identifiers | `skuList` (text) | Author types/pastes comma-separated SKU IDs. No picker — authors know their SKUs. |

No DAM references. No fragments. No nested items. Product data is resolved at runtime from external APIs.

### 1.6 Block Registration

```json
{
  "title": "Product List",
  "id": "product-list",
  "plugins": {
    "xwalk": {
      "page": {
        "resourceType": "core/franklin/components/block/v1/block",
        "template": {
          "name": "Product List",
          "model": "product-list"
        }
      }
    }
  }
}
```

---

## 2. Component Model Definition

### 2.1 Field Definitions

| Field | Component | Label | Required | Default | Validation | Authored / Derived |
|---|---|---|---|---|---|---|
| `skuList` | text | SKU List | Yes | "" | Must contain at least one SKU. Comma-separated alphanumeric IDs. | Authored |
| `showSize` | boolean | Size | No | true | -- | Authored |
| `showPrice` | boolean | List Price | No | true | -- | Authored |
| `showQuantity` | boolean | Quantity | No | true | -- | Authored |
| `showAddToCart` | boolean | Add to Cart | No | true | -- | Authored |
| `showPdpLink` | boolean | PDP UI | No | false | -- | Authored |

### 2.2 Derived Data (Not Authored — Fetched at Runtime)

| Data | Source | How Resolved |
|---|---|---|
| Product Name | Catalog API (via Product Microservice) | Fetched by SKU at runtime |
| Size / Format | Catalog API | Fetched by SKU at runtime |
| Price | Pricing API (via Product Microservice) | Fetched by SKU + user context at runtime |
| Availability | Pricing API | Fetched by SKU at runtime |
| Price Access Type (OrderNow, RequestQuote, NoPrice) | Pricing API | Determines CTA behavior per product row |

**Authors configure WHAT to show. The system resolves HOW to show it.**

---

## 3. Governance

| Rule | Product List Block |
|---|---|
| **Allowed in sections** | Yes |
| **Allowed on all page types** | Yes |
| **Is it a container** | **No** — flattened block |
| **Standalone** | **Yes** |
| **Editable regions** | SKU list + column toggles |
| **Locked regions** | Product data (names, prices, sizes) — not editable by author, fetched from APIs |

---

## 4. Nested Authoring Strategy

### Classification: Flattened Block

| Pattern | Applies? | Reason |
|---|---|---|
| **Flattened block** | **Yes** | Configuration-only block. Author provides SKU list + column preferences. No child items, no repeating elements. Product rows are rendered dynamically, not authored. |
| Parent-child | No | Product rows are NOT authored child items — they are API-driven. |
| Reference-based | No | Block is not a reference to another page or fragment. |

---

## 5. Runtime Rendering (How Authored Config Becomes a Product Table)

```
AUTHORED (in JCR):                    RENDERED (at delivery):

skuList = "A33251,17909,A39110"       ┌─────────────────────────────────────────────┐
showSize = true                       │ Product Name    │ Size  │ Price   │ Qty │ 🛒 │
showPrice = true                      ├─────────────────┼───────┼─────────┼─────┼────┤
showQuantity = true                   │ Reagent ABC     │ 100ml │ $299.00 │ [1] │ Add│
showAddToCart = true                  │ Buffer XYZ      │ 500ml │ $149.00 │ [1] │ Add│
showPdpLink = false                   │ Kit 123         │ 1 kit │ $899.00 │ [1] │ Add│
                                      └─────────────────────────────────────────────┘

Author provides config → Edge Worker + Microservice fetch data → HTML rendered with products
```

**Product Name column is always shown** (not configurable — it's the baseline identifier). The toggle columns (Size, Price, Quantity, Add to Cart, PDP UI) control which additional columns appear.
