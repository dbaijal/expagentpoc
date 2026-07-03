# Content Flow — Publish and Delivery

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## Overview

This document describes the end-to-end content flow for the TFS Edge Delivery Services (EDS) implementation, with **AEM as the authoring source** and **Akamai** as the customer-facing CDN.

There are two complementary flows:

- **Publish flow (write path)** — how authored content in AEM is published and made available to Edge Delivery Services.
- **End-user rendering flow (read path)** — how published content is delivered to end users through the CDN and Edge Delivery Services.

Content is authored in AEM using the Universal Editor and persisted in the JCR; on publish it is ingested by Edge Delivery Services and served — via Akamai — to end users. The authoring systems are never exposed directly to public traffic.

---

## 1. Publish Flow (Write Path)

When using the Universal Editor to author AEM content, publishing is as simple as clicking the **Publish** button in the Universal Editor. Once the author initiates publication, the flow is automatic:

1. The content author publishes AEM content in the **Universal Editor**.
2. A **publish event** is pushed to the Adobe pipeline queue.
3. The **Edge Delivery Services publish service** forwards the relevant events to the **Edge Delivery Services Admin API**.
4. **Edge Delivery pulls and ingests the semantic HTML from AEM Author.**
5. **AEM is updated with the publish status.**

The result is that the published, semantic HTML for the page is available in Edge Delivery Services for delivery. There is no replication to a separate publish instance and no Dispatcher in this path — Edge Delivery ingests directly from AEM Author.

> Reference: [Publishing from AEM Authoring (aem.live)](https://www.aem.live/docs/publishing-from-authoring)

---

## 2. End-User Rendering Flow (Read Path)

This describes how published content is delivered to end users, with Akamai as the customer-facing CDN fronting Edge Delivery Services.

### 2.1 End-User Request Initiation
An end user navigates to a TFS website URL. The browser sends the request to the public site domain, which is fronted by **Akamai CDN** (the customer / BYO CDN, in front of Edge Delivery Services). Akamai acts as the first caching and delivery layer for TFS public web traffic. Where configured, an **edge worker** also runs at this edge layer and participates in the response (see Section 2.5). *(The term "edge worker" is used generically for edge-compute logic at this layer; the specific platform — e.g. a CDN edge worker or Adobe edge compute/functions — is a downstream decision and does not change the flow described here.)*

### 2.2 Akamai Cache Evaluation
Akamai evaluates whether the requested HTML response is already available in its edge cache:
- **Cache hit:** Akamai returns the cached response directly to the browser, minimizing latency and reducing origin traffic.
- **Cache miss, expired, or invalidated entry:** Akamai forwards the request to the downstream Adobe Edge Delivery origin chain for response generation or cache retrieval.

This caching layer absorbs the majority of repeat traffic close to the end user while preserving fast time-to-first-byte for published pages.

### 2.3 Edge Delivery Request Resolution
The request is received by **Edge Delivery Services**, which evaluates the incoming host, path, and request metadata against the TFS site configuration. Using this configuration, Edge Delivery Services:
- Identifies the correct TFS site or regional variant
- Resolves the requested page path
- Applies the appropriate routing and delivery rules for the site

This enables multiple sites, locales, or regional variants to be served through a unified Edge Delivery architecture while preserving site-specific behaviour.

### 2.4 Response Assembly
Edge Delivery Services assembles the HTML response using:
- The **published semantic content** sourced from **AEM (the authoring source), served from the Edge Delivery content bus**
- The **TFS EDS codebase** — blocks, CSS, JavaScript, and site configuration

The runtime returns semantic, edge-optimized HTML for the requested page, along with the appropriate references to stylesheets, scripts, images, and other assets required by the experience.

### 2.5 Edge Worker Augmentation (Where Configured)
Because the edge worker runs on the **edge layer, in front of Edge Delivery Services**, it can **augment the response at the edge** as the Edge Delivery response passes back through to the browser. Where configured for a given use case, the edge worker may:
- Inject shared elements such as the **header/footer** (from the TFS header/footer microservice)
- Assemble **dynamic content** (e.g. product list) by calling backend services
- Inject **structured data** (JSON-LD) into the HTML

The edge worker **augments** the response; it does not replace Edge Delivery response assembly (Section 2.4) — Edge Delivery produces the base semantic HTML, and the edge worker adds edge-time augmentation. Its use is **scoped to confirmed use cases**; requests for content that needs no edge augmentation pass through unchanged.

### 2.6 Published Content Retrieval and Refresh
Published content is delivered by Edge Delivery Services from its delivery layer and associated caches. When newly published content becomes available, or when cached content has been invalidated, Edge Delivery Services serves the latest published content from the **Edge Delivery content/media bus** (populated on publish from AEM). This process is transparent to the end user and ensures that published authoring changes are reflected in delivery **without exposing the authoring systems directly to public traffic**.

### 2.7 Downstream Caching
Once the response is generated, it is cached through the Adobe-managed delivery layers according to Edge Delivery Services caching behaviour and response headers. The response is then returned to Akamai, which applies its own cache policies for the public domain. This layered caching strategy improves performance, scalability, and resilience while allowing content changes to propagate quickly through targeted invalidation.

### 2.8 Final Delivery to the Browser
Akamai returns the HTML response to the end user's browser. The browser then requests referenced assets — JavaScript, CSS, images, and fonts — which are also served through the configured CDN and Edge Delivery delivery path. Subsequent requests for the same page are typically served from the Akamai cache until the entry expires or is invalidated following a content update.

---

## 3. Summary

| Flow | Direction | Path |
|---|---|---|
| **Publish** | Write | Author (Universal Editor) → publish event → EDS publish service → EDS Admin API → EDS ingests semantic HTML from AEM Author → publish status back to AEM |
| **Rendering** | Read | End user → Akamai (cache; Edge Worker augmentation *where configured*) → Edge Delivery Services (resolve + assemble from published content + EDS codebase) → response cached at Adobe layers and Akamai → delivered to browser |

Authoring happens in AEM; delivery happens through Edge Delivery Services fronted by Akamai. Published content is served from the Edge Delivery content/media bus, and the authoring systems are never exposed to public traffic.
