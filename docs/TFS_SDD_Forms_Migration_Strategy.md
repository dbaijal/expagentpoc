# Migration Strategy — Forms (AEM as Authoring Source)

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services (Universal Editor / xWalk)

> **Companion to** the Form Authoring, Rule Editor & Validations, Dynamic Dropdowns & Prefill, and reCAPTCHA solution designs. This page defines **how existing TFS forms are migrated** from AEM 6.4 into the target model.

---

## 1. Overview

TFS forms are built on a suite of AEM core form components with heavy custom extensions — a dialog-driven authoring model with multiple action types, XF-based fieldset composition, a custom rule editor for show/hide logic, and server-side constraint aggregation. (See the Forms overview and Form Authoring solution designs for the full current-state analysis.)

In the target model, a form is authored as a **custom `form-container` block with child field items** in the Universal Editor and rendered client-side (no server-side form rendering).

The migration strategy is a **hybrid approach**: **non-form page content** is migrated by **scraping the page HTML**; the **form section** is migrated from the **AEM JCR**. Both are combined into a single target page.

---

## 2. Why a Hybrid Approach

TFS has a large inventory of pages, and it is not known upfront which pages contain forms — a form can appear on any page across the site hierarchy. A blanket JCR-only migration for all pages is not practical, and a scrape-only migration cannot reconstruct forms. The two sources are therefore combined:

| Content | Migration source | Reason |
|---|---|---|
| Non-form content (hero, text, images, product sections) | **HTML scraping** | Fully rendered page with CSS and layout intact; no structured config needed |
| Form section | **AEM JCR** (`.infinity.json`) | Form container config, XF paths, rules, selected options, constraints — **none of this is available from rendered HTML** |

**Why the form section must come from JCR (not scraping):** the rendered HTML shows inputs, but the **authoring intent lives only in the JCR** — XF fieldset paths, show/hide rules, action-type and submission configuration, `uniqueFormKey`, which options are authored/selected, and field constraints. These cannot be recovered by scraping, so forms follow the **JCR migration path**.

---

## 3. Prerequisites

Form migration cannot begin until the following are in place:

| # | Prerequisite | Detail |
|---|---|---|
| 1 | **Site / MSM structure set up** | Target sites and locale structure established (forms may depend on locale context). |
| 2 | **Form fieldset fragments migrated** | All AEM form XF fieldset variations migrated to the target as reusable form-fragment pages at the correct hierarchy. |
| 3 | **XF → fragment path mapping table complete** | Maps every AEM XF path to its corresponding **target fragment path**. Used directly by the migration script to resolve every XF inclusion (see §5.4). Without it, fragment references cannot be written. |
| 4 | **Form blocks developed / field mapping locked** | The `form-container` block and its child field items exist (or at minimum the **field-to-block mapping is confirmed**), so the script has a definitive target structure. |
| 5 | **Rule Editor option decided** | The chosen rule-authoring option (see the Rule Editor SDD) and **how it stores rules** must be known, because **rule migration output depends on the target rule storage format** (see §5.5). |

> Fragment migration + the mapping table, and the Rule Editor storage decision, are **hard dependencies** — the corresponding parts of form migration cannot run until they are resolved.

---

## 4. Migration Approach — Step by Step

**Step 1 — Scrape page HTML.** Capture all non-form content (hero, text, product images, promotional content) with full CSS and layout intact.

**Step 2 — Detect form presence.** Scan the scraped HTML for the AEM form container CSS class **`cmp-p-form-container`**, rendered by every AEM form container and a reliable indicator that the page contains a form.
- Not found → standard page migration; no further form action.
- Found → proceed to Step 3. (Detection is automatic — no manual page inventory needed.)

**Step 3 — Isolate the form section.** Locate the `cmp-p-form-container` element in the scraped HTML and remove it (and its rendered children). **Note its position** in the document — the migrated form will be placed here. The remaining scraped HTML (non-form content) is preserved as-is.

**Step 4 — Fetch the form page JCR.** Call the AEM JCR export (`…/<page>.infinity.json`) for the same page. This returns the complete JCR tree, including all form component nodes and their properties.

**Step 5 — Transform the form from JCR.** The transform script traverses the JCR form tree and produces the target form structure — the form container configuration, one entry per field, and rules (see §5).

**Step 6 — Assemble the page.** Combine the preserved non-form content (Step 3) with the transformed form section (Step 5), placing the form at the noted position.

**Step 7 — Produce and deploy the output.** The assembled page is produced as **importable target content**, packaged, and installed to the AEM as a Cloud Service author, then published (see §7).

---

## 5. What the Form Transform Does

The script reads the JCR form container node and produces the target form: the form-container configuration, one field entry per form field, and rule definitions where present.

