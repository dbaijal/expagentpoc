# Form Authoring in EDS (AEM as Authoring Source)

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services (Universal Editor / xWalk)

> **Companion to** the *Forms — Current State & EDS Migration Overview* foundation page. This page defines **how forms are authored** in the target model: the Form Container block and its child field items, the current authoring dialogs and their properties, the **authoring-time integration calls** made from those dialogs, and how those are provided in EDS. Rule handling (show/hide) is documented on its own page.
>
> *Collapsible sections use `<details>` blocks. When pasting into Confluence, replace each with an **Expand macro** (`/expand`).*

---

## 1. Overview

This document defines the **Form Container block** for AEM Edge Delivery Services with the Universal Editor (xWalk). It replaces the AEM 6.4 Form Container component and its child form components (Input, Options, Hidden, Upload, Button, Recaptcha, XF Inclusion, etc.) with a **single EDS block** that owns:

- Form layout and structure,
- Authoring of child form fields,
- Client-side validation, prefill, rules, and multi-step logic,
- Integration with the existing **TFS middleware** for submission and file upload.

Because TFS does **not use or license Adobe Adaptive Forms**, this custom block model — authored in the Universal Editor — is the mechanism used to preserve existing business behaviour and authoring intent while aligning with the EDS block model and client-side rendering.

---

## 2. Architecture — AEM 6.4 vs EDS (xWalk)

### 2.1 Current State (AEM 6.4)

Forms are built using a suite of core form components under the **`formcommons/components/form`** namespace. All configuration is stored as **JCR node properties**. The Form Container and each field component have their own **touch-UI dialog**, with tabs for **Properties, Constraints, and Accessibility**.

| Component | Resource Type |
|---|---|
| Form Container | `formcommons/components/form/container/v1/container` |
| Form Input | `formcommons/components/form/input` |
| Form Options | `formcommons/components/form/options` |
| Form Button | `formcommons/components/form/button` |
| Form Hidden | `formcommons/components/form/hidden` |
| Form Upload | `formcommons/components/form/upload` |
| Form Textarea | `formcommons/components/form/textarea` |
| Form Recaptcha | `formcommons/components/form/recaptcha` |
| Form XF Inclusion | `formcommons/components/form/xf-inclusion` |

- The **Form Container** is a parsys-style container; each field type is a **separate AEM component**.
- Each component has: its own **`cq:dialog`** (Coral UI), its own **Sling Model + HTL** rendering, and **optional dynamic dialog behaviour** (datasource servlets, conditional show/hide, and **live backend calls at authoring time** — see §4.3).
- **Conditional field visibility** is configured via a dedicated **Rule Editor toolbar button on each field component**, which opens a visual interface for building show/hide rules (documented on the Rule Handling page).
- Rendering is **server-side** (HTL + Sling Models).

### 2.2 Target State (EDS + Universal Editor)

- A single **`form-container` block** (`/blocks/form-container/`) acts as the parent `<form>`.
- Inside it, authors add **child items** (logical field types), each defined as a **UE component model**.
- Rendering is **client-side** via `form-container.js`, which reads the authored model, builds the `<form>`, and attaches validation, prefill, rules, multi-step, and middleware integration. **No server-side HTL/Sling** renders form fields in EDS.

### 2.3 Structural difference

| Aspect | AEM 6.4 | EDS (xWalk) |
|---|---|---|
| Container | Form Container component (parsys) | `form-container` block |
| Children | Separate AEM component per field type | Child item types inside `form-container` |
| Rendering | Server-side (HTL + Sling Models) | Client-side (`form-container.js`) |
| Authoring UI | Coral dialogs (dynamic — servlets for dropdowns, conditional UI) | Universal Editor component models (static JSON) + UE extensions where dynamic behaviour is needed (§3.3, §4) |
| Configuration | `cq:dialog` per component | `component-models.json`, `component-definition.json`, `component-filters.json` |
| Code location | Multiple OSGi bundles | Single `/blocks/form-container/` folder |

---

## 3. Authoring Experience in the Universal Editor

### 3.1 How authors build a form

