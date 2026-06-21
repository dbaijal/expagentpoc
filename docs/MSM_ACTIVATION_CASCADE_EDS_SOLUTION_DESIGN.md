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
- The **content-governance risk** associated with cascading activation, and Adobe's recommendation.
- The **out-of-the-box (OOTB)** AEM rollout configuration **"Activate on Blueprint activation,"** including precisely what it does and does not do.
- The **rollout vs. activation** distinction, which is fundamental to designing this correctly.
- The **publication model** in AEM as a Cloud Service with Edge Delivery — clarifying how a published page reaches EDS, given that the legacy replication-agent and Dispatcher model no longer applies.

> **Note:** Analysis of the current custom codebase (how activation cascade is implemented today and why it requires re-evaluation on AEM as a Cloud Service) is documented separately and is out of scope for this document.

---

## 2. Adobe Recommendation — Cascading Activation Carries Content-Governance Risk

Cascading **activation** (publication) from the global/blueprint node down to regional and country live copies must be approached with caution. Activation publishes **whatever content currently exists on each live copy's author node** — it does not validate whether that content is in sync, reviewed, or approved.

### 2.1 Key Risks

| Risk | Description |
|---|---|
| **Unapproved local overrides published** | Regional and country authors frequently break inheritance at the page or component level to apply local content. A live copy may hold an in-progress or unreviewed local override on its author node. A blanket cascade activation triggered from the global node would publish that local content to production **without any local review or approval gate.** |
| **Stale content published** | If a live copy has not been rolled out (content-synced) prior to activation, the cascade publishes its **outdated** content, creating a false impression that all markets are current. |
| **Local governance bypassed** | Many markets maintain their own review, approval, and translation-completion gates. A global-triggered cascade activation ignores these, allowing a single global publish action to force-publish content across many markets. |
| **Wide blast radius** | A single incorrect global activation propagates simultaneously across all markets, making errors harder to detect and harder to roll back on a per-market basis. |

### 2.2 Recommendation

Do **not** rely on an unconditional, automatic activation cascade. Instead:

- Treat **content rollout** (synchronization) and **activation** (publication) as **distinct, separately-governed steps.**
- Any cascade must **honor inheritance breaks and live-copy sync-cancellation flags**, and should **skip pages with pending or unapproved local changes.**
- Prefer a model in which a global change **cascades content synchronization and/or notifies the affected markets**, while **final publication remains under local or gated control** — particularly given Thermo Fisher's extensive use of local overrides and per-market translation/approval workflows.

This is fundamentally a **content-governance decision**, not solely a technical one, and should be confirmed with the business before implementation.

---

## 3. OOTB Capability — "Activate on Blueprint Activation" Rollout Configuration

AEM provides an out-of-the-box rollout configuration named **"Activate on Blueprint activation."**

### 3.1 Behavior

- **Trigger:** Activation (publication) of the **source / blueprint** page.
- **Action:** The associated **live copies are activated (published)** as part of the same event.
- **Critical distinction:** It performs **publication only — it does NOT perform a content rollout (synchronization).** It publishes whatever content already exists on each live copy's author node; it does **not** copy fresh content from the blueprint to the live copy.

### 3.2 Implication

The configuration alone does **not** propagate content *changes* to production. To publish updated content, a **rollout (content synchronization)** must occur **before** activation.

> **Validated in POC:** Activating the global node cascaded activation to both direct and indirect live copies, but the updated content only appeared on the delivered (EDS) URL after a separate rollout was performed, followed by publication.

### 3.3 Multiple Rollout Configurations

A live copy can have **multiple rollout configurations attached simultaneously.** They are **additive** — each fires on its own trigger. For example, "Standard rollout config" (content synchronization on modification/rollout) and "Activate on Blueprint activation" (publication on blueprint activation) can coexist on the same live-copy relationship.

---

## 4. "Activate on Blueprint Activation" — Detailed Behavior

The following table sets out, for clarity, exactly what this configuration does and does not do.

| Aspect | Behavior |
|---|---|
| **Triggered by** | Activation (publication) of the blueprint / source page |
| **What it does** | Activates (publishes) the live copy pages associated with that source |
| **Performs content sync (rollout)?** | **No** — it does not copy content from the blueprint to the live copy |
| **What gets published** | Whatever content is **currently on the live copy's author node** |
| **Cascade reach (OOTB)** | Resolves live-copy relationships; the multi-level reach should be validated for the specific implementation, as native relationship resolution is the limiting factor compared to custom recursive discovery |

