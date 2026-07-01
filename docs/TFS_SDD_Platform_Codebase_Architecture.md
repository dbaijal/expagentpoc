# Target Platform, Codebase & Deployment Architecture

### (AEM as Authoring Source with Edge Delivery Services)

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services (EDS)

---

## 1. Purpose

This document gives the client technical architects a clear mental model of the **target platform architecture** — specifically **which codebases exist, what each contains, how each is deployed, and — importantly — what code is NOT migrated** in the move to Edge Delivery Services with AEM as the authoring source.

It intentionally does not describe generic AEM Cloud or EDS concepts in the abstract. It describes **the architecture TFS will actually operate**, so the implementation team knows what code stays in AEM, what is replaced, and where each concern lives.

---

## 2. What "EDS with AEM as Authoring Source" Means

In the target model:

- **AEM (as a Cloud Service) is the authoring platform.** Authors create and manage pages, fragments, MSM/inheritance, translation, and workflows in AEM Author — as they do today.
- **On publish, content is delivered to Edge Delivery Services.** The published content is served from the **Edge Delivery content bus / media bus** and delivered to users via the CDN.
- **There is no Publish instance and no Dispatcher** in the traditional sense. Delivery is handled by Edge Delivery, not by a server-side Publish tier rendering HTL/JSP behind a Dispatcher.
- **Rendering happens at the Edge Delivery tier**, using **EDS blocks** (client-side JavaScript/CSS that decorate the delivered semantic HTML) — not server-side AEM component rendering.

### 2.1 Target Architecture — High Level

```
        AUTHORING TIER                          DELIVERY TIER
 ┌───────────────────────────┐         ┌───────────────────────────────┐
 │   AEM Author (AEMaaCS)     │         │      Edge Delivery Services     │
 │                           │ publish  │                                │
 │  • Page authoring (UE)     │────────▶│  Content Bus / Media Bus        │
 │  • MSM / inheritance       │         │            │                   │
 │  • Translation             │         │            ▼                   │
 │  • Workflows / rollouts     │         │   ┌─────────────────┐          │
 │  • Custom authoring code    │         │   │  Edge Worker     │  (edge, │
 │                            │         │   │  (where used)    │  request │
 └───────────────────────────┘         │   └─────────────────┘   time)   │
              │                          │            │                   │
              │ calls                    │            ▼                   │
              │ (integrations)           │           CDN ──────▶ End Users│
              ▼                          └───────────────────────────────┘
   ┌───────────────────────┐                          │
   │  App Builder           │ ◀────────────────────────┘
   │  (Adobe I/O Runtime)   │   invoked as a service (e.g. by edge/blocks)
   │  (where used)          │   for backend/data fetches & integrations
   └───────────────────────┘
```

**Reading the diagram:**
- **AEM Author** is the authoring tier; on **publish**, content flows to the **Edge Delivery** content/media bus and is served via the **CDN**. There is no separate Publish instance or Dispatcher.
- **Edge Worker** (where used) runs **at the edge**, on the delivery path, at request time.
- **App Builder** (where used) is **not** part of the delivery pipeline — it is a **separate service on Adobe I/O Runtime that is invoked** (by AEM author-side integrations, or by edge/blocks) for backend/data fetches and integrations. It sits alongside, called on demand, not inline in delivery.

**Key consequence:** because delivery/rendering no longer happens on a server-side AEM Publish tier, the **code that produced server-side rendering in AEM 6.4 is not carried forward** (Section 6). This is a **replatforming**, not a lift-and-shift of the existing codebase.

---

## 3. The Codebases Involved

The target solution is **not a single codebase.** It is composed of up to four distinct codebases, each with its **own repository, build, and deployment path**:

| Codebase | Purpose | Technology | Repository | Build / Deployment | Runs / Renders |
|---|---|---|---|---|---|
| **AEM Cloud codebase** | Authoring-platform code (workflows, rollouts, authoring customizations, repo-init) | Java / OSGi, Maven multi-module | Git (Cloud Manager–connected) | **Cloud Manager CI/CD pipeline** → AEMaaCS | AEM Author (authoring time) |
| **EDS codebase** | Delivery rendering — blocks, styles, scripts | JavaScript / CSS | Separate Git repo | **EDS code sync** (Git push → EDS pipeline) | Edge Delivery tier (client-side) |
| **Edge Worker codebase** *(where used)* | Edge-level logic (e.g. header/footer stitching, structured-data injection) | JavaScript (edge runtime) | Separate Git repo | Edge Worker deployment pipeline | CDN / edge (request time) |
| **App Builder codebase** *(where used)* | Off-platform services / integrations (e.g. backend data fetches, GCMS/commerce calls) | JavaScript (Adobe I/O Runtime) | Separate Git repo | App Builder deployment | Adobe I/O Runtime (service) |

