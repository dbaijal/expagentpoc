# Forms — Rule Editor & Validations

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services (Universal Editor / xWalk)

> **Companion to** the Form Authoring solution design. This page covers the **Rule Editor** (conditional show/hide of form fields) and **Form Validations** in the target model.

---

## 1. Overview & Context

TFS does **not use or license Adobe Adaptive Forms**. The conditional-logic **Rule Editor** that Adaptive Forms provides **out of the box** was therefore **custom-built by TFS** on top of the AEM 6.4 core form components. Any equivalent capability in the target model must likewise be **custom-developed** — it is not provided by the platform.

This document covers **how the Rule Editor feature can be achieved in the target model (EDS with AEM as the authoring source)**, the options available, and their trade-offs. It also covers the **Form Validations** approach.

**Scope of the Rule Editor:** **show/hide of form fields** based on the value of other fields — the same scope as the current TFS implementation. (No calculation, no dynamic value assignment — show/hide only.)

---

## 2. Current State — AEM 6.4 Rule Editor (Full Detail)

The current TFS Rule Editor is a **custom capability** with the following characteristics. This is captured in full so the target design can be assessed against the real behaviour.

### 2.1 Authoring surface
- The Rule Editor is registered as a **toolbar action that appears on every form component**.
- A **global clientlib** registers it dynamically for all components whose `resourceType` starts with `formcommons/components/form` or `tfsite/components/form`.
- **Rule types supported:** **Show / Hide only.**
- **Conditions (operators) supported:** `equal`, `notequal`, `lessthan`, `lessthanequalto`, `greaterthan`, `greaterthanequalto`, `startwith`, `endwith`, `contains`.
- **Logic operators:** `all` (AND) / `any` (OR).

### 2.2 Field discovery
- When the Rule Editor dialog opens, the **trigger-field dropdown** is populated via a **server-side Sling datasource servlet** (`GetFormFieldsServlet`) that **reads the JCR directly**.
- **Discovery scope:**
  - Only **direct children of the immediate parent form container** are discoverable.
  - Fields inside **nested panel containers** are **not** visible from the parent level.
  - Fields inside **Experience Fragment inclusions** are **not** discoverable.
  - **Multiple form containers** on a page are scoped **independently**.

### 2.3 Storage
- Rules are stored as **`rules` child nodes on each individual field component node in JCR** (per-field storage).

### 2.4 Render & runtime
- At render time, `ContainerModelImpl` **aggregates rules from all child fields** into a single JSON array and **encrypts** it using **AES-128-ECB**.
- The encrypted string is placed on the `<form>` element as a **`data-showhide` attribute**.
- At runtime, **`showhide.js`** fetches a **decryption key from a server endpoint**, decrypts the rules, and wires **jQuery change listeners** to apply show/hide.

> **Why this matters for the target:** the current design depends on **server-side JCR field discovery**, **per-field rule storage**, **server-side aggregation**, and **encryption with a server-provided key** — none of which exist in EDS (no server-side rendering, no Sling models, no server aggregation step). The target must therefore **re-implement the capability** with a client-side rule engine, and the **authoring surface** must be reconsidered because there are no Granite/Coral dialogs in the Universal Editor.

---

## 3. Target Model — EDS with AEM as Authoring Source

### 3.1 Common to all options — a custom rule engine

Regardless of how rules are **authored**, the **runtime rule engine is custom-developed within the form block** (there is no server-side rendering or aggregation in EDS). The engine:

1. **Reads** the authored rules for the form,
2. **Parses** them into an in-memory rule list,
3. Sets **initial visibility** (for `show` rules, hides the target field wrapper before the form is presented),
4. **Attaches** `change` / `input` listeners on the relevant source fields,
5. On change, **re-evaluates** each rule's conditions, applies the **logic operator** (`any`/`all`), and **shows/hides the target field's wrapper**.

Show/hide is applied to the **field wrapper element**, not the input directly. Rules are **never exposed in the rendered HTML** (read and applied at decoration time; no encrypted payload and no server key are required, unlike AEM).

**The variable across the three options below is the *authoring experience* — which in turn determines the development effort, risk, and how closely the author experience matches today.**

### 3.2 Option 1 — Author rules in a Spreadsheet

Rules are authored in a **dedicated rules spreadsheet**, exposed by EDS as **JSON**, and consumed by the form block at runtime.

- One **row = one rule/condition**; the form block fetches the sheet JSON, builds a field→rules map, and evaluates client-side.
- The form container block exposes a **property for the rules spreadsheet path/URL**, which the form uses to fetch and evaluate rules.

**Example sheet structure (one row = one rule/condition):**