1. Author adds a **Section** to the page.
2. Inside the Section, drops a **Form Container** block from the palette.
3. Inside the Form Container, adds **child items** from a curated set:
   - **Form Config** — form-level settings (form ID, action type / middleware target, redirect / thank-you, wizard step titles).
   - **Input Field** — single-line inputs (text, email, phone, number, date).
   - **Options Field** — dropdown, radio buttons, checkbox groups.
   - **Text Area** — multi-line text.
   - **Hidden Field** — technical/context values (sessionId, formId, product IDs, ERP info; sourced from static / URL param / cookie / timestamp).
   - **Upload Field** — file selector (multi-file supported via middleware).
   - **Form Button** — submit / reset (wizard navigation handled centrally in multi-step forms).
   - **Form Label** — static instruction / disclaimer / grouping text.
   - **Form Fragment** — reusable field group sourced from another form page.
   - **Form reCAPTCHA** — reCAPTCHA widget.
4. Each child item has its **own UE properties panel** for label, name, type, required, step, layout span, etc. Cross-cutting behaviour (validation, prefill, rules, multi-step, submission) is managed centrally by the Form Container.

### 3.2 Allowed child items (governance)

`component-filters.json` constrains the Form Container to **only the form child item types** listed above. Generic content blocks (cards, hero, text, image) **cannot** be nested inside a Form Container. The Form Container itself is allowed in any section and on all page types.

### 3.3 Static model limitation & UE extensions

Universal Editor component models are **static JSON**. Unlike Coral dialogs, they cannot by themselves call servlets for dynamic dropdowns or show/hide dialog fields conditionally at authoring time.

- **Handled natively in the model:** static text, checkboxes, selects with fixed options, labels, names, required flags, layout, step assignment.
- **Requires a UE extension:** dynamic authoring behaviour such as fetching a **GCMS Form ID** from an API, populating **Division / Eloqua** option lists from a backend, or providing an interactive configuration UI for an action type.

Where dynamic authoring behaviour is needed, it is provided through a **Universal Editor extension developed on Adobe App Builder** — specifically using UE **custom data type renderers**, which render a custom field UI (in an iframe within the properties rail) that can call a backend and write the resulting value back into the authored field. Authoring remains **no-code for authors**; the dynamic configuration UI is delivered by the extension rather than a Coral dialog.

> **Feasibility note.** The declarative field authoring (§5, §6) is well understood and directly supported by UE component models. The **dynamic authoring behaviours** in §4 are delivered via UE extensions (custom data type renderers) on App Builder; the exact implementation and effort for each are **confirmed during a feasibility check** ahead of build. This is a normal validation step — the direction is set; the feasibility step sizes and proves the dynamic pieces.

---

## 4. Current Authoring Dialogs & Their Integration Calls

The current dialogs are richly configured, and — critically — several make **live backend calls at authoring time**. This section captures both the dialog properties (for reference) and the calls (the defining complexity).

### 4.1 Dialog properties (current)

<details>
<summary><strong>Form Container dialog</strong></summary>

- **Action Type** — multi-select; one or more of **11 action types** (see §4.2). Selecting a type reveals a conditional configuration panel for that type.
- **MQO Form** — dropdown.
- **Division** — dropdown; **populated dynamically at authoring time** (Integration 1).
- **Per-action-type configuration panels** — conditional; shown only for selected action types.
- **Regional configuration** — repeatable rows (multifield) for action types that support regional overrides.
- **Thank-You Page / success handling** — redirect or message.
- **Form ID**.

</details>

<details>
<summary><strong>Form Input dialog</strong></summary>

- **Constraint / Type** — Text, Email, Phone, Number, Date, URL.
- **Label** (required), **Name** (required).
- **Enable Conditional Routing?** — toggle.
- **Multi-value configurations** — turn on multi-value; display confirmation input field.
- **Query parameter configurations** — pre-populate value from URL query parameter.
- **Value** — default/static value.
- Constraints tab: **Required**, **Required Message**, **Min/Max Length**, **Constraint Message**, **Read Only**.
- Accessibility tab: **Aria Label**.
- **ID** — auto-generated.

</details>

<details>
<summary><strong>Form Options dialog</strong></summary>

- **Type** — Drop-down / Radio / Checkbox / Multi-select.
- **Title** — rich-text field title.
- **Name** (required).
- **Enable Option to be MQO field?** — toggle.
- **Source** — Local (authored options) or **External / Datasource**.
- **Datasource** / **Content Fragment Root Path** — when Source = Datasource, the options are read from Content Fragments.
- **Options** — authored list (Local source).
- **Hint Text** and Accessibility (**Aria Label / Aria Describedby**).

</details>

<details>
<summary><strong>Form Textarea dialog</strong></summary>

- **Label**, **Name**, **Placeholder**, **Required**.
- Constraints: min/max length, validation messages.

</details>

<details>
<summary><strong>Form Hidden dialog</strong></summary>

