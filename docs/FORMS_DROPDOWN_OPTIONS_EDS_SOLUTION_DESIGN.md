# Forms Dropdown Options — EDS Solution Design

## 1. Overview

This document describes the approach for populating dropdown (select) fields in AEM Edge Delivery Services (EDS) forms, migrating from the current AEM 6.4 Content Fragment-based dropdown population to EDS-compatible data sources. It covers three approaches — Content Fragments via API, Spreadsheets, and Hybrid — with a clear recommendation.

---

## 2. Current State (AEM 6.4)

### How Dropdowns Are Populated Today

```
Author opens form field dialog
  → Selects "Dropdown" as field type
  → Selects Data Source: "Content Fragment"
  → Browses and selects a CF path (e.g., /content/dam/tfs/forms/data/countries)
  → Saves field configuration
  → At render time:
     AEM Sling Model reads the Content Fragment from JCR
     → Extracts key-value pairs from CF elements
     → Converts to <option> elements inside <select>
     → Browser receives fully populated dropdown
```

### Data Sources Used

| Data Source | Example | Usage |
|------------|---------|-------|
| **Content Fragments** | `/content/dam/tfs/forms/data/countries` | Country, State, Division, Product lists |
| **Generic Lists** | `/etc/acs-commons/lists/organization-types` | Organization Type, Salutation, Job Titles |
| **Inline Options** | Hardcoded in dialog | Small static lists (Yes/No, Gender) |

### Key Characteristics

- **Server-side rendering** — CF data is read during page render; browser receives pre-populated HTML
- **Sling Model mapping** — `FormConstraintsModel` reads CF path, extracts options, builds `<select>`
- **Cascading support** — Parent-child relationships via AJAX to `/bin/servlet/tf/form/cascadeoptions.json`
- **Multi-language** — CFs have locale-specific variations for translated labels
- **Caching** — Dispatcher caches rendered HTML; CF changes require cache invalidation

---

## 3. Fundamental Shift in EDS

In EDS, there is **no server-side rendering of form fields**. The form-container block JavaScript runs in the browser and must **fetch dropdown data at runtime**.

```
AEM 6.4:  Server reads CF → renders <option> → browser receives populated HTML
EDS:      Browser loads page → form JS fetches data source → populates <option> client-side
```

This means the data source must be **accessible via HTTP from the browser**.

---

## 4. Available Approaches

### 4.1 Option A: Content Fragments via AEM APIs

Continue using existing Content Fragments. The form JS fetches CF data via AEM's REST or GraphQL APIs at runtime.

#### CF REST API

```
GET https://publish-xyz.adobeaemcloud.com/api/assets/tfs/forms/data/countries.json
```

**Response structure:**
```json
{
  "properties": {
    "elements": {
      "options": {
        "value": [
          { "key": "US", "label": "United States" },
          { "key": "CA", "label": "Canada" },
          { "key": "IN", "label": "India" }
        ]
      }
    }
  }
}
```

**Form JS implementation:**
```javascript
async function loadOptionsFromCF(cfPath) {
  const resp = await fetch(`/api/assets${cfPath}.json`);
  if (!resp.ok) return [];
  const cf = await resp.json();
  return cf.properties.elements.options.value.map((item) => ({
    value: item.key,
    label: item.label,
  }));
}
```

#### GraphQL Persisted Queries

Pre-define a query in AEM and call it by name.

**Persisted query definition (`/tfs/getDropdownOptions`):**
```graphql
query ($listName: String!) {
  optionsListByPath(_path: $listName) {
    items {
      key
      label
      sortOrder
    }
  }
}
```

**Runtime call:**
```
GET https://publish-xyz.adobeaemcloud.com/graphql/execute.json/tfs/getDropdownOptions;listName=/content/dam/tfs/forms/data/countries
```

**Form JS implementation:**
```javascript
async function loadOptionsFromGraphQL(queryName, variables) {
  const params = Object.entries(variables)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join(';');
  const resp = await fetch(`/graphql/execute.json/${queryName};${params}`);
  if (!resp.ok) return [];
  const { data } = await resp.json();
  const items = Object.values(data)[0]?.items || [];
  return items.map((item) => ({
    value: item.key,
    label: item.label,
  }));
}
```

#### Assessment

| Factor | Rating | Notes |
|--------|--------|-------|
| Migration effort | Low | Reuses existing CFs as-is |
| Performance | Poor | API call to AEM publish (100-300ms per dropdown) |
| Reliability | Medium | Depends on AEM publish instance uptime |
| Scalability | Poor | 50K forms × multiple dropdowns = heavy load on AEM publish |
| CORS setup | Required | Browser calls AEM API cross-origin |
| Edge caching | Not default | Requires custom CDN rules for CF API responses |

