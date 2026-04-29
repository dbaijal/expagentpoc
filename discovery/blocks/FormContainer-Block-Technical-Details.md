# Form Container Block — Technical Details & UE Authoring Contract

**Block Name:** Form Container
**Maps To:** Form Container + child form components (AEM 6.4)
**Pattern:** Parent-Child (Container block with multi-type child items)

> **Note:** This document describes the UE authoring contract and block architecture for the Form Container. The field models shown below are based on a working reference implementation and represent the authoring approach. Exact field definitions, validation rules, middleware integration parameters, prefill sources, and conditional logic will be refined during implementation based on a detailed audit of the client's existing form configurations.

---

## 1. UE Authoring Contract

### 1.1 AEM Resource

| Property | Value |
|---|---|
| Resource Type (Parent) | `core/franklin/components/block/v1/block` |
| Resource Type (Child Items) | `core/franklin/components/block/v1/block/item` |
| Block Name | `Form Container` |
| Filter | `form-container` -> allows 9 child item types |
| Block Type | Container (parent-child with multi-type children) |
| Location | `blocks/form-container/` |

### 1.2 How Authors Build a Form in Universal Editor

```
Step 1: Author adds a Section to the page

Step 2: Inside the Section, author drops "Form Container" from the block palette

Step 3: Inside the Form Container, author adds child items:

        [+] Add:
        ├── Form Config        ← form-level settings (must be first)
        ├── Input Field        ← text, email, phone, number, date
        ├── Options Field      ← dropdown, radio, checkbox
        ├── Text Area          ← multi-line text
        ├── Hidden Field       ← technical/context values
        ├── Upload Field       ← file attachment
        ├── Form Button        ← submit / reset
        ├── Form Label         ← static text / instructions
        └── Form Fragment      ← reusable field group from another page

Step 4: Each child item has its own properties panel — author configures
        label, name, type, required, wizard step, layout span, etc.

Step 5: form-container.js reads all child items at delivery time and
        renders a complete <form> with validation, layout, and submission logic
```

### 1.3 Author View in Universal Editor

```
┌──────────────────────────────────────────────────────────────────┐
│ FORM CONTAINER BLOCK                                             │
│                                                                  │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │ [Config] Action: ELOQUA | Form ID: abc123 | Redirect: /ty │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Input] First Name              Required  | Step 1 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Input] Last Name               Required  | Step 1 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Input] Email (type: email)     Required  | Step 1 | Half │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Input] Phone (type: tel)                 | Step 1 | Half │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Options] Country (dropdown)    Required  | Step 2 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [TextArea] Comments                       | Step 2 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Hidden] sessionId (source: cookie)                        │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Upload] Attachment                       | Step 2 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Label] "By submitting, you agree to..."  | Step 2 | Full │   │
│ ├────────────────────────────────────────────────────────────┤   │
│ │ [Button] Submit                                            │   │
│ └────────────────────────────────────────────────────────────┘   │
│ [+] Add: Config | Input | Options | TextArea | Hidden | ...      │
└──────────────────────────────────────────────────────────────────┘

Properties Panel (when "Input Field — Email" is selected):
┌────────────────────────────────────────────────────┐
│ Field Name: [email                    ]            │
│ Label: [Email Address                 ]            │
│ Input Type: [Email ▼]                             │
│ Placeholder: [name@company.com        ]            │
│ Required: [Yes ▼]                                 │
│ Wizard Step: [Step 1 ▼]                           │
│ Column Span: [Half ▼]                             │
└────────────────────────────────────────────────────┘
```

### 1.4 Container vs Leaf Fields

The Form Container is the **most complex container block** in the project:

- **Parent (Form Container):** Container only — no content fields of its own. Governs which child types are allowed via filter.
- **Children (9 types):** Each child item type has its own model with type-specific fields. Author adds/removes/reorders items freely. Each type has a different properties panel.

### 1.5 References, Fragments, and Nested Items

| Reference Type | Field | UE Behavior |
|---|---|---|
| Form Fragment | `field_path` on form-fragment item (aem-content) | Opens content path picker — author selects a page containing another form-container block. Its fields are included at delivery. |
| Redirect URL | `config_redirect` on form-config item (text) | Author types URL or content path |

No DAM asset references. No nested blocks. All form structure is via child items within the container.

---

## 2. Child Item Types — Field Models

### 2.1 Form Config (form-level settings)

