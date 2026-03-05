# Thermo Fisher Scientific - Block Inventory

**Project:** AEM 6.4 to AEM Cloud + Edge Delivery Services Migration
**Date:** March 2026
**Source:** Site discovery analysis of thermofisher.com

---

## Table of Contents

1. [Summary](#summary)
2. [Standard Blocks (from EDS Library)](#standard-blocks-from-eds-library)
3. [Custom Blocks (to be built)](#custom-blocks-to-be-built)
4. [Block-to-Template Matrix](#block-to-template-matrix)
5. [Variant Details](#variant-details)
6. [Implementation Priority](#implementation-priority)

---

## Summary

| Category | Count |
|----------|-------|
| Standard blocks (from EDS library) | 5 |
| Custom blocks (to build) | 15 |
| **Total unique blocks** | **20** |
| **Total variants** | **~45+** |

---

## Standard Blocks (from EDS Library)

These blocks exist in the Edge Delivery Services boilerplate/library and can be used directly or extended with variants via CSS classes.

---

### 1. `hero`

**Description:** Full-width banner section with heading, description, and optional CTA. Used as the primary visual element at the top of landing pages.

| Variant | Description | Used In | CSS Class |
|---------|-------------|---------|-----------|
| `default` | Full-width image background + H1 + description + CTA button | T2 | — |
| `brand` | Brand logo image + anchor tab navigation | T6 | `brand` |
| `category` | Category image + H1 + description + search CTA | T2 | `category` |
| `events` | H1 with image background for events page | T9 | `events` |
| `video` | H1 + H3 subtitle + video play CTA button | T17 | `video` |

**xWalk Component Model:**
- `classes` dropdown: default, brand, category, events, video
- Properties: title, description, image, ctaText, ctaLink, videoId

---

### 2. `cards`

**Description:** The most versatile block. Grid-based card layouts used across nearly all templates. Each variant controls the card structure, number of columns, and content pattern.

| Variant | Description | Columns | Used In | CSS Class |
|---------|-------------|---------|---------|-----------|
| `icon` | Icon/image + title (clickable) | 4-col | T1, T2, T3 | `icon` |
| `feature` | Icon + H3 + description | 3-col | T2 | `feature` |
| `promo` | Badge overlay + image + title + description + CTA link | 4-col | T1, T7 | `promo` |
| `blog` | Image + title + excerpt | 3-col | T1 | `blog` |
| `product` | Image + H4 + description | 4-col | T1, T3 | `product` |
| `event` | H2 title + H4 date/location + description + image + CTA | 1-col (list) | T9 | `event` |
| `cross-sell` | Product image + title + price (horizontal) | 3-col | T4 | `cross-sell` |
| `resource` | Thumbnail image + H3 + description | 3-col | T6, T13 | `resource` |
| `education` | Image + title + description + CTA | 3-col | T2 | `education` |
| `link-card` | Text-only link with title | 3-col | T2 | `link-card` |
| `service` | H2 + paragraph + "Learn more" CTA | 2-col | T5 | `service` |
| `brand` | Logo image + H3 brand name + description + CTA link | 2-3 col | T11 | `brand` |
| `category` | H3 title + description (clickable, no image) | 4-col | T12 | `category` |
| `criteria` | Icon + H2 + description + link | 3-col | T14 | `criteria` |
| `story` | Scientist image + H3 quote + person name | 3-col | T17 | `story` |
| `showcase` | Large image + product title + "Learn more" CTA | 1-2 col | T16 | `showcase` |
| `info` | H3 + image + description + CTA | 3-col | T14 | `info` |

**xWalk Component Model:**
- `classes` dropdown: icon, feature, promo, blog, product, event, cross-sell, resource, education, link-card, service, brand, category, criteria, story, showcase, info
- Properties: image, title, description, ctaText, ctaLink, badge (for promo), personName (for story), date (for event)

---

### 3. `columns`

**Description:** Multi-column layout block for side-by-side content. Used for feature comparisons, promotional banners, link directories, and content layouts.

| Variant | Description | Used In | CSS Class |
|---------|-------------|---------|-----------|
| `default` | Generic 2+ column layout | Multiple | — |
| `image-links` | Image left + bulleted link list right | T2 | `image-links` |
| `comparison` | Side-by-side feature comparison (Standard vs Advanced) | T3 | `comparison` |
| `promo` | Image + text + CTA button (promotional) | T5, T8, T15 | `promo` |
| `links` | Multi-column categorized link lists | T6 | `links` |
| `support` | Multi-column support category directories | T8 | `support` |
| `feature` | 2-col: text + CTA left, image right (alternating) | T14, T17 | `feature` |
| `stats` | Animated statistics with labels + descriptions | T14 | `stats` |

**xWalk Component Model:**
- `classes` dropdown: default, image-links, comparison, promo, links, support, feature, stats
- Properties: varies by variant — image, title, description, links, ctaText, ctaLink, statValue, statLabel

---

### 4. `carousel`

**Description:** Horizontally scrollable content with navigation dots/arrows. Used for hero banners and product showcases on the homepage.

| Variant | Description | Used In | CSS Class |
|---------|-------------|---------|-----------|
| `hero` | Full-width rotating slides with image + heading + CTA | T1 | `hero` |
| `product` | Horizontal scrolling product cards | T1 | `product` |

**xWalk Component Model:**
- `classes` dropdown: hero, product
- Properties: slides (multi-field: image, title, description, ctaText, ctaLink)

---

### 5. `table`

**Description:** Structured data display in tabular format. Used for product variant selection and specifications.

| Variant | Description | Used In | CSS Class |
|---------|-------------|---------|-----------|
| `default` | Standard data table | Multiple | — |
| `product-variants` | Product SKU selection with size, quantity, catalog# | T4 | `product-variants` |

**xWalk Component Model:**
- `classes` dropdown: default, product-variants
- Properties: Standard table authoring in Universal Editor

---

## Custom Blocks (to be built)

These blocks need to be developed from scratch as they don't exist in the standard EDS library.

---

### 6. `tabs`

**Description:** Tabbed content area where clicking a tab shows/hides content panels. Used on product detail pages for Overview, FAQ, Specifications, and Documents sections.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T4 (PDP), T6 (Brand) |
| **Requires JS** | Yes (tab switching) |
| **Variants** | None |

**Content Model:** Tab label + tab content (rich text) as multi-field

---

### 7. `table-of-contents`

**Description:** Collapsible/expandable list of anchor links that jump to H2 sections on the page. Rendered as an accordion-style expandable widget.

| Property | Value |
|----------|-------|
| **Complexity** | Low |
| **Used In** | T5 (Services), T8 (Support), T15 (Directory) |
| **Requires JS** | Yes (expand/collapse, smooth scroll) |
| **Variants** | None — auto-generates from H2 headings on page |

---

### 8. `sidebar-nav`

**Description:** Left-sidebar tree navigation with expandable parent/child/sibling hierarchy. Shows the current page position in the content tree and allows navigation to related pages.

| Property | Value |
|----------|-------|
| **Complexity** | High |
| **Used In** | T3 (Sub-Category), T6 (Brand) |
| **Requires JS** | Yes (tree expand/collapse, active state) |
| **Variants** | `tree` (hierarchical nav), `links` (flat categorized link lists) |

**Implementation Notes:**
- Content authored as nested list in a fragment
- Or auto-generated from query index based on URL path
- Active page highlighting
- Expand/collapse with +/- icons
- Deep hierarchy support (6+ levels)

---

### 9. `sub-navigation`

**Description:** Simpler left-sidebar navigation for sub-page links. Unlike `sidebar-nav`, this is a flat list of sibling page links without tree hierarchy. Used for content hub pages.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T14 (Content Hub) |
| **Requires JS** | Minimal (active state only) |
| **Variants** | None |

**Difference from `sidebar-nav`:** Flat link list (no tree expand/collapse). Simpler authoring — just a list of links.

---

### 10. `product-detail`

**Description:** The main product information block on PDP pages. Displays brand label, product title, description, promotion banner, price, quantity selector, and add-to-cart button. Requires real-time API integration.

| Property | Value |
|----------|-------|
| **Complexity** | High |
| **Used In** | T4 (PDP) |
| **Requires JS** | Yes (API calls, cart, quantity) |
| **API Dependencies** | Pricing API, Inventory API, Cart API |
| **Variants** | None |

**Implementation Notes:**
- Price and inventory fetched via Edge Worker (SEO) or client-side API
- Add-to-cart triggers eCommerce integration
- Promotion banners are dynamic/personalized

---

### 11. `product-image`

**Description:** Product image gallery with zoom capability and thumbnail strip. Allows users to view product images in detail.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T4 (PDP) |
| **Requires JS** | Yes (zoom, thumbnail switching) |
| **Variants** | None |

---

### 12. `promo-banner`

**Description:** Dismissible promotional banner that appears at the top of pages. Shows promotional text with a CTA link. User can dismiss (remember state via cookie/localStorage).

| Property | Value |
|----------|-------|
| **Complexity** | Low |
| **Used In** | T1-T17 (all pages) |
| **Requires JS** | Yes (dismiss/close, persistence) |
| **Variants** | None — appears in header area globally |

---

### 13. `sticky-banner`

**Description:** Bottom-fixed promotional banner that stays visible as the user scrolls. Contains an image, promotional text, CTA link, and close button.

| Property | Value |
|----------|-------|
| **Complexity** | Low |
| **Used In** | T3, T16 |
| **Requires JS** | Yes (close, position fixed) |
| **Variants** | None |

---

### 14. `video`

**Description:** Brightcove video player embed. Supports inline playback with poster image. Used on brand hub, sustainability, and campaign pages.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T11 (Brand Hub), T14 (Content Hub), T17 (Campaign) |
| **Requires JS** | Yes (Brightcove player SDK) |
| **Variants** | None — Brightcove video ID authored |

**Content Model:** Video ID (Brightcove account + video ID), optional poster image

---

### 15. `filter-bar`

**Description:** Horizontal tab buttons for filtering a card grid by category. Used on promotions page to filter offers by type (% Off, Funding, Instruments, etc.).

| Property | Value |
|----------|-------|
| **Complexity** | High |
| **Used In** | T7 (Promotions) |
| **Requires JS** | Yes (client-side filtering, tab switching) |
| **Variants** | None |

**Implementation Notes:**
- Filters driven by card metadata (data attributes)
- Consider query index for card data source
- Tab labels are authored, filtering logic is JS

---

### 16. `event-list`

**Description:** Structured event listing with date, location, description, image, and CTA. Rendered as a table/list layout with optional search filtering.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T9 (Events) |
| **Requires JS** | Yes (search filtering) |
| **Variants** | None |

**Implementation Notes:**
- Events may come from external feed/API
- Consider query index or Edge Worker for data source
- Each row: H2 title (linked) + H4 date/location + description + image

---

### 17. `search-filter`

**Description:** Text input field for searching/filtering content on the page. Used on events and promotions pages for client-side search.

| Property | Value |
|----------|-------|
| **Complexity** | High |
| **Used In** | T7, T9 |
| **Requires JS** | Yes (live search/filter) |
| **Variants** | None |

---

### 18. `ai-search`

**Description:** Conversational AI-powered product search card. Appears on PDP pages, allowing users to ask questions about the product.

| Property | Value |
|----------|-------|
| **Complexity** | Medium |
| **Used In** | T4 (PDP) |
| **Requires JS** | Yes (AI API integration) |
| **Variants** | None |

---

### 19. `callout`

**Description:** Highlighted text box for emphasizing statistics, quotes, or key information. Used as inline content callouts within longer pages.

| Property | Value |
|----------|-------|
| **Complexity** | Low |
| **Used In** | T3 |
| **Requires JS** | No |
| **Variants** | None |

---

### 20. `sidebar-links`

**Description:** Quick-access sidebar widget with categorized action links and separators. Used on support pages for common actions like Order Status, Contact Us, Documents.

| Property | Value |
|----------|-------|
| **Complexity** | Low |
| **Used In** | T8 (Support Hub) |
| **Requires JS** | No |
| **Variants** | None |

---

## Block-to-Template Matrix

| Block | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | T9 | T10 | T11 | T12 | T13 | T14 | T15 | T16 | T17 |
|-------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `hero` | x | x | | | | x | | | x | | x | | | | | | x |
| `cards` | x | x | x | x | x | x | x | x | x | | x | x | x | x | | x | x |
| `columns` | | x | x | | x | x | | x | | | x | | | x | x | | x |
| `carousel` | x | | | | | | | | | | | | | | | | |
| `table` | | | | x | | | | | | | | | | | | | |
| `tabs` | | | | x | | x | | | | | | | | | | | |
| `table-of-contents` | | | | | x | | | x | | | | | | | x | | |
| `sidebar-nav` | | | x | | | x | | | | | | | | | | | |
| `sub-navigation` | | | | | | | | | | | | | | x | | | |
| `product-detail` | | | | x | | | | | | | | | | | | | |
| `product-image` | | | | x | | | | | | | | | | | | | |
| `promo-banner` | x | x | x | x | x | x | x | x | x | | x | x | x | x | x | x | x |
| `sticky-banner` | | | x | | | | | | | | | | | | | x | |
| `video` | | | | | | | | | | | x | | | x | | | x |
| `filter-bar` | | | | | | | x | | | | | | | | | | |
| `event-list` | | | | | | | | | x | | | | | | | | |
| `search-filter` | | | | | | | x | | x | | | | | | | | |
| `ai-search` | | | | x | | | | | | | | | | | | | |
| `callout` | | | x | | | | | | | | | | | | | | |
| `sidebar-links` | | | | | | | | x | | | | | | | | | |

**Legend:** T1=Homepage, T2=Category, T3=Sub-Category, T4=PDP, T5=Services, T6=Brand, T7=Promotions, T8=Support, T9=Events, T10=Blog (WP), T11=Brand Hub, T12=Division Hub, T13=Resource Hub, T14=Content Hub, T15=Directory, T16=Showcase, T17=Campaign

---

## Variant Details

### Cards Variant Summary (17 variants)

| # | Variant | Columns | Image | Title | Description | CTA | Badge | Special |
|---|---------|---------|:-----:|:-----:|:-----------:|:---:|:-----:|---------|
| 1 | icon | 4 | x | x | | | | Icon-sized images |
| 2 | feature | 3 | x | x | x | | | Icon + longer desc |
| 3 | promo | 4 | x | x | x | x | x | Badge overlay |
| 4 | blog | 3 | x | x | x | | | Excerpt text |
| 5 | product | 4 | x | x | x | | | Product image |
| 6 | event | 1 (list) | x | x | x | x | | Date + location sub-header |
| 7 | cross-sell | 3 | x | x | | | | Horizontal layout, price |
| 8 | resource | 3 | x | x | x | | | Thumbnail image |
| 9 | education | 3 | x | x | x | x | | Full card with CTA |
| 10 | link-card | 3 | | x | | | | Text-only link |
| 11 | service | 2 | | x | x | x | | H2 + description + CTA |
| 12 | brand | 2-3 | x | x | x | x | | Logo-sized image |
| 13 | category | 4 | | x | x | | | No image, clickable |
| 14 | criteria | 3 | x | x | x | x | | Icon-style image |
| 15 | story | 3 | x | x | | | | Person name below |
| 16 | showcase | 1-2 | x | x | | x | | Large format |
| 17 | info | 3 | x | x | x | x | | Standard info card |

### Columns Variant Summary (8 variants)

| # | Variant | Typical Cols | Image | Links | CTA | Special |
|---|---------|:----------:|:-----:|:-----:|:---:|---------|
| 1 | default | 2 | | | | Generic layout |
| 2 | image-links | 2 | x | x | | Image + bulleted links |
| 3 | comparison | 2 | | | | Side-by-side features |
| 4 | promo | 2 | x | | x | Promotional with CTA |
| 5 | links | 2-3 | | x | | Categorized link lists |
| 6 | support | 3-4 | | x | | Support directory |
| 7 | feature | 2 | x | | x | Text+CTA | Image (alternating) |
| 8 | stats | 3 | | | | Animated stat counters |

### Hero Variant Summary (5 variants)

| # | Variant | Image BG | Video | CTA | Special |
|---|---------|:--------:|:-----:|:---:|---------|
| 1 | default | x | | x | Standard banner |
| 2 | brand | x | | | Brand logo display |
| 3 | category | x | | x | Category landing |
| 4 | events | x | | | Event page banner |
| 5 | video | | x | x | Video play CTA |

---

## Implementation Priority

### Phase 1 - Foundation (Must Have)

These blocks are needed for the homepage and primary landing pages:

| Block | Reason |
|-------|--------|
| `hero` (default, category) | Primary visual on T1, T2 |
| `cards` (icon, feature, product) | Most common content pattern |
| `columns` (default, promo) | Layout and promotional banners |
| `carousel` (hero) | Homepage hero |
| `promo-banner` | Global element on all pages |

### Phase 2 - Content Scale

Additional blocks for category pages and brand pages:

| Block | Reason |
|-------|--------|
| `sidebar-nav` | Required for T3 and T6 |
| `cards` (promo, blog, education, brand, category, resource) | Extended card variants for content pages |
| `columns` (image-links, links, feature) | Sub-category and brand page layouts |
| `tabs` | PDP and brand pages |
| `table-of-contents` | Services, support, directory pages |
| `video` | Brand hub, content hub, campaign pages |

### Phase 3 - Commerce Integration

PDP-specific blocks:

| Block | Reason |
|-------|--------|
| `product-detail` | Core PDP block (API integration) |
| `product-image` | Product gallery |
| `table` (product-variants) | SKU selection |
| `cards` (cross-sell) | Buy-together recommendations |
| `ai-search` | Product Q&A |

### Phase 4 - Supporting Pages

Remaining blocks for support, events, promotions:

| Block | Reason |
|-------|--------|
| `filter-bar` | Promotions page filtering |
| `event-list` | Events listing |
| `search-filter` | Events and promotions search |
| `sidebar-links` | Support hub sidebar |
| `callout` | Content callouts |
| `sticky-banner` | Bottom promotional banners |
| `sub-navigation` | Content hub pages |
| `cards` (event, service, criteria, story, showcase, info) | Remaining card variants |
| `columns` (comparison, support, stats) | Remaining column variants |

---

*Block inventory generated as part of AEM 6.4 to AEM Cloud + Edge Delivery Services migration discovery for Thermo Fisher Scientific.*