| Rule ID | Target Field | Action | Logic | Condition Field | Operator | Value |
|---|---|---|---|---|---|---|
| rule-1 | state | show | AND | country | equal | US |
| rule-1 | state | show | AND | formType | notEqual | simple |
| rule-2 | phoneExtension | show | OR | country | equal | US |
| rule-2 | phoneExtension | show | OR | country | equal | CA |
| rule-3 | discount | show | AND | quantity | greaterThanEqualTo | 10 |
| rule-4 | otherInput | show | AND | reason | contains | other |
| rule-5 | prefix | show | AND | fullName | startsWith | Dr |

**Trade-offs:**

| Pros | Cons |
|---|---|
| **No UE customization** — spreadsheets are an **out-of-the-box** EDS authoring mechanism | **A separate sheet per form is not viable at TFS scale** (~5,000 forms → thousands of sheets to create and manage) |
| No App Builder, no extension, **no feasibility risk** | **Authoring happens in a different place from the form** — author edits a sheet, not the form in context |
| Lowest development effort | No visual rule builder; authors type field names / operators / values into cells (error-prone) |
| | Rules live in the sheet/JSON, **not in JCR** alongside the form |

### 3.3 Option 2 — Per-field rule capability in the Universal Editor

Today, a Rule Editor is provided **on each field** (toolbar action per component). A **similar per-field capability** can be provided in the target by modelling rule configuration **on each field** in the Universal Editor.

- Rule configuration (target/action/logic/conditions) is captured through **field configuration in the UE authoring model**, authored in context on the form.
- Rules are **stored with the form** (persisted to JCR via the Universal Editor), and read by the custom rule engine (§3.1) at runtime.

**Important callout — authoring experience will change.** The Universal Editor supports **static component models**; it does **not** support Granite/Coral dialogs or arbitrary custom widgets in the properties panel. Providing a per-field rule capability therefore requires **extending the Universal Editor** (see §3.5). The resulting authoring experience **will differ from the current AEM Rule Editor UI** — it will not be a like-for-like reproduction of the existing dialog/toolbar experience.

**Trade-offs:**

| Pros | Cons |
|---|---|
| Rules authored **in context, per field** — conceptually close to today | **Requires UE customization** (extension); not out-of-the-box |
| Rules **persist in JCR** with the form | **Authoring UI/experience changes** vs today |
| No separate sheet to manage per form | **Feasibility / POC required** (see §3.5) |
| | Development effort higher than Option 1 |

### 3.4 Option 3 — Custom Rule Editor UI (React app via App Builder)

A **custom Rule Editor UI** is built as a **React application** and surfaced in the Universal Editor via an **App Builder UI extension (custom data type renderer)** — the closest reproduction of a visual, builder-style rule editor.

- The author opens the Rule Editor, sees a **visual builder** (actions, target/condition field dropdowns, operators), builds rules, and **saves**.
- The extension reads the current value to **pre-populate on edit** and writes the value back so it **persists to JCR**.
- The custom rule engine (§3.1) reads the stored rules at runtime.

**Important callouts:**
- This requires **extending the Universal Editor** with a React app on **Adobe App Builder** (see §3.5). It is **custom development**, not configuration.
- **Field discovery** (listing the form's fields for the builder's dropdowns, including unsaved fields) is the primary item to prove in a **feasibility / POC**.
- The renderer surfaces as a **field UI within the properties rail** (iframe), **not** a full-screen dialog like today — so while the *capability* (visual builder, edit-time value round-trip) is achievable, the **authoring UI/placement will differ** from the current experience.

**Trade-offs:**

| Pros | Cons |
|---|---|
| Closest to today's **visual rule-builder** experience | **Highest development effort**; a full React app to build |
| Shows saved rules on edit; **persists to JCR** | **Requires App Builder** — see §3.6 (separate code, deployed & maintained outside the EDS codebase) |
| Central, consistent rule-authoring UI | **Feasibility / POC required** (field discovery, properties-rail UX) |
| | Authoring UI/placement still differs from the current dialog |

### 3.5 UE customization & feasibility (applies to Options 2 & 3)

- The Universal Editor **out of the box supports static component models**; **Granite/Coral dialogs and arbitrary custom widgets are not supported**. Reproducing per-field or visual rule authoring therefore requires **extending the Universal Editor** (via App Builder UI extensions / custom data type renderers).
- Because this is an extension of the platform's authoring capability, a **feasibility check / POC is required** before commitment — principally to validate **field discovery** (enumerating the form's fields for rule targets/conditions, including unsaved changes) and the **authoring UX** within the properties rail.
- **The authoring experience will not be identical to the existing AEM Rule Editor**, and may change — this must be clearly understood and accepted by TFS. The *capability* (show/hide rules, editable, persisted) is achievable; the *UI/UX* is a re-design.

