# Form Container Block — EDS Solution Design

## 1. Overview

This document provides a comprehensive design for the **form-container block** in AEM Edge Delivery Services (EDS) with Universal Editor (xWalk). It covers the block architecture, all child components, authoring experience, runtime behavior, cross-cutting features, effort estimates, and known limitations.

The form-container block replaces the AEM 6.4/6.5 Form Container component and its child components (Text Input, Dropdown, Hidden, File Upload, Captcha, etc.) with a single EDS block that supports draggable child components in Universal Editor.

---

## 2. Architecture: AEM 6.4 vs EDS

### 2.1 AEM 6.4/6.5 (Current State)

```
Form Container Component (parsys container)
├── Text Input Component         (separate OSGi component with dialog)
├── Dropdown Component           (separate OSGi component with dialog)
├── Hidden Field Component       (separate OSGi component with dialog)
├── File Upload Component        (separate OSGi component with dialog)
├── Captcha Component            (separate OSGi component with dialog)
├── Submit Button Component      (separate OSGi component with dialog)
└── ...
```

- Each field type is an **independent AEM component** with its own Sling Model, dialog, and rendering logic.
- Server-side rendering via HTL + Sling Models.
- Dialogs are dynamic (Coral UI) — can fetch data, show/hide fields conditionally at dialog-open time.

### 2.2 EDS with Universal Editor (Target State)

```
form-container Block (/blocks/form-container/)
├── form-config        (child component — form-level settings)
├── form-input         (child component — text, email, tel, number, date)
├── form-options       (child component — select, radio, checkbox)
├── form-textarea      (child component — multi-line text)
├── form-hidden        (child component — hidden value carriers)
├── form-upload        (child component — file attachment)
├── form-button        (child component — submit, reset)
├── form-label         (child component — rich text / instructional content)
└── form-fragment      (child component — reusable field groups from another page)
```

- **One EDS block** (`form-container`) with multiple **child components**.
- All child components are defined in `component-models.json` (dialog properties) and `component-definition.json` (UE component palette).
- `component-filters.json` defines which child components can be dropped inside form-container.
- All rendering is **client-side** via `form-container.js` — no server-side rendering.
- Dialogs are **static** (`component-models.json` is static JSON) — no dynamic behavior at dialog time.

### 2.3 Key Structural Difference

| Aspect | AEM 6.4/6.5 | EDS (xWalk) |
|--------|-------------|-------------|
| Container | Form Container component (parsys) | `form-container` block |
| Child fields | Independent AEM components | Child components within form-container |
| Rendering | Server-side (HTL + Sling Model) | Client-side (form-container.js) |
| Dialog | Dynamic Coral UI | Static component-models.json |
| Code location | Multiple OSGi bundles | Single `/blocks/form-container/` folder |
| Configuration files | `cq:dialog` per component | `component-models.json`, `component-definition.json`, `component-filters.json` |

---

## 3. Authoring Experience in Universal Editor

### 3.1 How Authors Build a Form

1. Author adds a **Section** to the page.
2. Inside the section, author drops a **Form Container** block from the component palette.
3. Inside the Form Container, author drops child components:
   - **Form Config** — sets action type, form ID, redirect, thank-you message, wizard step titles.
   - **Input Field** — text, email, phone, number, date fields.
   - **Options Field** — dropdown, radio buttons, checkboxes.
   - **Text Area** — multi-line text input.
   - **Hidden Field** — hidden value carriers (static, URL parameter, cookie, timestamp).
   - **Upload Field** — file attachment.
   - **Form Button** — submit or reset.
   - **Form Label** — static rich text content (instructions, disclaimers).
   - **Form Fragment** — reference to another page containing reusable form fields.
4. Each child component has its own **properties panel** in UE with field-specific settings.

### 3.2 Allowed Components (Filter)

The `component-filters.json` defines what can be dropped inside form-container:

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

Authors can ONLY drop these components inside a form-container. No other blocks or default content components are allowed.