---

### 4.2 Option B: Spreadsheets (Recommended)

Migrate dropdown data to EDS spreadsheets. Each list becomes a spreadsheet that is automatically available as edge-cached JSON.

#### Spreadsheet Structure

Author creates a spreadsheet at `/content/data/lists/countries`:

| Key | Label | Region | Sort Order |
|-----|-------|--------|-----------|
| US | United States | north-america | 1 |
| CA | Canada | north-america | 2 |
| IN | India | asia-pacific | 3 |
| DE | Germany | europe | 4 |
| JP | Japan | asia-pacific | 5 |
| GB | United Kingdom | europe | 6 |
| AU | Australia | asia-pacific | 7 |
| FR | France | europe | 8 |
| BR | Brasil | south-america | 9 |

#### Auto-Generated JSON Endpoint

The spreadsheet is automatically available as JSON at:

```
GET /content/data/lists/countries.json
```

**Response:**
```json
{
  "total": 9,
  "offset": 0,
  "limit": 9,
  "data": [
    { "Key": "US", "Label": "United States", "Region": "north-america", "Sort Order": "1" },
    { "Key": "CA", "Label": "Canada", "Region": "north-america", "Sort Order": "2" },
    { "Key": "IN", "Label": "India", "Region": "asia-pacific", "Sort Order": "3" }
  ]
}
```

#### Form JS Implementation

```javascript
async function loadOptionsFromSheet(sheetPath) {
  const resp = await fetch(`${sheetPath}.json`);
  if (!resp.ok) return [];
  const { data } = await resp.json();
  return data.map((row) => ({
    value: row.Key,
    label: row.Label,
  }));
}
```

#### Assessment

| Factor | Rating | Notes |
|--------|--------|-------|
| Migration effort | Medium | One-time migration of CF data to spreadsheets |
| Performance | Excellent | Edge-cached CDN response (5-20ms) |
| Reliability | Excellent | No AEM publish dependency; works independently |
| Scalability | Excellent | CDN handles load; zero impact on AEM |
| CORS setup | Not needed | Same-origin request |
| Edge caching | Built-in | Automatic with EDS infrastructure |

---

### 4.3 Option C: Hybrid

Use spreadsheets for most dropdown lists, Content Fragments via GraphQL only for complex structured data.

| Data Type | Recommended Source | Reason |
|-----------|-------------------|--------|
| Countries, States, Regions | Spreadsheet | High usage, static, needs edge caching |
| Organization Types, Salutations | Spreadsheet | Simple key-label pairs |
| Product Categories | Spreadsheet | Frequently accessed, benefits from CDN |
| Division Lists | Spreadsheet | Semi-static, simple structure |
| Complex hierarchical data | CF via GraphQL | Multi-level nesting, cross-references |
| Data managed by external teams | CF via GraphQL | Team cannot switch from CF workflow |

---

## 5. Recommendation

### Primary Approach: Spreadsheets

**Use spreadsheets as the default data source for all dropdown options.**

| Factor | CF (REST/GraphQL) | Spreadsheet | Winner |
|--------|-------------------|-------------|--------|
| **Response time** | 100-300ms (AEM publish) | 5-20ms (CDN edge) | Spreadsheet |
| **Reliability** | Depends on AEM publish uptime | Independent, CDN-served | Spreadsheet |
| **Scalability (50K forms)** | Heavy load on AEM publish | Zero load, CDN handles it | Spreadsheet |
| **CORS configuration** | Required | Not needed | Spreadsheet |
| **Edge caching** | Requires custom CDN rules | Built-in automatic | Spreadsheet |
| **Author experience** | Same CF workflow | Familiar spreadsheet format | Tie |
| **Migration effort** | Zero | One-time data migration | CF |
| **Complex data modeling** | Rich (nested, references) | Flat tabular | CF |
| **Multi-language support** | Built-in CF localization | Language columns in sheet | Tie |

### When to Use CF via GraphQL Instead

Only use Content Fragments if **all three** conditions apply:
1. The data is deeply nested/hierarchical and cannot be flattened into a spreadsheet
2. The data requires AEM-specific workflow (approvals, versioning, scheduled publishing)
3. An external team manages the CFs and cannot adopt spreadsheet authoring

For typical form dropdowns (countries, states, divisions, org types, product categories, salutations) — **spreadsheets are the clear choice**.

---

## 6. Authoring Experience

### How Authors Configure Dropdown Data Source

In the Universal Editor, the dropdown field's properties panel includes an "Options Source" field:

