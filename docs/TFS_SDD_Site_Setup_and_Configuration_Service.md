# AEM Site Setup & Configuration Service

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Purpose.** This document is the consolidated reference for **how the TFS Edge Delivery Services (EDS) site is provisioned and configured** — from the code repository, through the base (canonical) site, to the **Configuration Service** and **path mapping** for all locales. It supersedes and absorbs the earlier *AEM Sites Setup* and *Repoless Setup* pages.
>
> **Scope.** This document covers **provisioning and configuration only**. Neighbouring concerns are owned by their own solution designs and are *referenced, not re-explained* here: MSM / locale strategy, SEO (sitemaps, robots), templates, and page properties.

---

## 1. Overview

In the target model, a TFS EDS site is set up and configured through the **Configuration Service** — the current (Helix 5) mechanism for managing project configuration. This replaces the older document/repository-based configuration model, in which configuration lived in files such as `fstab.yaml` and `paths.json` inside the GitHub repository.

The Configuration Service enables the capability commonly referred to as **"repoless"** — running one or more site configurations from a **single shared codebase**, with content sourced from AEM. TFS uses this mechanism in its **single-site form**: **one EDS site configuration** serves **all locales** via **path mapping**, with locale content authored and governed in AEM through **MSM** (Multi Site Manager).

> **Terminology note (important for readers).** *"Repoless"* is not a separate product or a competing option to the Configuration Service — it is the **capability delivered by** the Configuration Service. Enabling the Configuration Service **is** what "enabling repoless" means. Repoless is most often described as *"one codebase, many sites,"* but that describes its most common use, **not** its definition. TFS uses the same mechanism with a **single site configuration**; this is fully valid and is, in fact, the simplest repoless topology.

**Why TFS is on this mechanism at all:** using **MSM on Edge Delivery Services with AEM authoring requires the repoless (Configuration Service) mechanism.** Because TFS is an MSM-based, multi-locale property, the Configuration Service is a **prerequisite**, not an optional enhancement.

---

## 2. What is the Configuration Service?

The **Configuration Service** aggregates and delivers configuration to the various consumers in the AEM Edge Delivery architecture, including:

- **Client** (the browser/runtime)
- **Delivery** (the delivery tier)
- **HTML Pipeline** (response assembly)
- **Admin Service** (publishing, previews, operations)

Key characteristics:

| Capability | Description |
|---|---|
| **Centralized configuration** | Site configuration (code source, content source, path mappings, access, sidekick, etc.) is held by the service rather than in repository files. |
| **Configuration inheritance** | Configuration can be inherited from **organization** and **profile** levels into individual **sites**, making large collections of sites easier to manage. |
| **Enables repoless** | Allows multiple site configurations to share a single code repository while pulling content from different AEM paths. |
| **API-driven** | Configuration is created and maintained via the **Admin API** (`https://admin.hlx.page`) using `/config/{org}/sites/{site}.json` endpoints. |

**Operated via the EDS Admin API.** The Configuration Service is managed through the Admin API endpoints (create/update site config, set access/technical account, etc.). See:

- Configuration Service setup — https://www.aem.live/docs/config-service-setup
- Repoless authoring — https://www.aem.live/developer/repoless-authoring
- Admin API reference — https://www.aem.live/docs/admin.html

---

## 3. Key Concepts Before Setup

### 3.1 The base (canonical) site

Every repoless / Configuration-Service setup begins with a **base site** (also called the **canonical site**):

> *"Regardless of how many sites you want to ultimately create in a repoless fashion, you must create your first site, which serves as your base site."*

The canonical site is subject to one firm rule:

> *"There must be **one** canonical site for which the `org/site` matches the GitHub `owner/repo`."* — required for proper **code-config association** and **CDN push invalidation**.

**For TFS this is the whole story, not an extra step:** because TFS runs a **single site**, that one site **is** the base/canonical site. Its name matches the GitHub repository, it owns the Code Sync relationship, and it anchors the entire configuration. TFS does **not** create a base site *plus* additional sites — the single path-mapped site fulfils both roles.