### 3.3 Static Model Limitation

In AEM 6.4/6.5, component dialogs were **dynamic** — they could fetch data from APIs, show/hide fields conditionally, and populate dropdowns at dialog-open time.

In EDS with Universal Editor, the component model (`component-models.json`) is **static JSON**. There is no runtime behavior in the authoring dialog.

| Dialog Behavior | AEM 6.4/6.5 | EDS (UE) | Solution in EDS |
|----------------|-------------|----------|-----------------|
| Static fields (text, checkbox, select with fixed options) | Coral UI dialog | `component-models.json` | Works natively |
| Dropdown with fixed options | Coral UI `<coral-select>` | `component: "select"` with static options | Works natively |
| **Dynamic dropdown** (e.g., fetch GCMS form IDs from API) | Coral UI + custom `datasource` servlet | **Not possible** with static model | **App Builder UI Extension** required |
| **Conditional dialog fields** (show field B when field A = X) | `granite:data` + Coral UI `showhide` | `condition` property in model (limited) | Works for simple cases; complex logic needs **UI Extension** |
| Content browser / path picker | Coral PathBrowser | `component: "aem-content"` | Works natively |
| Asset picker | Coral Asset Finder | `component: "aem-asset"` | Works natively |

**Items requiring App Builder UI Extension:**

| # | Dynamic Dialog Feature | Current AEM Behavior | Why UI Extension Is Needed |
|---|----------------------|---------------------|---------------------------|
| 1 | GCMS Form ID fetch | Dialog dropdown calls servlet to list available GCMS form IDs | UE model is static; cannot fetch API data at dialog time |
| 2 | Eloqua Form ID lookup | Dialog fetches Eloqua forms list via API | Same — static model cannot make API calls |
| 3 | Dynamic validation rules preview | Dialog shows preview of rules from linked spreadsheet | Optional — for author convenience only |

---

## 4. Block & Component Inventory

### 4.1 Form Container Block

| Property | Value |
|----------|-------|
| **Block name** | `form-container` |
| **Block folder** | `/blocks/form-container/` |
| **Files** | `form-container.js`, `form-container.css` |
| **Type** | Container block (accepts child components) |
| **UE resource type** | `core/franklin/components/block/v1/block` |
| **Filter** | `form-container` (restricts allowed children) |

**Responsibilities:**
- Reads all child component rows and delegates to field creator functions.
- Assembles `<form>` element with 12-column grid layout.
- Handles form submission (payload assembly, POST to backend, redirect/thank-you).
- Orchestrates multi-step wizard (progress bar, step navigation, per-step validation).
- Integrates cross-cutting features (rules engine, dropdown population, reCAPTCHA, prefill).

### 4.2 Child Components

#### 4.2.1 Form Config

| Property | Value |
|----------|-------|
| **Component ID** | `form-config` |
| **Model ID** | `form-config` |
| **UE title** | "Form Config" |
| **Purpose** | Form-level configuration — action type, form ID, redirect, thank-you, wizard step titles |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `config` |
| `field_action` | Action Type | select | ELOQUA, GCMS, LSG, NONLSG, MARKETO, EMAIL, CORA, API |
| `config_formid` | Form ID | text | Unique identifier for this form |
| `config_redirect` | Redirect URL | text | Page to redirect after successful submission |
| `config_thankyou` | Thank You Message | text | Message shown on success (if no redirect) |
| `config_steptitles` | Wizard Step Titles | text | Comma-separated titles (e.g., "Contact Info, Preferences, Review") |

**AEM 6.4 equivalent:** Form Container dialog fields (action URL, form ID, success/error handling).

---

#### 4.2.2 Input Field