**Must be the first child item.** Configures form-level behavior.

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "config" | Fixed — identifies this as config item |
| `field_action` | select | Action Type | Yes | "ELOQUA" | Eloqua, GCMS, LSG, Non-LSG, Marketo, Email, CORA, Custom API |
| `config_formid` | text | Form ID | Yes | "" | Unique identifier for this form |
| `config_redirect` | text | Redirect URL | No | "-" | Page to redirect after successful submission |
| `config_thankyou` | text | Thank You Message | No | "-" | Message shown on success (if no redirect) |
| `config_steptitles` | text | Wizard Step Titles | No | "-" | Comma-separated titles (e.g., "Contact Info, Preferences, Review"). Leave empty for single-page forms. |

### 2.2 Input Field (text, email, phone, number, date)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "input" | Fixed |
| `field_name` | text | Field Name | Yes | "" | Name used in form submission data |
| `field_label` | text | Label | Yes | "" | Visible label above the field |
| `config_type` | select | Input Type | No | "text" | Text, Email, Phone, Number, Date |
| `config_placeholder` | text | Placeholder | No | "" | Placeholder text inside the field |
| `validation_required` | select | Required | No | "false" | Yes / No |
| `meta_step` | select | Wizard Step | No | "—" | —, Step 1, Step 2, Step 3, Step 4, Step 5 |
| `meta_span` | select | Column Span | No | "12" (full) | Full Width (12), Half (6), One Third (4), Two Thirds (8), One Quarter (3) |

### 2.3 Options Field (dropdown, radio, checkbox)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "options" | Fixed |
| `field_name` | text | Field Name | Yes | "" | |
| `field_label` | text | Label | Yes | "" | |
| `config_display` | select | Display Type | No | "select" | Dropdown, Radio Buttons, Checkboxes |
| `config_options` | text | Options | Yes | "" | Comma-separated list of options |
| `config_placeholder` | text | Placeholder | No | "" | For dropdown — e.g., "Please Select" |
| `validation_required` | select | Required | No | "false" | Yes / No |
| `meta_step` | select | Wizard Step | No | "—" | Same step options as Input Field |
| `meta_span` | select | Column Span | No | "12" | Same span options as Input Field |

### 2.4 Text Area (multi-line text)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "textarea" | Fixed |
| `field_name` | text | Field Name | Yes | "" | |
| `field_label` | text | Label | Yes | "" | |
| `config_placeholder` | text | Placeholder | No | "" | |
| `validation_required` | select | Required | No | "false" | Yes / No |
| `meta_step` | select | Wizard Step | No | "—" | Same options |
| `meta_span` | select | Column Span | No | "12" | Same options |

### 2.5 Hidden Field (technical/context values)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "hidden" | Fixed |
| `field_name` | text | Field Name | Yes | "" | e.g., sessionId, formId, actionType |
| `config_value` | text | Value | No | "" | Static value or parameter name |
| `config_source` | select | Value Source | No | "static" | Static Value, URL Parameter, Cookie, Timestamp |
| `meta_step` | select | Wizard Step | No | "—" | Same options |
| `meta_span` | select | Column Span | No | "12" | Same options |

### 2.6 Upload Field (file attachment)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "upload" | Fixed |
| `field_name` | text | Field Name | Yes | "" | |
| `field_label` | text | Label | Yes | "" | |
| `config_label` | text | Button Text | No | "Choose File" | Text for file selection button |
| `validation_required` | select | Required | No | "false" | Yes / No |
| `meta_step` | select | Wizard Step | No | "—" | Same options |
| `meta_span` | select | Column Span | No | "12" | Same options |

### 2.7 Form Button (submit/reset)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "button" | Fixed |
| `field_label` | text | Button Label | No | "Submit" | |
| `config_role` | select | Button Type | No | "submit" | Submit, Reset |

### 2.8 Form Label (static text/instructions)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "label" | Fixed |
| `content_text` | richtext | Text | Yes | "" | Instructions, disclaimers, grouping titles. Supports formatting. |
| `meta_step` | select | Wizard Step | No | "—" | Same options |
| `meta_span` | select | Column Span | No | "12" | Same options |

### 2.9 Form Fragment (reusable field groups)

| Field | Component | Label | Required | Default | Options / Notes |
|---|---|---|---|---|---|
| `field_type` | text | Component | Auto | "fragment" | Fixed |
| `field_path` | aem-content | Fragment Page | Yes | "" | Path to a page containing a form-container block. Its fields are included at delivery. |
| `meta_step` | select | Wizard Step | No | "—" | Override step for all fields from this fragment |
| `meta_span` | select | Column Span | No | "12" | Not typically used — individual fragment fields define their own spans |

---