### 5.1 Locate the form container
Traverse the JCR tree for the node with `sling:resourceType = tfsite/components/form/container`. **Node names in JCR are dynamic and cannot be hardcoded** — traversal is by `sling:resourceType`.

### 5.2 Form container configuration
Extract the container properties and map them to the target form-config:

| AEM JCR property | Target form-config | Notes |
|---|---|---|
| `customActionType` | action / `field_action` | Action-type code(s) mapped to the target action value (e.g. `1001` → Eloqua); comma-separated if multiple |
| `eloquaFormName` | eloqua form name | Present if Eloqua (1001) |
| `eloquaInstance` | eloqua instance | Present if Eloqua (1001) |
| `division` | division | Division code (lpd, cad, …) |
| `isMqo` | mqo | Boolean |
| `nonLSGType` | nonlsg type | lead / case / quote / bulk quote |
| `uniqueFormKey` | form key | Present for the relevant action types; carried as authored config |
| `redirect` | redirect | AEM path → **remapped** to the target equivalent path |
| `multistep` (derived) | multistep | Only if the form is multi-step |

> **Thank-you / redirect pages** are separate pages (often gated) and are migrated as their own pages; the `redirect` value is remapped to the migrated target path.

### 5.3 Field transformers
Iterate the child nodes of the form container **in order**. Each node's `sling:resourceType` selects the transformer that produces the corresponding child field item:

| AEM `sling:resourceType` | Target field item | Transformer |
|---|---|---|
| `tfsite/components/form/input` | input | Input transformer |
| `tfsite/components/form/textarea` | input (type: textarea) | Input transformer |
| `tfsite/components/form/options` | options | Options transformer |
| `tfsite/components/form/button` | button | Button transformer |
| `tfsite/components/form/xfinclusion` | fragment | XF resolver (see §5.4) |
| `tfsite/components/form/recaptcha` | recaptcha | Recaptcha transformer |
| `tfsite/components/form/hidden` | hidden | Hidden transformer |
| `tfsite/components/form/upload` | upload | Upload transformer |

**Input transformer — property mapping:**

| AEM JCR property | Target field key |
|---|---|
| `jcr:title` | label |
| `name` | field name |
| `type` | type |
| `constraints/required` | required |
| `constraints/requiredMessage` | required message |
| `constraints/confConstraintMessage` | constraint message |
| `constraints/minlength` | min length |
| `constraints/maxlength` | max length |
| `placeholder` | placeholder |
| `hideonload` | field initially hidden — **visibility handled by a rule** (see §5.5) |

**Options transformer — property mapping:**

| AEM JCR property | Target field key |
|---|---|
| `jcr:title` | label |
| `name` | field name |
| `type` (checkbox / radio / select) | display type |
| `source` | source (local / dynamic) |
| `items/item*/text` + `items/item*/value` | options (Display,value pairs) |
| `constraints/required` | required |
| `constraints/requiredMessage` | required message |

> **Dynamic options / cascading / authoring-time lookups:** where a form uses **dynamic dropdowns**, **cascading options**, or **authoring-time backend calls** (e.g. GCMS Fetch, datasource-driven options), those forms depend on the corresponding integrations being available (see the Dynamic Dropdowns & Prefill and Form Authoring SDDs). The sample long-form lead forms use **local** options and do not require these; forms that do are a higher-complexity tier (see §8).

### 5.4 XF inclusion resolution — direct path mapping
AEM form XF inclusions store an XF **fragment path** in JCR (`fragmentPath`, with `fragmentType` STANDARD / CUSTOM).

- For each `xfinclusion` node, the script **looks up the XF path in the XF → fragment mapping table (§3, prerequisite 3)** and writes a **fragment field item pointing at the mapped target fragment path**.
- **No best-match / variation resolver is required.** The migration performs a **direct path → path mapping** (the JCR XF path to its equivalent target fragment path). The mapping table is responsible for encoding the correct target path (including any variation), so the locale-aware best-match logic that existed at AEM runtime does **not** need to be re-implemented in the migration script.

> This is a deliberate simplification: XF resolution reduces to a **table lookup**, provided the mapping table is complete and accounts for the correct variation per target path.

### 5.5 Rule migration
In AEM, show/hide rules are stored as **`rules` child nodes on individual field components** — one `rules` node per field that has a rule.

- The script reads each field's `rules` node and extracts the rule definition: action (`type`: show/hide), logic (`check`: any/all), the target field, and each condition (`entries/item*` → `controlName` + `condition` + `search`), resolving field **IDs to field names** via a lookup built during the field pass.
- The extracted rules are then written in the **format expected by the chosen rule-editor storage** — **this output is dependent on the Rule Editor option selected** (see the Rule Editor SDD). The rule *source* extraction from JCR is common; the *target format* follows whichever storage the Rule Editor design lands on.

