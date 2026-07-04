# Forms — Current State & EDS Migration Overview

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Purpose.** This page is the **foundation document** for TFS forms. It summarizes the **current forms implementation**, the **target approach** on Sites + Edge Delivery Services (EDS) **without AEM Adaptive Forms**, and — most importantly — the **complexity and risks** that the EDS migration must account for. Detailed design for each area (custom field authoring, rule handling, dynamic lookups, submission integration, migration approach) is captured in the **follow-up pages** linked at the end.
>
> **Key message up front:** TFS forms are a **heavily customized, high-complexity** area. Because there is **no AEM Forms / Adaptive Forms license**, and because EDS + Universal Editor use **static component models rather than Granite/Coral dialogs**, the existing form customizations and authoring experience **cannot be lifted and shifted** — each must be **verified as reproducible** and, where reproducible, **redesigned and re-implemented** (in some cases via custom Universal Editor extensions built on App Builder). Feasibility of reproducing every customization must be confirmed before commitment.

---

## 1. Current Forms Implementation (AEM 6.4)

- TFS forms are built on **AEM 6.4** (Sites). **Adaptive Forms is not used and not licensed.**
- Forms are built on the **AEM Core Components foundation form components** (form container, text, options, button, hidden, file upload, etc.), which have been **heavily customized**.
- The customization spans several areas:
  - **Customized authoring dialogs** — the standard component dialogs have been extended well beyond out-of-the-box fields.
  - **Calls made from the author dialog** — dialogs execute logic at authoring time, including **API calls and database lookups** (e.g. fetching an ID from a backend system while the author configures the form).
  - **A custom rule editor** — present on fields to configure **conditional behaviour (primarily show/hide)** without a developer.
  - **Custom submission and integration logic**, session handling, and marketing-system integration.

This is not a standard forms implementation — it is a **bespoke forms platform built on top of core components**, which is the root of the migration complexity.

---

## 2. Form Volume & Types

- Approximately **5,000 forms** exist in the current site.
- Forms vary across:
  - **Classic UI** and **Touch UI** implementations,
  - Different business units / use cases,
  - Different integration patterns (over a **common middleware**).
- Classic and Touch forms **differ in how the `formSessionID` is generated and managed** — a divergence that adds complexity to tracking, troubleshooting, and migration.

---

## 3. Submission Architecture & Middleware

- Form submissions are handled by a **TFS-owned middleware** (`aem-datahub-formprocessor`).
- The middleware is responsible for:
  - Receiving submission payloads from TFS forms,
  - Handling **document/file uploads**,
  - Forwarding data to downstream systems (e.g. **Eloqua, Marketo**, and other marketing/data platforms),
  - Applying TFS-specific business rules at submission time.
- The **browser calls the middleware directly** on submit. **No application gateway** is required between the frontend and the middleware for this flow.

---

## 4. Assumption — Middleware Unchanged

- **Assumption:** the middleware **remains unchanged** for the migration.
  - EDS-based forms will submit to the **same middleware endpoints**, using the same contract.
  - No major changes are expected to the middleware initially.

---

## 5. EDS Ownership (Scope of the Frontend)

In the target model, the **EDS frontend is responsible for**:

- **Rendering** the form,
- **Preparing the submission data** on the client side,
- **Calling the middleware** with the correct payload (including required IDs and the session ID).

Business logic, routing, and downstream integration remain the **middleware's** responsibility — EDS does not replace the middleware; it produces the form and the correctly-shaped submission.

---

## 6. Target State — Unified Session Handling

- Today, **Classic and Touch UI use different `formSessionID` logic**.
- In the target state, this is **standardized into a single session-handling flow**:
  - On page render, the frontend **calls the middleware to obtain a `formSessionID`**,
  - The `formSessionID` is stored in a **hidden form field**,
  - On submit, the `formSessionID` is included in the payload to the middleware.
- **Benefits:** one unified pattern across all EDS forms, simpler implementation and debugging, and cleaner alignment with downstream systems.

---

## 7. Discovery Findings — Complexity & the EDS Authoring Constraint

**Forms are the most complex and heavily customized area of the TFS implementation, and this is where the migration risk concentrates.**

### 7.1 No Adaptive Forms — and the spreadsheet option is not viable

- TFS does **not use or license Adaptive Forms**.
- The **out-of-the-box EDS way to author forms is spreadsheet/document-based.** Given the **volume (~5,000 forms)** and their **complexity**, TFS does **not** want to author forms through spreadsheets.
- Without an Adaptive Forms license, the remaining path is **custom block development in the Universal Editor (UE)** — i.e. purpose-built form blocks and field components that authors compose in UE (no spreadsheet).

### 7.2 The core constraint — UE has static models, not Granite/Coral dialogs

This is the central technical reality that shapes everything:

- EDS + Universal Editor authoring is based on **static component models** (a **fixed, declarative set of standard field types** — text, number, select, multiselect, boolean, date, richtext, path picker, etc.).
- **There are no Granite/Coral dialogs, and no arbitrary custom widgets** in the UE properties panel by default.
- Therefore, the **existing customized dialogs and authoring experience cannot be reproduced 1:1**:
  - The current dialogs rely on custom widgets, in-dialog logic, and **authoring-time backend calls** (API/DB lookups) that have **no native equivalent** in UE's declarative model.
  - Achieving an authoring experience *similar* to today requires **extending Universal Editor through App Builder** (e.g. custom field UI that runs its own logic and writes values back into the content). This is **custom development**, not configuration.

