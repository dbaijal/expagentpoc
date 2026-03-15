# Data-Driven Filterable Lists — EDS Approach

## AEM 6.4 Table + Row Component → EDS `data-list` Block

---

## 1. Current AEM 6.4 Implementation

### How It Works Today

```
Author configures Table Component properties (search, filter, sortable, paginate)
         ↓
Author defines Filter Groups (e.g., Event Type) and all possible filter values
         ↓
Author adds Row child components via responsive grid
         ↓
Author fills each Row's data model (title, date, description, filter mappings, search keywords)
         ↓
TableServlet serializes table + row data to JSON
         ↓
PragmaDataTable.js initializes client-side rendering
         ↓
User sees: search bar + filter dropdowns + event cards with images
```

### Table Component Configuration (Author Dialog)

| Property | Type | Purpose |
|----------|------|---------|
| Searchable | Checkbox | Enables text search bar across row content |
| Enable Pagination | Checkbox | Shows pagination controls below the list |
| Default per page value | Dropdown (5/10/15/20/25) | Number of items per page when pagination is enabled |
| Filterable | Checkbox | Enables faceted filter dropdowns |
| Left Navigation for Filters | Checkbox | Places filters in left sidebar instead of top bar |

### Filter Configuration

- Author creates **filter groups** (Event Type, Country, Month) in the Table Filters tab
- Author manually adds **all possible values** per group (Conference, Tradeshow, Virtual Event, Webinar, etc.)
- On each Row component, author maps the row to filter values (e.g., `Event Type~Tradeshow, Country~United States`)
- Filter values that don't match any row are shown as **grayed out**

### Row Component Data Model

Each Row child contains:

| Field | Example |
|-------|---------|
| Title | SLAS 2026 |
| Title Link | /events/slas-2026.html |
| Date / Subtitle | February 7–11, 2026 \| Boston, MA US |
| Description | SLAS is a tradeshow highlighting automated instruments... |
| Image | /content/dam/events/slas-2026.jpg |
| Search Keywords | SLAS, Automation, dispensers, pipette tips, liquid handling |
| Filter Mapping | Event Type~Tradeshow, Country~United States, Month~February |

### Key Pain Point

**Author maintains data in TWO places:**
1. Table Component config → filter groups and their values
2. Each Row component → data fields AND filter mappings

Adding a new filter value requires editing the Table config AND updating relevant Rows.

---

## 2. EDS Approach — Spreadsheet-Driven `data-list` Block

### How It Works in EDS

```
Author maintains a spreadsheet (e.g., events.xlsx)
         ↓
EDS auto-generates a JSON endpoint (/events-data.json)
         ↓
data-list block on the page fetches this JSON on page load
         ↓
Block JS auto-derives filter values from spreadsheet column data
         ↓
Block JS builds search bar, filter dropdowns, and item list
         ↓
User sees: same experience — search + filters + event cards
```

**Author maintains data in ONE place:** The spreadsheet. Everything — display content, filter values, and search keywords — derives from this single source.

---

## 3. Spreadsheet Structure

The spreadsheet replaces both the Table Component config and all Row children.

### Example: `events.xlsx`

| title | date | end-date | location | description | link | image | event-type | country | month | search-keywords |
|-------|------|----------|----------|-------------|------|-------|------------|---------|-------|-----------------|
| SLAS 2026 | 2026-02-07 | 2026-02-11 | Boston, MA US | SLAS is a tradeshow highlighting automated instruments and consumables. | /events/slas-2026 | /images/slas.jpg | Tradeshow | United States | February | SLAS, Automation, dispensers |
| BioPro Materials Virtual Summit | 2026-04-07 | 2026-04-07 | Virtual | Join our one-day virtual event to connect with downstream operations teams. | https://labroots.com/biopro | /images/biopro.jpg | Virtual Event | Global | April | bioprocessing, downstream |
| USCAP 2026 | 2026-03-23 | 2026-03-26 | San Antonio, TX | Join Thermo Fisher at the USCAP 115th Annual Meeting. Visit booth #423. | /events/uscap | /images/uscap.jpg | Conference | United States | March | USCAP, pathology |