| Property | Value |
|----------|-------|
| **Component ID** | `form-input` |
| **Model ID** | `form-input` |
| **UE title** | "Input Field" |
| **Purpose** | Single-line text-based input (text, email, phone, number, date) |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `input` |
| `field_name` | Field Name | text | Name used in form submission data |
| `field_label` | Label | text | Visible label for the field |
| `config_type` | Input Type | select | Text, Email, Phone (tel), Number, Date |
| `config_placeholder` | Placeholder | text | Placeholder text inside the field |
| `validation_required` | Required | select | Yes / No |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | Full Width (12), Half (6), One Third (4), Two Thirds (8), One Quarter (3) |

**Runtime rendering:** Creates `<input>` with appropriate `type`, `name`, `placeholder`, `required` attributes inside a `.field-wrapper.input-wrapper`.

**AEM 6.4 equivalent:** Text Input component, Email component, Phone component, Number component, Date Picker component.

---

#### 4.2.3 Options Field

| Property | Value |
|----------|-------|
| **Component ID** | `form-options` |
| **Model ID** | `form-options` |
| **UE title** | "Options Field" |
| **Purpose** | Selection fields — dropdown (select), radio buttons, or checkboxes |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `options` |
| `field_name` | Field Name | text | Name used in form submission |
| `field_label` | Label | text | Visible label |
| `config_display` | Display Type | select | Dropdown, Radio Buttons, Checkboxes |
| `config_options` | Options | text | Comma-separated list OR path to spreadsheet |
| `config_placeholder` | Placeholder | text | Placeholder for dropdown (e.g., "Please Select") |
| `validation_required` | Required | select | Yes / No |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | Full Width (12), Half (6), etc. |

**Runtime rendering:**
- **Dropdown:** `<select>` with `<option>` elements.
- **Radio:** `<div role="radiogroup">` with radio inputs.
- **Checkboxes:** `<div role="group">` with checkbox inputs.

**Options population:**
- If value starts with `/` → treated as path to spreadsheet → fetches `.json` endpoint.
- Otherwise → parsed as inline comma-separated values.

**AEM 6.4 equivalent:** Dropdown/Select component, Radio Button Group component, Checkbox Group component.

See: [Forms Dropdown Options Design](./FORMS_DROPDOWN_OPTIONS_EDS_SOLUTION_DESIGN.md)

---

#### 4.2.4 Text Area

| Property | Value |
|----------|-------|
| **Component ID** | `form-textarea` |
| **Model ID** | `form-textarea` |
| **UE title** | "Text Area" |
| **Purpose** | Multi-line text input |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `textarea` |
| `field_name` | Field Name | text | Name used in form submission |
| `field_label` | Label | text | Visible label |
| `config_placeholder` | Placeholder | text | Placeholder text |
| `validation_required` | Required | select | Yes / No |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | Full Width (12), Half (6), etc. |

**Runtime rendering:** Creates `<textarea>` with `min-height: 120px` and `resize: vertical`.

**AEM 6.4 equivalent:** Text Area component.

---

#### 4.2.5 Hidden Field

| Property | Value |
|----------|-------|
| **Component ID** | `form-hidden` |
| **Model ID** | `form-hidden` |
| **UE title** | "Hidden Field" |
| **Purpose** | Invisible value carriers for tracking, context, or integration parameters |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `hidden` |
| `field_name` | Field Name | text | Name used in form submission |
| `config_value` | Value | text | Static value or parameter name (depends on source) |
| `config_source` | Value Source | select | Static Value, URL Parameter, Cookie, Timestamp |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | (not visually relevant but included for consistency) |

**Runtime value resolution:**

| Source | Behavior |
|--------|----------|
| `static` | Uses the authored value directly |
| `query` | Reads value from URL query parameter matching the authored key |
| `cookie` | Reads value from browser cookie matching the authored key |
| `timestamp` | Sets value to current ISO 8601 timestamp at render time |

**AEM 6.4 equivalent:** Hidden Field component.

---

#### 4.2.6 Upload Field

