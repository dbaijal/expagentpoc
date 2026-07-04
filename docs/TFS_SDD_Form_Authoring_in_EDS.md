# Form Authoring in EDS (AEM as Authoring Source)

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Companion to** the *Forms — Current State & EDS Migration Overview* foundation page. This page describes **how forms are authored** in the target model — the current form components and dialogs, the authoring-time integration calls, and the target authoring approach. Rule handling (show/hide) is covered on its own page.
>
> *Collapsible sections below use `<details>` blocks. When pasting into Confluence, replace each with an **Expand macro** (`/expand`).*

---

## 1. Approach

Because TFS does **not use or license Adobe Adaptive Forms**, forms in the target model are built as **custom form blocks authored in the Universal Editor (UE)**, with **UE extensions developed on Adobe App Builder** where the current dialogs provide rich or dynamic authoring behaviour. Spreadsheet-driven authoring is not used, given the volume (~5,000 forms) and the level of customization.

> **Feasibility note.** The approach above is the intended direction. Reproducing the current customized authoring behaviours through UE extensions — in particular the dynamic, authoring-time dialog behaviours described in §4 — **requires a feasibility check to confirm the exact implementation approach and effort.** This is a normal validation step ahead of build; the current server-side components and dialogs are **re-designed and re-implemented** for EDS/UE rather than lifted and shifted.

---

## 2. Current State — Form Components (AEM 6.4)

Forms today are built on **customized AEM Core Components foundation form components** (no Adaptive Forms). Each component has its own touch-UI dialog (Properties / Constraints / Accessibility) and server-side rendering (HTL + Sling Models). The component set:

| Component | Purpose |
|---|---|
| Form Container | Parent container; owns form-level config, action-type routing, submission |
| Form Input | Single-line inputs (text, email, phone, number, date) |
| Form Options | Dropdown, radio group, checkbox group |
| Form Textarea | Multi-line text |
| Form Hidden | Technical / context values |
| Form Upload | File upload |
| Form Button | Submit / reset |
| Form Recaptcha | reCAPTCHA |
| Form XF Inclusion | Reusable field groups (experience-fragment style inclusion) |

**Multi-step forms:** TFS forms include **multi-step (wizard) forms**, where fields are grouped into sequential steps with progress indication. This is an authored capability that the target model must support.

### 2.1 Dialog properties (current)

The dialogs are heavily configured. The full per-dialog property lists are captured below (collapsed for readability).

<details>
<summary><strong>Form Container dialog</strong></summary>

- **Action Type** — multi-select; one or more of 11 action types (see §3). Selecting a type reveals a conditional configuration panel for that type.
- **MQO Form** — dropdown (Yes / No).
- **Division** — dropdown; populated dynamically at authoring time (see §4, Integration 1).
- **Per-action-type configuration panels** — conditional; shown only for selected action types (see §3).
- **Regional configuration** — repeatable rows (multifield) for action types that support regional overrides.
- **Thank-You Page** — content path for post-submission redirect / message.
- **Form ID** — identifier for the form.

</details>

<details>
<summary><strong>Form Input dialog</strong></summary>

- **Constraint** — field constraint / input type (e.g. Text, Email, Phone, Number, Date).
- **Label** — field label (required).
- **Name** — submission field name (required).
- **Enable Conditional Routing?** — toggle.
- **Multi-value configurations** — turn on multi-value; display confirmation input field.
- **Query parameter configurations** — pre-populate value from URL query parameter (toggle).
- **Value** — default/static value.
- **ID** — auto-generated field ID.

</details>

<details>
<summary><strong>Form Options dialog</strong></summary>

- **Type** — Drop-down / Radio / Checkbox.
- **Title** — rich-text field title.
- **Name** — submission field name (required).
- **Enable Option to be MQO field?** — toggle.
- **Source** — option source (e.g. static list or **Datasource**).
- **Datasource** — datasource reference when Source = Datasource.
- **Content Fragment Root Path** — path to the content fragment(s) providing options.
- **Help Message** (About tab).

</details>

<details>
<summary><strong>Form Textarea dialog</strong></summary>

