# Architecture

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## 1. Architecture Overview

The target TFS architecture is a modern, layered, **edge-first** solution that combines **AEM as a Cloud Service** (authoring), the **Universal Editor** (in-context authoring), and **Edge Delivery Services (EDS)** (delivery) to serve high-performance, edge-rendered experiences.

**AEM remains the authoring source of truth.** Authors work in AEM using the Universal Editor; content changes persist in AEM and are then **published to Edge Delivery Services**. In parallel, front-end code (blocks, styles, scripts, and configuration) is maintained in **GitHub** and deployed to the EDS runtime via **code sync**. When a page is requested, the runtime combines the latest **published content from AEM** with the **deployed code** to render **semantic HTML**, which is cached and served through the **CDN** to end users.

AEM Assets (as a Cloud Service) provides the centralized **Digital Asset Management (DAM)** for images and rich media, with assets optimized and delivered at the edge. Where dynamic or shared elements are required at delivery, an **Edge Worker** operates at the edge as a lightweight middleware layer, and **App Builder** (Adobe I/O Runtime) provides off-platform services/integrations that are invoked on demand. Personalization and analytics are enabled through **client-side** integrations, keeping them decoupled from the authoring layer.

The architecture highlights four key flows:
- **Authoring flow** — Universal Editor → AEM → EDS
- **Code sync** — GitHub → EDS runtime
- **Content sync** — AEM publish events → EDS content bus
- **Page delivery** — CDN / edge → end user

---

## 2. Key Architectural Principles

### 2.1 AEM as the Single Source of Truth
All web content is authored and managed in **AEM as a Cloud Service** using the Universal Editor. Content — including MSM/inheritance, translation, and workflow governance — lives in AEM. EDS consumes only **published** content; it does not author or own content.

### 2.2 Edge-Based Delivery with EDS
Published content is delivered through **Edge Delivery Services**, leveraging the CDN for global performance and scalability. Pages are served as **optimized semantic HTML**, rendered/decorated by EDS blocks at the delivery tier — aligned with modern performance and SEO best practices. There is no traditional Publish instance or Dispatcher in the delivery path.

### 2.3 Edge Worker as Middleware Layer
Where required, an **Edge Worker** operates at the edge as a lightweight middleware between the browser and backend systems — for example, to inject shared elements (header/footer), assemble dynamic content (e.g. product list), or inject structured data. It intercepts the delivery response at request time; its use is scoped to confirmed use cases.

### 2.4 Client-Side Personalization and Analytics
Personalization, experimentation, and analytics are enabled via **client-side** integrations that operate in the browser using JavaScript SDKs — avoiding tight coupling with the authoring layer.

### 2.5 Git-Based Development and Code Sync
Front-end code, block definitions, and EDS configuration are managed in **GitHub**. Automated **code sync** propagates changes from GitHub to the Edge Delivery Services runtime. Authoring code for AEM (workflows, rollouts, customizations) is managed and deployed separately via the AEM Cloud codebase (Cloud Manager). *(See the platform/codebase architecture for the full codebase and deployment breakdown.)*

### 2.6 Secure Authoring and Access Control
Authors authenticate via **Thermo Fisher ID** (corporate identity), federated with **Adobe IMS**. IMS groups in the Admin Console manage team-level permissions, and AEM (JCR-based) ACLs govern repository access. Authoring and delivery access are managed with appropriate role separation (author vs admin; preview vs publish).

---

## 3. System Responsibility Matrix

| System | Primary Responsibility | Key Capabilities | Interactions / Consumers |
|---|---|---|---|
| **AEM Author (AEMaaCS)** | Content authoring & governance (source of truth) | Universal Editor authoring; MSM / inheritance; translation; workflows / rollouts; page metadata | Publishes content to EDS; references assets from AEM Assets; invokes App Builder for integrations |
| **Edge Delivery Services (EDS)** | Content delivery & runtime rendering | Edge-optimized delivery; HTML-first, block-based rendering; high performance & scalability | Serves content to end users via CDN; consumes published content from AEM; runs block decoration |
| **AEM Assets (DAM)** | Digital Asset Management | Centralized asset repository; image/video optimization & renditions; brand governance & reuse | Assets referenced by AEM content; delivered/optimized at the edge |
| **Edge Worker** *(where used)* | Edge middleware & content assembly | Header/footer injection; dynamic content assembly (e.g. product list); structured-data injection | Runs at the CDN/edge at request time; may call backend services / App Builder |
| **App Builder (Adobe I/O Runtime)** *(where used)* | Off-platform services / integrations | Backend/data fetches; API orchestration for authoring/delivery use cases | Invoked on demand by AEM authoring integrations and/or the edge |
| **CDN** | Edge caching & delivery | Global caching; secure, high-performance delivery; HTTPS enforcement | Serves cached content to end users; fronts EDS |
| **Identity (Thermo Fisher ID + Adobe IMS)** | Authentication & access control | Corporate SSO; IMS group-based permissions | Governs authoring/admin access to AEM and EDS |
| **Client-side personalization & analytics** | Personalization, experimentation, measurement | Browser-side SDK integrations | Operate in the browser on delivered pages; decoupled from authoring |

> Edge Worker and App Builder are used **where applicable** to confirmed use cases; their scope is confirmed with TFS.

---

## 4. Content Flow — Authoring to Delivery

1. **Author** creates/edits content in AEM using the **Universal Editor**; content persists in **AEM** (the source of truth), with MSM/inheritance, translation, and workflow governance applied on the authoring tier.
2. On **publish**, a publish event flows through the Edge Delivery pipeline and the content is **ingested into the EDS content bus** (assets are served from AEM Assets / DAM).
3. In parallel, front-end **code** (blocks, styles, scripts, config) is deployed from **GitHub** to the EDS runtime via **code sync**.
4. On a page request, EDS combines the **published content** with the **deployed code** to render **semantic HTML**; where configured, an **Edge Worker** augments the response at the edge (e.g. header/footer, dynamic content, structured data).
5. The rendered HTML is **cached and served via the CDN** to the end user. Client-side personalization/analytics SDKs run in the browser on the delivered page.

---

## 5. Logical Architecture Diagram

```
        AUTHORING TIER                              DELIVERY TIER
 ┌───────────────────────────┐            ┌──────────────────────────────────┐
 │   AEM Author (AEMaaCS)     │            │        Edge Delivery Services      │
 │   Universal Editor          │  publish   │                                   │
 │   • MSM / inheritance       │──────────▶ │   Content Bus  →  render (blocks) │
 │   • Translation             │  (content  │                        │          │
 │   • Workflows / rollouts    │   sync)    │                        ▼          │
 │   • Page metadata           │            │                   CDN (Edge)      │
 └───────────────────────────┘            │              ┌───────────────┐    │
        ▲                                   │              │  Edge Worker   │    │
        │ references                        │              │  (where used)  │    │
        │                                   │              └───────┬───────┘    │
 ┌───────────────┐                          │                      ▼            │
 │  AEM Assets    │                          │                  End Users        │
 │  (DAM)         │                          └──────────────────────────────────┘
 └───────────────┘
        ▲
        │ code sync (blocks, styles, scripts, config)
 ┌───────────────┐          ┌───────────────────────────┐
 │   GitHub       │          │  App Builder (I/O Runtime) │
 │  (EDS code)    │          │  invoked on demand         │
 └───────────────┘          └───────────────────────────┘
```

*(Reference logical diagram — the authoring flow, code sync, content sync, and page delivery flows described in Sections 1 and 4.)*