| Property | Value |
|----------|-------|
| **Component ID** | `form-upload` |
| **Model ID** | `form-upload` |
| **UE title** | "Upload Field" |
| **Purpose** | File attachment input with styled button |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `upload` |
| `field_name` | Field Name | text | Name used in form submission |
| `field_label` | Label | text | Visible label |
| `config_label` | Button Text | text | Text on the file selection button (default: "Choose File") |
| `validation_required` | Required | select | Yes / No |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | Full Width (12), Half (6), etc. |

**Runtime rendering:** Native `<input type="file">` is visually hidden; a styled `<span class="upload-btn">` acts as the visible trigger. A `<span class="upload-filename">` displays "No file chosen" or the selected filename.

**AEM 6.4 equivalent:** File Upload / Attachment component.

---

#### 4.2.7 Form Button

| Property | Value |
|----------|-------|
| **Component ID** | `form-button` |
| **Model ID** | `form-button` |
| **UE title** | "Form Button" |
| **Purpose** | Submit or reset button |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `button` |
| `field_label` | Button Label | text | Text on the button (default: "Submit") |
| `config_role` | Button Type | select | Submit, Reset |

**Note:** In wizard/multi-step mode, the authored submit button is removed and replaced by auto-generated Back/Next/Submit navigation buttons.

**AEM 6.4 equivalent:** Submit Button component, Reset Button component.

---

#### 4.2.8 Form Label

| Property | Value |
|----------|-------|
| **Component ID** | `form-label` |
| **Model ID** | `form-label` |
| **UE title** | "Form Label" |
| **Purpose** | Static rich text content within the form (instructions, disclaimers, legal text) |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `label` |
| `content_text` | Text | richtext | Rich text content (bold, links, lists) |
| `meta_step` | Wizard Step | select | — (none), Step 1–5 |
| `meta_span` | Column Span | select | Full Width (12), Half (6), etc. |

**Runtime rendering:** Renders the rich text content inside a `.label-wrapper` div. No form field behavior — purely informational.

**AEM 6.4 equivalent:** Static Text / Rich Text component within forms.

---

#### 4.2.9 Form Fragment

| Property | Value |
|----------|-------|
| **Component ID** | `form-fragment` |
| **Model ID** | `form-fragment` |
| **UE title** | "Form Fragment" |
| **Purpose** | Reusable field groups — references another page containing a form-container with shared fields |

**Authored Properties:**

| Property | Label | Type | Options / Description |
|----------|-------|------|----------------------|
| `field_type` | Component | text (hidden) | Always `fragment` |
| `field_path` | Fragment Page | aem-content | Path to a page containing a form-container block |
| `meta_step` | Wizard Step | select | Override step for all fragment fields |
| `meta_span` | Column Span | select | Not used — individual fragment fields define their own spans |

**Runtime behavior:**
1. Fetches the referenced page (`.plain.html` first, fallback to `.html`).
2. Finds the `form-container` block in the fetched HTML.
3. Extracts field rows (excluding config and button rows).
4. Renders extracted fields inside a `.form-fragment-anchor` wrapper.
5. Fragment fields inherit the fragment's step assignment if the individual field has no step.

**Use case:** Common field groups (e.g., address block: street, city, state, zip, country) can be authored once and reused across multiple forms.

**AEM 6.4 equivalent:** Experience Fragment reference within forms, or form field groups.

---

## 5. Multi-Step Wizard

### 5.1 How It Works

When fields have **Wizard Step** assignments (Step 1, 2, 3...), the form automatically enables wizard mode:

1. Fields are grouped into `<fieldset>` elements by step number.
2. A **progress bar** with numbered step indicators and titles is rendered.
3. **Back / Next / Submit** navigation buttons replace the authored submit button.
4. Only the current step's fieldset is visible; others are hidden.
5. **Per-step validation** runs on "Next" click — user cannot advance with invalid fields.
6. "Back" navigation does **not** trigger validation.
7. On final "Submit", **all steps are re-validated** before submission.

### 5.2 Wizard Configuration

| Configuration | Where Authored | Example |
|--------------|---------------|---------|
| Step assignment per field | Each field's "Wizard Step" property | Step 1, Step 2, etc. |
| Step titles | Form Config's "Wizard Step Titles" property | "Contact Info, Preferences, Review" |

