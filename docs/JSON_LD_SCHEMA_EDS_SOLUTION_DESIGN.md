# JSON-LD Schema.org Structured Data — EDS Solution Design

## 1. Overview

This document describes all available approaches for implementing schema.org structured data (JSON-LD) in AEM Edge Delivery Services (EDS) with Universal Editor (xWalk). It covers four approaches — client-side JS injection, manual metadata, automated AEM publish-time enrichment, and Edge Worker — with detailed pros, cons, and recommendations for each.

---

## 2. What Is JSON-LD?

JSON-LD (JavaScript Object Notation for Linked Data) is the recommended format for embedding schema.org structured data in web pages. It helps search engines understand page content and can trigger rich results in search listings.

### Example

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "VideoObject",
  "name": "Product Demo Video",
  "description": "A walkthrough of our latest product features",
  "thumbnailUrl": ["https://example.com/thumb.jpg"],
  "uploadDate": "2024-06-15T10:00:00Z",
  "duration": "PT2M30S",
  "contentUrl": "https://example.com/video.mp4"
}
</script>
```

### Where It Lives

The `<script type="application/ld+json">` tag must be in the page `<head>` (preferred) or `<body>`. For SEO best practice, it should be in the **initial HTML markup** — not injected by JavaScript after page load.

---

## 3. EDS Built-In Support: `json-ld` Special Metadata Property

EDS has a **built-in special metadata property** called `json-ld`. When set on a page, the EDS pipeline automatically renders it as a `<script type="application/ld+json">` tag in the `<head>`.

### How It Works

| Authoring Type | How to Set json-ld |
|---|---|
| **Google Docs / SharePoint** | Add a row in the Metadata table at the bottom of the document with key `json-ld` and value as the JSON string |
| **Universal Editor (xWalk)** | Set the `json-ld` field in the Page Properties panel (requires `json-ld` field in `page-metadata` component model) |

### Component Model Setup (xWalk)

To expose the `json-ld` field in Universal Editor, add it to the `page-metadata` model in `component-models.json`:

```json
{
  "id": "page-metadata",
  "fields": [
    {
      "component": "text",
      "valueType": "string",
      "name": "jcr:title",
      "label": "Title"
    },
    {
      "component": "text",
      "valueType": "string",
      "name": "jcr:description",
      "label": "Description"
    },
    {
      "component": "text",
      "valueType": "string",
      "name": "json-ld",
      "label": "JSON-LD",
      "description": "Schema.org structured data in JSON format. Rendered as <script type=application/ld+json> in page head."
    }
  ]
}
```

### Where to Find Page Properties in Universal Editor

In Universal Editor, page metadata is **NOT** a visible block on the page canvas. It is accessed through the **Page Properties panel**:

1. Open page in Universal Editor
2. Click on the **page background** (empty area, not any block) — or select the page root in the Content Tree
3. The **right-side Properties panel** shows page-level properties (Title, Description, JSON-LD)

### Pipeline Processing

The EDS pipeline processes the `json-ld` property at the CDN level:

```
jcr:content node has json-ld = '{"@context":"https://schema.org",...}'
        |
        v
EDS Pipeline reads the property
        |
        v
Renders in <head>:
<script type="application/ld+json">
{"@context":"https://schema.org",...}
</script>
```

### Multiple JSON-LD Objects

To include multiple schema.org objects on a single page, wrap them in a **JSON array**:

```json
[
  {"@context":"https://schema.org","@type":"WebPage","name":"My Page"},
  {"@context":"https://schema.org","@type":"VideoObject","name":"My Video","duration":"PT1M9S"}
]
```

This renders as a single `<script>` tag containing the array. Google supports JSON-LD arrays.

### Where json-ld Can Be Tested

| Environment | json-ld rendered in `<head>`? |
|---|---|
| **localhost** (`aem up`) | No — local CLI does not process pipeline metadata |
| **aem.page** (Preview) | Yes — pipeline processes metadata |
| **aem.live** (Live) | Yes — pipeline processes metadata |

To test, preview or publish the page, then: Right-click → View Page Source → search for `ld+json`.

---

## 4. Approach 1: Client-Side JavaScript (Block JS Injection)

### How It Works

The block's `decorate()` function dynamically fetches metadata from an external API and injects JSON-LD into the page `<head>` at runtime.

```
Page loads in browser
  → Block JS executes (e.g., video.js)
  → Block reads authored data (account name + video ID)
  → Resolves Account ID from config spreadsheet
  → Calls external API (e.g., Brightcove Playback API) from browser
  → Gets metadata (title, description, duration, thumbnail)
  → Builds JSON-LD object
  → Creates <script type="application/ld+json"> element
  → Appends to document <head>