```
+---------------------------------------+
| Dropdown Field Properties             |
+---------------------------------------+
| Label:          [Country/Region     ] |
| Field Name:     [country            ] |
| Required:       [x]                   |
| Placeholder:    [- Please Select -  ] |
| Options Source:  [/content/data/lists/countries] |
+---------------------------------------+
```

**Component model for the dropdown field:**
```json
{
  "component": "aem-content",
  "name": "optionsSource",
  "label": "Options Source",
  "valueType": "string",
  "description": "Path to the spreadsheet containing dropdown options. Leave blank for inline options."
}
```

The `aem-content` picker allows authors to browse and select the spreadsheet from AEM content.

### Decision Logic in Form JS

The form-container block detects the data source type based on the authored value:

| Authored Value | Detection | Behavior |
|---------------|-----------|----------|
| `/content/data/lists/countries` | Starts with `/` | Fetch from spreadsheet `.json` endpoint |
| `US=United States, CA=Canada, IN=India` | Contains commas, no `/` prefix | Parse as inline comma-separated options |
| *(empty)* | No value | No options — field renders as empty select |

**Form JS detection logic:**
```javascript
async function populateDropdown(selectElement, optionsSource) {
  if (!optionsSource) return;

  if (optionsSource.startsWith('/')) {
    // Path-based: fetch from spreadsheet
    const options = await loadOptionsFromSheet(optionsSource);
    options.forEach((opt) => {
      const option = document.createElement('option');
      option.value = opt.value;
      option.textContent = opt.label;
      selectElement.append(option);
    });
  } else {
    // Inline: parse comma-separated values
    optionsSource.split(',').forEach((item) => {
      const [value, label] = item.includes('=')
        ? item.split('=').map((s) => s.trim())
        : [item.trim(), item.trim()];
      const option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      selectElement.append(option);
    });
  }
}
```

---

## 7. Multi-Language Support

### Approach: Language Columns in Spreadsheet

For dropdowns that need translated labels, add language-specific columns:

**Spreadsheet `/content/data/lists/organization-types`:**

| Key | Label (en) | Label (de) | Label (ja) | Label (zh) |
|-----|-----------|-----------|-----------|-----------|
| academic | Academic | Akademisch | 学術 | 学术 |
| commercial | Commercial | Kommerziell | 商業 | 商业 |
| governmental | Governmental | Staatlich | 政府 | 政府 |

**Form JS reads the current page language and selects the correct column:**

```javascript
async function loadLocalizedOptions(sheetPath) {
  const resp = await fetch(`${sheetPath}.json`);
  if (!resp.ok) return [];
  const { data } = await resp.json();

  // Detect current language from page URL (e.g., /us/en/... → "en")
  const lang = document.documentElement.lang || 'en';
  const labelColumn = `Label (${lang})`;
  const fallbackColumn = 'Label (en)';

  return data.map((row) => ({
    value: row.Key,
    label: row[labelColumn] || row[fallbackColumn] || row.Key,
  }));
}
```

### Alternative: Separate Sheets Per Language

For sites with very different option lists per locale:

```
/content/data/lists/countries-en
/content/data/lists/countries-de
/content/data/lists/countries-ja
```

The form JS constructs the path based on current language:

```javascript
const lang = document.documentElement.lang || 'en';
const localizedPath = `${basePath}-${lang}`;
```

### Recommendation

| Approach | Best For |
|----------|---------|
| Language columns in single sheet | Same options across languages, just labels differ |
| Separate sheets per language | Different option sets per locale (e.g., region-specific products) |

---

## 8. Cascading Dropdowns (Parent-Child)

### Current AEM 6.4 Flow

```
Author configures:
  Country dropdown → data source: /content/dam/tfs/forms/data/countries
  State dropdown → data source: /content/dam/tfs/forms/data/states
                 → parent field: country

User selects Country = "US"
  → AJAX call to /bin/servlet/tf/form/cascadeoptions.json?parent=country&value=US
  → Server filters states CF for US states
  → Returns filtered options
  → State dropdown populated with US states only
```

### EDS Approach: Filtered Spreadsheet

**States spreadsheet** (`/content/data/lists/states`):

| Key | Label | Parent |
|-----|-------|--------|
| AL | Alabama | US |
| AK | Alaska | US |
| CA | California | US |
| ON | Ontario | CA |
| QC | Quebec | CA |
| BC | British Columbia | CA |
| MH | Maharashtra | IN |
| KA | Karnataka | IN |
| DL | Delhi | IN |

**Authoring in UE — child dropdown field properties:**