- **Concatenated Multiple Form Hidden** — toggle.
- **Concatenate Conditional Lead ID** — toggle.
- **Name** (required).
- **Hidden Value Type** — static, URL parameter, cookie, timestamp, **Pick from Product Data Display Object**.
- **Product Data Display Parameter Name** — when sourced from a product data object.
- **ID** — auto-generated.

</details>

<details>
<summary><strong>Form Upload dialog</strong></summary>

- **Element Name** (required), **Element Title** (label / guidance, e.g. max file size).
- **File Select Button Text**.
- **Turn on multiple file upload** — toggle.
- Constraints: allowed types / size.
- **ID** — auto-generated.

</details>

<details>
<summary><strong>Form Button dialog</strong></summary>

- **Button Label**, **Button Type** (Submit / Reset), optional **Aria Label**.

</details>

<details>
<summary><strong>Form reCAPTCHA / Form XF Inclusion dialogs</strong></summary>

- **reCAPTCHA:** site key / type.
- **XF Inclusion:** reference (path) to the reusable field group to include.

</details>

### 4.2 Action types (current)

Authors choose **one or more** of **11 action types** in the Form Container dialog. Selecting an action type reveals a **conditional configuration panel** specific to that type. Each action type drives how the submission is routed downstream by the middleware.

| Code | Action Type | Key configuration fields (current) |
|---|---|---|
| 1001 | Eloqua | `eloqua-form-name`, `eloqua-instance`, `eloqua-regions` (repeatable) |
| 1002 | GCMS | `gcms-form-id` (**Fetch** button — Integration 2), `gcms-regions` (repeatable, each with Fetch) |
| 1003 | LSG | Full spec TBD (to be confirmed with TFS) |
| 1004 | Non-LSG | `nonlsg-type` (lead / case / quote / bulk quote) |
| 1005 | Marketo | `marketo-form-id` |
| 1006 | Email | `email-template`, `email-subject`, `email-mailto` (repeatable), regional config, `email-key` (Integration 3) |
| 1007 | CORA | `cora-template`, `cora-subject`, `cora-mailto` (repeatable), regional CORA config, `email-key` (Integration 3) |
| 1009 | PDX S3 | `s3-form-id` |
| 1010 | PDX ELMS | `elms-type` |
| 1011 | FSBIO | `fsbio-template`, `fsbio-subject`, `fsbio-mailto` (repeatable), regional config, `email-key` (Integration 3) |
| 1013 | Genesys DB | Full spec TBD (to be confirmed with TFS) |

**Regional configuration** (action types 1001, 1002, 1006, 1007, 1011) is authored today via **repeatable Granite multifield** components — each storing per-region overrides (in AEM, individual JCR child nodes).

### 4.3 Authoring-time integration calls (the defining complexity)

Three live AJAX calls are made **from within the AEM dialog at authoring time**. Understanding these is essential — they are the reason this authoring experience is complex, and they are what §7 provides in the target model.

**Integration 1 — Division dropdown**
- **Trigger:** dialog open.
- **Call:** Sling Datasource → Content Fragments at `/content/dam/formcommons/cf/division`.
- **Response:** populates the Division dropdown with `{text, value}` pairs. (The MQO Form dropdown follows the same pattern.)
- **Why:** authors must pick a valid division from a centrally-managed list, not free-type it.

**Integration 2 — GCMS Form ID Fetch**
- **Trigger:** author clicks the **Fetch** button in the GCMS action panel.
- **Call:** `GET /apps/lifetech/generateFormId`.
- **Response:** XML containing a `<formId>` element; the value is written into the `gcms-form-id` field. Also used **per-region** for regional GCMS IDs in the repeatable multifield. The value remains editable after fetching (manual override allowed).
- **Why:** the GCMS Form ID is **required by the middleware** at submission and is issued by a backend system (GCMD), not entered by hand.

**Integration 3 — Email attributes persistence**
- **Trigger:** **dialog save** — the call is **blocking** (`e.preventDefault()` holds submission; the dialog does not close and the node is not saved until the call succeeds).
- **Call:** `POST /bin/servlet/tf/form/postemailattributes.json`.
- **Payload:** email configuration (template, subject, mailto list, regional config).
- **Response:** returns/confirms an **`emailResourceAllocatorKey`**.
- **Applies to:** Email (1006), CORA (1007), FSBIO (1011).
- **Note on key ownership:** in the current AEM system the `uniqueFormKey` was **pre-stored in JCR and sent to the middleware** — the key originated in AEM, and the middleware stored the key-to-email-config mapping. For EDS this ownership must be re-confirmed (see §7.1).