- **Label** — field label.
- **Name** — submission field name.
- **Placeholder** — placeholder text.
- **Required** — mandatory flag.
- **Constraints / validation messages** (Constraints tab).

</details>

<details>
<summary><strong>Form Hidden dialog</strong></summary>

- **Concatenated Multiple Form Hidden** — toggle.
- **Concatenate Conditional Lead ID** — toggle.
- **Name** — submission field name (required).
- **Hidden Value Type** — e.g. static, URL parameter, cookie, timestamp, **Pick from Product Data Display Object**.
- **Product Data Display Parameter Name** — parameter name when sourced from a product data object.
- **ID** — auto-generated field ID.

</details>

<details>
<summary><strong>Form Upload dialog</strong></summary>

- **Element Name** — submission field name (required).
- **Element Title** — label / instructional text (e.g. max file size, filename guidance).
- **File Select Button Text** — button label.
- **Turn on multiple file upload** — toggle.
- **Constraints** (allowed types / size, Constraints tab).
- **ID** — auto-generated field ID.

</details>

<details>
<summary><strong>Form Button dialog</strong></summary>

- **Button Label** — text.
- **Button Type** — Submit / Reset.

</details>

<details>
<summary><strong>Form Recaptcha dialog</strong></summary>

- reCAPTCHA configuration (site key / type).

</details>

<details>
<summary><strong>Form XF Inclusion dialog</strong></summary>

- **Fragment reference** — path to the reusable field group to include.

</details>

---

## 3. Action Types (Current)

The Form Container supports **11 action types**. Each selected action type reveals its own configuration in the dialog and determines how the submission is routed downstream (via the TFS middleware).

| Code | Action Type | Notes |
|---|---|---|
| 1001 | Eloqua | Form name, instance, regional config (repeatable) |
| 1002 | GCMS | GCMS Form ID (via **Fetch**), regional config (each with Fetch) |
| 1003 | LSG | Configuration spec to be confirmed with TFS |
| 1004 | Non-LSG | Type (lead / case / quote / bulk quote) |
| 1005 | Marketo | Marketo form ID |
| 1006 | Email | Template, subject, mailto (repeatable), regional config, email key |
| 1007 | CORA | Template, subject, mailto (repeatable), regional config, email key |
| 1009 | PDX S3 | Form ID |
| 1010 | PDX ELMS | Type |
| 1011 | FSBIO | Template, subject, mailto (repeatable), regional config, email key |
| 1013 | Genesys DB | Configuration spec to be confirmed with TFS |

**Regional configuration** (Eloqua, GCMS, Email, CORA, FSBIO) is authored today as **repeatable multifields** (per-region overrides).

---

## 4. Authoring-Time Integration Calls (Current) — Key Section

A defining characteristic of TFS forms is that the **dialogs make live backend calls at authoring time**. Understanding these is essential, because they are the hardest part of the authoring experience to reproduce. There are **three** such calls today:

### Integration 1 — Division dropdown
- **When:** dialog opens.
- **What:** a Sling datasource reads Content Fragments (division data) and **populates the Division dropdown** with option values.
- **Why:** authors must select a valid division from a centrally-managed list, not free-type it.

### Integration 2 — GCMS Form ID "Fetch"
- **When:** author clicks the **Fetch** button in the GCMS action panel (and per region for regional GCMS IDs).
- **What:** a servlet call retrieves a **GCMS Form ID** which is written into the field.
- **Why:** the GCMS Form ID is **required by the middleware** at submission; it is obtained from a backend system rather than entered manually.

### Integration 3 — Email attributes persistence
- **When:** on **dialog save** — the call is **blocking** (the dialog does not close and the node is not saved until it succeeds).
- **What:** a servlet persists the email configuration (template, subject, mailto list, regional config) and returns/confirms an **`emailResourceAllocatorKey`**.
- **Why:** the key is required to map the submission to its email configuration downstream. Applies to action types **Email (1006), CORA (1007), FSBIO (1011)**.

> These calls exist because the dialogs are not just data entry — they **fetch, validate, and persist data against backend systems at authoring time**. Reproducing this behaviour is the central design challenge for the target authoring experience.