### 3.6 App Builder — separate codebase (important)

For Option 3 (and any App-Builder-backed part of Option 2), the extension is an **Adobe App Builder application**. This code **does not live in the EDS codebase** — it is a **separate project that is built, deployed, secured, and maintained independently**, against the UE extension APIs. This is an ongoing engineering and operational commitment that must be planned for, and is a key input to the effort/risk comparison.

### 3.7 Options summary

| | Option 1 — Spreadsheet | Option 2 — Per-field in UE | Option 3 — React app (App Builder) |
|---|---|---|---|
| Authoring surface | Rules sheet (separate from form) | Per-field config in UE, in context | Visual rule-builder UI in UE |
| Closeness to today's UX | Low | Medium | Highest (still not identical) |
| Rules persisted in JCR | No (sheet/JSON) | Yes | Yes |
| UE customization required | **No** (OOTB) | **Yes** (extension) | **Yes** (extension + React app) |
| App Builder / separate codebase | No | Where extension needed | **Yes** |
| Feasibility / POC required | No | **Yes** | **Yes** |
| Development effort / risk | **Lowest** | Medium | **Highest** |
| Scale concern | Sheet-per-form not viable at ~5,000 forms | — | — |
| Common runtime rule engine | Yes | Yes | Yes |

> **Decision driver:** In all three options the **custom rule engine (§3.1) is the same** and must be built. The **only real difference is the authoring experience**, which determines **effort and risk**. The choice is therefore a trade-off between *how closely the author experience matches today* and *how much custom development / feasibility risk / separate-codebase maintenance* TFS is willing to take on. This should be a conscious, documented decision by TFS.

---

## 4. Form Validations

The validation layer performs **client-side validation using HTML5-native capabilities and lightweight JavaScript**, with **middleware as the final source of truth**.

- **Client-side validation first** — all validations run in the browser before submission (including multi-step forms), preventing unnecessary middleware calls when fields are invalid.
- **HTML5 + lightweight JS (no jQuery)** — native input types/attributes (`required`, `type="email"`, `pattern`, `min`, `max`, `minlength`, `maxlength`) supplemented by small custom JavaScript helpers. This **replaces the jQuery Validate plugin** used in AEM and removes that dependency.
- **Standard validation types covered** — required fields, email format, phone number format, pattern (regex) checks, min/max length, numeric/decimal validation, URL validation, and file-upload constraints (allowed types, max size, max count).
- **Custom constraints supported** — cross-field business rules (e.g. "confirm email must match email", "end date after start date") implemented via **custom JavaScript in the form block**.
- **Consistent across steps** — for multi-step forms, validation runs **per step on "Next"** and again **on final submit**, so users cannot proceed with invalid data at any step.
- **Backend as source of truth** — the middleware remains the final validation authority; where it returns errors, the front end **surfaces them inline** next to the corresponding fields.
- **No encrypted constraint delivery** — unlike AEM (which aggregated and **encrypted** all field constraints into a `data-constraints` attribute for jQuery Validate), EDS reads validation rules **directly from the field definitions at runtime**. No encrypted payload, no server-side aggregation.

---

## 5. Scope Boundaries

| Boundary | Detail |
|---|---|
| Rule types | **Show and hide only** — same as AEM |
| Operators | equal, notequal, lessthan, lessthanequalto, greaterthan, greaterthanequalto, startwith, endwith, contains |
| Logic | any (OR) / all (AND) |
| Runtime engine | Custom, client-side, within the form block — required in **all** options |
| Field discovery (Options 2/3) | Fields inside Fragment inclusions may not be discoverable — consistent with current AEM behaviour; to be validated in POC |
| JS library | Vanilla JavaScript (no jQuery) |
| Rule exposure | Rules are not present in the rendered page HTML |
| App Builder code | Lives **outside** the EDS codebase; separately built, deployed, maintained |

---

## 6. Open Items

- **Authoring option decision (1 / 2 / 3)** — driven by the required authoring experience vs. accepted effort/risk and separate-codebase maintenance.
- **Feasibility / POC** (Options 2 & 3) — UE field discovery for rule targets/conditions; properties-rail authoring UX; extent to which the current UI can be approximated.
- **Acceptance that the authoring UI/experience will differ** from the current AEM Rule Editor.

---

## 7. Related Pages

- Form Authoring in EDS *(block model, field items, authoring-time integrations)*
- Forms — Current State & EDS Migration Overview
- Dynamic Dropdowns & Prefill
- AEM Cloud Codebase & Deployment *(if any server-side logic is migrated to Cloud)*