### 7.3 The existing form code cannot be migrated — it must be redesigned

- Because there are **no Granite/Coral dialogs** in the target and the rendering/authoring model is fundamentally different, the **existing form code (dialogs, server-side rendering, dialog logic) cannot be carried over**.
- All form authoring UI and behaviour must be **redesigned and re-implemented** for EDS/UE:
  - Declarative field configuration → native UE component models,
  - Repeating groups (multi-value dialog sections) → nested/child components (UE has no native multifield),
  - **Rich/interactive field UI and authoring-time lookups** (e.g. dynamic dropdowns, fetching an ID from a backend at author time) → **custom UE extensions built on App Builder**,
  - **Rule handling (show/hide)** → authored conditions evaluated by custom block JavaScript at runtime.

---

## 8. Risk — App Builder Customization & Feasibility

**This is the primary risk area of the forms migration and must be called out explicitly.**

- Reproducing the current authoring experience (custom dialogs, dynamic dropdowns, authoring-time API/DB lookups, per-field configuration) depends on **custom Universal Editor extensions developed with App Builder**.
- **App Builder development is a significant, higher-risk effort**, and a **feasibility check is required** to confirm that **each existing customization can actually be replicated** in EDS/UE + App Builder **before** the approach is committed.
- Even where reproducible, the outcome carries honest caveats:
  - The **same authoring experience or field behaviour may not be achievable**, because UE does **not** provide Granite/Coral dialogs.
  - What is delivered will be a **re-designed** experience of *similar capability*, not a pixel-for-pixel or behaviour-for-behaviour copy of the 6.4 dialogs.
  - Each custom extension is a **separately built, hosted, secured, and maintained** application — an ongoing engineering and maintenance commitment.
- Additional feasibility/validation points:
  - **Authoring-time backend calls** (e.g. ID lookups) that require secrets or internal-network access must be routed through **App Builder backend actions**, not directly from the browser/editor.
  - **Client-side submission to the middleware** must be validated for **CORS, credential/secret handling, and spam/abuse protection**, since calls now originate from the browser rather than server-to-server.

---

## 9. Trade-off — Adaptive Forms (Licensed) vs Full Customization

For transparency, the two paths must be weighed openly:

| | **Custom build on EDS/UE (no license)** | **AEM Forms / Adaptive Forms (licensed SKU)** |
|---|---|---|
| Rule editor (show/hide, conditions) | **Custom development** required | **Out of the box** |
| Dynamic dropdowns / datasource-driven fields | Custom UE extension + App Builder | Native support |
| Rich authoring dialogs / field widgets | Custom UE extensions (feasibility-dependent) | Native |
| Backend integrations | Custom App Builder services | Native connectors + rule editor |
| Licensing cost | None | **Paid SKU** |
| Custom engineering & maintenance | **High** (build & maintain a bespoke forms platform) | **Low** (product-provided) |

- **Adaptive Forms is a paid SKU**, but it provides the **rule editor and integrations out of the box** — precisely the capabilities TFS built by hand in 6.4.
- The custom-build path **avoids licensing cost** but **shifts substantial effort and risk to custom development**, effectively re-building — and then maintaining — much of what Adaptive Forms provides natively.
- **The more customizations that must be reproduced, the stronger the case to re-evaluate the Adaptive Forms license.** This trade-off should be a conscious, documented decision by TFS, not an implicit one.

---

## 10. Explicit Call-outs (Transparency)

To be unambiguous for all readers and stakeholders:

1. **This is a full customization and re-implementation, not a migration.** The existing form code and dialogs do not carry over to EDS/UE and must be **redesigned and rebuilt**.
2. **The authoring experience will change.** UE uses **static component models**, not Granite/Coral dialogs — so the current dialog-driven authoring experience **cannot be assumed to be reproduced as-is**, and some field behaviours may not be achievable in the same way.
3. **Every customization must be individually verified as reproducible** in EDS/UE (+ App Builder) **before** commitment. Feasibility is **not assumed**.
4. **App Builder extension development is the key risk** and requires a **feasibility/POC phase** to prove that the required authoring experiences and field behaviours can be delivered.
5. **Volume amplifies risk.** With ~5,000 forms, a **per-form triage** (which forms map cleanly vs. which depend on complex customizations) is required to scope effort realistically.
6. **The Adaptive Forms licensing decision is a deliberate trade-off** between licensing cost and the cost/risk of building and maintaining an equivalent custom platform.

---

## 11. Follow-up Pages (Detailed Design)

The detailed "how" for each area is documented separately:

- Form authoring in EDS/UE — custom form blocks & field components
- Custom Universal Editor extensions (App Builder) — dynamic dropdowns, authoring-time lookups, custom field UI
- Rule handling (show/hide) design
- Submission & middleware integration (payload, session ID, uploads, CORS/security)
- Forms migration approach & per-form triage (~5,000 forms)

> *(Link the specific follow-up pages here in Confluence.)*
