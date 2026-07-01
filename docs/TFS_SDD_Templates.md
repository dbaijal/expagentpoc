# Templates — Solution Design

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## 1. Overview

This document explains how **templates** work in the target model — Edge Delivery Services with AEM as the authoring source — and how this differs from the **editable templates** used in AEM 6.4 today. The intent is to give a clear understanding of the template concept in the target environment, the governance mechanisms available, and a parity comparison against AEM editable templates.

**The key point:** templates in the target model are **not** the same as AEM editable templates. In AEM, an editable template defines the page **structure**, **initial content**, and **policies** (allowed components), and pages remain linked to that template's structure. In the target model, a template provides **initial content only** — a starting point for a new page — and governance is handled through different, code-based mechanisms rather than an editable-template policy model.

---

## 2. Templates in the Target Model (vs AEM Editable Templates)

| | AEM 6.4 Editable Template | Target (EDS + AEM Authoring) |
|---|---|---|
| What it defines | Structure + initial content + policies | **Initial content only** |
| Ongoing link to pages | Pages stay linked to the template structure | **Independent copy** — no ongoing structural link |
| Effect of editing the template | Structure changes can propagate to pages | **Does not affect** pages already created from it |
| Component governance | Template **policies** define allowed components | Handled in **code** (filters + editor logic) — no policy UI |

A template in the target model is essentially **a page that has been designated as a template**, so that authors can start a new page from it with predefined initial content.

---

## 3. Creating and Using a Template (Universal Editor)

Authors work with templates using the standard AEM authoring capability:

- **Marking a page as a template:** in the page's **Page Properties → Advanced → Template Settings**, the author toggles **"Use Page as Template"** and saves. The page is then available as a template. (Templates a site is allowed to use are configured via **Allowed Templates** in the site's page properties.)
- **Creating a page from a template:** via **Create → Page**, the author selects the desired template on the **Template** tab and completes the wizard. The new page starts with the template's content.

Reference: [Universal Editor — Templates (Adobe)](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/sites/authoring/universal-editor/templates)

---

## 4. Template = Initial Content Only (Independent Copies)

This is the most important behavioural difference from AEM editable templates:

- A page created from a template is an **independent copy** of the template's content.
- **The author can modify the resulting page freely, with no restrictions from the template.**
- **If the template is later changed, pages already created from it do NOT change.** There is no ongoing structural inheritance from the template to its pages (unlike AEM editable templates, where template structure changes could affect linked pages).

In other words, the template **seeds** a new page's starting content; it does not govern that page's structure thereafter.

---

## 5. Recommended Approach — Where Template Pages Live

Because template pages are content (not code under `/conf` as with AEM editable-template definitions), we recommend organising them clearly and keeping them out of the live-copy/rollout flow:

- Maintain a dedicated **`Templates` folder** in the content tree, **parallel to the region trees**, holding the pages designated as templates.
- **Exclude this folder from MSM** — template pages should **not** be part of blueprint/live-copy rollout, so they are not rolled out to locales as if they were site content.

```
/content/lifetech
├── Templates          ← recommended: designated template pages (excluded from MSM)
├── fragments          ← centralized fragment library
├── global
├── north-america
├── latin-america
├── europe
└── ...
```

> This folder placement is a **recommended organisational approach**, not a platform requirement. The `Templates` folder holds the template *pages*; a site's usable templates are registered via **Allowed Templates** in site properties.

---

## 6. Authoring Experience

From the author's point of view:

1. The author chooses **Create → Page** and selects a template on the **Template** tab.
2. The new page opens with the template's **initial content** already in place.
3. The author **edits the page freely** — adding, changing, or removing content — with no constraints imposed by the template.
4. Editing the template later has **no effect** on this page.

This keeps page creation fast and consistent (a known starting point) while giving authors full control of each page after creation.

---

## 7. Component Governance Mechanisms

