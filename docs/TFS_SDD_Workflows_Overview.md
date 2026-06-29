# Workflows — Overview

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

---

## 1. Purpose

This page provides an overview of how existing AEM **workflows** behave and are migrated when moving TFS from AEM 6.4 On-Prem to AEM as a Cloud Service with Edge Delivery Services (EDS), where **AEM remains the authoring source** and EDS is the delivery layer.

Detailed analysis of each individual workflow is captured in its respective sub-page (Section 7).

---

## 2. Scope — In-Scope Workflows

Discovery identified **five workflow models** in active use, which are in scope for migration:

1. Lifetech - Simple Review Publication
2. Lifetech - Major Review Publication
3. Life Tech - Scheduled Page/Asset Activation
4. Life Tech - Scheduled Page/Asset Deactivation
5. Lifetech - Deactivation Publication

Other workflow models may exist in the current instance, but only the above are confirmed as actively used and in scope. The detailed behavior of each is documented in its dedicated analysis page.

---

## 3. Workflow Behavior in the Target Model

- Content authoring and governance continue on the **AEM Author tier**.
- All workflows execute **within AEM**, as they do today — approvals, review, and content lifecycle remain AEM responsibilities.
- **EDS has no workflow engine.** EDS is responsible only for delivery — receiving published content and serving it via the Preview and Live endpoints.
- Workflows remain in AEM; EDS consumes only **approved and published** content.

This means the workflow model is conceptually **unchanged** — the same review, approval, and lifecycle behavior is preserved — with EDS added as the delivery layer that serves the approved output.

---

## 4. Publishing Behavior

- Workflow completion triggers **standard AEM publish / deactivation** actions.
- The published (or deactivated) result is then **delivered to EDS** for serving on the Preview and Live endpoints.
- **Scheduled** activation and deactivation continue to operate via the **AEM scheduler** within the AEM Author tier; when the scheduled time fires, the standard publish/deactivation action runs and the result is delivered to EDS.

This is described at a conceptual level here; the underlying publish/delivery mechanism is covered in the publishing/delivery documentation.

---

## 5. Migration of Workflow Models

- The in-scope workflow models are migrated to AEM as a Cloud Service and **refactored for AEMaaCS compatibility**.
- Custom workflow steps are reviewed and updated:
  - Replace any **deprecated APIs**.
  - Ensure compatibility with the AEMaaCS runtime.
- Any logic that relies on **custom replication agents** or **hard-coded/custom publish scripts** is removed or refactored — publishing to EDS is handled by the platform's delivery mechanism, not by custom replication inside workflows.

The workflows remain **unchanged conceptually**, but require **technical refactoring** to run on AEMaaCS.

---

## 6. Identity, Permissions and Governance

- **Identity:** authentication is handled via **Adobe IMS**; users and groups are managed in the **Admin Console**. Workflow-related groups are migrated and mapped to IMS-backed groups, and workflow participant steps are updated where group IDs or paths change.
- **Permissions / governance:** AEMaaCS continues to use JCR-based ACLs. The existing governance model is preserved:
  - **Authors** initiate workflows; they cannot publish directly.
  - **Approvers** complete workflows, triggering publish / deactivation.
- **Email notifications:** where workflows send email, SMTP is configured per environment and email triggers/templates are validated.

---

## 7. Detailed Workflow Analysis

Each in-scope workflow has a dedicated analysis page covering its detailed behavior and migration considerations:

- Lifetech - Simple Review Publication
- Lifetech - Major Review Publication
- Life Tech - Scheduled Page/Asset Activation
- Life Tech - Scheduled Page/Asset Deactivation
- Lifetech - Deactivation Publication