```

### What Crawlers See

```
Initial HTML <head>  →  No JSON-LD ❌
After JS executes    →  JSON-LD appears ✅ (only if crawler runs JS)
```

### Reference Implementation

```javascript
async function injectVideoSchema(accountId, playerId, videoId) {
  try {
    // Get policy key from player config
    const configResp = await fetch(
      `https://players.brightcove.net/${accountId}/${playerId}_default/config.json`
    );
    if (!configResp.ok) return;
    const playerConfig = await configResp.json();
    const policyKey = playerConfig?.video_cloud?.policy_key;
    if (!policyKey) return;

    // Fetch video metadata from Playback API
    const apiResp = await fetch(
      `https://edge.api.brightcove.com/playback/v1/accounts/${accountId}/videos/${videoId}`,
      { headers: { Accept: `application/json;pk=${policyKey}` } }
    );
    if (!apiResp.ok) return;
    const video = await apiResp.json();

    // Convert duration from ms to ISO 8601
    const totalSec = Math.floor((video.duration || 0) / 1000);
    const minutes = Math.floor(totalSec / 60);
    const seconds = totalSec % 60;

    // Build and inject JSON-LD
    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'VideoObject',
      name: video.name || '',
      description: video.description || '',
      thumbnailUrl: [video.thumbnail || video.poster || ''],
      uploadDate: video.published_at || '',
      duration: `PT${minutes}M${seconds}S`,
      contentUrl: (video.sources?.find((s) => s.type === 'video/mp4') || {}).src || '',
      embedUrl: `https://players.brightcove.net/${accountId}/${playerId}_default/index.html?videoId=${videoId}`,
    };

    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.textContent = JSON.stringify(jsonLd);
    document.head.appendChild(script);
  } catch (err) {
    console.error('Failed to generate video schema.org:', err);
  }
}
```

### Developer Effort

Low — JavaScript only, no backend or infrastructure changes.

### Pros and Cons

| Pros | Cons |
|---|---|
| Simplest to implement — just JS code in block | NOT in initial HTML — depends on JS execution |
| Zero authoring burden | Google uses two-wave indexing — JS-rendered content may take days/weeks to be indexed |
| Always fresh — fetches from API on every page load | Non-Google crawlers (Bing, Yandex, social scrapers) often do not execute JS |
| No AEM backend code needed | Extra API calls on every page load (performance cost for visitor) |
| No infrastructure changes | If external API is slow or down, JSON-LD silently fails |
| Works immediately — no publish/deploy needed | Cannot verify via "View Page Source" — only visible in DevTools Elements tab |

### Best For

- Non-SEO-critical structured data
- Internal pages not indexed by search engines
- Quick prototypes or proof of concepts
- Temporary solution while backend approach is being built

---

## 5. Approach 2: Manual Metadata (Author Enters JSON-LD in UE)

### How It Works

The author manually types or pastes the JSON-LD string into the `json-ld` field in the Page Properties panel in Universal Editor.

```
Author opens page in Universal Editor
  → Clicks page background to open Page Properties
  → Finds "JSON-LD" text field
  → Manually pastes JSON-LD string
  → Publishes page
  → EDS pipeline reads json-ld property from jcr:content
  → Renders <script type="application/ld+json"> in <head>