### 5.3 Progress Bar

```
  ●─────────○─────────○
Step 1    Step 2    Step 3
Contact   Prefs     Review
```

- Completed steps show as filled/active dots.
- Current step shows as highlighted.
- Previous steps are clickable for backward navigation.
- Future steps are not clickable (must advance via "Next").

---

## 6. Cross-Cutting Features

These features span the entire form system and are not specific to any single child component.

### 6.1 Conditional Rules Engine

Spreadsheet-driven conditional visibility, required state, disabled state, and value setting based on field values at runtime.

| Aspect | Detail |
|--------|--------|
| **Configuration** | Dedicated rules spreadsheet per form |
| **Operators** | 9 operators: equal, notEqual, lessThan, lessThanEqualTo, greaterThan, greaterThanEqualTo, startsWith, endsWith, contains |
| **Logical operators** | ALL (AND) / ANY (OR) via Rule ID grouping |
| **Actions** | show, hide, required, disable, setValue |
| **Performance** | Dependency map — only affected fields re-evaluated on change |

See: [Forms Rule Editor Design](./FORMS_RULE_EDITOR_EDS_SOLUTION_DESIGN.md)

### 6.2 Dropdown Options (Spreadsheet-Driven)

Dropdown fields can be populated from EDS spreadsheets (edge-cached JSON) instead of inline options.

| Aspect | Detail |
|--------|--------|
| **Data source** | Spreadsheet path (e.g., `/content/data/lists/countries`) |
| **Cascading** | Parent-child filtering (e.g., country → state) via `Parent` column |
| **Multi-language** | Language columns in spreadsheet or separate sheets per locale |
| **Performance** | CDN edge-cached (5-20ms), client-side filtering for cascading |

See: [Forms Dropdown Options Design](./FORMS_DROPDOWN_OPTIONS_EDS_SOLUTION_DESIGN.md)

### 6.3 Google reCAPTCHA v2 Invisible

Bot protection with regional disable and fallback domain routing.

| Aspect | Detail |
|--------|--------|
| **Type** | Invisible v2 (no user interaction) |
| **Configuration** | Centralized spreadsheet at `/content/config/recaptcha` |
| **Regional disable** | GDPR/privacy compliance — no script loaded for EU/DE/FR/RU paths |
| **Fallback domain** | `recaptcha.net` for CN/HK/TW (where google.com is blocked) |
| **Per-form opt-out** | `form-disable` rows in config spreadsheet |
| **Multi-step** | Executes only on final submit, not on each step |
| **Verification** | Server-side via middleware (preferred) or App Builder |

### 6.4 User Profile Prefill

Automatic field population for logged-in users via a backend User Details API.

| Aspect | Detail |
|--------|--------|
| **Trigger** | User session object (`window._lt`) becomes available |
| **API call** | Backend User Details endpoint (existing servlet or App Builder) |
| **Mapping** | Field name/id-based — global mapping in shared JS module |
| **Dirty field protection** | Fields already touched by user are NOT overwritten |
| **Multi-step** | All fields (including hidden steps) are prefilled at once |
| **Resilience** | Best-effort — form remains usable if prefill API fails |

### 6.5 Validation

Client-side validation before submission, including per-step validation for wizard forms.

| Aspect | Detail |
|--------|--------|
| **Approach** | HTML5 native + lightweight custom JS (no jQuery) |
| **HTML5 validators** | required, type="email", pattern, min, max, minlength, maxlength |
| **Custom validators** | Phone format, email domain, cross-field checks via custom JS |
| **Step forms** | Validation per step on "Next"; all steps re-validated on final submit |
| **Rule engine integration** | Fields hidden by rules engine are excluded from validation |
| **Backward navigation** | "Back" does not trigger validation |
| **Backend** | Middleware remains final authority for server-side validation |

### 6.6 Layout Grid

