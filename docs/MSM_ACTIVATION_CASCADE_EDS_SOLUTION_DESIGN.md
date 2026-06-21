# MSM Activation Cascade — EDS Solution Design

**Document Type:** Solution Design (Draft)
**Version:** 0.1
**Date:** June 2026
**Status:** Draft for Review

---

## 1. Executive Summary

This document addresses the **MSM Activation Cascade** use case for the Thermo Fisher migration to AEM Edge Delivery Services (EDS) with AEM as the authoring source (Crosswalk).

In the current AEM environment, activating (publishing) a page from the global node cascades publication down through the regional and country live copies, so that a single global activation propagates to all dependent markets. This behavior is delivered today through custom code.

This document covers:
- The **risk** associated with cascading activation to live copies, and Adobe's recommendation.
- How the activation cascade can be achieved **out-of-the-box** using the **"Activate on Blueprint activation"** rollout configuration, and what it does and does not do (including the rollout-vs-activation distinction).
- How **publication works** from AEM authoring to Edge Delivery, given that the legacy replication-agent and Dispatcher model no longer applies.

> **Note:** Analysis of the current custom codebase (how activation cascade is implemented today and why it requires re-evaluation on AEM as a Cloud Service) is documented separately and is out of scope for this document.

---

## 2. Adobe Recommendation — Cascading Activation Carries Content-Governance Risk

Cascading **activation** (publication) to live copies — for example, automatically publishing regional and country live copies when the source/blueprint is activated — should be approached with caution. Activation publishes **whatever content currently exists on each live copy's author node**; it does not validate whether that content is in sync, reviewed, or intended for publication.

### 2.1 Key Risks

| Risk | Description |
|---|---|
| **Incorrect or unintended content published** | A live copy may hold local overrides or in-progress changes on its author node. A cascaded activation would publish that content to production as-is, increasing the likelihood of incorrect content being released. |
| **Stale content published** | If a live copy has not been rolled out (content-synced) prior to activation, the cascade publishes its outdated content, creating a false impression that all markets are current. |
| **Wide blast radius** | A single global activation propagates across all dependent markets simultaneously, making errors harder to detect and to roll back on a per-market basis. |

### 2.2 Recommendation

Given the above, **automatically cascading activation across live copies is not recommended**, as it increases the risk of incorrect or unintended content being published across markets. The appropriate publication behavior should be determined in consultation with the business as part of the broader solution design.

---

## 3. Achieving Activation Cascade Out-of-the-Box

AEM provides a standard (out-of-the-box) rollout configuration named **"Activate on Blueprint activation"** that delivers the activation cascade without custom code.

**What it does, in simple terms:** when the source (blueprint) page is activated, all of its live copies are also activated (published) automatically — **provided each live copy has this rollout configuration applied.** A single activation on the source therefore publishes the source and its live copies together.

**What it does not do:** it does **not** copy content. It only publishes; it does not synchronise content from the source to the live copies. Each live copy is published with whatever content currently exists on it.

This is because **rollout** and **activation** are two separate operations in AEM:

| Operation | What it means |
|---|---|
| **Rollout** | Copies (synchronises) content from the source page to the live copy. Happens on AEM Author only — nothing is published. |
| **Activation** | Publishes the current content of a page to delivery. It does not copy any content between pages. |

The "Activate on Blueprint activation" configuration covers the **activation** part only. So if content has changed on the source, the change must first be **rolled out** to the live copies, and then **activation** publishes it. (Confirmed in our proof-of-concept: activating the source cascaded activation to its live copies, but updated content appeared on the delivered URL only after a rollout was performed first, followed by activation.)

A page can have **more than one rollout configuration** applied at the same time; they work together. For example, a configuration that synchronises content and the "Activate on Blueprint activation" configuration can both be applied to the same live copy.

> **To confirm:** the configuration must be present on each live copy in the chain for the cascade to reach it. The exact multi-level reach (direct and indirect live copies) should be validated against the final content structure.

---

## 4. How Publication Works — From AEM Authoring to Edge Delivery

This section clarifies, at a high level, how a page published in AEM reaches Edge Delivery in the Crosswalk model. Importantly, **the traditional AEM publish-instance, replication-agent and Dispatcher model does not apply here** — publishing is an event-driven flow in which Edge Delivery fetches content from AEM.

**Publish and delivery flow** (per Adobe documentation, [*Publishing from AEM Authoring*](https://www.aem.live/docs/publishing-from-authoring), aem.live):

1. **AEM Authoring (Universal Editor):** the content author publishes the page.
2. **AEM Page Publish Events → AEM Publish Queue:** the publish action raises page publish events onto the publish queue.
3. **Edge Delivery Subscriber Service:** subscribes to the page publish events from the queue (the "Page Publish Event Subscriber"), and on receiving an event, requests a content fetch.
4. **Edge Delivery Services Rendering and Delivery:** on the content-fetch request, requests the page and assets from AEM.
5. **AEM / HTL Page Rendering and Asset Delivery:** renders and delivers the HTML page with assets back to Edge Delivery, which then renders and serves it.

**In short:** AEM acts as the **content source**. When a page is published, a publish event flows through the **AEM Publish Queue** to the **Edge Delivery Subscriber Service**, which triggers **Edge Delivery** to **fetch the rendered HTML page and assets from AEM** and serve them. There is no replication to a separate publish instance and no Dispatcher in this path.

```
                    Edge Delivery Services
                    Rendering and Delivery
                       ▲                 │
       request content │                 │ request page
       fetch           │                 ▼ and assets
       Edge Delivery              AEM / HTL Page Rendering,
       Subscriber Service            Asset Delivery
                       ▲                 │
   page publish event  │                 │ deliver HTML
   subscriber          │                 │ page with assets
                AEM Publish Queue         │
                       ▲                 │
   AEM page            │                 │
   publish events      │                 │
                AEM Authoring  ◄──────────┘
                (Universal Editor)
```

> **Security note:** per Adobe documentation, the Edge Delivery Services Admin API is by default not protected and can be used to publish or unpublish without authentication. Access control around it is a configuration consideration.

---

## 5. Summary

| Topic | Conclusion |
|---|---|
| **Activation cascade — OOTB** | The standard "Activate on Blueprint activation" rollout configuration cascades **publication** to live copies when the source is activated (if each live copy has the configuration). It publishes only — it does not synchronise content. |
| **Rollout vs. Activation** | Rollout copies content to live copies (author only); activation publishes a page's current content to delivery. To publish a content change, roll out first, then activate. |
| **Publication to Edge Delivery** | Event-driven: a publish event flows through the AEM Publish Queue to the Edge Delivery Subscriber Service, which triggers Edge Delivery to fetch the rendered HTML page and assets from AEM. No replication agents and no Dispatcher. |
| **Recommendation** | Automatically cascading activation across live copies is **not recommended**, as it increases the risk of incorrect, unintended, or stale content (including local overrides) being published. Appropriate publication behavior to be agreed with the business. |

---

## 6. Open Items for Confirmation

1. Confirm the multi-level **cascade reach** of "Activate on Blueprint activation" for the target live-copy structure (direct and indirect live copies), and that the configuration is applied at each level.
2. Confirm the desired **publication behavior** with the business, given that automatic activation cascade is not recommended.
3. Confirm **access control** requirements for the Edge Delivery Services Admin API.
