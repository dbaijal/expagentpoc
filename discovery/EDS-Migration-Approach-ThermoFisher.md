# Thermo Fisher Scientific — EDS Migration Approach

## AEM 6.4 to AEM Cloud (xWalk / Universal Editor) with Edge Delivery Services

---

## 1. Fundamental Architecture Shift

### What Changes from AEM 6.4 to EDS

| Concept | AEM 6.4 | EDS (xWalk + Universal Editor) |
|---------|---------|-------------------------------|
| Layout control | Container / Parsys / Responsive Grid — author resizes columns freely | No responsive grid. Layout is governed by the block itself |
| Component model | Component dialog with tabs, fields, multifields | Block properties panel with fields (text, select, boolean, etc.) |
| Variant selection | Free-form style system classes | `classes` dropdown (single-select for layout) + boolean toggles for configurations |
| Content reuse | Experience Fragments, Content Fragments | Fragments (`.plain.html`) and spreadsheet JSON endpoints |
| Navigation | Auto-generated from JCR content tree | Query-index based (auto) or manually authored links |
| Data-driven lists | Table Component + Row children + Servlet → JSON | Spreadsheet → JSON endpoint → Block JS |
| Section styling | Background component, container styles | Section Metadata block (style, background image) |
| Sidebar layout | `span3 + span9` parsys grid | Block-level CSS grid/flexbox within a section |

### Key Principle: Same Content Structure = One Block

If the authored content fields are the same across different visual layouts, it is **one block with layout variants** — not multiple blocks. The block's CSS handles the visual differences.

---

## 2. Block Inventory & Approach

### 2.1 Cards Block

**Replaces:** Product tiles, service tiles, feature grids, icon cards

**Rule:** Cards = always 2+ items in a grid. Each item has the same content structure.

| Variant | Use Case | What Author Does |
|---------|----------|-----------------|
| `cards` (default) | Standard image + title + description + CTA | Author adds 2+ card items |
| `cards (two-up)` | 2-column grid | Select "Two Up" from layout dropdown |
| `cards (three-up)` | 3-column grid | Select "Three Up" from layout dropdown |
| `cards (no-images)` | Title + description + CTA, no images | Author simply doesn't add an image — block detects automatically |
| `cards (overlay)` | Image as full background, text overlay at bottom | Select "Overlay" from layout dropdown |
| `cards (list)` | Horizontal layout, image left + text right, stacked vertically | Select "List" from layout dropdown |

**Author properties (container level):**
- Layout: single-select dropdown (two-up / three-up / four-up / overlay / list)
- No-border: boolean toggle (additive — works with any layout)

**Author properties (item level):**
- Image (optional — absence auto-detected)
- Title
- Description
- CTA link

**Important:** Cards block requires 2 or more items. For a single prominent item, use Hero, Columns, or Default content instead.

---

### 2.2 Columns Block

**Replaces:** Side-by-side layouts, 60/40 content panels, text + image pairs

**Rule:** Use Columns when content items sit side-by-side and the title IS the link (no separate CTA button). Use Cards when there's a separate CTA.

| Variant | Use Case | What Author Does |
|---------|----------|-----------------|
| `columns` (default) | Equal width columns (50/50) | Author adds 2+ column items |
| `columns (60-40)` | Wider left, narrower right | Select "60 / 40" from width dropdown |
| `columns (40-60)` | Narrower left, wider right | Select "40 / 60" from width dropdown |
| `columns (70-30)` | Dominant left, supporting right | Select "70 / 30" from width dropdown |
| `columns (links)` | Column of categorized links (e.g., Resources & Support) | Author adds link lists within columns |

**Author properties (container level):**
- Column Widths: single-select dropdown (Equal / 60-40 / 40-60 / 70-30 / 30-70)
- Number of columns is automatic based on how many items the author adds

**Decision framework — Cards vs Columns:**
- Title + separate CTA button → **Cards**
- Title IS the clickable link (no separate CTA) → **Columns**

---

### 2.3 Hero Block

**Replaces:** Page heading hero, promotional banner, full-width overlay content

**Rule:** Single prominent item with background image and overlaid text. Always full section width.

| Variant | Use Case |
|---------|----------|
| `hero` (default) | Background image + heading + description + CTA |
| `hero (overlay)` | Darker overlay for text contrast on busy images |