## 3. Governance — Allowed Child Items

### 3.1 Form Container Filter

```json
{
  "id": "form-container",
  "components": [
    "form-config",
    "form-input",
    "form-options",
    "form-textarea",
    "form-hidden",
    "form-upload",
    "form-button",
    "form-label",
    "form-fragment"
  ]
}
```

**Only** these 9 child item types can be added inside a Form Container. No generic content blocks (cards, hero, text, image) can be nested inside.

### 3.2 Block-Level Governance

| Rule | Form Container |
|---|---|
| **Allowed in sections** | Yes — listed in section-level filter |
| **Allowed on all page types** | Yes |
| **Is it a container** | **Yes** — most complex container (9 child types) |
| **Allowed children** | 9 form-specific child types only (via `form-container` filter) |
| **Fragment-only** | **No** — authored directly on pages. Can also be used in fragment pages for form reuse. |
| **Standalone** | **Yes** — author drops it into any section |
| **Min/max items** | Must have at least Form Config + one field + Form Button |

---

## 4. Nested Authoring Strategy

### Classification: Parent-Child (Multi-Type Container)

| Pattern | Applies? | Reason |
|---|---|---|
| Flattened block | No | Forms have variable numbers of fields, each with different types and configurations. Cannot flatten. |
| **Parent-child (multi-type)** | **Yes** | Parent container with 9 different child item types. Most complex container in the project. Each child type has its own model. |
| Reference-based | Partially — Form Fragment child references another form page | The container itself is not reference-based, but one child type (Form Fragment) uses reference pattern for field group reuse. |

---

## 5. Architecture — AEM 6.4 vs EDS

| Aspect | AEM 6.4 | EDS (xWalk) |
|---|---|---|
| Container | Form Container component (parsys) | `form-container` block |
| Children | Separate AEM components per field type | Child item types inside form-container |
| Rendering | Server-side (HTL + Sling Models) | Client-side (`form-container.js`) |
| Authoring UI | Coral dialogs (dynamic — servlets for dropdowns) | Universal Editor models (static JSON) |
| Configuration | `cq:dialog` per component | `component-models.json`, `component-definition.json`, `component-filters.json` |
| Code location | Multiple OSGi bundles | Single `blocks/form-container/` folder |
| Submission | Server-side to middleware | Client-side to middleware (same middleware, different caller) |

### 5.1 Static Model Limitation

UE models are static JSON — unlike Coral dialogs which could call servlets for dynamic dropdowns and show/hide fields conditionally. For dynamic authoring needs (e.g., GCMS form ID lookup, Eloqua form ID lists), an **App Builder UI Extension** will be required. Authoring remains no-code for authors; dynamic configuration UIs are provided via the UI Extension, not via Coral dialogs.

---

## 6. Multi-Step Wizard Support

The Form Container supports multi-step wizard forms natively:

| Aspect | How It Works |
|---|---|
| **Step assignment** | Each child item has a `meta_step` field (Step 1–5). Author assigns fields to steps. |
| **Step titles** | Configured on Form Config: `config_steptitles` (comma-separated, e.g., "Contact Info, Preferences, Review") |
| **Step rendering** | `form-container.js` groups fields by step, shows only current step, hides others |
| **Navigation** | Container renders Back/Next/Submit buttons. Next on intermediate steps, Submit on final. |
| **Validation** | On Next: validates current step fields only. On Submit: validates all steps. Back: no validation. |
| **Single-page forms** | Leave `config_steptitles` empty and `meta_step` as "—" on all fields |

---

## 7. What Will Be Refined During Implementation

The following aspects are dependent on the client's existing form configurations and middleware contracts. They will be defined during the implementation phase:

| Aspect | Why Deferred |
|---|---|
| Additional validation rules (regex patterns, min/max length, custom validators) | Requires audit of existing form field configurations across the site |
| Prefill sources and data mapping | Depends on middleware API contract and user profile data structure |
| Conditional show/hide rules between fields | Business-specific — varies per form |
| Dynamic dropdown options (from GCMS, Eloqua, Content Fragments) | Requires App Builder UI Extension — implementation depends on API availability |
| reCAPTCHA integration specifics | Depends on client's reCAPTCHA provider, keys, and verification flow |
| File upload middleware contract (endpoint, auth, size limits) | Existing middleware — implementation maps to it |
| Additional hidden field value sources (ERP info, catalog IDs) | Client-specific context — varies per form |
| Additional action types beyond those listed | May be discovered during form audit |
| Form-specific CSS layout refinements | Implementation-level styling |
