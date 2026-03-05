# Thermo Fisher Scientific - Site Discovery Report

**Site:** https://www.thermofisher.com
**Date:** March 2026
**Purpose:** Identify all page templates, layouts, content patterns, and recommended EDS block mappings for AEM 6.4 to AEM Cloud (xWalk + Edge Delivery Services) migration.

---

## Table of Contents

1. [Site Architecture Overview](#site-architecture-overview)
2. [Page Templates](#page-templates)
   - [T1: Homepage](#t1-homepage)
   - [T2: Top-Level Category Landing](#t2-top-level-category-landing)
   - [T3: Sub-Category Product Page](#t3-sub-category-product-page)
   - [T4: Product Detail Page (PDP)](#t4-product-detail-page-pdp)
   - [T5: Services Landing](#t5-services-landing)
   - [T6: Brand Page](#t6-brand-page)
   - [T7: Promotions Page](#t7-promotions-page)
   - [T8: Support Hub](#t8-support-hub)
   - [T9: Events & Webinars](#t9-events--webinars)
   - [T10: Blog Post (WordPress)](#t10-blog-post-wordpress)
   - [T11: Brand Hub](#t11-brand-hub)
   - [T12: Division/Department Hub](#t12-divisiondepartment-hub)
   - [T13: Resource Hub Card Grid](#t13-resource-hub-card-grid)
   - [T14: Content Hub with Sub-Navigation](#t14-content-hub-with-sub-navigation)
   - [T15: Product Directory / Site Map](#t15-product-directory--site-map)
   - [T16: Product Showcase](#t16-product-showcase)
   - [T17: Campaign / Storytelling Page](#t17-campaign--storytelling-page)
3. [Common Elements Across All Templates](#common-elements-across-all-templates)
4. [Estimated Block Inventory](#estimated-block-inventory)
5. [Key Migration Considerations](#key-migration-considerations)
6. [Recommended Migration Priority](#recommended-migration-priority)
7. [URL Analysis Status](#url-analysis-status)

---

## Site Architecture Overview

The Thermo Fisher web presence operates across **multiple domains**:

| Domain | Purpose | Platform |
|--------|---------|----------|
| `www.thermofisher.com` | Main e-commerce & scientific products | AEM 6.4 |
| `corporate.thermofisher.com` | Corporate communications (About Us, CSR, Investors, News) | AEM (separate instance) |
| `www.thermofisher.com/blog/` | Blog / Accelerating Science | WordPress |
| `jobs.thermofisher.com` | Careers | Separate platform |
| `ir.thermofisher.com` | Investor Relations | Separate platform |
| `newsroom.thermofisher.com` | Press / News | Separate platform |

**Key finding:** The migration scope should focus on `www.thermofisher.com` main site. Blog, Corporate, Careers, IR, and Newsroom are separate platforms with separate migration decisions.

### URL Structure Pattern

```
https://www.thermofisher.com/{country}/{language}/home/{section}/{category}/{sub-category}.html
```

Example: `https://www.thermofisher.com/us/en/home/life-science/antibodies/primary-antibodies.html`

- Multi-locale support (us/en, ca/en, in/en, etc.)
- Deep content hierarchy (up to 6+ levels)
- `.html` extension on all pages

---

## Page Templates

---

### T1: Homepage

**Description:** The main landing page for thermofisher.com. Features a hero carousel, featured content sections, promotions, and new product highlights. Full-width layout with multiple content zones.

**Screenshot:** `discovery/homepage-full.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home.html | US English homepage |
| 2 | https://www.thermofisher.com/in/en/home.html | India English homepage (same template, localized content) |
| 3 | https://www.thermofisher.com/ca/en/home.html | Canada English homepage |

#### Layout Structure

```
+-------------------------------------------------------+
| [Promo Banner - dismissible]                           |
+-------------------------------------------------------+
| [Header: Logo | Search | Utility Nav | Cart]           |
+-------------------------------------------------------+
| [Mega Navigation]                                      |
+-------------------------------------------------------+
| [Hero Carousel - full width, rotating slides + CTA]    |
+-------------------------------------------------------+
| [Featured Education - H2 + card grid]                  |
+-------------------------------------------------------+
| [Accelerating Science Blog - H2 + blog card grid]      |
+-------------------------------------------------------+
| [Online Offers Carousel - H2 + product carousel]       |
+-------------------------------------------------------+
| [Promotions - H2 + 4-col promo cards grid]             |
+-------------------------------------------------------+
| [New Products - H2 + 4-col product cards grid]         |
+-------------------------------------------------------+
| [Footer - 5-column links + legal]                      |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Promo Banner | Dismissible text + link at top | `promo-banner` (custom) |
| Hero Carousel | Full-width rotating images + heading + CTA | `carousel` (hero variant) |
| Featured Education | H2 heading + 4-col icon/image cards | `cards` (icon variant) |
| Blog Posts | H2 heading + 3-col image + title + excerpt cards | `cards` (blog variant) |
| Online Offers | H2 heading + horizontal scrolling product cards | `carousel` (product variant) |
| Promotions | H2 heading + 4-col promo cards with badges | `cards` (promo variant) |
| New Products | H2 heading + 4-col product cards | `cards` (product variant) |

---

### T2: Top-Level Category Landing

**Description:** Category landing pages that serve as entry points into major product areas. Feature a hero banner, sub-category navigation cards, feature highlights, application areas, and educational content. Full-width layout without sidebar.

**Screenshot:** `discovery/antibodies-category.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/life-science/antibodies.html | Antibodies category |
| 2 | https://www.thermofisher.com/us/en/home/industrial/chromatography.html | Chromatography category |
| 3 | https://www.thermofisher.com/us/en/home/life-science/cell-culture.html | Cell Culture category |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Life Sciences > Antibodies]       |
+-------------------------------------------------------+
| [Hero Banner - full width, image + heading + CTA]      |
+-------------------------------------------------------+
| [Sub-Category Cards - 4-col icon/image + title grid]   |
+-------------------------------------------------------+
| [Why Choose Section - 3-col feature cards]             |
+-------------------------------------------------------+
| [Applications - image + bullet link list]              |
+-------------------------------------------------------+
| [Research Areas - 3-col link grid]                     |
+-------------------------------------------------------+
| [Related Categories - image + links]                   |
+-------------------------------------------------------+
| [Educational Content - 3-col content cards]            |
+-------------------------------------------------------+
| [Related Pages - link list]                            |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Hero Banner | Full-width image background + H1 + description + CTA button | `hero` |
| Sub-Category Cards | 4-col grid: image + title (clickable) | `cards` (icon variant) |
| Feature Cards | 3-col: icon + H3 + description | `cards` (feature variant) |
| Application Areas | Image left + bulleted link list right | `columns` (image-links variant) |
| Research Link Grid | 3-col grid of text links | `cards` (link-card variant) |
| Related Categories | Image + title + link list | `columns` or `cards` |
| Educational Cards | 3-col: image + title + description + CTA | `cards` (education variant) |

---

### T3: Sub-Category Product Page

**Description:** Deep product category pages that include a left sidebar tree navigation alongside detailed product content. Uses a two-column layout (sidebar + main content area). Contains product spotlights, validation information, and resource links.

**Screenshot:** (sub-category page, similar structure to Primary Antibodies)

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/life-science/antibodies/primary-antibodies.html | Primary Antibodies sub-category |
| 2 | https://www.thermofisher.com/us/en/home/life-science/cell-culture/mammalian-cell-culture.html | Mammalian Cell Culture sub-category |
| 3 | https://www.thermofisher.com/us/en/home/industrial/spectroscopy-elemental-isotope-analysis/molecular-spectroscopy.html | Molecular Spectroscopy sub-category |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Life Sciences > Antibodies > ...] |
+-------------------------------------------------------+
| +---------------+-----------------------------------+  |
| | [Left Sidebar]| [H1 Title]                        |  |
| | - Parent link | [Intro paragraph]                 |  |
| | - Current     | [Sub-Category Icon Cards - 4-col] |  |
| |   - Child 1   | [Validation Comparison - 2-col]   |  |
| |   - Child 2   | [Spotlight Callout]               |  |
| |   - Child 3   | [Portfolio Highlights - 2-col]    |  |
| | - Sibling 1   | [Complementary Products - 4-col]  |  |
| | - Sibling 2   | [Resource Links - 3-col lists]    |  |
| +---------------+-----------------------------------+  |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Left Sidebar Nav | Expandable tree navigation with parent/child/sibling links | `sidebar-nav` (custom, tree variant) |
| Intro Text | H1 + descriptive paragraph(s) | Default content |
| Sub-Category Icons | 4-col: icon/image + title (clickable) | `cards` (icon variant) |
| Comparison Panels | 2-col side-by-side feature comparison (Standard vs Advanced) | `columns` (comparison variant) |
| Spotlight Callout | Highlighted text box with statistic or emphasis | `callout` (custom) or `columns` (highlight variant) |
| Portfolio Highlights | 2-col: product image + H3 + description | `cards` (product-highlight variant) |
| Complementary Products | 4-col: image + H4 + description | `cards` (product variant) |
| Resource Links | 3-col lists of categorized links (Learning, Selection, Analysis) | `columns` (links variant) |

---

### T4: Product Detail Page (PDP)

**Description:** The most complex template. Displays individual product information including images, pricing, SKU selection, and tabbed technical content. Requires real-time API integration for pricing and inventory data.

**Screenshot:** `discovery/product-detail.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/order/catalog/product/11668019 | Lipofectamine 2000 (catalog number URL format) |
| 2 | https://www.thermofisher.com/order/catalog/product/A1001 | Another product (catalog format) |
| 3 | https://www.thermofisher.com/antibody/product/CD4-Antibody-Monoclonal-RPA-T4/11-0049-42 | Antibody product (antibody URL format) |

> **Note:** PDP URLs follow two patterns:
> - `/order/catalog/product/{catalogNumber}` - General products
> - `/antibody/product/{name}/{catalogNumber}` - Antibody products
> These differ from the standard `/us/en/home/...` URL pattern.

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs]                                          |
+-------------------------------------------------------+
| +---------------------+-----------------------------+  |
| | [Product Image(s)]  | [Brand Label]               |  |
| |                     | [Product Title - H1]        |  |
| |                     | [Product Description]       |  |
| |                     | [Promotion Banner]          |  |
| |                     | [Price + Qty + Add to Cart] |  |
| +---------------------+-----------------------------+  |
+-------------------------------------------------------+
| [SKU / Variant Selection Table]                        |
+-------------------------------------------------------+
| [AI Search Card - "Ask a question"]                    |
+-------------------------------------------------------+
| [Buy Together - Cross-sell Recommendations]            |
+-------------------------------------------------------+
| [Tabbed Content]                                       |
|   Tab 1: Product Overview                              |
|   Tab 2: FAQ                                           |
|   Tab 3: Specifications                                |
|   Tab 4: Documents                                     |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Product Image Gallery | Zoomable product image(s) + thumbnails | `product-image` (custom) |
| Product Info | Brand label + H1 + description + promo | `product-detail` (custom) |
| Pricing & Cart | Price, quantity selector, add-to-cart button | Part of `product-detail` (custom, API-driven) |
| SKU Table | Table with variant options (size, quantity, catalog#) | `table` (product-variants variant) or custom |
| AI Search | Conversational search card | `ai-search` (custom) |
| Cross-sell | "Buy Together" horizontal product cards | `cards` (cross-sell variant) |
| Tabbed Content | Multiple content tabs (Overview, FAQ, Specs, Docs) | `tabs` (custom) |

> **Migration Complexity: HIGH**
> - Pricing/inventory: Requires **Edge Worker** (for SEO) or client-side API call
> - SKU selection: Interactive JavaScript behavior
> - Add-to-cart: eCommerce integration
> - Cross-sell: Recommendation engine (Adobe Target or custom API)
> - Tabs: Custom block with JS for tab switching

---

### T5: Services Landing

**Description:** Text-heavy content pages that list available services with descriptions and links. Uses a simple full-width layout with a collapsible table of contents and 2-column service card grid.

**Screenshot:** `discovery/services-page.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/products-and-services/services.html | Main services hub |
| 2 | https://www.thermofisher.com/us/en/home/products-and-services/services/custom-services.html | Custom services sub-page |
| 3 | https://www.thermofisher.com/us/en/home/products-and-services/services/training-services.html | Training services sub-page |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [H1 "Services" - blue background banner]               |
+-------------------------------------------------------+
| [Table of Contents - collapsible accordion]            |
+-------------------------------------------------------+
| [Intro Paragraph - descriptive text]                   |
+-------------------------------------------------------+
| [HR divider]                                           |
+-------------------------------------------------------+
| [Service Cards - 2-col grid]                           |
|   +------------------------+------------------------+  |
|   | H2 Title              | H2 Title               |  |
|   | Description paragraph | Description paragraph  |  |
|   | "Learn more" link     | "Learn more" link      |  |
|   +------------------------+------------------------+  |
|   (repeats for all services ~12 items)                 |
+-------------------------------------------------------+
| [Tagline - "The world leader in serving science"]      |
+-------------------------------------------------------+
| [Bottom Promo Banners - 2-col image + text cards]      |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Page Title Banner | H1 with colored background | Default content with section metadata (background) |
| Table of Contents | Collapsible/accordion link list | `table-of-contents` (custom) or default content with anchor links |
| Intro Text | Paragraph(s) of descriptive text | Default content |
| Service Cards | 2-col grid: H2 + paragraph + CTA link (repeating) | `cards` (service variant) or `columns` |
| Tagline | Emphasized single-line text | Default content with section metadata |
| Bottom Promos | 2-col: image + text + CTA button | `columns` (promo variant) |

---

### T6: Brand Page

**Description:** Dedicated brand landing pages showcasing a specific Thermo Fisher brand (Thermo Scientific, Invitrogen, etc.). Uses a unique layout with a left sidebar for product/technique links and main content area with categorized product listings and resources.

**Screenshot:** `discovery/brand-page.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/brands/thermo-scientific.html | Thermo Scientific brand |
| 2 | https://www.thermofisher.com/us/en/home/brands/applied-biosystems.html | Applied Biosystems brand |
| 3 | https://www.thermofisher.com/us/en/home/brands/invitrogen.html | Invitrogen brand |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Brands > Thermo Scientific]       |
+-------------------------------------------------------+
| [H1 Brand Name]                                        |
+-------------------------------------------------------+
| +------------------+--------------------------------+  |
| | [Left Sidebar]   | [Brand Logo Image]             |  |
| | Popular Products | [Anchor Nav: Life Sci |         |  |
| |  - Product 1     |   Clinical | Applied | Rsrc]   |  |
| |  - Product 2     | [H2 + Description Paragraph]   |  |
| |  - ...           | [HR]                            |  |
| | Key Applications | [Life Sciences - H2]            |  |
| |  - App 1         |   2-col product link lists      |  |
| |  - App 2         | [Popular product lines text]    |  |
| |  - ...           | [HR]                            |  |
| | Resources        | [Clinical - H2]                 |  |
| |  - Resource 1    |   2-col product link lists      |  |
| |  - Resource 2    | [HR]                            |  |
| |  - ...           | [Applied Science - H2]          |  |
| |                  |   2-col product link lists      |  |
| |                  | [Resources - H2]                |  |
| |                  |   3-col icon + title + desc     |  |
| |                  |   Link lists + Learning centers |  |
| +------------------+--------------------------------+  |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Brand Hero | Brand logo image + anchor tab navigation | `hero` (brand variant) |
| Left Sidebar | Categorized link lists (Products, Applications, Resources) | `sidebar-nav` (custom, links variant) |
| Anchor Navigation | Horizontal tabs linking to page sections | Section metadata with anchor IDs |
| Category Link Lists | 2-col link lists grouped by category | `columns` (links variant) |
| Popular Lines Text | Emphasized paragraph listing brand product lines | Default content (bold/italic) |
| Resource Cards | 3-col: thumbnail image + H3 title + description | `cards` (resource variant) |
| Learning Center Links | Categorized link list | Default content or `columns` (links variant) |

---

### T7: Promotions Page

**Description:** A filterable grid of promotional offers. Features filter tabs at the top and a responsive grid of promotion cards with badges, product images, and CTAs.

**Screenshot:** `discovery/promotions-page.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/products-and-services/promotions.html | Main promotions page |
| 2 | https://www.thermofisher.com/us/en/home/products-and-services/new-products.html | New products page (similar layout) |
| 3 | https://www.thermofisher.com/us/en/home/clinical/clinical-genomics/precision-medicine-pharmacogenomics/precision-medicine-offers.html | Category-specific promotions |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Promotions]                       |
+-------------------------------------------------------+
| [H1 "Promotions"]                                      |
+-------------------------------------------------------+
| [H3 "Featured promotions from our best marketplaces"]  |
+-------------------------------------------------------+
| [Filter Tabs: All | % Off | Funding | Instruments |    |
|  Special Deal | Gift | Aspire]                         |
+-------------------------------------------------------+
| [Promo Cards Grid - 4 columns]                         |
|   +--------+--------+--------+--------+               |
|   | Badge  | Badge  | Badge  | Badge  |               |
|   | Image  | Image  | Image  | Image  |               |
|   | Title  | Title  | Title  | Title  |               |
|   | Desc   | Desc   | Desc   | Desc   |               |
|   | CTA    | CTA    | CTA    | CTA    |               |
|   +--------+--------+--------+--------+               |
|   (multiple rows)                                      |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Page Title | H1 + H3 subtitle | Default content |
| Filter Tabs | Horizontal tab buttons for filtering | `filter-bar` (custom, JS-driven) |
| Promo Cards Grid | 4-col: badge overlay + image + title + description + CTA link | `cards` (promo variant with badge) |

> **Note:** Filter functionality requires custom JavaScript. Cards likely loaded from a data source/API. Consider client-side filtering with query index or Edge Worker for initial data fetch.

---

### T8: Support Hub

**Description:** A directory-style page organized into multiple support categories with link lists. Uses a multi-column layout for quick access to various support resources. Includes a collapsible table of contents and sidebar quick links.

**Screenshot:** `discovery/support-page.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/support.html | Main support hub |
| 2 | https://www.thermofisher.com/us/en/home/technical-resources/contact-us.html | Contact us page (similar structure) |
| 3 | https://www.thermofisher.com/us/en/home/support/how-to-place-orders.html | How to order (support sub-page) |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [H1 "Help and Support" - blue background banner]       |
+-------------------------------------------------------+
| [Table of Contents - collapsible]                      |
+-------------------------------------------------------+
| +------------------------------------+-----------+     |
| | [Multi-Column Link Directory]      | [Sidebar] |     |
| |                                    |           |     |
| | Order Help:                        | Order     |     |
| |   - FAQs                          | Status    |     |
| |   - Account                       | tracking  |     |
| |   - Orders                        |           |     |
| |                                    | Contact   |     |
| | Contact Us:                        | Us        |     |
| |   - Email & phone                 |           |     |
| |   - Change location               | Documents |     |
| |                                    | & Certs   |     |
| | eSolutions | Product Support       |           |     |
| | Technical Info | Training          |           |     |
| +------------------------------------+-----------+     |
+-------------------------------------------------------+
| [Bottom Promo Cards - 2-col image + text + CTA]        |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Page Title Banner | H1 with blue background | Default content + section metadata |
| Table of Contents | Collapsible section list | `table-of-contents` (custom) |
| Link Directory | Multi-column categorized link lists (H2/H3 headers + links) | `columns` (support variant) or default content |
| Sidebar Quick Links | Highlighted action links with separators | `sidebar-links` (custom) |
| Bottom Promos | 2-col: image + text + CTA button | `columns` (promo variant) |

---

### T9: Events & Webinars

**Description:** A searchable listing of upcoming events and webinars. Displayed in a table/list format with search filter input. Each event entry includes an image, title, date/location, description, and link.

**Screenshot:** `discovery/events-page.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/events.html | Main events hub |
| 2 | https://www.thermofisher.com/us/en/home/events/slas-2026.html | Individual event detail page |
| 3 | https://www.thermofisher.com/us/en/home/events/integrated-cryoem-data-management-with-cryoflow2.html | Webinar detail page |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [H1 "Events and Webinars" - image background banner]   |
+-------------------------------------------------------+
| [H4 Welcome description text]                          |
+-------------------------------------------------------+
| [Search/Filter Input - "Filters" + text box]           |
+-------------------------------------------------------+
| [Event Listing - table/list format]                    |
|   +------------------------------------------------+   |
|   | [H2 Event Title (linked)]                      |   |
|   | [H4 Date Range | Location]                     |   |
|   | [Description paragraph]                        |   |
|   | ["Learn more" link]              [Event Image] |   |
|   +------------------------------------------------+   |
|   (repeats for each event, ~12+ events visible)        |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Page Banner | H1 with background image | `hero` (events variant) |
| Description | H4 welcome text | Default content |
| Search/Filter | Text input for filtering events | `search-filter` (custom, JS-driven) |
| Event List | Repeating rows: title + date + description + image + CTA | `cards` (event variant) or custom `event-list` block |

> **Note:** Events are likely populated from an API or CMS feed. The search/filter is client-side JavaScript. Consider query index for event data or Edge Worker if events come from an external system.

---

### T10: Blog Post (WordPress)

**Description:** Blog articles live on a **separate WordPress instance** at `/blog/`. These are NOT part of the AEM site and follow WordPress templates. Migration of blog content is a **separate scope decision**.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/blog/ask-a-scientist/top-5-protein-quantitation-assays/ | Blog article |
| 2 | https://www.thermofisher.com/blog/behindthebench/ | Blog category |
| 3 | https://www.thermofisher.com/blog/ask-a-scientist/ | Blog category |

#### Layout Structure

```
+-------------------------------------------------------+
| [WordPress Header - different from main site]          |
+-------------------------------------------------------+
| [Blog Post Title - H1]                                 |
+-------------------------------------------------------+
| [Author + Date + Category]                             |
+-------------------------------------------------------+
| [Article Body - rich text, images, embedded media]     |
+-------------------------------------------------------+
| [Author Bio]                                           |
+-------------------------------------------------------+
| [Related Posts]                                        |
+-------------------------------------------------------+
| [WordPress Footer]                                     |
+-------------------------------------------------------+
```

> **Migration Decision:** Blog runs on WordPress, not AEM. Options:
> 1. Keep blog on WordPress (no migration needed)
> 2. Migrate blog content to EDS as authored pages
> 3. Keep WordPress but integrate header/footer with EDS

---

### T11: Brand Hub

**Description:** The top-level brands index page that showcases all Thermo Fisher brands. Features a Brightcove video player at the top, followed by an introductory section, and a grid of brand cards, each with the brand logo, name, description, and CTA link.

**Screenshot:** `discovery/brands-hub.png`

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/brands.html | Main brands hub page |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Brands]                           |
+-------------------------------------------------------+
| [H1 "Our Brands"]                                      |
+-------------------------------------------------------+
| [Video Player - Brightcove embed]                      |
+-------------------------------------------------------+
| [H2 Intro heading + description paragraphs]            |
+-------------------------------------------------------+
| [Brand Cards Grid - 2-3 columns]                       |
|   +----------------------------+                       |
|   | Brand Logo (image)         |                       |
|   | H3 Brand Name              |                       |
|   | Description paragraph      |                       |
|   | CTA link ›                 |                       |
|   +----------------------------+                       |
|   (repeats for ~10 brands)                             |
+-------------------------------------------------------+
| [Bottom Promo Cards - 2-col image cards]               |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Video Player | Brightcove video embed | `video` (custom, Brightcove variant) |
| Intro Section | H2 + descriptive paragraphs | Default content |
| Brand Cards | Grid: logo image + H3 + description + CTA | `cards` (brand variant) |
| Bottom Promos | 2-col image promo cards | `columns` (promo variant) |

---

### T12: Division/Department Hub

**Description:** Division-level landing pages (e.g., Life Science) that serve as flat category directories. Simple layout with H1, a row of popular quick links, and a 4-column grid of category cards. No sidebar, no hero image.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/life-science.html | Life Science division hub |
| 2 | https://www.thermofisher.com/us/en/home/industrial.html | Industrial & Applied Sciences hub |
| 3 | https://www.thermofisher.com/us/en/home/clinical.html | Clinical & Diagnostics hub |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Life Science]                     |
+-------------------------------------------------------+
| [H1 "Life Science"]                                    |
+-------------------------------------------------------+
| [Popular Quick Links Row - inline text links]          |
+-------------------------------------------------------+
| [Category Cards Grid - 4 columns]                      |
|   +----------+----------+----------+----------+        |
|   | H3 Title | H3 Title | H3 Title | H3 Title |       |
|   | Desc     | Desc     | Desc     | Desc     |       |
|   +----------+----------+----------+----------+        |
|   (repeats for ~16-20 categories)                      |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Popular Links | Inline row of text links | Default content (styled paragraph with links) |
| Category Cards | 4-col: H3 title + description (clickable) | `cards` (category variant) |

---

### T13: Resource Hub Card Grid

**Description:** Resource listing pages displaying a grid of resource/learning center cards. Features H1, introductory paragraphs, and a 3-column card grid with images, titles, and descriptions. No sidebar, no hero.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/technical-resources/learning-centers.html | Learning Centers hub |
| 2 | https://www.thermofisher.com/us/en/home/technical-resources/technical-reference-library.html | Technical Reference Library |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [H1 "Learning Centers"]                                |
+-------------------------------------------------------+
| [Intro paragraphs - descriptive text]                  |
+-------------------------------------------------------+
| [Resource Cards Grid - 3 columns]                      |
|   +---------------+---------------+---------------+    |
|   | Image         | Image         | Image         |   |
|   | H3 Title      | H3 Title      | H3 Title      |   |
|   | Description   | Description   | Description   |   |
|   +---------------+---------------+---------------+    |
|   (repeats for ~20+ resources)                         |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Intro Text | H1 + descriptive paragraphs | Default content |
| Resource Cards | 3-col: image + H3 + description (linked) | `cards` (resource variant) |

---

### T14: Content Hub with Sub-Navigation

**Description:** Rich content pages with a left sub-navigation sidebar and multi-section content area. Used for corporate content like sustainability programs and partnering pages. Content includes feature cards (text + image), criteria grids, video embeds, and info stats panels.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/about-us/product-stewardship/sustainable-product-design.html | Greener by Design (sustainable design) |
| 2 | https://www.thermofisher.com/us/en/home/about-us/partnering-licensing.html | Partnering & Licensing |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs]                                          |
+-------------------------------------------------------+
| [H1 Page Title]                                        |
+-------------------------------------------------------+
| +------------------+--------------------------------+  |
| | [Left Sub-Nav]   | [Info Stats Panel]             |  |
| | - Overview       |   "Global reach" "Innovation"  |  |
| | - Sub-page 1     |   "Mission" + descriptions     |  |
| | - Sub-page 2     | [HR]                           |  |
| | - Sub-page 3     | [Intro paragraphs]             |  |
| | - Sub-page 4     | [Feature Cards - 2 col]        |  |
| |                  |   Text+CTA | Image              |  |
| |                  | [Criteria Cards Grid]           |  |
| |                  |   Icon + H2 + desc + link       |  |
| |                  | [Video Player]                  |  |
| |                  | [Info Cards - 3 col]            |  |
| |                  |   H3 + image + desc + CTA       |  |
| +------------------+--------------------------------+  |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Sub-Navigation | Vertical list of sub-page links | `sub-navigation` (custom) or `sidebar-nav` (links variant) |
| Info Stats Panel | Animated stats with labels + descriptions | `columns` (stats variant) |
| Feature Cards | 2-col: text + CTA left, image right | `columns` (feature variant) |
| Criteria Cards | Grid: icon + H2 + description + link | `cards` (criteria variant) |
| Video Player | Brightcove embed | `video` (custom) |
| Info Cards | 3-col: H3 + image + description + CTA | `cards` (info variant) |

---

### T15: Product Directory / Site Map

**Description:** Large directory pages organized by categories with multi-column link grids. Features an optional collapsible Table of Contents, H2 category headings, and grouped link lists separated by HR dividers. Used for "Shop All Products", "Applications & Techniques", and "Protocols" pages.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/order.html | Shop All Products |
| 2 | https://www.thermofisher.com/us/en/home/applications-techniques.html | Applications & Techniques |
| 3 | https://www.thermofisher.com/us/en/home/references/protocols.html | Protocols |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs]                                          |
+-------------------------------------------------------+
| [H1 "Shop All Products" / "Applications & Techniques"] |
+-------------------------------------------------------+
| [Table of Contents - collapsible (optional)]           |
+-------------------------------------------------------+
| [Quick Links Row - Promotions, New Products, etc.]     |
+-------------------------------------------------------+
| [HR]                                                   |
+-------------------------------------------------------+
| [H2 Category 1]                                        |
|   2-col link grid (product/topic links)                |
| [HR]                                                   |
| [H2 Category 2]                                        |
|   2-col link grid                                      |
| [HR]                                                   |
| ... (repeats for ~6-10 categories)                     |
+-------------------------------------------------------+
| [Bottom Promo Banners - 2-col: image + text + CTA]     |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Table of Contents | Collapsible accordion with anchor links | `table-of-contents` (custom) |
| Quick Links | Inline row of featured links | Default content (styled links) |
| Category Link Grids | H2 + 2-col link grids, HR separated | Default content (with section metadata for columns) |
| Bottom Promos | 2-col: image + text + CTA | `columns` (promo variant) |

---

### T16: Product Showcase

**Description:** Simple product highlight page featuring a few large product cards with images, titles, and CTAs. Used for "New Products" landing. Clean layout with minimal content.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/order/new-products.html | New Products (redirected from products-and-services/new-products.html) |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Breadcrumbs: Home > Products > New Products]          |
+-------------------------------------------------------+
| [H1 "New Products"]                                    |
+-------------------------------------------------------+
| [Product Showcase Cards - large format]                |
|   +------------------------------------------------+   |
|   | Large Product Image                            |   |
|   | Product Title                                  |   |
|   | "Learn more" CTA link                          |   |
|   +------------------------------------------------+   |
|   (2-4 featured products)                              |
+-------------------------------------------------------+
| [Sticky Bottom Promo Banner]                           |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Product Cards | Large: image + title + CTA link | `cards` (showcase variant) |
| Sticky Banner | Bottom-fixed promotional banner with image + text + CTA | `sticky-banner` (custom) |

---

### T17: Campaign / Storytelling Page

**Description:** Editorial/marketing campaign pages combining video hero, story cards featuring scientists, and feature sections. Used for brand storytelling and inspiration content. Rich visual layout with video integration.

#### Sample URLs

| # | URL | Notes |
|---|-----|-------|
| 1 | https://www.thermofisher.com/us/en/home/brands/inspire/keep-seeking.html | Keep Seeking campaign |
| 2 | https://www.thermofisher.com/us/en/home/brands/inspire/keep-seeking/meet-innovators.html | Meet the Innovators sub-page |

#### Layout Structure

```
+-------------------------------------------------------+
| [Header + Mega Nav]                                    |
+-------------------------------------------------------+
| [Hero Section]                                         |
|   H1 "Keep Seeking"                                    |
|   H3 Subtitle                                          |
|   "Watch the video" CTA (Brightcove)                   |
+-------------------------------------------------------+
| [Intro Section - descriptive paragraph]                |
+-------------------------------------------------------+
| [Story Cards Grid - 3 columns]                         |
|   +---------------+---------------+---------------+    |
|   | Scientist Img | Scientist Img | Scientist Img |    |
|   | H3 Quote      | H3 Quote      | H3 Quote      |   |
|   | Person Name   | Person Name   | Person Name   |   |
|   +---------------+---------------+---------------+    |
|   (5+ story cards across multiple rows)                |
+-------------------------------------------------------+
| [Feature Section - alternating layout]                 |
|   Image + "Meet The Innovators" title + desc + CTA     |
+-------------------------------------------------------+
| [Feature Section - alternating layout]                 |
|   "5 Steps to Science-life Balance" + desc + CTA       |
+-------------------------------------------------------+
| [Footer]                                               |
+-------------------------------------------------------+
```

#### Content Sections & EDS Block Mapping

| Section | Content Pattern | Recommended EDS Block |
|---------|----------------|----------------------|
| Video Hero | H1 + H3 subtitle + video play CTA | `hero` (video variant) + `video` |
| Story Cards | 3-col: scientist image + H3 quote + name | `cards` (story variant) |
| Feature Sections | Alternating: image + title + description + CTA | `columns` (feature variant) |

---

## Common Elements Across All Templates

These elements appear on most or all pages and will need dedicated EDS blocks or integrations:

| Element | Present On | Current Behavior | EDS Approach |
|---------|-----------|-----------------|--------------|
| **Top Promo Banner** | All pages | Dismissible colored banner with text + CTA | `promo-banner` block (custom) |
| **Global Header** | All pages | Logo, search bar, utility nav (Order Status, Quick Order, Sign In, Cart) | `header` block |
| **Mega Navigation** | All pages | Multi-level dropdown with L1 (main categories) + L2/L3 (sub-categories) + featured links | `nav.md` + custom header JS for L2/L3 auto-generation |
| **Search Bar** | All pages | Autocomplete search with category selector | Custom search integration |
| **Breadcrumbs** | Category, Sub-Cat, PDP, Brand, Support | Hierarchical path links | Default content or `breadcrumb` block |
| **Cookie Consent** | All pages | OneTrust-style consent banner (Accept All / Reject All / Manage Settings) | 3rd party integration via `delayed.js` |
| **Footer** | All pages | 5-column link layout (Ordering, Support, Resources, About, Portfolio) + legal links | `footer` block |
| **Feedback Button** | Most pages | Floating "Give Feedback" button (right edge) | 3rd party widget via `delayed.js` |
| **Chat Widget** | Support + Product pages | Customer support chat (appears after eligibility check) | 3rd party integration via `delayed.js` |
| **Bottom Sticky Banner** | Some pages | Promotional banner fixed to bottom of viewport | `sticky-banner` (custom) or `promo-banner` variant |

---

## Estimated Block Inventory

### Standard Blocks (from EDS library)

| Block | Variants Needed | Complexity | Used In Templates |
|-------|----------------|------------|-------------------|
| `hero` | default, brand, category, events, video | Low | T1, T2, T6, T9, T11, T17 |
| `cards` | icon, feature, promo, blog, product, event, cross-sell, resource, education, link-card, service, brand, category, criteria, story, showcase, info | Medium (many variants) | T1-T9, T11-T14, T16, T17 |
| `columns` | default, image-links, comparison, promo, links, support, feature, stats | Low | T2, T3, T5, T6, T8, T14, T15, T17 |
| `carousel` | hero, product | Medium | T1 |
| `table` | default, product-variants | Low | T4 |

### Custom Blocks (need to be built)

| Block | Complexity | Used In Templates | Notes |
|-------|------------|-------------------|-------|
| `tabs` | Medium | T4, T6 | Content tab switching (JS) |
| `table-of-contents` | Low | T5, T8, T15 | Collapsible anchor link list |
| `sidebar-nav` | High | T3, T6 | Tree nav with expand/collapse |
| `sub-navigation` | Medium | T14 | Vertical sub-page nav links (simpler than sidebar-nav tree) |
| `product-detail` | High | T4 | API integration for pricing/inventory |
| `product-image` | Medium | T4 | Zoomable image gallery |
| `promo-banner` | Low | T1-T9 (all) | Dismissible top/inline banners |
| `sticky-banner` | Low | T3, T16 | Bottom-fixed promotional banner |
| `video` | Medium | T11, T14, T17 | Brightcove video player embed |
| `filter-bar` | High | T7 | Client-side category filtering (JS) |
| `event-list` | Medium | T9 | Event cards with date/location |
| `search-filter` | High | T7, T9 | Search input with live filtering |
| `ai-search` | Medium | T4 | Conversational product search |
| `callout` | Low | T3 | Highlighted text/stat callout |
| `sidebar-links` | Low | T8 | Quick action links |

### Summary

| Category | Count |
|----------|-------|
| Standard blocks (from library) | 5 |
| Custom blocks (to build) | 15 |
| Total block variants | ~45+ |
| **Total blocks** | **~20** |

---

## Key Migration Considerations

### 1. Product Detail Pages (PDP) - Highest Complexity
- Pricing, inventory, and SKU data require **real-time API integration**
- Recommendation: **Edge Worker** for pricing (SEO-critical) + client-side for cart/interactive features
- Add-to-cart functionality needs eCommerce integration
- Cross-sell recommendations may come from Adobe Target or custom API
- Estimated volume: **50,000+ pages**

### 2. Left Sidebar Navigation
- Appears on Sub-Category (T3) and Brand (T6) pages
- Requires custom JS block with tree expand/collapse behavior
- Content hierarchy is deep (6+ levels)
- Consider auto-generating from query index vs. authored content

### 3. Mega Navigation
- Deep L2/L3 levels with hundreds of category links
- Too many links to author manually in `nav.md`
- **Recommendation:** Hybrid approach - L1 authored, L2/L3 auto-generated from query index
- Needs lazy-loading for 88K+ pages

### 4. Search Integration
- Global search with autocomplete appears on every page
- Category-scoped search selector
- Requires custom integration (likely Coveo, Algolia, or custom search service)

### 5. eCommerce Integration
- Cart, Quick Order, Order Status, Supply Center
- Pricing API calls
- User authentication (Sign In)
- These are deep integrations beyond content migration

### 6. Third-Party Integrations (via delayed.js)
- Adobe Launch (already identified)
- Adobe Analytics
- Adobe Target (A/B testing, recommendations)
- OneTrust (cookie consent)
- Kampyle (feedback widget)
- Chat service (customer support)

### 7. Blog on WordPress
- Separate platform, separate migration decision
- If migrating: simple content pages, standard blog template
- If keeping: need header/footer visual consistency with EDS

### 8. Corporate Site (Separate Domain)
- `corporate.thermofisher.com` is a different site
- About Us, CSR, Investor Relations content redirects there
- Separate migration scope

### 9. Multi-Locale Support
- Site serves multiple countries/languages (us/en, ca/en, in/en, etc.)
- URL structure: `/{country}/{language}/home/...`
- Need locale-aware content strategy in EDS

### 10. URL Pattern Differences
- Most content pages: `/us/en/home/{path}.html`
- Product catalog: `/order/catalog/product/{catalogNumber}`
- Antibody products: `/antibody/product/{name}/{id}`
- Search results: `/search/results?query=...`
- Blog: `/blog/{category}/{slug}/`
- URL redirects and rewrite rules will be critical

---

## Recommended Migration Priority

| Priority | Template | Est. Page Count | Rationale |
|----------|----------|----------------|-----------|
| **P1** | T1: Homepage | 1 per locale | Flagship page, establishes design system and global components |
| **P1** | T2: Top-Level Category | ~20-30 | High traffic entry points, defines category UX |
| **P1** | T3: Sub-Category Product | ~500-1,000 | Bulk of navigational content, reusable template |
| **P2** | T4: Product Detail (PDP) | ~50,000+ | Highest volume, most complex (API dependencies) |
| **P2** | T6: Brand Pages | ~10 | High brand visibility, limited count |
| **P2** | T11: Brand Hub | 1 | Brands landing page |
| **P2** | T12: Division Hub | ~5-10 | Life Science, Industrial, Clinical division hubs |
| **P3** | T5: Services Landing | ~20 | Text-heavy, simpler layout |
| **P3** | T8: Support Hub | ~50 | Link directories, relatively straightforward |
| **P3** | T7: Promotions | ~1 + dynamic | Filter-driven, needs JS infrastructure |
| **P3** | T9: Events | ~1 + dynamic | API/feed driven listing |
| **P3** | T13: Resource Hub | ~10 | Learning centers, technical references |
| **P3** | T14: Content Hub | ~20-30 | Sustainability, partnering, corporate content pages |
| **P3** | T15: Product Directory | ~3-5 | Order, Applications, Protocols |
| **P3** | T16: Product Showcase | ~1 | New products landing |
| **P3** | T17: Campaign Pages | ~5-10 | Marketing/storytelling (Inspire, Keep Seeking) |
| **P4** | T10: Blog | Hundreds | WordPress - separate platform decision |
| **P4** | Corporate site | Separate | Different domain, different migration project |

### Suggested Migration Phases

**Phase 1 - Foundation:**
- Global components (header, footer, nav)
- Design tokens and styles
- Homepage (T1)
- 2-3 Top-Level Category pages (T2)

**Phase 2 - Content Scale:**
- Sub-Category pages (T3)
- Brand pages (T6)
- Services pages (T5)

**Phase 3 - Commerce Integration:**
- Product Detail Pages (T4) with Edge Worker/API integration
- Search integration
- eCommerce functionality (cart, checkout)

**Phase 4 - Supporting Pages:**
- Support Hub (T8)
- Promotions (T7)
- Events (T9)
- Blog decision and execution

---

## URL Analysis Status

The following URLs were analyzed during discovery. This tracks which URLs are live, which return 404, and which redirect.

### Live Pages Analyzed

| URL | Template | Key Findings |
|-----|----------|-------------|
| `/us/en/home.html` | T1: Homepage | Hero carousel, card grids, promos |
| `/us/en/home/life-science/antibodies.html` | T2: Category Landing | Full-width, no sidebar |
| `/us/en/home/life-science/antibodies/primary-antibodies.html` | T3: Sub-Category | Sidebar + main content |
| `/order/catalog/product/11668019` | T4: PDP | Complex API-driven |
| `/us/en/home/products-and-services/services.html` | T5: Services | TOC + 2-col service cards |
| `/us/en/home/brands/thermo-scientific.html` | T6: Brand Page | Sidebar + categorized links |
| `/us/en/home/products-and-services/promotions.html` | T7: Promotions | Filter tabs + card grid |
| `/us/en/home/support.html` | T8: Support Hub | Multi-col link directory |
| `/us/en/home/events.html` | T9: Events | Filterable event listing table |
| `/us/en/home/brands.html` | T11: Brand Hub | Video + brand cards grid |
| `/us/en/home/life-science.html` | T12: Division Hub | Flat category card grid |
| `/us/en/home/life-science/protein-biology.html` | T2/T3 Hybrid | Sidebar + promo banners + category cards |
| `/us/en/home/brands/invitrogen.html` | T6: Brand Page | Sidebar + hero + categories |
| `/us/en/home/technical-resources/learning-centers.html` | T13: Resource Hub | 3-col image + title + desc cards |
| `/us/en/home/about-us/product-stewardship/sustainable-product-design.html` | T14: Content Hub | Sub-nav + feature cards + video |
| `/us/en/home/about-us/partnering-licensing.html` | T14: Content Hub | Stats panel + sidebar nav + info cards |
| `/us/en/home/order.html` | T15: Product Directory | TOC + categorized link grids |
| `/us/en/home/references/protocols.html` | T15: Product Directory | 2-col categorized protocol links |
| `/us/en/home/applications-techniques.html` | T15: Product Directory | TOC + 6 category sections |
| `/us/en/home/order/new-products.html` | T16: Product Showcase | Featured product cards + sticky banner |
| `/us/en/home/brands/inspire/keep-seeking.html` | T17: Campaign | Video hero + story cards + feature sections |

### 404 Pages (Not Found)

| URL | Notes |
|-----|-------|
| `/us/en/home/brands/brand-inspire.html` | Brand Inspire — page removed or moved |
| `/us/en/home/references/gibco-cell-culture-basics/cell-culture-protocols-and-references.html` | Gibco References — likely restructured |
| `/us/en/home/life-science/antibodies/primary-antibodies/image-gallery.html` | Image Gallery — page removed |
| `/us/en/home/life-science/cell-culture/cell-lines.html` | Cell Lines — page removed or moved |
| `/us/en/home/technical-resources/knowledge-base.html` | Knowledge Base — page removed |
| `/us/en/home/about-us/aspire-member-program.html` | Aspire Member Program — page removed |

### Redirected Pages

| Original URL | Redirected To | Notes |
|-------------|---------------|-------|
| `/us/en/home/about-us/product-stewardship.html` | `/us/en/home/about-us/product-stewardship/sustainable-product-design.html` | Redirects to sub-page |
| `/us/en/home/o.html` (Offers) | `/us/en/home.html` | Redirects to homepage |
| `/us/en/home/about-us/events.html` | `/us/en/home/events.html` | Redirects to events hub |
| `/us/en/home/products-and-services/new-products.html` | `/us/en/home/order/new-products.html` | Redirects to order section |

---

## Appendix: Screenshots

All screenshots captured during discovery are available in the `discovery/` folder:

| File | Description |
|------|-------------|
| `homepage-full.png` | Homepage full-page screenshot |
| `antibodies-category.png` | Top-level category (Antibodies) |
| `product-detail.png` | Product Detail Page (Lipofectamine 2000) |
| `services-page.png` | Services landing page |
| `brand-page.png` | Brand page (Thermo Scientific) |
| `promotions-page.png` | Promotions page with filter tabs |
| `support-page.png` | Support hub page |
| `events-page.png` | Events and Webinars page |
| `brands-hub.png` | Brand Hub page with video + brand cards |

---

*Report generated as part of AEM 6.4 to AEM Cloud + Edge Delivery Services migration discovery.*
*Updated: March 2026 — Added templates T11-T17 and URL analysis status from extended URL analysis.*