12-column CSS grid layout for flexible field arrangement.

| Span Value | Width | Use Case |
|-----------|-------|----------|
| 12 | Full width | Default — textarea, single fields |
| 6 | Half width | Side-by-side fields (first name + last name) |
| 4 | One third | Three-column layouts |
| 8 | Two thirds | Wide field next to a narrow one |
| 3 | One quarter | Four-column layouts |

Mobile (< 600px): All fields collapse to full width regardless of span setting.

---

## 7. Runtime Flow

### 7.1 Page Load

```
Browser loads page
  │
  ├─ EDS serves static HTML (no user data, no form logic)
  │
  └─ form-container.js decorate() executes:
      │
      ├─ 1. Extract form config (action, formid, redirect, thankyou, steptitles)
      │
      ├─ 2. Create <form> element with 12-column grid
      │
      ├─ 3. Process each child row:
      │     ├─ Read field type from first cell
      │     ├─ Delegate to appropriate creator function
      │     ├─ Apply step assignment (data-step)
      │     ├─ Apply column span (--field-span CSS variable)
      │     └─ Append to form
      │
      ├─ 4. Process fragment rows:
      │     ├─ Fetch fragment page (.plain.html)
      │     ├─ Extract field rows from fragment's form-container
      │     └─ Render inside .form-fragment-anchor wrapper
      │
      ├─ 5. If multi-step → setup wizard (progress bar, navigation)
      │
      ├─ 6. Fetch & initialize rules engine (if rulesSheet configured)
      │
      ├─ 7. Populate spreadsheet-driven dropdowns
      │
      ├─ 8. Initialize reCAPTCHA (if region not disabled)
      │
      └─ 9. Attach submit handler
```

### 7.2 Form Submission

```
User clicks Submit (or Next → ... → Submit in wizard mode)
  │
  ├─ 1. Run client-side validation (all fields / current step)
  │     └─ If invalid → show errors, focus first invalid field, STOP
  │
  ├─ 2. Check reCAPTCHA
  │     ├─ If disabled (region/form) → skip
  │     └─ If active → grecaptcha.execute() → get token
  │
  ├─ 3. Assemble payload
  │     ├─ Collect all field values (generatePayload)
  │     ├─ Add reCAPTCHA token if present
  │     └─ Include formId and action type
  │
  ├─ 4. POST to backend endpoint
  │     └─ { data: {...fields}, action: "ELOQUA", formId: "..." }
  │
  └─ 5. Handle response
        ├─ Success + redirect → window.location.href = redirectURL
        ├─ Success + thank-you → replace form with success message
        └─ Failure → log error, re-enable submit button
```

---

## 8. Effort Estimates

### 8.1 Block & Components

| # | Item | Type | Description | Capabilities to Match | Effort |
|---|------|------|-------------|----------------------|--------|
| 1 | **form-container** | Block | Parent container. Form assembly, submission, wizard, 12-col grid. | Form action/endpoint config, payload assembly, POST submission, redirect/thank-you, multi-step wizard with progress bar | L |
| 2 | **form-config** | Component | Form-level settings. | Action type selection, form ID, redirect URL, thank-you message, wizard step titles | S |
| 3 | **form-input** | Component | Text-based input fields. | Text/email/tel/number/date types, placeholder, required, prefill support, layout span | M |
| 4 | **form-options** | Component | Selection fields. | Dropdown/radio/checkbox, inline options, spreadsheet-driven options, cascading parent-child, placeholder, required | L |
| 5 | **form-textarea** | Component | Multi-line text input. | Placeholder, required, resizable, layout span | S |
| 6 | **form-hidden** | Component | Hidden value carriers. | Static value, URL parameter, cookie, timestamp sources | S |
| 7 | **form-upload** | Component | File attachment. | File type restriction, file size validation, styled button, filename display | M |
| 8 | **form-button** | Component | Submit/reset buttons. | Submit, reset, disabled state during submission | S |
| 9 | **form-label** | Component | Rich text content. | Bold, links, lists, no form behavior, layout span | S |
| 10 | **form-fragment** | Component | Reusable field groups. | Fetch fields from external page, step inheritance, field reuse across forms | M |