### 3.2 The code repository is permanent; `fstab.yaml` / `paths.json` are scaffolding

The GitHub repository (created from the boilerplate) is the **permanent source of code** — blocks, CSS, JS, and configuration — and remains connected via **AEM Code Sync**.

However, the files used to *bootstrap* the very first site in the classic tutorial — **`fstab.yaml`** (content mount point) and **`paths.json`** (path mappings) — are **transitional scaffolding**. Once the site is migrated to the Configuration Service, these files are **removed**, because their responsibilities move into the **site configuration** held by the service.

> **Also moved to the Configuration Service.** Beyond `fstab.yaml` and `paths.json`, several other repository config files are superseded once on the Configuration Service — notably **`robots.txt`**, **`helix-sitemap.yaml`**, **`helix-query.yaml`**, and the **sidekick config** (`tools/sidekick/config.json`) — because the service's configuration overrides these repository files. The **details of those** (e.g. how sitemaps and robots are managed) are covered in the **SEO Solution Design** and related SDDs; this document only notes that they migrate to the site configuration.

---

## 4. Setup Sequence (End to End)

The setup is a **two-phase flow**: (A) bootstrap the site the classic way to get it rendering, then (B) migrate it onto the Configuration Service. The classic-phase files are then removed.

> **Note on screenshots.** Screenshot placeholders are marked `[SCREENSHOT: …]`. Several of these correspond to images already present in the earlier *AEM Sites Setup* / *Repoless Setup* pages and can be reused directly in the final Confluence doc.

### Phase A — Create the code repository and bootstrap the base site

#### Step 1 — Create the repository from the boilerplate

Create the TFS EDS repository from Adobe's crosswalk boilerplate:

- Boilerplate: `https://github.com/adobe-rnd/aem-boilerplate-xwalk`
- Click **Use this template → Create a new repository**.
- Name it to match the canonical site (e.g. `tfs-eds`). **This repository is the permanent code source.**

> `[SCREENSHOT: GitHub "Use this template" → Create a new repository]` *(reusable from existing AEM Sites Setup page)*

#### Step 2 — (Classic bootstrap) point the site at AEM content

In the new repo, edit `fstab.yaml` to mount the AEM author content:

```
mountpoints:
  /: https://<aem-author>/bin/franklin.delivery/tfs/tfs-eds/main
```

Then (classic bootstrap) edit `paths.json` to map content to site URLs, e.g. `/content/tfs-eds/:/`.

> These two files are **temporary** — see Step 7 where they are removed after migrating to the Configuration Service.

#### Step 3 — Enable AEM Code Sync

Install/enable the **AEM Code Sync** GitHub app on the repository so code changes propagate to the code bus.

- Navigate to `https://github.com/apps/aem-code-sync` → **Configure** → select the org → grant access to **only** the TFS repository → **Save**.

> `[SCREENSHOT: AEM Code Sync GitHub app → Repository access → Only select repositories]` *(reusable from existing page)*

#### Step 4 — Create the AEM site from the site template

1. Download the latest **AEM authoring with EDS site template** (crosswalk) from the boilerplate release.
2. In AEM as a Cloud Service → **Sites** console → **Create → Site from template**.
3. On **Select a site template**, **Import** the downloaded template (import once; reusable thereafter).
4. Provide **Site title**, **Site name** (the canonical `<site-name>`, e.g. `tfs-eds`), and the **GitHub URL** of the repo. Click **Create**.

> `[SCREENSHOT: AEM Sites console → Create → Site from template]` *(reusable from existing page)*
> `[SCREENSHOT: Create-site wizard → Import site template]`

#### Step 5 — Verify authoring and initial publish

1. Open the new site's `index.html` → **Edit** (Universal Editor opens; sign in with Adobe if prompted).
2. Confirm the page renders in the Universal Editor.
3. **Quick publish** the initial pages and verify at `https://main--<repo>--<owner>.aem.page`.

> `[SCREENSHOT: Universal Editor rendering the newly created site]`

