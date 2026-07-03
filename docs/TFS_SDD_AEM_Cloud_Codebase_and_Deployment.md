# AEM as a Cloud Service — Codebase, Repository & Deployment

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Purpose.** This document describes the **AEM authoring-side codebase** — the code deployed to the **AEM as a Cloud Service (AEMaaCS) author** environment. It covers the Maven archetype used, the repository/module structure, how the project is built and deployed through **Cloud Manager pipelines**, and the key changes versus the current **AEM 6.4 on-premise** codebase (immutable vs mutable content, run modes, OSGi configuration).
>
> **Scope note.** This is the **AEM Cloud codebase**, managed in the **Cloud Manager Git repository** and deployed to AEM author. It is **separate** from the Edge Delivery Services front-end code (blocks, CSS, JS), which lives in a **GitHub** repository and is delivered via Code Sync — that is covered by the *Site Setup & Configuration Service* solution design.

---

## 1. Overview

TFS currently runs an **AEM 6.4 on-premise** codebase. Migrating to **AEM as a Cloud Service** requires the code to be restructured to the **Cloud Service project model** — a Maven multi-module project generated from the **AEM Project Archetype**, built with Cloud Manager, and deployed to the author (and publish) tier through **Cloud Manager pipelines**.

In the target model, this AEM Cloud codebase provides the **authoring-side capabilities** — component/page models for the Universal Editor, OSGi services and configurations, service users and ACLs, MSM/rollout configuration, workflows, and any authoring integrations. It does **not** render delivery HTML (that is Edge Delivery's role); it supports **authoring** in AEM as the content source.

---

## 2. Maven Archetype

The project is generated from the **AEM Project Archetype** (Adobe's official, Maven-based project generator), targeting Cloud Service.

```bash
mvn -B org.apache.maven.plugins:maven-archetype-plugin:3.2.1:generate \
  -D archetypeGroupId=com.adobe.aem \
  -D archetypeArtifactId=aem-project-archetype \
  -D archetypeVersion=<latest> \
  -D aemVersion=cloud \
  -D appTitle="TFS" \
  -D appId="tfs" \
  -D groupId="com.thermofisher.aem"
```

Key points:

- **`aemVersion=cloud`** generates a project that complies with the AEMaaCS structure and validations (this is the essential difference from a 6.4-targeted archetype run).
- The archetype produces the **immutable/mutable-separated** module layout described in Section 3, wired for Cloud Manager builds.
- The generated project includes the **SDK-based** local development setup and the **`analyse`** module that runs the same code-quality/immutability checks Cloud Manager enforces — allowing issues to be caught locally before pushing.

---

## 3. Repository & Module Structure

The project is a Maven multi-module build. Only the **`all`** package is deployed by Cloud Manager; every other module is either embedded into `all` or used for build/validation.

| Module | Contents | Package type | Deployed by Cloud Manager? |
|---|---|---|---|
| **`all`** | Container that aggregates all deployable artifacts | container | **Yes** (the only deployable) |
| **`core`** | Java / OSGi bundles (services, models, servlets) | JAR (embedded) | via `all` |
| **`ui.apps`** | Components, HTL, client libraries — deploys to **`/apps`** (immutable) | application | via `all` |
| **`ui.apps.structure`** | Defines/enforces the `/apps` repository structure (dependency guard) | application | via `all` |
| **`ui.config`** | **OSGi configurations** and **Repo Init** scripts | application | via `all` |
| **`ui.content`** | Content, `/conf` configuration, taxonomies, sample/seed content (mutable) | content | via `all` |
| **`ui.frontend`** | (Optional) front-end build for AEM-rendered client libs | — | compiled into `ui.apps` |
| **`dispatcher`** | Dispatcher/Apache configuration (flexible Cloud Service dispatcher) | — | via pipeline (image build) |
| **`it.tests` / `ui.tests`** | Integration and UI tests run by the pipeline | — | build/test only |
| **`analyse`** | Runs immutability & best-practice checks locally | — | build only |

### 3.1 The immutable / mutable separation (a hard rule)

AEMaaCS strictly separates **immutable** and **mutable** repository areas, and **a single content package cannot deploy to both**:

- **Immutable** — **`/apps`** and **`/libs`**: *"After AEM starts, you cannot create, update, or delete content in these areas at runtime."* Code (components, HTL, client libs, OSGi config) is deployed here **only via a pipeline deployment**.
- **Mutable** — **`/content`, `/conf`, `/var`, `/etc`, `/home`**, etc.: runtime-writable; authored content and runtime configuration live here.

Consequences enforced by the build:

- **Code and content are separated into discrete packages** — `ui.apps` (code → `/apps`) vs `ui.content` (content → mutable trees).
- **Content packages depend on code packages, never the reverse.**
- Code packages require the **repository-structure package** (`ui.apps.structure`) to validate dependencies.
- Sub-packages are embedded under `/apps/tfs-packages/(application|content|container)/install[.author|.publish]`.

---

## 4. Deployment via Cloud Manager

Code is **not** deployed by hand. It is built and deployed through **Cloud Manager**, which owns the Git repository, the build, the quality gates, and the rolling deployment.

### 4.1 Source control — two-repository model

AEMaaCS deployments use **Cloud Manager Git**. The recommended TFS setup maintains **two Git repositories**:

| Repository | Purpose |
|---|---|
| **Customer Git** (e.g. GitHub) | Day-to-day development — feature branches, pull requests, code reviews, team collaboration. |
| **Cloud Manager Git** (Adobe-hosted) | Used **exclusively** for Cloud Manager deployments. As a best practice, code is **not committed directly** here. |

**Branch synchronization.** Branches are synchronized one-way from **Customer Git → Cloud Manager Git**, so that pushing to a development branch triggers the corresponding Cloud Manager pipeline. A typical mapping:

| Customer Git branch | Cloud Manager Git branch | Purpose |
|---|---|---|
| `develop` | `develop` | Lower-environment (Dev) deployments |
| `qa` | `qa` | QA environment deployments |
| `main` | `main` | Stage and Production deployments |

Synchronization is automated (e.g. a **GitHub Action** on push to the source branch that pushes to the Cloud Manager remote using stored `CM_GIT_*` credentials/secrets). Jenkins or custom scripts are equally valid.

> `[SCREENSHOT: GitHub Action workflow — "Sync to Cloud Manager Git" (push develop → adobe-develop)]`

### 4.2 Pipelines

Cloud Manager pipelines are triggered automatically for lower environments and manually (by a user with the **Deployment Manager** role) for Stage/Production; promotion to Production is approved by a Development Manager / Business Owner / Program Manager, and manual approval is required when there are build warnings (e.g. low test coverage).

| Pipeline | Trigger | What it does |
|---|---|---|
| **DEV / QA (non-production)** | Auto on merge to `develop` / `qa` | Build + code-quality, deploy to the lower environment. A **separate Dispatcher (Web Tier) pipeline** handles dispatcher config for these environments; the full-stack code pipeline does **not** deploy dispatcher config (intentional separation). |
| **Production** | Manual (Deployment Manager), promoted after Stage | Build → **Stage** deploy + automated tests → approval → **Production** deploy. The Production pipeline deploys **both application code and dispatcher config** together. |

### 4.3 Build → Test → Deploy

1. **Build (Maven).** Cloud Manager runs a **containerized Maven build** of the project. Output artifacts: the **`all`** content package and the **Dispatcher** configuration.
2. **Code quality.** Static analysis / code-quality scanning and **immutability checks** run automatically (the same checks the local `analyse` module runs). Failing the quality gate stops the pipeline.
3. **Stage deploy & test.** The `all` package is deployed to **Stage**, where **functional, UI, and (where configured) experience-audit tests** run.
4. **Image build.** Content and Dispatcher packages are converted into the deployable **container images**.
5. **Production deploy (rolling).** *"The same build artifact is deployed to the Production environment"* using a **rolling** strategy (instances detached from the load balancer one at a time) for **zero-downtime**; the **Dispatcher cache is cleared with each deployment**.

Code reaches **both author and publish** instances through this coordinated package deployment. Author-only or publish-only artifacts are targeted using the `install.author` / `install.publish` run-mode-scoped install folders.

---

## 5. Key Changes vs the On-Premise (6.4) Codebase

This is the part that most affects the migration of the existing TFS codebase.

### 5.1 Immutable `/apps` — no runtime changes to code

On 6.4, code and content were often deployed together and `/apps` could be modified at runtime. On AEMaaCS, **`/apps` and `/libs` are immutable at runtime** — all code changes go **only through a pipeline**. Any existing pattern that writes to `/apps` at runtime, or mixes code and content in one package, must be **refactored** into the code/content package split.

### 5.2 Run modes

- AEMaaCS supports a **fixed, predefined set of run modes** — the tier (`author`, `publish`) and the environment type (`dev`, `stage`, `prod`). **Custom/arbitrary run modes are not supported.**
- Configuration is therefore scoped using the standard OSGi config-folder run-mode combinations, e.g. `config`, `config.author`, `config.publish`, `config.author.dev`, `config.publish.prod`, etc.
- Any 6.4 logic that relied on **custom run modes** must be re-mapped onto the supported set (or moved to environment-specific **variables**, see 5.3).

### 5.3 OSGi configuration

- OSGi configuration lives in **`ui.config`** and is deployed as part of the immutable code — not edited via the Felix/OSGi web console at runtime (the console is read-only in the cloud).
- Configurations use the **`.cfg.json`** format under the appropriate run-mode config folders.
- **Environment-specific values (per dev/stage/prod) and secrets** are supplied through **Cloud Manager environment variables and secrets**, referenced in OSGi config as `$[env:VARIABLE_NAME]` and `$[secret:SECRET_NAME]` — replacing the on-prem practice of hard-coding per-environment values or maintaining separate config sets per server.

### 5.4 Service users, ACLs & repository initialization (Repo Init)

- Service users, their ACLs, and baseline repository structures are created declaratively via **Repo Init** scripts in **`ui.config`**, executed on startup — replacing manual/one-off provisioning done on 6.4 instances.

### 5.5 Dispatcher

- The Dispatcher moves to the **flexible Cloud Service dispatcher** module in the project, validated by the SDK dispatcher tools and deployed as part of the pipeline. Existing 6.4 dispatcher rules must be migrated into this structure (and validated locally before deploy).

### 5.6 No direct instance access / other constraints

- No SSH/filesystem access to instances; no runtime bundle installs; **replication uses the cloud publishing pipeline / Sling Content Distribution**, not the classic replication agents configured by hand.
- Indexes (`/oak:index`) are managed through the deployment process, not edited live.

> **Migration implication.** The existing 6.4 code is **re-platformed, not lifted and shifted**: split into code vs content packages, custom run modes re-mapped, OSGi/secret values externalized to Cloud Manager variables, service users/ACLs moved to Repo Init, and the dispatcher migrated to the flexible module. Legacy code that assumes runtime-writable `/apps`, console-based config, or direct instance access must be re-worked.

---

## 6. High-Level Migration Path (Codebase)

1. **Generate** the AEMaaCS project skeleton from the AEM Project Archetype (`aemVersion=cloud`).
2. **Port Java/OSGi** code into `core`; resolve any deprecated/removed API usage flagged by the analyser.
3. **Split** existing packages into **code** (`ui.apps`) and **content** (`ui.content`); introduce `ui.apps.structure`.
4. **Move OSGi config** into `ui.config` as `.cfg.json`; externalize per-env values/secrets to Cloud Manager variables.
5. **Re-map run modes** to the supported set.
6. **Convert** service-user/ACL provisioning to **Repo Init**.
7. **Migrate the Dispatcher** configuration to the flexible module; validate with SDK tools.
8. **Wire Cloud Manager** — Git repository, production and non-production pipelines, environment variables/secrets.
9. **Run pipelines** — validate build, quality gates, stage tests, then production rolling deploy.

---

## 7. Ownership

| Concern | Owner |
|---|---|
| Archetype generation & project structure | Adobe / Developers |
| Porting 6.4 code; package split; OSGi/Repo Init/run-mode changes | Adobe / Developers |
| Cloud Manager setup (Git, pipelines, environment variables/secrets) | TFS + Adobe |
| Dispatcher migration & validation | Adobe / Developers |
| Approvals / production deploy gate | TFS |

---

## 8. References

- AEM Project Archetype: https://experienceleague.adobe.com/en/docs/experience-manager-core-components/using/developing/archetype/overview
- Project & content package structure: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/implementing/developing/aem-project-content-package-structure
- Deploying code with Cloud Manager: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/implementing/using-cloud-manager/deploy-code
- OSGi configuration (env variables & secrets): https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/implementing/deploying/configuring-osgi
- Repo Init: https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/implementing/deploying/overview#repo-init