### 8.2 Cross-Cutting Features

| # | Feature | Description | Effort |
|---|---------|-------------|--------|
| 11 | **Rules Engine** | Spreadsheet-driven conditional show/hide/enable/disable/setValue | L |
| 12 | **Dropdown Population** | Spreadsheet-driven options with CDN caching | M |
| 13 | **Cascading Dropdowns** | Parent-child filtering (country → state) | M |
| 14 | **reCAPTCHA Integration** | Regional disable, fallback domain, centralized config | M |
| 15 | **User Profile Prefill** | Backend API call, field-name mapping, dirty field protection | M |
| 16 | **Validation Framework** | HTML5 native + custom validators, per-step validation, inline errors | M |
| 17 | **Multi-step Wizard** | Step grouping, progress bar, navigation, per-step validation | M |
| 18 | **Layout Grid** | 12-column CSS grid with responsive collapse | S |

### 8.3 App Builder UI Extensions (for Dynamic Dialogs)

| # | Extension | Purpose | Effort |
|---|-----------|---------|--------|
| 19 | **GCMS Form ID Picker** | Fetch GCMS form IDs from API and display in UE properties panel | M |
| 20 | **Eloqua Form ID Picker** | Fetch Eloqua form IDs from API and display in UE properties panel | M |

**Effort key:** S = 1-2 days, M = 3-5 days, L = 5-10 days

### 8.4 Total Effort Summary