```
+---------------------------------------+
| Dropdown Field Properties             |
+---------------------------------------+
| Label:          [State/Province     ] |
| Field Name:     [state              ] |
| Options Source:  [/content/data/lists/states] |
| Parent Field:   [country            ] |
+---------------------------------------+
```

**Component model:**
```json
[
  {
    "component": "aem-content",
    "name": "optionsSource",
    "label": "Options Source",
    "valueType": "string"
  },
  {
    "component": "text",
    "name": "parentField",
    "label": "Parent Field (for cascading)",
    "valueType": "string",
    "description": "Field name of the parent dropdown. Options will be filtered by parent's selected value."
  }
]
```

**Form JS for cascading:**

```javascript
function setupCascading(childSelect, parentFieldName, sheetPath) {
  const form = childSelect.closest('form');
  const parentSelect = form.querySelector(`[name="${parentFieldName}"]`);
  if (!parentSelect) return;

  let allOptions = [];

  // Load all child options once
  async function loadAllOptions() {
    const resp = await fetch(`${sheetPath}.json`);
    if (!resp.ok) return;
    const { data } = await resp.json();
    allOptions = data;
  }

  // Filter and populate based on parent value
  function filterOptions() {
    const parentValue = parentSelect.value;
    childSelect.innerHTML = '';

    // Add placeholder
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = '- Please Select -';
    placeholder.disabled = true;
    placeholder.selected = true;
    childSelect.append(placeholder);

    // Filter by parent value
    const filtered = allOptions.filter((row) => row.Parent === parentValue);
    filtered.forEach((row) => {
      const option = document.createElement('option');
      option.value = row.Key;
      option.textContent = row.Label;
      childSelect.append(option);
    });

    // Enable/disable child based on whether options exist
    childSelect.disabled = filtered.length === 0;
  }

  // Initialize
  loadAllOptions().then(() => {
    filterOptions(); // Apply initial state
    parentSelect.addEventListener('change', filterOptions);
  });
}
```

### Cascading Flow in EDS

```
Page loads
  → form-container.js decorates fields
  → Detects "state" dropdown has parentField = "country"
  → Fetches ALL states from /content/data/lists/states.json (cached on CDN)
  → State dropdown initially disabled (no country selected)

User selects Country = "US"
  → change event fires on country dropdown
  → JS filters cached states array: Parent === "US"
  → State dropdown populated with Alabama, Alaska, California...
  → State dropdown enabled

User changes Country to "India"
  → JS re-filters: Parent === "IN"
  → State dropdown repopulated with Maharashtra, Karnataka, Delhi...
```

**Key difference from AEM 6.4:** No AJAX call per parent change. All child options are fetched once (edge-cached) and filtered client-side. This is **faster and more reliable**.

---

## 9. Data Migration Plan

### One-Time Migration of CF Data to Spreadsheets

| Step | Action | Details |
|------|--------|---------|
| 1 | **Inventory existing CFs** | List all Content Fragments used as dropdown data sources |
| 2 | **Export CF data** | Use AEM query or package manager to export CF key-value pairs |
| 3 | **Create spreadsheet templates** | Define column structure (Key, Label, Region, Sort Order, Parent) |
| 4 | **Populate spreadsheets** | Transfer CF data into spreadsheets |
| 5 | **Validate JSON endpoints** | Verify each spreadsheet produces correct `.json` output |
| 6 | **Update form field references** | Change data source paths from CF paths to spreadsheet paths |
| 7 | **Test all dropdowns** | Verify population, cascading, multi-language across forms |

### Estimated Effort

| Item | Count (Estimate) | Effort |
|------|-----------------|--------|
| Unique dropdown lists (countries, states, org types, etc.) | 15-30 | 1-2 days |
| Cascading relationships (country→state) | 3-5 | 0.5 day |
| Multi-language variants | Per list × languages | 1-2 days |
| Form field reference updates | Per form (bulk scriptable) | 1 day |
| **Total** | | **3-6 days** |

---

## 10. Spreadsheet Organization

### Recommended Folder Structure

```
/content/data/
├── lists/
│   ├── countries              ← Country dropdown options
│   ├── states                 ← State/Province (cascading from countries)
│   ├── organization-types     ← Academic / Commercial / Governmental
│   ├── salutations            ← Mr / Mrs / Dr / Prof
│   ├── divisions              ← MSD / LSG / CMD / etc.
│   ├── job-titles             ← Job title options
│   ├── product-categories     ← Product category list
│   ├── inquiry-types          ← Quote / Demo / Support / etc.
│   └── phone-country-codes    ← +1, +91, +44, etc.
└── forms/
    └── rules/
        ├── webinar-form       ← Rules for webinar registration form
        └── contact-form       ← Rules for contact us form
```

