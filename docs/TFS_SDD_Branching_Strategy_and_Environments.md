# Branching Strategy & Environments

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Purpose.** This document defines the **branching strategy** and **environment model** for the TFS target platform — covering both the **Edge Delivery Services (EDS) front-end codebase** (GitHub) and the **AEM as a Cloud Service codebase** (Cloud Manager), and how their branches map to environments.

---

## 1. Environments

**AEM as a Cloud Service** provides three environments for TFS:

- **Dev**
- **Stage**
- **Production**

These are the environments used across both the AEM authoring codebase and the corresponding Edge Delivery Services delivery.

---

## 2. Edge Delivery Services — Branch-Driven Delivery Model

Edge Delivery Services replaces traditional environment-based deployments with a **branch-driven, edge-first delivery model** that eliminates build and release bottlenecks.

- Development occurs on **feature branches**, where every change automatically generates a **live preview at the edge**, enabling early validation without deploying to dedicated environments.
- Quality, security, and compliance are enforced **before** changes go live, through **pull-request reviews** and automated validation checks, rather than through environment promotion.
- Once changes are approved and merged into the **`main`** branch, code updates go live on the **production edge** — with no deployment window or downtime.
- **Rollback is immediate and low-risk**, achieved by reverting a Git change rather than restoring environments or redeploying artifacts.
- This model significantly reduces delivery time and operational overhead while maintaining enterprise-grade governance, making it ideal for large-scale, content-driven digital experiences.

### 2.1 AEM Code Sync

When code is pushed to any branch, **AEM Code Sync** automatically:

- Publishes the code to Edge Delivery Services' **code bus** for high availability.
- **Purges the CDN caches** for that environment when changes are made.

No manual build or deployment step is required — code changes are live on the target environment within seconds of the push.

### 2.2 Branch types and naming

Development happens on short-lived **feature** and **bugfix** branches, each created from the **`dev`** branch and merged back into `dev` via a **Pull Request (PR)**:

| Type | Purpose | Naming convention | Example |
|---|---|---|---|
| **Feature** | Implement new features or blocks | `feature-<feature-name>` | `feature-accordion-block` |
| **Bugfix** | Fix defects | `bugfix-<bug-description>` | `bugfix-hero-image-alignment` |
| **Environment** | Long-lived branches mapped to environments | `dev`, `stage`, `main` | — |

---

## 3. Branch ↔ Environment Mapping

Each Edge Delivery Services environment is a **site configuration** that pairs a **code branch** (GitHub) with a **content source** (the corresponding AEM author environment).

| Environment | EDS code branch | Content source (AEM author) |
|---|---|---|
| **Dev** | `dev` | Dev author |
| **Stage** | `stage` | Stage author |
| **Production** | `main` | Production author |

- The **`main`** branch is used for **production** in Edge Delivery Services, and is the recommended production branch.

### 3.1 Preview and Publish URLs

Each branch/environment in Edge Delivery Services is served through **two URLs** — a **Preview** URL and a **Publish (Live)** URL:

| URL | Purpose | Pattern |
|---|---|---|
| **Preview** (`.aem.page`) | Renders authored content that has been **previewed** but not yet published — used for validation before go-live | `https://<branch>--<site>--<org>.aem.page/` |
| **Publish / Live** (`.aem.live`) | Serves **published** content — the delivery target fronted by the CDN | `https://<branch>--<site>--<org>.aem.live/` |

In each environment, authors validate their work on the **Preview** URL before **Publish**, at which point the change is served from the **Live** URL. For example, the `main` branch exposes the production preview and live URLs, while `dev` and `stage` expose their respective environment URLs.

---

## 4. Pull Request Process

Changes are promoted through branches using pull requests, with reviews enforced at each step:

1. Developers create **feature/bugfix** branches from **`dev`**.
2. Complete the development work in their respective branches.
3. Raise a **PR to merge into `dev`**; after review and validation in the **Dev** environment, the change is accepted.
4. Promote via a **PR from `dev` → `stage`** for validation in the **Stage** environment.
5. Promote via a **PR from `stage` → `main`** to go live in **Production**.

Direct pushes to the environment branches (`dev`, `stage`, `main`) are not permitted; all changes flow through reviewed pull requests.

**CI/CD characteristics (EDS front-end):**
- GitHub-native delivery acting as the single source of truth.
- No Maven builds required for front-end code.
- Simplified, instant releases directly to the edge network.
- Git-based rollback rather than restoring environments or redeploying artifacts.

---

## 5. AEM as a Cloud Service Codebase — Consistent Branch Model

The AEM authoring-side codebase (deployed through **Cloud Manager** pipelines) uses the **same branch names** for consistency with the Edge Delivery Services model:

| AEM Cloud branch | Cloud Manager deploys to |
|---|---|
| `dev` | **Dev** environment |
| `stage` | **Stage** environment |
| `main` | **Production** environment |

- `dev` is deployed to the **Dev** environment, `stage` to the **Stage** environment, and `main` to the **Production** environment.
- Deployment is performed by **Cloud Manager pipelines** (build → quality gates → deploy). *(See the AEM Cloud Codebase & Deployment solution design for repository, pipeline, and deployment detail.)*

Using consistent branch names across both codebases keeps the development and promotion workflow aligned end-to-end: the same `dev` / `stage` / `main` progression applies to both the Edge Delivery front-end code and the AEM authoring code.

---

## 6. Environment Promotion Flow

Code is promoted through the branches in sequence; each promotion is a reviewed Pull Request, validated on the target environment before promoting further.

```
feature / bugfix branch
        │  (PR + code review)
        ▼
       dev  ──────────►  Dev environment
        │  (PR, after Dev validation)
        ▼
      stage ──────────►  Stage environment
        │  (PR, after Stage validation)
        ▼
      main  ──────────►  Production
```

Each promotion requires: a Pull Request, code review / approval, and validation on the target environment before promoting further.

> `[SCREENSHOT / DIAGRAM: Environment promotion flow — feature/bugfix → dev → stage → main]`

---

## 7. References

- Getting Started — Developer Tutorial: https://www.aem.live/developer/tutorial
- AEM Code Sync: https://github.com/apps/aem-code-sync
- Deploying code with Cloud Manager: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/implementing/using-cloud-manager/deploy-code