---

### 2.4 Default Content (No Block Needed)

**Replaces:** Simple text sections, promotional strips, announcement bars

**What it is:** Standard content (headings, paragraphs, links, images) authored directly in a section without wrapping in a block. Styling comes from **Section Metadata**.

**Examples identified on the site:**
- Purple promotional strips with heading + CTA + background color/image
- "Download assets" announcement bars
- Introductory text paragraphs
- Green facts / statistics sections

**What Author Does:**
- Authors heading, paragraph, and link directly
- Adds Section Metadata block to the section for visual styling (background color, background image, style variant like `dark-promo`)

---

### 2.5 Section Metadata

**Not a visible block** — it's a configuration block that applies styling to the entire section it belongs to.

**Common uses identified:**

| Style | Purpose |
|-------|---------|
| `dark-promo` | Dark background, white text, promotional strip |
| `overlap-top` | Pulls section up to overlap the previous section (e.g., cards overlapping hero) |
| `two-column-promo` | Side-by-side layout for hero + stacked cards |
| Background image | Full-width section background |

**How hero/cards overlap works:**
- Hero section has extra bottom padding
- Following section has Section Metadata with `style: overlap-top`
- CSS applies `margin-top: -100px` and `z-index: 2` to create the overlap effect

---

### 2.6 Separator / Divider

**No block needed.** In EDS, the section boundary (`---`) is the natural separator. A single global CSS rule styles the visible line between sections:

- Every section break creates a `<div class="section">` boundary
- CSS adds `border-top` to each section for the visible divider line
- Works automatically on every page — no author action required

---

## 3. Navigation Approach

### Current AEM 6.4 Navigation Patterns (5 Types Found)

| # | AEM Pattern | Where Used | How It Works Today |
|---|-------------|------------|-------------------|
| 1 | Auto Navigation (`cmp-autonavigation`) | sustainable-design.html | Generates tabs from JCR child pages |
| 2 | Manual Navigation (`cmp-manualnavigation`) | sustainable-design.html | Hand-picked tab links wrapping auto-nav |
| 3 | Custom Navigation (Anchor List + Accordion) | agrigenomics resources | Horizontal tabs + sidebar TOC with anchor links |
| 4 | Nav List Custom Manual (`leftnav-manual`) | thermo-scientific.html | Left sidebar with manually curated categorized links |
| 5 | Nav List Auto (`leftnav-auto`) | real-time-pcr-assays.html | Left sidebar auto-generated from content tree |

### EDS Approach — 2 Blocks (down from 5 AEM components)

#### Block: `sub-nav` (Single block with 2 variants)

**Consolidates:** Auto Navigation, Manual Navigation, Nav List Auto, Nav List Custom Manual