> **Important — there is no 1:1 equivalent of AEM Template Policies in the target model.** The AEM editable-template policy model (author-configurable, per-template allowed-component governance managed through the Template Editor UI) does **not** exist out-of-the-box in EDS with AEM as the authoring source.
>
> A **similar level of governance can be achieved**, but it is **not OOTB** — it requires **custom development**. It is implemented in **code**, using a combination of:
> - **Universal Editor filters** (`component-filters.json`) — allowed components within a section/container;
> - **Template metadata** (e.g. `template` / `theme` values rendered as body classes) — to identify the page type;
> - **Custom logic in `editor-support.js`** — to dynamically change allowed components and editing behaviour per "template" (page type).
>
> In other words, governance is **not configured by an author in a UI** as it was in AEM; it is **built and maintained by developers in code.**

The mechanisms that together deliver this governance are described below.

### 7.1 Component Configuration Files

| File | Purpose |
|---|---|
| `component-definition.json` | Defines the **catalog of components/blocks** available to authors (what appears in the "add component" list). |
| `component-models.json` | Defines the **fields/properties** each component exposes in its edit dialog (the Properties panel). |
| `component-filters.json` | Defines **containment rules** — which components/blocks are allowed **inside which section or container**. This is the mechanism for controlling allowed child blocks per container/zone. |

### 7.2 Template Metadata (Body Class)

- A **`template` metadata** value can be defined for a page and is rendered by EDS as a **class on the page `<body>`** (for example, `<body class="... article-template">`).
- To make this available to authors, the **`template` property is added to the page-metadata model** (the page-metadata component model / page properties), so it can be set per page in the Universal Editor.
- This body class is then used in **CSS** (for template-specific styling) and in **JavaScript logic** (to apply template-specific behaviour). Any behaviour that needs to differ by page type can key off this class.

### 7.3 Author Editability Rules (`editor-support.js`)

- Where component restrictions or editability rules need to vary — including behaviour that depends on the page type/template — this is implemented in **`editor-support.js`**.
- This script can **alter what and how sections and blocks are editable** in the Universal Editor, based on information available on the page (for example, the path or the template body class).
- Example: a section can be **locked so that no new components can be added, while still allowing authors to edit the components already placed on the page.**

---

## 8. Governance Is Defined in Code (No Template Editor UI)

A consequential difference for TFS to note: in AEM 6.4, template structure and policies were managed through the **Template Editor UI** and were editable (to a degree) by an appropriately-permissioned user.

In the target model:

- There is **no Template Editor UI** for structure/policy governance.
- Component governance and editability rules are **defined in code** — `component-filters.json` (containment) and `editor-support.js` (editability / per-page-type rules).
- Therefore, **changes to governance go through the development/deployment process**, not through an authoring UI.

---

## 9. Ownership

| Concern | Owner |
|---|---|
| Template pages and their initial content | TFS Authors |
| Which templates a site may use (Allowed Templates) | TFS Authors / configuration |
| Component governance rules (`component-filters.json`, `editor-support.js`) | Adobe / Developers |
| Component catalog and models (`component-definition.json`, `component-models.json`) | Adobe / Developers |

Template *content* is an authoring responsibility; component **governance** is a code responsibility owned by developers.

---

## 10. Parity Summary — AEM Editable Templates vs Target

| Capability | AEM 6.4 Editable Template | Target (EDS + AEM Authoring) |
|---|---|---|
| Initial content for new pages | Yes | **Yes** — via "Use Page as Template" |
| Template structure governs pages | Yes (structure + inheritance) | **No** — pages are independent copies |
| Template change propagates to existing pages | Yes (structure) | **No** |
| Allowed components — per container/zone | Via template policy | **Yes** — via `component-filters.json` |
| Allowed components — per template/page type | Via template policy | **Via code** — `editor-support.js` keyed on template body class (no policy model) |
| Editability rules (lock section, restrict edits) | Via policy / component config | **Via code** — `editor-support.js` |
| Page-type-specific styling/behaviour | Template + policy | **Via `template` body class** + CSS/JS |
| Managed via a Template Editor UI | Yes | **No** — governance defined in code |