---

## 5. Form Container Block Design (Target)

### 5.1 Responsibilities

The `form-container` block (`/blocks/form-container/`):

- Declares and renders a single `<form>` element.
- Reads and interprets all child items to render fields, apply the **12-column layout**, and group fields into **wizard steps**.
- Implements client-side behaviour: **validation** (required, type, pattern, min/max length), **conditional rules** (show/hide — see Rule Handling page), **prefill** (profile, product/catalog context), **reCAPTCHA**, **file-upload orchestration** (browser → middleware).
- Orchestrates **submission**: session ID retrieval, payload construction, submission to the middleware.
- **Does not persist data or files** — all persistence remains in the TFS middleware.

### 5.2 UE authoring contract

| Property | Value |
|---|---|
| Resource type (parent) | `core/franklin/components/block/v1/block` |
| Resource type (child items) | `core/franklin/components/block/v1/block/item` |
| Block name | Form Container |
| Filter | `form-container` → allows the form child item types |
| Block type | Container (parent–child, multi-type children) |

Parent = container only (no content fields of its own); governs allowed child types via the filter. Children each have their own model with type-specific fields; authors add / remove / reorder freely.

### 5.3 Injected system hidden fields (runtime)

Beyond authored fields, the block injects hidden fields the middleware requires — generated programmatically, not authored: e.g. **`formSessionId`** (runtime), **`actionType`**, **web URL**, **region / country / language / division** (page metadata + config), and action-type identifiers (Eloqua site/form, GCMS form ID, `emailResourceAllocatorKey`, Marketo form ID) sourced from the Form Config values.

---

## 6. Child Item Field Models (Target)

Each child item is a logical field type with its own UE component model. Exact field definitions are refined during implementation against the current dialogs/JCR as source of truth.

<details>
<summary><strong>Form Config</strong> (must be first child)</summary>

| Field | Component | Notes |
|---|---|---|
| `field_action` | select | Action Type (see §4.2) — drives middleware routing |
| `config_formid` | text | Form ID (required) |
| `config_redirect` | text | Redirect URL on success |
| `config_thankyou` | text | Thank-you message (if no redirect) |
| `config_steptitles` | text | Comma-separated wizard step titles; empty = single-page |

</details>

<details>
<summary><strong>Input Field</strong></summary>

`field_name` (req), `field_label` (req), `config_type` (text/email/phone/number/date), `config_placeholder`, `validation_required`, plus Constraints (required message, min/max length, constraint message, read-only), Accessibility (aria-label), `meta_step`, `meta_span`.

</details>

<details>
<summary><strong>Options Field</strong></summary>

`field_name` (req), `field_label` (req), `config_display` (dropdown/radio/checkbox/multiselect), **source** (Local / External-datasource), `config_options` (Local), `config_placeholder`, `validation_required` + message, Accessibility (aria-label / aria-describedby), `meta_step`, `meta_span`.

</details>

<details>
<summary><strong>Text Area / Hidden / Upload / Button / Label / Fragment / reCAPTCHA</strong></summary>

- **Text Area:** name, label, placeholder, required, step, span.
- **Hidden:** name, `config_value`, `config_source` (static / URL param / cookie / timestamp), step, span.
- **Upload:** name, label, button text, required, step, span.
- **Button:** label, role (submit / reset).
- **Label:** richtext content, step, span.
- **Fragment:** `field_path` (aem-content picker → a page containing a `form-container`), step.
- **reCAPTCHA:** site key / size.

</details>

---

## 7. Authoring-Time Integrations in EDS (Target) & Implementation Options

The three authoring-time calls (§4.3) are provided in EDS through **UE extensions (custom data type renderers) backed by server-side actions**. The **server-side action** for each integration can be delivered two ways — **decided at implementation time**:

- **Option A — Reuse existing servlets.** Migrate/retain the existing AEM servlet integration logic on **AEM as a Cloud Service**, and have the UE extension / App Builder invoke those servlets. Reuses proven integration logic; ties to the **AEM Cloud codebase** (Cloud Manager).
- **Option B — New App Builder actions.** Build new **Adobe I/O Runtime (App Builder)** serverless actions that perform the equivalent integrations.

**Decision basis (TBD at implementation):** effort to build new APIs (Option B) vs. effort to migrate the existing servlets to Cloud and confirm their compatibility (Option A). **Where feasible, reusing existing proven integration logic is preferred**, to avoid rebuilding capability the authors already rely on. The choice is confirmed once servlet compatibility on Cloud is assessed.