```

### What Crawlers See

```
Initial HTML <head>  →  JSON-LD present ✅ (rendered by EDS pipeline)
```

### Authoring Experience

```
+---------------------------------------+
| Page Properties                       |
+---------------------------------------+
| Title:       [Digital Solutions    ]  |
| Description: [Transforming lab...  ]  |
| JSON-LD:     [{"@context":"https:  ]  |  ← author pastes JSON here
|              [//schema.org","@type ]  |
|              [":"WebPage",...}      ]  |
+---------------------------------------+
```

### Developer Effort

Minimal — only requires adding the `json-ld` field to the `page-metadata` component model.

### Pros and Cons

| Pros | Cons |
|---|---|
| In initial HTML — SEO-optimal | Heavy author burden — must manually type/paste JSON |
| No JS dependency — all crawlers see it immediately | Author must know schema.org syntax (error-prone) |
| Uses built-in EDS `json-ld` special property — no custom code | Author may not have required metadata (e.g., video title, duration, thumbnail URL) — must look it up externally |
| Works on all environments (preview + live) | Data goes stale — if source data changes, author must manually update |
| Simple to test — View Page Source shows it | Not scalable — impractical for large sites (50K+ pages) |
| Multiple schemas via JSON array `[{...},{...}]` | Copy-paste errors likely — one wrong character breaks JSON |

### Best For

- Small sites with few pages needing structured data
- Static schemas that rarely change (Organization, WebSite, BreadcrumbList)
- One-off pages where manual effort is acceptable
- Pages where the structured data is simple and author-known (e.g., FAQ schema where the author wrote the Q&A)

---

## 6. Approach 3: Automated AEM Publish-Time Enrichment

### How It Works

Custom AEM code (Event Listener, Workflow Step, or Servlet) automatically populates the `json-ld` property on the page's `jcr:content` node at publish time by fetching metadata from external APIs.

```
Author adds block (e.g., Video) with minimal data (account + video ID)
  → Author clicks "Publish"
  → AEM Replication Event fires
  → Custom Event Listener catches it
  → Scans page content nodes for relevant blocks (e.g., video blocks)
  → Extracts authored data (account name + video ID)
  → Resolves full identifiers from AEM config
  → Calls external API (e.g., Brightcove Playback API) server-side
  → Gets metadata (title, description, duration, thumbnail, publish date)
  → Builds JSON-LD object (or JSON array for multiple blocks)
  → Updates json-ld property on page's jcr:content node
  → Page continues publishing to EDS
  → EDS pipeline reads json-ld → renders in <head>
```

### What Crawlers See

```
Initial HTML <head>  →  JSON-LD present ✅ (rendered by EDS pipeline)
```

### Architecture

```
                    AEM JCR (Content Repository)
                    ┌─────────────────────────────────┐
                    │  /content/mysite/page/jcr:content │
                    │                                   │
                    │  jcr:title = "Digital Solutions"   │
                    │  json-ld = ""  ← initially empty   │
                    └─────────────────────────────────┘
                                    |
                         Page Publish Event fires
                                    |
                                    v
                    ┌─────────────────────────────────┐
                    │  Custom Event Listener            │
                    │                                   │
                    │  1. Scan page for target blocks    │
                    │  2. Extract authored data           │
                    │  3. Call external API (server-side) │
                    │  4. Build JSON-LD from response     │
                    │  5. Update json-ld property         │
                    └─────────────────────────────────┘
                                    |
                                    v
                    ┌─────────────────────────────────┐
                    │  json-ld property now populated   │
                    │  '{"@context":"https://schema.org" │
                    │    "@type":"VideoObject",...}'      │
                    └─────────────────────────────────┘
                                    |
                         Page publishes to EDS CDN
                                    |
                                    v
                    EDS pipeline renders JSON-LD in <head> ✅
```

### Trigger Options

| Trigger | When json-ld Gets Updated | Use Case |
|---|---|---|
| **Publish Event Listener** | Automatically when author clicks Publish | Default — every publish gets fresh data |
| **Workflow Step** | Added to publish workflow chain | When publish process is already customized |
| **Scheduled Job** | Runs on a schedule (e.g., nightly) to refresh all relevant pages | Keeps data fresh even if external source changes without page re-publish |
| **On-Demand Servlet** | Admin triggers via URL (e.g., `/bin/refresh-schema?path=/content/...`) | Manual refresh for specific pages |

### Reference Implementation (Java)

```java
@Component(service = EventHandler.class, immediate = true,
  property = {
    EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
  })
public class SchemaEnrichmentHandler implements EventHandler {

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void handleEvent(Event event) {
        ReplicationAction action = ReplicationAction.fromEvent(event);
        if (action == null || action.getType() != ReplicationActionType.ACTIVATE) return;

        String pagePath = action.getPath();

        try (ResourceResolver resolver = getServiceResolver()) {
            Resource pageContent = resolver.getResource(pagePath + "/jcr:content");
            if (pageContent == null) return;

            // 1. Scan page for blocks that need schema enrichment
            // 2. Extract authored data (account, video ID, etc.)
            // 3. Call external API to get metadata
            // 4. Build JSON-LD
            // 5. Update property

            ModifiableValueMap props = pageContent.adaptTo(ModifiableValueMap.class);
            String jsonLd = buildJsonLd(pageContent);

            if (jsonLd != null && !jsonLd.isEmpty()) {
                props.put("json-ld", jsonLd);
                resolver.commit();
            }
        } catch (Exception e) {
            log.error("Schema enrichment failed for: {}", pagePath, e);
        }
    }
}
```

### Handling Multiple Blocks on Same Page

If a page has multiple blocks that each need schema.org (e.g., two videos), the Event Listener builds a **JSON array**:

```json
[
  {"@context":"https://schema.org","@type":"VideoObject","name":"Video 1","duration":"PT1M9S"},
  {"@context":"https://schema.org","@type":"VideoObject","name":"Video 2","duration":"PT3M22S"}
]
```

### Data Freshness Strategy

```
Publish-time enrichment (default)
  → Fresh at every publish

Scheduled job (supplement)
  → Runs nightly: scans all pages with video blocks
  → Re-fetches metadata from external API
  → Updates json-ld property
  → Triggers re-publish if data changed
  → Ensures data stays fresh even if external source updates
```

### Developer Effort

Medium — requires AEM backend Java/OSGi development (Event Listener or Workflow Step), plus external API integration.

### Pros and Cons

| Pros | Cons |
|---|---|
| In initial HTML — SEO-optimal | Requires AEM backend development (Java/OSGi) |
| Zero authoring burden — fully automated | json-ld only refreshes on publish — if external data changes without re-publish, it is stale |
| Always accurate at publish time — fetches fresh data | Adds slight delay to publish process (external API call ~100-200ms) |
| Uses built-in EDS `json-ld` pipeline — no Edge Worker | API credentials may be needed for server-side external API access |
| Familiar AEM development pattern (Event Listener / Workflow) | If external API fails during publish, need error handling strategy |
| Can be extended with scheduled job for periodic refresh | AEM Author instance needs outbound network access to external API |
| Handles multiple blocks on same page via JSON array | Need to handle edge cases (block removed, video ID changed, etc.) |
| Matches current AEM 6.4 server-side rendering behavior | |

### Best For

- Production use with dynamic schemas (VideoObject, Product, Event)
- SEO-critical structured data that must be in initial HTML
- Large-scale sites (50K+ pages) where manual metadata is impractical
- Use cases where author provides minimal data but schema needs rich metadata from external systems

---

## 7. Approach 4: Edge Worker (CDN-Level HTML Transformation)

### How It Works

An Edge Worker (Cloudflare Worker, Fastly Compute@Edge) intercepts the HTML response at the CDN level, detects relevant blocks, fetches metadata from external APIs, and injects JSON-LD into the `<head>` before the response reaches the browser.

```
Author publishes page (json-ld property is empty or not set)
  → Page HTML reaches EDS CDN edge
  → Edge Worker intercepts the HTML response
  → Parses HTML body, detects target block markup
  → Extracts authored data from block content
  → Fetches external API (with edge-level caching)
  → Builds JSON-LD object
  → Injects <script type="application/ld+json"> into <head>
  → Returns modified HTML to browser/crawler
```

### What Crawlers See

```
Initial HTML <head>  →  JSON-LD present ✅ (injected before browser receives HTML)
```

### Architecture

```
Browser/Crawler requests page
        |
        v
EDS CDN Edge
        |
        v
┌────────────────────────────────────────┐
│  Edge Worker                            │
│                                          │
│  1. Fetch original HTML from origin      │
│  2. Parse HTML for target blocks         │
│  3. Extract authored data                │
│  4. Check edge cache for metadata        │
│     ├─ HIT  → use cached data (~2ms)     │
│     └─ MISS → call external API (~100ms) │
│              → cache response (1hr TTL)   │
│  5. Build JSON-LD                         │
│  6. Inject into <head>                    │
│  7. Return modified HTML                  │
└────────────────────────────────────────┘
        |
        v
Browser/Crawler receives HTML WITH JSON-LD in <head>
```

### Caching Strategy

| Setting | Value | Reason |
|---|---|---|
| **TTL** | 1 hour | Balances freshness with performance |
| **Cache Key** | `{apiSource}:{identifier}` (e.g., `bc:3663210762001:6293625135001`) | Unique per content item |
| **Storage** | Edge KV store or Cache API | Persistent across requests |
| **Invalidation** | TTL-based auto-expire | When source data changes, it picks up within TTL window |

### Performance Impact

```
First request (cache MISS):
  → External API call: ~100-200ms added latency
  → Total added latency: ~150-250ms

Subsequent requests (cache HIT):
  → Read from edge cache: ~1-2ms
  → Total added latency: ~2-5ms (negligible)
```

### Reference Implementation

```javascript
async function handleRequest(request, env) {
  const response = await fetch(request);
  const contentType = response.headers.get('content-type') || '';

  // Only process HTML responses
  if (!contentType.includes('text/html')) return response;

  let html = await response.text();

  // Detect target blocks in HTML
  const blockRegex = /<div class="video[^"]*">([\s\S]*?)<\/div>\s*<\/div>/g;
  const schemas = [];

  // Load config (edge-cached)
  const configResp = await fetch(
    `${new URL(request.url).origin}/content/config/brightcove.json`
  );
  const config = configResp.ok ? (await configResp.json()).data : [];

  let match;
  while ((match = blockRegex.exec(html)) !== null) {
    // Extract data from block markup
    // Resolve identifiers from config
    // Fetch external API (with caching)
    // Build schema object
    const schema = await fetchAndBuildSchema(match[0], config, env.CACHE);
    if (schema) schemas.push(schema);
  }

  // Inject JSON-LD into <head>
  if (schemas.length > 0) {
    const jsonLdTags = schemas
      .map((s) => `<script type="application/ld+json">${JSON.stringify(s)}</script>`)
      .join('\n');
    html = html.replace('</head>', `${jsonLdTags}\n</head>`);
  }

  return new Response(html, {
    status: response.status,
    headers: response.headers,
  });
}
```

### Developer Effort

High — requires Edge Worker development, edge caching infrastructure, HTML parsing logic, and external API integration at the edge level.

### Pros and Cons

| Pros | Cons |
|---|---|
| In initial HTML — SEO-optimal | Most complex to implement — Edge Worker development |
| Zero authoring burden | Requires edge compute infrastructure (Cloudflare Workers / Fastly Compute) |
| Always fresh — configurable cache TTL | HTML parsing at edge is fragile — block markup changes can break extraction |
| No AEM backend code needed | Adds latency on cache MISS (~100-200ms for external API call) |
| Decoupled from publish process — does not slow down authoring | Debugging is harder — edge logs, not local |
| Can enrich ANY page retroactively without re-publishing | Edge Workers have execution time limits and memory constraints |
| Works even if page was published without json-ld | Two external dependencies at edge: config + external API |
| | May conflict with other CDN configurations or edge rules |

### Best For

- Cannot modify AEM backend (no Java development available)
- Need to retroactively enrich pages already published without re-publishing
- External data changes frequently and short TTL cache is acceptable
- Organization prefers CDN-level processing over application-level processing

---

## 8. Side-by-Side Comparison

| Criteria | 1. Client-Side JS | 2. Manual Metadata | 3. AEM Publish-Time | 4. Edge Worker |
|---|---|---|---|---|
| **In initial HTML?** | No | Yes | Yes | Yes |
| **SEO-friendly?** | Partial (Google yes, others no) | Yes | Yes | Yes |
| **Author effort** | None | High (paste JSON) | None | None |
| **Developer effort** | Low (JS only) | Minimal (model field) | Medium (AEM Java) | High (Edge infra) |
| **Data freshness** | Real-time (every page load) | Stale (manual update) | Fresh at publish time | Configurable TTL cache |
| **Scalability** | Good | Poor (manual) | Good | Good |
| **Infrastructure** | None | None | AEM Event Listener | Edge compute + KV cache |
| **Dependency** | Browser JS execution | Author accuracy | AEM publish pipeline | CDN Edge Worker |
| **AEM 6.4 parity** | No (was server-side) | Partially | Yes — closest match | Yes |
| **Risk level** | Low | Low (but error-prone) | Low | Medium (edge complexity) |
| **Can enrich old pages?** | Yes (on load) | Yes (manual re-edit) | Needs re-publish | Yes (retroactive) |
| **Multiple schemas?** | Yes (multiple injections) | Yes (JSON array) | Yes (JSON array) | Yes (multiple injections) |

---

## 9. Recommendation by Use Case

| Use Case | Recommended Approach | Why |
|---|---|---|
| **Dynamic schemas from external API** (VideoObject, Product) | **Approach 3** — AEM Publish-Time | Automated, in initial HTML, zero author effort, standard AEM pattern |
| **Static schemas that rarely change** (Organization, WebSite) | **Approach 2** — Manual Metadata | Simple, in initial HTML, data is stable and author-known |
| **Structural schemas derived from page** (BreadcrumbList) | **Approach 1** — Client-Side JS | Data comes from page structure (URL path), not external API. Less SEO-critical for rich results |
| **No AEM backend access available** | **Approach 4** — Edge Worker | Only option that adds to initial HTML without AEM changes |
| **Quick prototype or proof of concept** | **Approach 1** — Client-Side JS | Fastest to implement, no deploy dependencies |
| **Mixed schemas on same page** | **Approach 3 + 2** combined | Automated enrichment for dynamic data + manual metadata for static data, combined as JSON array |
| **Retroactive enrichment of published pages** | **Approach 4** — Edge Worker | Only approach that enriches without re-publishing |

---

## 10. Implementation Priority

For a typical enterprise migration (e.g., AEM 6.4 to EDS with xWalk):

| Phase | What to Implement | Approach |
|---|---|---|
| **Phase 1 — Immediate** | Add `json-ld` field to `page-metadata` model | Enables Approach 2 (Manual) |
| **Phase 2 — Sprint 1** | Static schemas (Organization, WebSite) via manual metadata | Approach 2 |
| **Phase 3 — Sprint 2-3** | AEM Event Listener for dynamic schemas (Video, Product) | Approach 3 |
| **Phase 4 — Optional** | Scheduled job for periodic refresh of dynamic schemas | Extension of Approach 3 |
| **Fallback** | Edge Worker if AEM backend is not feasible | Approach 4 |

---

## 11. Limitations

| Limitation | Detail | Mitigation |
|---|---|---|
| **Local dev (`aem up`) does not render json-ld** | The local AEM CLI does not emulate the pipeline's metadata processing | Test on aem.page (Preview) or aem.live (Live) |
| **Single text field for json-ld in UE** | The `json-ld` field is a plain text input — no JSON validation in the editor | Use JSON array for multiple schemas; validate via Google Rich Results Test after publish |
| **Approach 3 requires outbound network from AEM** | AEM Author instance must be able to call external APIs | Ensure firewall rules allow outbound HTTPS to required API endpoints |
| **Approach 3 adds publish latency** | External API call during publish adds ~100-200ms | Acceptable for most use cases; can be made async if needed |
| **Approach 4 requires CDN customization** | Edge Workers may need specific CDN vendor support | Confirm CDN capabilities (Cloudflare Workers, Fastly Compute, etc.) early in project |