### Column Purposes

| Column Type | Columns | Purpose |
|-------------|---------|---------|
| **Display** | title, date, end-date, location, description, link, image | Content shown in each event card |
| **Filter facets** | event-type, country, month | Columns that become filter dropdowns — values auto-extracted |
| **Search** | search-keywords | Hidden keywords for text search (not displayed) |
| **Linking** | link | URL to event detail page — supports both internal and external URLs |

---

## 4. Block Configuration (Author Properties)

The `data-list` block has configuration properties that match the current AEM Table Component dialog — giving the author the same level of control.

### Property Mapping: AEM 6.4 → EDS

| AEM 6.4 Table Config | EDS Block Property | Type | Purpose |
|----------------------|-------------------|------|---------|
| *(new in EDS)* | Data Source | Text (path) | Path to spreadsheet JSON endpoint (e.g., `/events-data`) |
| Searchable | Searchable | Boolean toggle | Enable/disable text search bar |
| Enable Pagination | Enable Pagination | Boolean toggle | Show/hide pagination controls |
| Default per page value | Default per page | Select dropdown | 5 / 10 / 15 / 20 / 25 per page |
| Filterable | Filterable | Boolean toggle | Enable/disable filter dropdowns |
| Left Navigation for Filters | Left Navigation for Filters | Boolean toggle | Filters in left sidebar vs top bar |
| Filter Groups config | Filter Columns | Text | Comma-separated spreadsheet column names to use as filters |

### What Author Sees in Universal Editor

```
┌─ Data List Block Properties ──────────────────┐
│                                                │
│  Data Source:  [ /events-data             ]    │
│                                                │
│  [x] Searchable                                │
│                                                │
│  [ ] Enable Pagination                         │
│                                                │
│  Default per page value: [ 15 per page   ▾]   │
│                                                │
│  [x] Filterable                                │
│                                                │
│  [ ] Left Navigation for Filters               │
│                                                │
│  Filter Columns:                               │
│  [ event-type, country, month            ]     │
│                                                │
└────────────────────────────────────────────────┘
```

---

## 5. How Filters Work — Simplified

### AEM 6.4 (Author does 3 steps)

1. **Table config** → Create filter group "Event Type"
2. **Table config** → Manually add values: Conference, Roadshow/Tour, Tradeshow, Training Course, User Meeting, Virtual Event, Webinar, Workshop, Seminar
3. **Each Row** → Map row to filter: `Event Type~Tradeshow`

### EDS (Author does 1 step)

1. **Spreadsheet** → Fill `event-type` column with value: `Tradeshow`

That's it. The block automatically:
- Reads all unique values from the `event-type` column
- Builds the filter dropdown with those values
- Matches rows to filters based on column data
- Grays out filter values that have no matching rows (when combined with other active filters)

### Side-by-Side Comparison

| Concern | AEM 6.4 | EDS |
|---------|---------|-----|
| Define filter groups | Author creates in Table Filters tab | Author specifies column names in block config |
| Define filter values | Author manually types each value in Table config | Auto-derived from spreadsheet row data |
| Map row to filter | Author sets per row (`Event Type~Tradeshow`) | Author fills column value (`Tradeshow`) — same data drives both display and filter |
| Grayed-out filters | JS checks row data-attributes | JS checks filtered dataset — same visual behavior |
| Add new filter value | Edit Table config AND edit relevant rows | Just fill the value in a new spreadsheet row — filter value appears automatically |
| Remove stale values | Must manually delete from Table config | Disappears automatically when no rows have that value |
| Reorder filter values | Manual ordering in Table config | Alphabetical by default, or custom sort in block JS |

---

## 6. Authoring Workflow Comparison

### Adding a New Event