---

## 5. Target Authoring Approach (EDS + Universal Editor)

- Forms are authored as a **custom `form-container` block** with **child field components** (input, options, textarea, hidden, upload, button, recaptcha, fragment, and form-level config), defined as **UE component models**.
- Field configuration that maps to standard, declarative inputs (label, name, required, placeholder, options, type, layout, wizard step) is handled by **UE component models** directly.
- **Multi-step forms** are supported by grouping fields into steps within the block model.
- Where the current dialogs provide **dynamic or interactive authoring behaviour** (the calls in §4, dynamic dropdowns, fetch-and-store), the target uses **UE extensions built on App Builder** to provide that behaviour and write the resulting values into the authored content.
- Rendering is **client-side**; there is no server-side HTL/Sling rendering of form fields in EDS.

> **Feasibility note (repeated for this section).** The dynamic authoring behaviours in §4 are the items that **require a feasibility check** to confirm they can be delivered as UE extensions and to size the effort. The declarative field authoring is well understood; the dynamic authoring-time behaviours are what the feasibility phase validates.

---

## 6. Authoring-Time Integrations — Implementation Options

The three authoring-time calls (§4) are today served by **existing AEM servlets**. For the target model there are **two viable options**, to be **decided at implementation time**:

- **Option A — Reuse existing servlets.** Migrate/retain the existing servlet integration logic on **AEM as a Cloud Service**, and have App Builder (or the authoring UI) **invoke those servlets**. Reuses proven integration logic.
- **Option B — New App Builder actions.** Build **new Adobe I/O Runtime (App Builder) serverless actions** that perform the equivalent integrations.

**Decision basis (TBD at implementation):** the trade-off is **effort to build new APIs (Option B)** vs. **effort to migrate the existing servlets to Cloud and confirm their compatibility (Option A)**. Where feasible, **reusing existing, proven integration logic is preferred** to avoid rebuilding capability the authors already rely on. The final choice is confirmed at implementation once servlet compatibility on Cloud is assessed.

> **Where reused servlets live:** Option A ties to the **AEM Cloud codebase** (deployed via Cloud Manager); App Builder actions (Option B) are a separate deployable. See the *AEM Cloud Codebase & Deployment* solution design.
>
> **Reachability & secrets:** whichever option is chosen, authoring-time calls that require secrets or internal-network access run **server-side** (servlet on Cloud, or App Builder action) — not directly from the browser/editor.

---

## 7. Open Items

- **`emailResourceAllocatorKey` ownership — to be confirmed with TFS.** In the current AEM system the key originated in AEM (pre-stored in JCR) and the middleware held the key-to-config mapping. For the target model, **TFS must confirm whether this key is generated on the AEM side or by the middleware**, as it affects the authoring flow (Integration 3) and the submission contract.
- **LSG (1003)** and **Genesys DB (1013)** action-type configuration specs — to be confirmed with TFS.

---

## 8. Items to Validate (Feasibility)

The following are confirmed during the feasibility phase, ahead of build:

1. **Custom field authoring UI in UE** — reproducing rich per-field configuration beyond the standard UE component-model field types, via UE extensions on App Builder.
2. **Authoring-time fetch** (Integration 2 / GCMS) — a UE extension that invokes the backend and writes the result onto the field.
3. **Save-time persistence** (Integration 3 / email attributes) — re-modelling the current blocking-on-save behaviour into an acceptable authoring step, aligned with the `emailResourceAllocatorKey` ownership decision.
4. **Dynamic dropdowns** (Integration 1 / division) — populating options from backend data at authoring time.
5. **Multi-step forms** — step grouping in the block model.
6. **Integration option A vs B** — servlet compatibility on Cloud vs. new App Builder actions.

---

## 9. Related Pages

- Forms — Current State & EDS Migration Overview *(foundation / risk)*
- Rule handling (show/hide) design
- Submission & middleware integration (session ID, uploads, security)
- Forms migration approach & per-form triage (~5,000 forms)
- AEM Cloud Codebase & Deployment *(for servlet reuse / Option A)*