Mapping of the three integrations:

| Integration | Current (AEM servlet) | Target |
|---|---|---|
| **1 — Division / MQO dropdown** | Sling datasource → Content Fragments (`/content/dam/formcommons/cf/division`) | Server-side action → AEM (e.g. GraphQL/CF) providing options to the authoring UI (Option A reuse, or Option B new action) |
| **2 — GCMS Form ID Fetch** | Servlet (`/apps/lifetech/generateFormId`) | UE extension "Fetch" control → server-side action → GCMS backend; result written to the field |
| **3 — Email attributes persistence** | Blocking `POST` (`/bin/servlet/tf/form/postemailattributes.json`) → returns `emailResourceAllocatorKey` | Server-side action; the "blocking-on-save" behaviour re-modelled as an explicit authoring step |

> **Reachability & secrets:** whichever option, authoring-time calls that need secrets or internal-network access run **server-side** (servlet on Cloud, or App Builder action) — never directly from the browser/editor. Network allowlisting and CORS must be confirmed with TFS.

### 7.1 Open decision — `emailResourceAllocatorKey` ownership

The `emailResourceAllocatorKey` (email key) links a form to its email configuration in the middleware. **Who generates it must be confirmed by TFS + Adobe** before Integration 3 can be finalized:

| | Option A — AEM/authoring side generates | Option B — Middleware generates |
|---|---|---|
| AEM equivalent? | Yes — matches current pattern (key pre-stored in JCR, sent to middleware) | No — AEM always sent an existing key |
| Behaviour | Authoring side generates the key, includes it in the POST payload, stores it on the form | Authoring side sends only email config; middleware returns the key, which is then stored on the form |
| Payload | `{actionType, emailResourceAllocatorKey, emailConfig}` | `{actionType, emailConfig}` |
| Response | Acknowledgement | Returns new key |

This decision changes the Integration 3 payload/response contract and the authoring step logic.

---

## 8. Multi-Step (Wizard) Forms

TFS forms include multi-step forms; the block supports this natively:

- **Step assignment:** each child item has a **Wizard Step** (`meta_step`, Step 1–5). Step titles are set on Form Config (`config_steptitles`).
- **Rendering:** `form-container.js` groups fields by step, shows the current step, hides others, and renders a **progress indicator** and **Back / Next / Submit** navigation.
- **Validation:** Next validates only the current step; Submit validates all steps; Back does not validate.
- **Fragments in steps:** a Form Fragment's fields are fetched and resolved into their step before rendering, and participate in validation/submission as first-class fields.
- **Single-page forms:** leave step titles empty and `meta_step` unset.

---

## 9. Complexity Areas (for planning)

- **Dynamic action-type configuration** — up to 11 action types (multi-select), each with its own conditional configuration, repeatable regional rows, and (GCMS) Fetch controls. Requires careful authoring-UI state management so only active action types are persisted and edit mode reconstructs them correctly.
- **Regional config** — five action types use `region:subregion:value` repeatable overrides; add/remove/reorder must round-trip correctly between the authoring UI and stored values.
- **Authoring-time backend calls** — Integrations 1–3, including the blocking save-time persistence (Integration 3) and the key-ownership decision (§7.1).
- **Fragments** — reusable field groups fetched at delivery; field-name uniqueness and step participation must be handled.
- **Volume** — ~5,000 forms; a **per-form triage** is required to separate forms that map cleanly to the block model from those depending on complex customizations.

---

## 10. Open Items

- **`emailResourceAllocatorKey` ownership** — AEM/authoring side vs middleware (§7.1). *Owner: TFS + Adobe.*
- **GCMS ID generation API** — exact endpoint, auth, request/response. *Owner: TFS Backend.*
- **Email middleware API contract** — endpoint, auth, payload/response schema. *Owner: TFS Backend.*
- **Network accessibility** — middleware/GCMS reachable from the chosen server-side action host; allowlisting/CORS. *Owner: TFS + Adobe Infrastructure.*
- **Action-type specs** — LSG (1003), Genesys DB (1013). *Owner: TFS.*
- **Integration Option A vs B** — servlet reuse on Cloud vs new App Builder actions (§7). *Decided at implementation.*

---

## 11. Related Pages

- Forms — Current State & EDS Migration Overview *(foundation / risk)*
- Rule handling (show/hide) design
- Submission & middleware integration (session ID, uploads, security)
- Forms migration approach & per-form triage (~5,000 forms)
- AEM Cloud Codebase & Deployment *(for servlet reuse / Option A)*