> **Note:** Edge Worker and App Builder are used **only where applicable** to confirmed use cases. Their scope is subject to TFS confirmation of the relevant requirements (see Assumptions).

The critical point for the architect: **these are separate repositories with separate deployment mechanisms.** EDS code is **not** part of the AEM Maven repository, and AEM code is **not** deployed through the EDS code-sync pipeline. They are independent.

---

## 4. Deployment of Each Codebase

| Codebase | How it is deployed |
|---|---|
| **AEM Cloud** | Committed to the Cloud Manager–connected Git repo; built and deployed via the **Cloud Manager CI/CD pipeline** (with its build, code-quality, and deployment stages) to AEMaaCS Author. |
| **EDS** | Committed to its Git repo; **code sync** picks up the change and the EDS pipeline makes blocks/scripts/styles available at the Edge Delivery tier — no Maven build, no Cloud Manager. |
| **Edge Worker** *(where used)* | Deployed to the edge/CDN runtime through its own deployment pipeline, independent of AEM and EDS code sync. |
| **App Builder** *(where used)* | Deployed to Adobe I/O Runtime through the App Builder deployment tooling, independent of the others. |

The takeaway: **four different deployment tracks**, each triggered and operated independently.

---

## 5. Repository Structure and What Goes in Each

### 5.1 AEM Cloud Codebase (Maven multi-module)

The AEM Cloud repository follows the standard AEM as a Cloud Service Maven multi-module structure:

```
aem-cloud-repo/
├── core/          → Java: OSGi services, custom workflow steps, listeners
│                    (authoring-side logic only — not rendering)
├── ui.apps/       → Immutable code under /apps
│                    (authoring UI customizations, overlays)
├── ui.config/     → OSGi configuration (run-mode based)
├── ui.content/    → Bootstrap/structural content under /conf, /content
│                    (e.g. baseline configuration nodes)
├── all/           → Container package that aggregates the above
└── dispatcher/    → Dispatcher config (as applicable for the author/edge setup)
```

> **Important nuance:** several of these modules are **much lighter than in a traditional AEM implementation**, because **rendering code is not migrated** (Section 6). For example, `ui.apps` contains authoring-side customizations and overlays — **not** the rendering components, HTL scripts, and client libraries that a server-rendered AEM site would carry.

**What goes in the AEM Cloud codebase — authoring-platform code only:**

- **Workflows** — the in-scope workflow models and any custom workflow steps.
- **Rollout / MSM customizations** — custom rollout configs/logic supporting the inheritance model.
- **Authoring customizations / overlays** — e.g. a custom **relocate** feature, custom authoring-console behaviour.
- **Translation customizations** — any custom logic supporting the translation workflow.
- **Repo-init** — groups, ACLs, service users (Section 8).
- **OSGi services/configs** for the above (Section 7).

### 5.2 EDS Codebase

```
eds-repo/
├── blocks/        → EDS blocks (JS + CSS) — the rendering/decoration units
├── styles/        → Global styles
├── scripts/       → Core scripts (aem.js, scripts.js), decoration logic
├── head.html      → Global <head> (favicon, global metadata, structured data)
├── fstab.yaml     → Content source mapping
└── (config)       → helix-query.yaml, helix-sitemap.yaml, redirects, etc.
```

**What goes here:** everything that renders/decorates content at the Edge Delivery tier — **blocks, styles, scripts** — plus delivery configuration (sitemaps, redirects, metadata head).

### 5.3 Edge Worker Codebase *(where used)*

Edge-level JavaScript for logic that must run at request time at the edge — for example **header/footer stitching, Product List, Video**, or structured-data injection into the HTML. Its own repo and deployment.

### 5.4 App Builder Codebase *(where used)*

Adobe I/O Runtime actions for **off-platform services / integrations** — for example backend/data fetches (e.g. GCMS or other systems) that need to be called securely from, or on behalf of, the authoring/delivery experience. Its own repo and deployment.

---

## 6. What Is NOT Migrated (Critical Clarification)