### Phase B — Migrate the site onto the Configuration Service

#### Step 6 — Set up the Configuration Service (site configuration)

**6a. Retrieve an access token.** Log in to the Admin Service and obtain an auth token:

- Go to `https://admin.hlx.page/login` and use `login_adobe` to authenticate with the Adobe identity provider.
- You are forwarded to `https://admin.hlx.page/profile`.
- Using browser dev tools, copy the value of the `auth_token` cookie set by the Admin Service.
- Pass it on subsequent requests as: `-H 'x-auth-token: <your-token>'`

> `[SCREENSHOT: Admin Service profile page / auth_token cookie in dev tools]`

**6b. Create/update the site configuration.** POST the site configuration to the Admin API. This is where the **code source**, the **AEM content source**, and — critically for TFS — the **path mappings** are defined:

```bash
curl --location 'https://admin.hlx.page/config/<org>/sites/<site-name>.json' \
  --header 'content-type: application/json' \
  --header 'x-auth-token: <your-token>' \
  --data-raw '{
    "code": {
      "owner": "<org>",
      "repo": "<repo>",
      "source": {
        "type": "github",
        "url": "https://github.com/<org>/<repo>"
      }
    },
    "content": {
      "source": {
        "url": "https://<aem-author>/bin/franklin.delivery/<org>/<site-name>/main",
        "type": "markup",
        "suffix": ".html"
      }
    },
    "access": {
      "admin": {
        "role": {
          "config_admin": [ "<tech-account-id>@techacct.adobe.com" ],
          "admin": [ "<admin-email>@<domain>.<tld>" ]
        },
        "requireAuth": "auto"
      }
    },
    "sidekick": {
      "project": "AEM XWalk Boilerplate",
      "editUrlLabel": "AEM Editor",
      "editUrlPattern": "{{contentSourceUrl}}{{pathname}}?cmd=open"
    },
    "public": {
      "paths": {
        "mappings": [
          "/content/<site-name>/<locale-path>/:/"
        ],
        "includes": [
          "/content/<site-name>/<locale-path>/"
        ]
      }
    }
  }'
```

> **Placeholders templatized on purpose.** `<tech-account-id>`, `<admin-email>`, and `<your-token>` are placeholders — do **not** commit real account identifiers or tokens into the published SDD. Substitute real values only in the operational (non-published) runbook.

**6c. Set the technical account for publishing.** In AEM: **Tools → Cloud Services → Edge Delivery Services Configuration →** select the auto-created configuration **→ Properties → Authentication tab →** copy the **technical account ID** (`<tech-account-id>@techacct.adobe.com`; same for all sites on one AEM author environment). Then apply access:

```bash
curl --request POST \
  --url https://admin.hlx.page/config/<org>/sites/<site-name>/access.json \
  --header 'Content-Type: application/json' \
  --header 'x-auth-token: <your-token>' \
  --data '{
    "admin": {
      "role": {
        "admin": [ "<admin-email>@<domain>.<tld>" ],
        "config_admin": [ "<tech-account-id>@techacct.adobe.com" ]
      },
      "requireAuth": "auto"
    }
  }'
```

> `[SCREENSHOT: AEM → Tools → Cloud Services → Edge Delivery Services Configuration → Authentication tab (technical account ID)]` *(reusable from existing Repoless Setup page)*

#### Step 7 — Point AEM at the Configuration Service and remove scaffolding

1. In AEM → **Tools → Cloud Services → Edge Delivery Services Configuration →** select the configuration **→ Properties**.
2. Change **project type** to **"aem.live with repoless config setup"** → **Save & Close**.
3. **Remove the now-superseded files from GitHub:** **`fstab.yaml`** and **`paths.json`** (path mapping now lives in the site configuration under `public.paths.mappings`).
   - **Brief note:** the following also migrate to the site configuration and can likewise be removed from the repo — **`robots.txt`**, **`helix-sitemap.yaml`**, **`helix-query.yaml`**, **`tools/sidekick/config.json`**. Their handling is detailed in the **SEO Solution Design** and related SDDs (not repeated here).