**Key architectural decision:** One block, two independent concerns:
- **Mode** — Auto or Manual (how data is sourced)
- **Layout** — Horizontal tabs or Sidebar (how it's displayed)

| Variant Combination | Replaces AEM Pattern | What Author Does |
|--------------------|--------------------|-----------------|
| `sub-nav` (manual, horizontal) | Manual Navigation | Author lists page links in the block |
| `sub-nav` (auto, horizontal) | Auto Navigation | Author enters a root path; block fetches child pages from query-index |
| `sub-nav` (manual, sidebar) | Nav List Custom Manual | Author lists categorized links (bold headers + link items) in a fragment |
| `sub-nav` (auto, sidebar) | Nav List Auto | Author enters a root path; block fetches child/grandchild pages from query-index |

**Author properties:**
- Layout: single-select dropdown (Horizontal Tabs / Sidebar)
- Navigation Mode: single-select dropdown (Manual / Auto)
- Root Path: text field (used only in Auto mode — path from which child pages are pulled)

**How Auto mode works in EDS:**
- EDS maintains a `query-index.json` that indexes every page with its metadata
- Block fetches this index and filters pages under the author's specified root path
- Child pages appear automatically as navigation links
- Authors control ordering and display via page metadata properties:
  - `nav-order` — Sort position in auto-nav
  - `nav-title` — Shorter display name override
  - `nav-hidden` — Exclude a page from auto-nav

**Auto vs Manual decision:**
| Concern | Auto | Manual |
|---------|------|--------|
| New child page added | Appears automatically in nav | Author must update link list |
| Cross-section links | Not possible (path-filtered) | Yes, any URL |
| Maintenance | Low (self-updating) | Higher (manual updates) |
| Best for | Structured product/section hierarchies | Curated brand hubs, hand-picked lists |

#### Block: `table-of-contents`

**Replaces:** Custom Navigation sidebar TOC / Anchor List + Accordion

**How it works:**
- Block automatically scans all H2 headings on the page
- Generates an anchor link list
- Collapsible on mobile, always visible on desktop
- No author configuration needed for the link items — they're auto-generated from page content

**What Author Does:**
- Adds the TOC block to the page
- Optionally sets a title (defaults to "Table of Contents")
- Selects style: Collapsible (default) or Always Visible

---

## 4. Data-Driven Filterable Lists (Events / Resources)

### Current AEM 6.4 Approach

```
Author configures Table Component properties (search, filter, paginate)
         |
Author defines filter groups and their values (Event Type, Country, Month)
         |
Author adds Row child components, fills data fields per row
         |
TableServlet serializes table + row data to JSON
         |
PragmaDataTable.js renders HTML table with client-side interactivity
         |
User sees: search bar + filter dropdowns + event cards with images
```

**Author maintains data in TWO places:** Table config (filter definitions) + each Row component (data values and filter mappings).

### EDS Approach — Spreadsheet-Driven `data-list` Block

```
Author maintains a spreadsheet (e.g., events.xlsx)
         |
EDS auto-generates a JSON endpoint (/events-data.json)
         |
data-list block fetches JSON on page load
         |
Block JS builds search, filters, and list from the data
         |
Filter values are auto-derived from spreadsheet column values
```

**Author maintains data in ONE place:** The spreadsheet. Filter values are automatically extracted from the data — no separate filter configuration needed.

#### Spreadsheet Structure (events.xlsx)

| title | date | end-date | location | description | link | image | event-type | country | month | search-keywords |
|-------|------|----------|----------|-------------|------|-------|------------|---------|-------|-----------------|
| SLAS 2026 | 2026-02-07 | 2026-02-11 | Boston, MA | SLAS is a tradeshow... | /events/slas-2026 | /images/slas.jpg | Tradeshow | United States | February | SLAS, Automation |
| BioPro Summit | 2026-04-07 | 2026-04-07 | Virtual | Join our event... | https://labroots.com/... | /images/biopro.jpg | Virtual Event | Global | April | bioprocessing |

- Columns like `event-type`, `country`, `month` become **filter facets** automatically
- The `search-keywords` column enables text search across hidden terms
- The `link` column supports **both internal and external URLs**

#### Block Configuration (Author Properties)

| Property | Type | Purpose | AEM 6.4 Equivalent |
|----------|------|---------|-------------------|
| Data Source | Text (path) | Path to spreadsheet JSON (e.g., `/events-data`) | N/A (data was in the component itself) |
| Searchable | Boolean toggle | Enable/disable text search bar | Table → Searchable checkbox |
| Enable Pagination | Boolean toggle | Show/hide pagination controls | Table → Enable Pagination checkbox |
| Default per page | Select dropdown | 5 / 10 / 15 / 20 / 25 per page | Table → Default per page dropdown |
| Filterable | Boolean toggle | Enable/disable filter dropdowns | Table → Filterable checkbox |
| Left Navigation for Filters | Boolean toggle | Filters in left sidebar vs top bar | Table → Left Navigation checkbox |
| Filter Columns | Text | Comma-separated column names to use as filters | Table → Filter Groups config |

#### What Author Sees in Universal Editor

```
+-- Data List Block Properties -----+
|                                   |
|  Data Source: /events-data        |
|                                   |
|  [x] Searchable                   |
|  [ ] Enable Pagination            |
|  Default per page: [15 per page]  |
|  [x] Filterable                   |
|  [ ] Left Navigation for Filters  |
|                                   |
|  Filter Columns:                  |
|  event-type, country, month       |
+-----------------------------------+
```

#### How Filters Work (No Separate Configuration Needed)

| Concern | AEM 6.4 | EDS |
|---------|---------|-----|
| Define filter groups | Author creates in Table Filters tab | Column names in spreadsheet |
| Define filter values | Author manually types each value | Auto-derived from row data |
| Map row to filter | Author sets per row (Event Type~Tradeshow) | Author fills column value (Tradeshow) |
| Grayed-out filters | JS checks data attributes | JS checks filtered dataset — same behavior |
| Add new filter value | Must edit Table config AND row | Just add value in spreadsheet row — appears automatically |
| Remove stale values | Must manually delete from config | Disappears when no rows have it |

#### Why Spreadsheet Is Recommended

- **Mirrors AEM experience:** Author fills rows in a sheet — same mental model as adding Row children in AEM
- **Handles external links:** Some events link to external sites (labroots.com, on24.com) — not just internal pages
- **Bulk editing:** Update 50 events in a spreadsheet vs editing 50 child components
- **Single source of truth:** Data, filters, and search all derive from one spreadsheet
- **Reusable pattern:** Same `data-list` block can power event listings, resource libraries, publication catalogs — just point to a different spreadsheet

#### Hybrid Approach (Spreadsheet + Individual Pages)

The spreadsheet drives the listing page. Individual event detail pages can still exist independently. The spreadsheet `link` column simply points to the detail page URL — whether internal (`/events/slas-2026`) or external (`https://labroots.com/...`).

---

## 5. DA vs xWalk Authoring Differences

For this migration (xWalk / Universal Editor), these differences apply:

| Concern | Document Authoring (DA) | xWalk (Universal Editor) |
|---------|------------------------|--------------------------|
| Block variant selection | Free-form text: `Cards (two-up, no-border)` | Structured controls: dropdown + toggles |
| Multiple classes | Comma-separated in parentheses | Single-select `classes` dropdown for layout + separate boolean fields for configurations |
| Boolean configurations | Written as class name: `Cards (no-border)` | Toggle switch in properties panel → renders as `data-no-border="true"` attribute |
| Layout + config combined | `Cards (three-up, no-border)` — one field | Layout dropdown = "Three Up" + No-border toggle = ON — two separate fields |

**CSS targeting difference:**
- DA class-based: `.cards.no-border { border: none; }`
- xWalk data-attribute: `.cards[data-no-border="true"] { border: none; }`

---

## 6. Patterns Quick Reference

| Visual Pattern | EDS Approach | Block Used |
|---------------|-------------|------------|
| Grid of items with image + title + CTA | Cards block | `cards` |
| Grid of items without images | Cards block (no-images auto-detected) | `cards` |
| Image as background, text overlay grid | Cards block (overlay variant) | `cards (overlay)` |
| Side-by-side text + image | Columns block | `columns` |
| 60/40 or 40/60 layouts | Columns block with width variant | `columns (60-40)` |
| Grid where title IS the link | Columns block | `columns` |
| Full-width hero with background image | Hero block | `hero` |
| Promotional strip (heading + CTA + background) | Default content + Section Metadata | No block needed |
| Horizontal line divider between sections | Section boundary | No block needed |
| Hero/cards overlap effect | Section Metadata `overlap-top` | CSS only |
| Horizontal tab navigation | Sub-nav block | `sub-nav` |
| Left sidebar navigation | Sub-nav block (sidebar variant) | `sub-nav (sidebar)` |
| In-page anchor TOC | Table of Contents block | `table-of-contents` |
| Filterable event/resource listing | Data List block + spreadsheet | `data-list` |
| Single prominent item (NOT a card) | Hero, Columns, or Default content | Context-dependent |

---

## 7. Block Count Summary

| Block | Variants | Replaces (AEM 6.4) |
|-------|----------|-------------------|
| **Cards** | default, two-up, three-up, four-up, overlay, list, no-images (auto) | Product tiles, service tiles, feature grids, icon cards |
| **Columns** | default (50-50), 60-40, 40-60, 70-30, 30-70, links | Side-by-side content, text+image panels, link lists |
| **Hero** | default, overlay | Page heading hero, promotional banners |
| **Sub-Nav** | horizontal+manual, horizontal+auto, sidebar+manual, sidebar+auto | Auto Nav, Manual Nav, Nav List, Nav List Manual |
| **Table of Contents** | collapsible, always-visible | Anchor List, Accordion TOC |
| **Data List** | configurable via properties | PragmaDataTable + Row components |
| **Section Metadata** | N/A (configuration block) | Background component, container styles |

**Total: 6 content blocks + Section Metadata** — consolidating 15+ AEM 6.4 components into a streamlined block library.