Because Edge Delivery does **not** render pages server-side, the AEM 6.4 code that existed **to render pages on the Publish tier is not migrated** — it has no equivalent target and would not be reused. This is the single most important point for the client's implementation team.

**Not migrated / replaced by EDS blocks:**

| AEM 6.4 (server-side rendering) | Target |
|---|---|
| HTL / JSP rendering scripts | **Not migrated** — rendering handled by EDS blocks |
| Rendering Sling Models (WCMUse / component models used to render) | **Not migrated** — content is authored and served; blocks decorate |
| Rendering servlets / component rendering logic | **Not migrated** |
| Component dialogs used for server-side components | Replaced by EDS block model / Universal Editor authoring |
| Client libraries (clientlibs) for component rendering | Replaced by EDS block JS/CSS |
| Dispatcher / rewrite rules for delivery | Replaced by Edge Delivery + CDN |

**In short:** the **delivery/rendering layer is rebuilt as EDS blocks**, not migrated as code. Only **authoring-platform code** (Section 5.1) is carried into the AEM Cloud codebase. This is why the target AEM codebase is comparatively small and focused.

> This directly reframes several traditional AEM-Cloud-migration concerns. There is **no wholesale migration of rendering Sling Models/servlets** — that layer is replaced by EDS blocks. Consequently, **package decomposition, immutable (`/apps`) vs mutable (`/conf`, `/content`) boundaries, OSGi configuration, and Sling Model migration apply only to the small residual authoring codebase**, not to a full rendering application. Authoring-side Java (workflow steps, rollout logic, listeners) is reviewed for AEM as a Cloud Service compatibility and carried into the AEM Cloud codebase.

---

## 7. OSGi Configuration and Secrets (for the residual AEM code)

The OSGi/config strategy applies **only to the authoring-platform code that remains in the AEM Cloud codebase** — it is not a large rendering-config surface.

- **Environment-specific configuration** is managed via **run-mode based OSGi configs** in `ui.config` (e.g. author configs, per-environment values).
- **Secrets and sensitive values** (for example, **SMTP credentials used when workflows send email notifications**, or integration credentials) are **not** committed as plaintext configs. They are provided as **Cloud Manager environment variables / secret configurations** and referenced by the OSGi configuration.
- Only services genuinely needed for **authoring** remain (workflow, rollout, translation support, integrations). Services that existed to support **server-side delivery** are not present, per Section 6.

This addresses "where do environment values and secrets live" — they live in **run-mode configs (non-secret)** and **Cloud Manager secrets (sensitive)**, scoped to the residual authoring code.

---

## 8. Repo-init — Groups, ACLs and Service Users

Access control and service identities are provisioned via **repo-init** in the AEM Cloud codebase:

- **Groups and ACLs** — author/approver groups and their permissions (supporting the workflow governance model).
- **Service users** — for any custom authoring-side services that require repository access.
- **Identity** — users/groups are IMS-backed and managed in the Admin Console; repo-init establishes the AEM-side groups/permissions they map to.

---

## 9. Summary

| Codebase | Contains | Deployment | Renders/Runs |
|---|---|---|---|
| **AEM Cloud** | Authoring-platform code: workflows, rollouts, authoring customizations (e.g. relocate), translation customizations, repo-init, OSGi/secrets | Cloud Manager CI/CD | AEM Author |
| **EDS** | Blocks, styles, scripts, head, delivery config | EDS code sync | Edge Delivery (client-side) |
| **Edge Worker** *(where used)* | Edge-time logic (header/footer, Product List, Video, etc.) | Edge Worker pipeline | CDN / edge |
| **App Builder** *(where used)* | Off-platform services / integrations | App Builder deployment | Adobe I/O Runtime |

**The core message:** the target is a **multi-codebase architecture** with **independent repositories and deployments**; the **server-side rendering code from AEM 6.4 is not migrated** (it is rebuilt as EDS blocks); and the **AEM Cloud codebase is a focused, authoring-platform codebase**, not a full rendering application.

---

## 10. Assumptions and Open Items

| Item | Status |
|---|---|
| Edge Worker usage and scope | To be confirmed by TFS against specific use cases (e.g. header/footer, Product List, Video) |
| App Builder usage and scope | To be confirmed against integration/data-fetch use cases |
| Exact residual authoring-code inventory (which custom services/workflow steps carry over) | Finalised during implementation, based on review of the current custom code |
| Which OSGi configs become Cloud Manager secrets vs run-mode configs | Finalised during implementation |