4. Re-open the site in the Universal Editor, confirm it renders, modify content, and **re-publish**.
5. Verify at `https://main--<site-name>--<org>.aem.page/`.

> `[SCREENSHOT: Edge Delivery Services Configuration → project type = "aem.live with repoless config setup"]` *(reusable from existing Repoless Setup page)*

---

## 5. Path Mapping for Locales (TFS Model)

TFS serves **all locales from a single EDS site configuration** on a shared domain, using **path mapping** rather than one site per locale.

- **Where it is defined:** the site configuration's **`public.paths.mappings`** (and `includes`) arrays — created/maintained via the Admin API (Step 6b). This **replaces** the old `paths.json`.
- **Shape:** each locale (country-language) maps its AEM content path to its public URL, e.g. `/content/tfs-eds/<country>/<language>/:/<country>/<language>/`.
- **Content & governance:** locale content is authored and governed in **AEM via MSM** (language masters → regional/country Live Copies). The EDS site config only maps and serves; MSM relationships and rollout remain in AEM.

**Why single-site + path mapping (rationale).** With ~41 country-language locales on one domain, creating a separate site configuration per locale would multiply operational overhead (per-site auth, sitemaps, invalidation, and sync across dozens of configs). A **single site configuration with path mappings** keeps operations manageable while still satisfying the MSM-on-EDS requirement (which is met simply by being on the Configuration Service / repoless mechanism).

> **Design consideration to keep on record.** Because all locales share **one** site configuration, anything that must genuinely differ **per locale** (e.g. locale-specific access, redirects, or sitemap grouping) is handled **within** the shared configuration or in code — not via separate site configs. If a small number of locales ever require true per-site configuration divergence, that subset (and only that subset) could warrant its own site config. This is noted as a consideration, not a current requirement. Locale/MSM specifics are owned by the **MSM / Locale Solution Design**.

---

## 6. Configuration Files — Before vs After

| Concern | Classic (document/repo-based) | Target (Configuration Service) |
|---|---|---|
| Content mount point | `fstab.yaml` in repo | `content.source` in site config |
| Path mappings | `paths.json` in repo | `public.paths.mappings` in site config |
| Robots | `robots.txt` in repo | site config *(see SEO SDD)* |
| Sitemap | `helix-sitemap.yaml` in repo | site config *(see SEO SDD)* |
| Query index | `helix-query.yaml` in repo | site config |
| Sidekick config | `tools/sidekick/config.json` in repo | `sidekick` block in site config |
| Code (blocks/CSS/JS) | GitHub repo (via Code Sync) | **unchanged** — GitHub repo remains the code source |
| Access / technical account | n/a in classic bootstrap | `access` block in site config (Admin API) |

---

## 7. Ownership

| Concern | Owner |
|---|---|
| Repository creation, boilerplate, Code Sync | Adobe / Developers |
| Base/canonical site creation | Adobe / Developers |
| Configuration Service setup (site config, access, technical account) | Adobe / Developers |
| Path-mapping definitions for locales | Adobe / Developers (values confirmed with TFS) |
| Locale/MSM strategy and rollout | TFS + Adobe *(see MSM Solution Design)* |
| Sitemap / robots handling | *(see SEO Solution Design)* |
| Authoring content per locale | TFS Authors |

---

## 8. References

- Getting Started — Universal Editor Developer Tutorial: https://www.aem.live/developer/ue-tutorial
- Setting up the Configuration Service: https://www.aem.live/docs/config-service-setup
- Reusing code across sites (repoless authoring): https://www.aem.live/developer/repoless-authoring
- MSM with AEM authoring on EDS: https://www.aem.live/developer/repoless-multisite-manager
- Repoless — one codebase, many sites: https://www.aem.live/docs/repoless
- Admin API reference: https://www.aem.live/docs/admin.html
- Crosswalk boilerplate: https://github.com/adobe-rnd/aem-boilerplate-xwalk
