# Page Properties — Solution Design

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> This document is a companion to the **Templates** solution design and describes how custom **page properties** are implemented in the target model.

---

## 1. Overview

In AEM 6.4, TFS uses a number of **custom page properties** — values authors set on a page (for example, which division a page belongs to, and other business/computation flags) that support business logic. These are added today by **overlaying the out-of-the-box page-properties dialog** and surfacing custom fields in the **Basic / Advanced** tabs; the values are stored on the page and read by server-side code.

This document explains how custom page properties are implemented in the target model (EDS with AEM as the authoring source): **where the fields are defined, where authors edit them, and how the values are consumed** — since the target model uses a different mechanism than dialog overlays and does not render pages server-side.

---

## 2. What Changes in the Target Model

| Aspect | AEM 6.4 (current) | Target (EDS + AEM Authoring) |
|---|---|---|
| How fields are defined | Custom **`cq:dialog` overlay** on the OOTB page-properties dialog | Defined in a **`page-metadata` component model** (`component-models.json`) |
| Where authors edit them | Page Properties dialog (Basic / Advanced tabs) | **Page Properties in the Universal Editor** |
| Where values are stored | Page `jcr:content` node | Page node (AEM authoring) — unchanged |
| How values are consumed | Read by **server-side** Sling Models / HTL for rendering & logic | Emitted as page metadata (e.g. `<meta>` / body class) and consumed by **block CSS/JS**, at the **edge**, or by **AEM authoring-side logic** — **not** by server-side rendering |

Two shifts matter: **(a)** the field definition moves from a dialog overlay to a **model**, and **(b)** because there is no server-side page rendering in EDS, the way property values are *used* changes (Section 4).

---

## 3. Where Page Properties Are Defined and Authored (Target)

Custom page properties are defined through a **`page-metadata` component model** and edited by authors in the Universal Editor Page Properties.

### 3.1 The `page-metadata` component model

Per Adobe's content-modeling guidance, a component model for custom page metadata is created with the **ID `page-metadata`** in **`component-models.json`**. The fields defined there are made available to the author in the Universal Editor.

```json
{
  "id": "page-metadata",
  "fields": [
    {
      "component": "text",
      "name": "division",
      "label": "Division"
    }
    // ... additional custom page properties
  ]
}
```

- Each entry in `fields` becomes a **field in the Page Properties panel** the author fills in.
- AEM automatically maps several **built-in page properties** (e.g. `title`, `description`, `robots`, `canonical`, `keywords`, `cq:tags`, last-modified/published times); custom TFS fields are added to the model alongside these.

### 3.2 Per-template (page-type) page properties

Where different **page types** need different property sets, per-template metadata models are supported using the naming convention **`<template>-metadata`**, where `<template>` matches the value stored in the page's `template` metadata property (see the Templates solution design).

- This allows, for example, a division-specific or page-type-specific set of properties to appear only for pages of that type.
- It is the target mechanism closest to "different properties on different templates."

### 3.3 Configuration files involved

| File | Role |
|---|---|
| `component-models.json` | Defines the **`page-metadata`** (and any `<template>-metadata`) model — i.e. the page property **fields**. |
| `component-definition.json` | Lists available components/blocks (catalog). |
| `component-filters.json` | Controls containment/authoring behaviour for components. |

> **Recommended practice:** custom page properties are **modelled once** in the `page-metadata` model (and per-template models where a page type needs its own set), rather than recreating dialog overlays. Authors then set the values per page in the Universal Editor Page Properties.

---

## 4. How Property Values Are Consumed

Because the target model has **no server-side page rendering**, the way a page property is *used* must be chosen per property. The value is authored the same way (Section 3); how it is consumed depends on its purpose:

| Purpose of the property | Target implementation |
|---|---|
| Page-type / styling / behavioural flag (e.g. division driving look or behaviour) | Emit as a **body class / metadata value**; **block CSS/JS** keys off it |
| SEO / head metadata | Emitted as a `<meta>` tag via the metadata mechanism |
| Logic used by a block on the page | Value is available to **block JavaScript**, which reads it and computes client-side |
| Logic that must run at delivery/request time | Implemented in an **Edge Worker** (delivery layer) — not in AEM |
| Logic used by AEM authoring processes (e.g. workflows, rollout, authoring services) | **Remains in AEM** — authoring-side code can still read the property from the page node |

**Key distinction:** authoring-side consumers (workflows, rollout, authoring services) can **still read** these properties, because authoring remains in AEM. What changes is that **rendering/delivery logic that previously read these properties server-side must move to the client (block JS) or the edge (Edge Worker)** — since EDS does not render pages server-side.

---

## 5. Limitations and Considerations

1. **No `cq:dialog` overlay model.** Existing dialog overlays are not carried forward; custom fields are **re-modelled** in the `page-metadata` (and `<template>-metadata`) model. This is a re-implementation, not a lift-and-shift of the dialog code.

2. **No server-side rendering of property-driven logic.** Any logic that previously used a page property to **render** content server-side must be **re-designed** to run client-side (block JS) or at the edge (Edge Worker). Properties whose only role was driving server-side rendering need their consumption re-planned (Section 4).

3. **Field-type / widget parity.** Standard field types are available in the model (text, select, boolean, etc.), but **complex custom AEM dialog widgets** (bespoke multifields, custom pathfields with special logic, custom validation) may not have an exact model equivalent and may need simplification or a custom authoring approach.

4. **Property curation.** The current system has many accumulated page properties. As part of implementation, TFS confirms **which properties are still required** and their purpose; only those are modelled and their consumption designed. (Blindly re-modelling every legacy property is not recommended.)

5. **Governance is code-defined.** As with templates, the page-property model and any per-template variations are **defined in code** by developers; authors set the values. There is no dialog-overlay authoring of the field definitions themselves.

---

## 6. Ownership

| Concern | Owner |
|---|---|
| Defining the `page-metadata` / `<template>-metadata` models (the fields) | Adobe / Developers |
| Implementing consumption (body class / meta / block JS / Edge Worker) | Adobe / Developers |
| Confirming which page properties are required and their purpose | TFS |
| Setting property values per page | TFS Authors |

---

## 7. Reference

- [Content modeling for AEM authoring projects (aem.live)](https://www.aem.live/developer/component-model-definitions) — defines the `page-metadata` component model and per-template (`<template>-metadata`) models.
