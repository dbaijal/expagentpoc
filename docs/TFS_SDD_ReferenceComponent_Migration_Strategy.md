# Migration Strategy — Reference Components

**Project:** Thermo Fisher Scientific (TFS) — AEM 6.4 On-Prem → AEM as a Cloud Service + Edge Delivery Services
**Authoring Model:** AEM as authoring source with Edge Delivery Services
**Document Scope:** Decision and strategy for AEM Reference Components (`foundation/components/reference`)

## Overview

This page defines the decision and strategy for handling AEM **Reference Components** in the TFS migration. It records why a wholesale migration of reference components into EDS fragments is **not** recommended, and how reference-component content is handled instead. No separate reference-component migration workstream is created — the handling described here is performed as part of standard page migration.

## Context

In AEM, a **Reference Component** (`foundation/components/reference`) is an individual component container placed on a page that points to a component on another (source) page by its JCR path and renders that content in place. A single page may contain several reference components. They provide a content-reuse mechanism — the same source content can appear on multiple pages.

The question to be resolved for migration: **do we flatten reference components (remove them as containers) or retain them — and if retained, do they need to be split out as individual EDS fragments?**

## How Reuse Works in the Target

EDS achieves content reuse through **fragments** — content authored once and referenced across pages via a fragment block. A reference component can therefore, technically, be converted into a fragment.

## What the Inventory Shows

Analysis of the existing reference-component inventory shows that they are **predominantly small, single-purpose items** — single lines of text and small raw-HTML snippets — stored under **auto-generated JCR node names** (for example `text_9db8`, `ltrawhtml_be5a`, `ltrawhtml_45bb`). They are largely **not** large blocks of genuinely shared content.

Example hierarchy analysed: `/content/lifetech/global/en/reference-components`

Sample nodes observed:
- `/content/lifetech/global/en/reference-components/placeholder-page/jcr:content/MainParsys/text_9db8`
- `/content/lifetech/global/en/reference-components/ux-drop-drawer/jcr:content/MainParsys/ltrawhtml_be5a`
- `/content/lifetech/global/en/reference-components/ux-na-homepage-base/jcr:content/MainParsys/ltrawhtml_45bb`

## Why Converting Every Reference Component to a Fragment Is Not Recommended

Migrating the reference-component repository as-is — one fragment per reference component — would:

- **Inflate the fragment count significantly** — a large number of fragments created for trivial, single-use content (in EDS, content is composed of fragments and blocks rather than referenced JCR nodes, so each item would become its own fragment).
- **Carry opaque, auto-generated names** (e.g. `ltrawhtml_be5a`) into fragment paths, reducing clarity and long-term maintainability.
- **Add unnecessary runtime overhead** — each fragment is a separate fetch, for content that is not actually reused.

## Decision

- **Flatten reference components by default.** During page migration, the referenced content is brought **inline** into the host page, rather than being retained as a separate referenced container or split into an individual fragment. The reference-component repository is **not** migrated wholesale into fragments.
- **Promote genuinely reused content to fragments — selectively.** Where, during migration, a piece of content is observed to **appear repeatedly across pages**, it is made into a **fragment** (with a clean, meaningful name) so it can be authored once and reused — easing authoring and preserving single source of truth. This is applied selectively to genuinely shared content, not to the reference-component set as a whole.

## Outcome

This keeps the EDS fragment library **clean, well-named, and limited to content that genuinely benefits from reuse**, while avoiding unnecessary fragments, fetches, and maintenance overhead for trivial single-use content.

> *Based on the observed reference-component inventory; to be confirmed against the full / production inventory.*