### Naming Convention

| Convention | Example | Notes |
|-----------|---------|-------|
| All lowercase | `countries` not `Countries` | EDS convention |
| Hyphen-separated | `organization-types` not `organizationTypes` | Consistent with EDS paths |
| Plural nouns | `countries` not `country` | Represents a list |
| Descriptive names | `phone-country-codes` not `codes` | Clear purpose |

---

## 11. Performance Comparison

### AEM 6.4 vs EDS

| Metric | AEM 6.4 (CF + Sling Model) | EDS (Spreadsheet) |
|--------|---------------------------|-------------------|
| **Initial dropdown population** | 0ms (server-rendered in HTML) | 5-20ms (CDN fetch + JS population) |
| **Cascading dropdown update** | 100-300ms (AJAX to AEM servlet) | <5ms (client-side filter of cached data) |
| **Cache strategy** | Dispatcher cache (invalidation required) | CDN edge cache (automatic) |
| **AEM publish load** | Read per page render + AJAX per cascade | Zero load |
| **Offline resilience** | Fails if AEM publish is down | Works from CDN cache |
| **50K forms impact** | Significant AEM publish load | No impact (CDN scales) |

### Net Result

- **Initial load:** Slightly slower in EDS (5-20ms fetch vs 0ms pre-rendered) — imperceptible to users
- **Cascading updates:** Significantly faster in EDS (client-side filter vs server AJAX)
- **Scalability:** Dramatically better in EDS (CDN vs AEM publish)
- **Overall:** EDS provides better real-world performance at scale

---

## 12. Comparison: AEM 6.4 vs EDS

| Aspect | AEM 6.4 | EDS | Impact |
|--------|---------|-----|--------|
| **Data source** | Content Fragments in JCR | Spreadsheets on CDN edge | One-time migration |
| **Population mechanism** | Server-side Sling Model | Client-side JS fetch | No visual difference to end user |
| **Cascading dropdowns** | AJAX to AEM servlet per change | Client-side filter of cached data | Faster cascading in EDS |
| **Multi-language** | CF locale variations | Language columns in spreadsheet | Same capability, different format |
| **Caching** | Dispatcher cache | CDN edge cache (automatic) | Better caching in EDS |
| **Author experience** | Select CF path in dialog | Select spreadsheet path in UE | Similar experience |
| **AEM publish dependency** | Required at render time | Not required | More resilient in EDS |
| **Generic Lists** | ACS Commons Lists | Spreadsheet equivalent | Direct replacement |
| **Inline options** | Hardcoded in dialog | Comma-separated in field property | Same capability |

---

## 13. Limitations

| Limitation | Detail | Mitigation |
|-----------|--------|------------|
| **No server-side pre-rendering** | Dropdowns populate after JS loads | Fetch occurs early in form decoration; placeholder "- Please Select -" shown immediately. Population happens in <50ms from CDN. |
| **Flat data structure** | Spreadsheets are tabular, no deep nesting | Sufficient for all typical dropdown use cases. For deeply nested hierarchies, use CF via GraphQL as exception. |
| **No built-in versioning** | Spreadsheets don't have CF-style versioning | Use AEM's page versioning for the spreadsheet. Or maintain a "Last Updated" column for audit. |
| **One-time migration** | CF data must be migrated to spreadsheets | Scriptable export/import. Estimated 3-6 days for all lists. |
| **Large datasets (1000+ options)** | Full JSON fetched on page load | Use pagination (`?offset=0&limit=100`) or lazy-load on dropdown open. For very large lists, consider search-as-you-type pattern instead of dropdown. |

---

## 14. Implementation Checklist

- [ ] Inventory all Content Fragments used as dropdown data sources
- [ ] Inventory all Generic Lists used as dropdown data sources
- [ ] Define spreadsheet column structure per list type
- [ ] Create spreadsheet templates in `/content/data/lists/`
- [ ] Migrate CF data to spreadsheets
- [ ] Add `optionsSource` and `parentField` to dropdown field component model
- [ ] Implement `populateDropdown()` in form-container.js
- [ ] Implement `setupCascading()` for parent-child dropdowns
- [ ] Implement multi-language label selection logic
- [ ] Implement inline comma-separated option parsing (fallback)
- [ ] Test all dropdown types (simple, cascading, multi-language, inline)
- [ ] Performance test with large option lists (500+ items)
- [ ] Validate with screen readers (ARIA attributes on dynamic options)
- [ ] Document spreadsheet authoring guide for content authors