| Category | Items | Estimated Effort |
|----------|-------|-----------------|
| Block + Components (#1–#10) | 10 items | ~25-35 days |
| Cross-Cutting Features (#11–#18) | 8 items | ~25-35 days |
| App Builder UI Extensions (#19–#20) | 2 items | ~6-10 days |
| **Total** | **20 items** | **~56-80 days** |

Note: Some components and features have been partially implemented. Effort estimates assume building from scratch with full parity to existing AEM 6.4/6.5 capabilities.

---

## 9. Configuration Files Summary

### 9.1 component-models.json

Defines the **properties panel** (dialog) for each child component. Each model specifies the fields that appear when an author selects a component in UE.

| Model ID | Fields | Purpose |
|----------|--------|---------|
| `form-config` | field_type, field_action, config_formid, config_redirect, config_thankyou, config_steptitles | Form-level settings |
| `form-input` | field_type, field_name, field_label, config_type, config_placeholder, validation_required, meta_step, meta_span | Input field properties |
| `form-options` | field_type, field_name, field_label, config_display, config_options, config_placeholder, validation_required, meta_step, meta_span | Options field properties |
| `form-textarea` | field_type, field_name, field_label, config_placeholder, validation_required, meta_step, meta_span | Textarea properties |
| `form-hidden` | field_type, field_name, config_value, config_source, meta_step, meta_span | Hidden field properties |
| `form-upload` | field_type, field_name, field_label, config_label, validation_required, meta_step, meta_span | Upload field properties |
| `form-button` | field_type, field_label, config_role | Button properties |
| `form-label` | field_type, content_text, meta_step, meta_span | Label/rich text properties |
| `form-fragment` | field_type, field_path, meta_step, meta_span | Fragment reference properties |

### 9.2 component-definition.json

Defines the **component palette** in UE — what authors see when adding components.

| Component | UE Title | Resource Type | Template Defaults |
|-----------|----------|--------------|-------------------|
| `form-container` | Form Container | `block/v1/block` | name: "Form Container", filter: "form-container" |
| `form-config` | Form Config | `block/v1/block/item` | field_type: "config", field_action: "ELOQUA" |
| `form-input` | Input Field | `block/v1/block/item` | field_type: "input", config_type: "text", meta_span: "12" |
| `form-options` | Options Field | `block/v1/block/item` | field_type: "options", config_display: "select" |
| `form-textarea` | Text Area | `block/v1/block/item` | field_type: "textarea" |
| `form-hidden` | Hidden Field | `block/v1/block/item` | field_type: "hidden", config_source: "static" |
| `form-upload` | Upload Field | `block/v1/block/item` | field_type: "upload", config_label: "Choose File" |
| `form-button` | Form Button | `block/v1/block/item` | field_type: "button", field_label: "Submit" |
| `form-label` | Form Label | `block/v1/block/item` | field_type: "label" |
| `form-fragment` | Form Fragment | `block/v1/block/item` | field_type: "fragment" |

### 9.3 component-filters.json

Defines **containment rules** — what can be dropped where.

```
form-container accepts:
  form-config, form-input, form-options, form-textarea,
  form-hidden, form-upload, form-button, form-label, form-fragment
```

---

## 10. External Configuration (Spreadsheets)

| Configuration | Spreadsheet Path | Purpose |
|--------------|-----------------|---------|
| Dropdown options | `/content/data/lists/{list-name}` | Dropdown data source (countries, states, org types, etc.) |
| Form rules | `/content/forms/rules/{form-name}` | Conditional visibility rules per form |
| reCAPTCHA config | `/content/config/recaptcha` | Global reCAPTCHA settings, regional rules, per-form overrides |

---

## 11. Related Design Documents

| Document | Covers |
|----------|--------|
| [Forms Dropdown Options Design](./FORMS_DROPDOWN_OPTIONS_EDS_SOLUTION_DESIGN.md) | Spreadsheet-driven dropdown population, cascading, multi-language, data migration |
| [Forms Rule Editor Design](./FORMS_RULE_EDITOR_EDS_SOLUTION_DESIGN.md) | Conditional rules engine, spreadsheet structure, operators, runtime flow, reference implementation |
| [Forms Brightcove Video Design](./FORMS_BRIGHTCOVE_VIDEO_EDS_SOLUTION_DESIGN.md) | Brightcove video player integration in EDS |
| [JSON-LD Schema Design](./JSON_LD_SCHEMA_EDS_SOLUTION_DESIGN.md) | Schema.org structured data approaches for EDS pages |

---

## 12. Comparison: AEM 6.4 vs EDS Form System

| Aspect | AEM 6.4/6.5 | EDS (xWalk) |
|--------|-------------|-------------|
| **Architecture** | Multiple OSGi components, each with HTL + Sling Model | Single block with child components, all rendered by form-container.js |
| **Rendering** | Server-side (HTL) | Client-side (JavaScript) |
| **Dialog** | Dynamic Coral UI (can fetch API data, show/hide fields) | Static component-models.json (no runtime dialog behavior) |
| **Dynamic dialogs** | Built-in via Coral UI datasources | Requires App Builder UI Extension |
| **Rules engine** | Server-side CSS injection + client-side JS, visual builder | Client-side only, spreadsheet-authored |
| **Dropdowns** | Content Fragments via Sling Model | Spreadsheets via CDN-cached JSON |
| **Cascading** | AJAX to AEM servlet per change | Client-side filter of cached data (faster) |
| **reCAPTCHA** | AEM Cloud Service config + servlet verification | Centralized spreadsheet config + middleware verification |
| **Prefill** | AEM servlet + client-side AJAX | Same pattern — backend API + client-side mapping |
| **Validation** | jQuery Validate plugin | HTML5 native + lightweight custom JS |
| **Multi-step** | Custom client-side wizard JS | Built into form-container.js |
| **Layout** | AEM responsive grid (6/12 columns) | CSS 12-column grid with --field-span variable |
| **Fragments** | Experience Fragments | Form Fragment component (fetches page content) |
| **Performance** | Server-rendered HTML, dispatcher-cached | CDN edge-cached, client-side rendering |
| **Scalability** | Depends on AEM publish capacity | CDN handles load, zero AEM publish dependency at runtime |