**AEM 6.4:**
1. Navigate to events page in AEM Author
2. Open Table Component's parsys
3. Add new Row component
4. Fill Row dialog: title, link, date, description, image
5. Fill Row filter mapping: `Event Type~Webinar, Country~Global, Month~March`
6. Fill Row search keywords
7. If "Webinar" is a new filter value → also edit Table Component → Filters tab → add "Webinar" to Event Type group
8. Publish page

**EDS:**
1. Open `events.xlsx` spreadsheet
2. Add a new row: fill title, date, location, description, link, image, event-type, country, month, search-keywords
3. Save spreadsheet
4. Preview/Publish

### Removing an Expired Event

**AEM 6.4:**
1. Navigate to events page
2. Find the Row component in the parsys
3. Delete the Row
4. Check if this was the last row with a specific filter value → if yes, also clean up the Table Filters config
5. Publish

**EDS:**
1. Open spreadsheet
2. Delete the row
3. Save — filter value automatically disappears if no other rows use it

### Bulk Updates (e.g., Update 20 Event Dates)

**AEM 6.4:** Open each of 20 Row component dialogs individually, update the date field, save each one.

**EDS:** Update 20 cells in the date column of the spreadsheet. Save once.

---

## 7. Why Spreadsheet Is Recommended

| Advantage | Detail |
|-----------|--------|
| **Single source of truth** | Data, filters, and search all derive from one spreadsheet — no duplication |
| **Mirrors AEM mental model** | Author fills rows in a sheet — same concept as adding Row children in AEM, but more efficient |
| **Handles external links** | Some events link to external sites (labroots.com, on24.com) — the `link` column supports any URL |
| **Bulk editing** | Update many items at once in a spreadsheet vs editing individual component dialogs |
| **No stale filters** | Filter values are always in sync with actual data — no orphaned values to clean up |
| **Reusable across the site** | Same `data-list` block pattern can be reused for other filterable listings (resources, publications, webinars, product catalogs) — just point to a different spreadsheet |
| **Easy handoff** | Non-technical content authors can manage a spreadsheet without needing AEM authoring expertise |

---

## 8. Reusability — One Block, Many Use Cases

The `data-list` block is generic. By pointing it to different spreadsheets with different columns, the same block can power any filterable listing on the site:

| Use Case | Spreadsheet | Filter Columns | Display Columns |
|----------|-------------|----------------|-----------------|
| Events & Webinars | events.xlsx | event-type, country, month | title, date, location, description, image, link |
| Resource Library | resources.xlsx | resource-type, topic, format | title, description, thumbnail, download-link |
| Publication Catalog | publications.xlsx | year, journal, research-area | title, authors, abstract, doi-link |
| Product Selector | products.xlsx | category, application, brand | title, description, image, product-link |

The block configuration properties (searchable, filterable, pagination, filter columns) allow each instance to behave differently without any code changes.

---

## 9. Summary

| Aspect | AEM 6.4 (Current) | EDS (Proposed) |
|--------|-------------------|----------------|
| **Data storage** | Table Component + Row children in JCR | Spreadsheet (`.xlsx`) with auto-generated JSON endpoint |
| **Filter definitions** | Authored separately in Table Filters tab | Auto-derived from spreadsheet column values |
| **Author touchpoints** | 2 places (Table config + each Row) | 1 place (spreadsheet) |
| **Block configuration** | Table Component dialog (searchable, filterable, paginate) | Block properties panel (same toggles and dropdowns) |
| **Client behavior** | PragmaDataTable.js renders table + search + filter | data-list block JS renders cards + search + filter |
| **Adding new items** | Add Row child component, fill dialog, map filters | Add row to spreadsheet |
| **Bulk management** | Edit each Row dialog individually | Edit spreadsheet cells — save once |
| **Reusability** | One Table component per use case | One `data-list` block, multiple spreadsheets |
| **External links** | Supported via Row link field | Supported via spreadsheet `link` column |