**AEM rule source → normalized rule (before formatting to target storage):**

| AEM JCR (`rules` node) | Normalized rule | Notes |
|---|---|---|
| `type` | action | show / hide |
| `check` | logic | any / all |
| `target` (field ID) | target (field name) | Resolved via ID → name map |
| `entries/item*` (`controlName` + `condition` + `search`) | condition(s) | `field` + operator + value |

*Example (from a representative form):* an "Others" text field hidden on load, shown when a "Types of samples" checkbox group **contains** "Others" → one rule, action `show`, logic `any`, one `contains` condition. (Rule scope is **show/hide only**, matching the current system.)

---

## 6. Before / After — Form Structure

| Dimension | AEM 6.4 (current) | Target (EDS + AEM authoring) |
|---|---|---|
| Form config storage | JCR node properties | form-container configuration (authored content) |
| Field authoring | Per-field touch-UI dialogs | Child field items in the `form-container` block |
| XF inclusion | Runtime best-match resolution (custom server code) | Fragment field item with **directly mapped** target fragment path |
| Show/hide rules | Per-field JCR nodes; encrypted DOM attribute | Rules in the chosen rule storage; in-memory client-side evaluation |
| Constraint delivery | Server-side aggregation, encrypted payload | Field-level constraints; HTML5-native + lightweight JS |
| Rendering | Server-side HTL + Sling Models | Client-side (form block) |

---

## 7. Output & Deployment

- The migration script's job is to produce **importable target content** for each migrated page (non-form content + the transformed form section), in the form the **xWalk import pipeline** consumes.
- That content is then **converted to the target content structure, packaged, and installed to the AEM as a Cloud Service author**, and **published** — after which the `form-container` block renders the form client-side.
- The exact intermediate representation the script emits is an **implementation detail of the import tooling** and is confirmed during implementation; the **outcome is authored form content on the AEM author instance** (not a standalone data file), consistent with the rest of the site migration.

> Non-form content on a form page is produced through the **standard page-migration path**; the transformed form section is injected at the position noted in Step 3, and the combined page is imported as a single page.

---

## 8. Per-Form Triage & Scale

With ~5,000 forms, forms are **triaged and classified by the capabilities they use**, which drives sequencing and effort:

- **Fragments** — how many XF fieldsets (gated on the fragment library + mapping table).
- **Rules** — presence/complexity of show/hide rules (gated on the Rule Editor decision).
- **Options** — local vs **dynamic / cascading** dropdowns (dynamic → gated on the dropdown integration).
- **Authoring-time calls** — e.g. GCMS Fetch, datasource-driven options (higher-complexity tier; gated on those integrations).
- **Action type** — Eloqua / Email / CORA / GCMS / etc.

Forms using only fragments + local options + simple rules + a standard action type are the **straightforward tier** and good early candidates; forms with dynamic/cascading options or authoring-time integrations are a **higher-complexity tier**.

---

## 9. Migration Script — Capability Summary

| Capability | Approach |
|---|---|
| Form detection | Scan scraped HTML for `cmp-p-form-container` (automatic) |
| Form container config | JCR container node → form-config (action type, Eloqua, division, MQO, redirect) |
| Field items | Per-`sling:resourceType` transformer (input, options, button, hidden, upload, recaptcha, textarea) |
| Field constraints | JCR `constraints` node → field constraints (required, messages, min/max length) |
| XF inclusions | **Direct path mapping** via XF → fragment mapping table (no best-match) |
| Show/hide rules | JCR `rules` nodes → normalized rules → **chosen rule storage format** |
| Non-form content | HTML scraping (CSS/layout preserved) |
| Assembly | Non-form content + transformed form section → single page |
| Deployment | Imported to AEM author (content package) → published |

---

## 10. Open Items & Dependencies

- **XF → fragment mapping table** must be complete before XF resolution can run (and must encode the correct target variation per path).
- **Rule Editor storage decision** — determines the rule-migration output format (§5.5).
- **Dynamic-dropdown / authoring-time integrations** (GraphQL options, GCMS Fetch, datasource) must be available before forms depending on them are migrated.
- **Action-type specifics** — any action types with specs still to be confirmed with TFS.
- **Redirect / thank-you and gated pages** — migrated as related pages; redirect paths remapped.

---

## 11. Related Pages

- Forms — Current State & EDS Migration Overview
- Form Authoring in EDS
- Forms — Rule Editor & Validations
- Dynamic Dropdowns & Prefill
- Forms — reCAPTCHA