### 4.1 Rollout vs. Activation — Two Orthogonal Operations

Understanding this distinction is essential to designing the activation cascade correctly:

| Operation | Definition | Where it operates | Copies content between nodes? | Result |
|---|---|---|---|---|
| **Rollout** | Content **synchronization** — copies content from the blueprint JCR node to the live copy JCR node | **AEM Author only** | **Yes** | Nothing is published; pages remain on author |
| **Activation** | **Publication** — pushes whatever currently exists on AEM Author to the delivery tier | Author → Delivery | **No** | Content is published to delivery |

**Neither operation implies the other.** "Activate on Blueprint activation" is purely the **publication** half. This is precisely why a content change requires a **rollout first, then activation**, in order to reach production.

---

## 5. Publication Model — How Content Reaches Edge Delivery from AEM Authoring (Crosswalk)

A key clarification for stakeholders: in AEM as a Cloud Service with Edge Delivery (Crosswalk), the **legacy replication-agent and Dispatcher model does not apply.** Publishing is an event-driven flow that **ingests** content into Edge Delivery, rather than replicating content to a separate publish instance.

### 5.1 Documented Publish Flow

Per Adobe documentation (*Publishing from AEM Authoring*, aem.live), when an author publishes, the following sequence occurs:

1. **The content author publishes AEM content in the Universal Editor.**
2. **A publish event is pushed to the Adobe pipeline queue.**
3. **The Edge Delivery Services publish service forwards the relevant events to the Edge Delivery Services Admin API.**
4. **Edge Delivery pulls and ingests semantic HTML from AEM Author.**
5. **AEM is updated with the publish status.**

### 5.2 Key Components

| Component | Role |
|---|---|
| **Universal Editor** | The authoring interface where the publish action originates |
| **Adobe pipeline queue** | Receives the publish event |
| **Edge Delivery Services publish service** | Forwards the relevant events to the Admin API |
| **Edge Delivery Services Admin API** | The integration point that drives ingestion |
| **AEM Author** | The source from which Edge Delivery pulls and ingests semantic HTML |

### 5.3 Security Note

Per the documentation, by default the **Edge Delivery Services Admin API is not protected** and can be used to publish or unpublish documents without authentication. Access control around the Admin API is therefore a configuration consideration for the implementation.

### 5.4 Implications for the Activation Cascade Use Case

- "Activation" (publish) in this model means the publish event flows through the **Adobe pipeline queue → Edge Delivery Services publish service → Edge Delivery Services Admin API**, and **Edge Delivery ingests the page's semantic HTML from AEM Author.** It is **not** a replication-agent push to a separate publish instance.
- **There is no Dispatcher** in this delivery path; ingestion and delivery are handled by Edge Delivery.
- AEM serves as the **content source** that Edge Delivery ingests from; the publish event signals Edge Delivery to ingest the current author content.

---

## 6. Summary

| Topic | Conclusion |
|---|---|
| **Cascading activation risk** | Automatic, unconditional cascade can publish unapproved local overrides and stale content; rollout and activation should be governed separately, with sync-cancellation and inheritance breaks honored. |
| **OOTB "Activate on Blueprint activation"** | Cascades **publication** to live copies on blueprint activation, but performs **no content synchronization**; updated content requires a prior rollout. |
| **Rollout vs. Activation** | Orthogonal operations — rollout syncs content on author; activation publishes existing author content to delivery. |
| **Publication to EDS** | Event-driven ingestion via the Adobe pipeline and Edge Delivery Services Admin API; no replication agents and no Dispatcher in the delivery path. |

---

## 7. Open Items for Confirmation

1. Confirm the multi-level **cascade reach** of OOTB "Activate on Blueprint activation" for the target live-copy structure (direct vs. indirect live copies).
2. Confirm the **content-governance model** with the business: whether final publication per market should remain under local/gated control rather than an automatic global cascade.
3. Confirm **access control** requirements for the Edge Delivery Services Admin API.
4. Preview (`.page`) versus published (`.live`) delivery behavior is not detailed in this document and should be sourced/confirmed separately if required in the final SDD.
