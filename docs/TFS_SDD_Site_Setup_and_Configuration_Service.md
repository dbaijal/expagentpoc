# AEM Site Setup & Configuration Service

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services

> **Purpose.** This document describes how the TFS Edge Delivery Services (EDS) site is set up and configured — from the code repository through the **Configuration Service** and **path mapping** for locales.

---

## 1. Overview

For TFS, the EDS site is configured through the **Configuration Service** — the current (Helix 5) mechanism for managing project configuration.

A single EDS site is created from the AEM crosswalk boilerplate, connected to the TFS AEM as a Cloud Service authoring instance, and configured through the Configuration Service using the **Admin API**. All TFS locales are served from this single site using **path mapping**.

---

## 2. Configuration Service

The **Configuration Service** is the mechanism used to manage and deliver a site's configuration — including its **code source**, **content source**, **access control**, and **public path mappings**. It aggregates and delivers configuration to the consumers in the Edge Delivery architecture (Client, Delivery, HTML Pipeline, and Admin Service).

Configuration is created and maintained through the **Admin API** at `https://admin.hlx.page`, using the `/config/{org}/sites/{site}.json` endpoints.

**References:**
- Setting up the Configuration Service — https://www.aem.live/docs/config-service-setup
- Admin API reference — https://www.aem.live/docs/admin.html

---

## 3. Site Setup Steps

### Step 1 — Create the site from the boilerplate

1. Create the TFS EDS repository from Adobe's crosswalk boilerplate: `https://github.com/adobe-rnd/aem-boilerplate-xwalk` — click **Use this template → Create a new repository** and give it a name (e.g. `tfs-eds`).

   > `[SCREENSHOT: GitHub "Use this template" → Create a new repository]`

2. Enable the **AEM Code Sync** GitHub app on the repository so code is available to Edge Delivery Services: go to `https://github.com/apps/aem-code-sync` → **Configure** → select the organization → grant access to the TFS repository → **Save**.

   > `[SCREENSHOT: AEM Code Sync → Repository access → Only select repositories]`

3. In AEM as a Cloud Service → **Sites** console → **Create → Site from template**. On **Select a site template**, **Import** the AEM authoring with Edge Delivery Services (crosswalk) site template, then select it and click **Next**.

   > `[SCREENSHOT: AEM Sites console → Create → Site from template]`

4. Provide the **Site title**, **Site name** (e.g. `tfs-eds`), and the **GitHub URL** of the repository, then click **Create**.

   > `[SCREENSHOT: Create-site wizard → site details]`

5. Open the new site's `index.html` → **Edit**. The Universal Editor opens (sign in with Adobe if prompted). Confirm the site renders.

   > `[SCREENSHOT: Universal Editor rendering the newly created site]`

### Step 2 — Retrieve the access token

An access token is required to make Configuration Service (Admin API) calls.

1. Go to `https://admin.hlx.page/login` and use `login_adobe` to authenticate with the Adobe identity provider. You are forwarded to `https://admin.hlx.page/profile`.
2. Using your browser's developer tools, copy the value of the `auth_token` cookie set by the Admin Service.
3. Pass it on subsequent requests as a header: `-H 'x-auth-token: <your-token>'`

> `[SCREENSHOT: Admin Service profile page — auth_token cookie in developer tools]`

### Step 3 — Create the site configuration

Create the site configuration through the Admin API. This defines the **code source**, the **AEM content source**, and the **public path mappings**.

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

### Step 4 — Set the technical account and access

1. In AEM → **Tools → Cloud Services → Edge Delivery Services Configuration** → select the configuration that was created for your site → **Properties**.
2. On the **Authentication** tab, copy the **technical account ID** (it looks like `<tech-account-id>@techacct.adobe.com`; it is the same for all sites on a single AEM author environment).

   > `[SCREENSHOT: Edge Delivery Services Configuration → Authentication tab — technical account ID]`

3. Apply the access configuration through the Admin API, using the technical account ID and the administrator email(s):

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

### Step 5 — Update the AEM configuration

1. In AEM → **Tools → Cloud Services → Edge Delivery Services Configuration** → select the configuration → **Properties**.
2. On the **Site** tab, set **Path Mapping** to **Config Service** → **Save & Close**.

   > `[SCREENSHOT: Edge Delivery Service Configuration → Site tab → Path Mapping = "Config Service"]`

### Step 6 — Validate

1. Open the site in the Universal Editor and confirm it renders correctly.
2. Modify some content and **publish**.
3. Visit the published site at `https://main--<site-name>--<org>.aem.page/` and verify the changes are reflected.

---

## 4. Path Mapping for Locales

**Path mapping** defines how internal content paths in AEM are mapped to the public URL structure of the website, and it also controls which content paths are published to Edge Delivery Services. For TFS, path mappings are managed through the **Configuration Service** as part of the site configuration.

TFS serves **all locales from this single EDS site** on a shared domain: rather than a separate site per locale, one site configuration holds per-locale path mappings. Locale content is authored and governed in AEM; the site configuration maps and serves it.

### 4.1 Configuration structure

Path mapping is configured under `public.paths` in the site configuration (Step 3), using three arrays:

| Key | Purpose |
|---|---|
| **`mappings`** | Transforms an internal AEM path to a public URL path. Format: `<internal path>:<external path>`. Uses prefix matching (no glob patterns). |
| **`includes`** | Determines **which** AEM content trees are published to Edge Delivery Services. Without a matching include, content is not published regardless of mappings. Supports glob patterns (`*`, `**`). |
| **`excludes`** *(optional)* | Removes specific paths from publishing, applied after `includes`. A path is published only when it matches an `include` **and** matches no `exclude`. |

> When multiple mappings match a path, the **most specific (last) match wins** — order mappings from least to most specific.

### 4.2 How TFS locales are mapped

Each locale (country-language) maps its AEM content path to its public URL. For example:

```json
{
  "public": {
    "paths": {
      "mappings": [
        "/content/tfs-eds/us/en/:/",
        "/content/tfs-eds/gb/en/:/gb/en/",
        "/content/tfs-eds/de/de/:/de/de/"
      ],
      "includes": [
        "/content/tfs-eds/"
      ]
    }
  }
}
```

In this example the US-English root maps to the domain root (`/`), while other locales map to their own URL prefixes (`/gb/en/`, `/de/de/`, …). The single `includes` entry publishes the whole `tfs-eds` content tree; each locale is then exposed at its mapped public path.

> Reference: [Path mapping for AEM authoring (aem.live)](https://www.aem.live/developer/authoring-path-mapping)

---

## 5. References

- Getting Started — Universal Editor Developer Tutorial: https://www.aem.live/developer/ue-tutorial
- Setting up the Configuration Service: https://www.aem.live/docs/config-service-setup
- Path mapping for AEM authoring: https://www.aem.live/developer/authoring-path-mapping
- Admin API reference: https://www.aem.live/docs/admin.html
- Crosswalk boilerplate: https://github.com/adobe-rnd/aem-boilerplate-xwalk
