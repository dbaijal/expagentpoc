# Major Review Workflow — Migration Analysis: AEM 6.4 to AEM Cloud Service + EDS (xWalk)

## 1. Current Workflow Definition (AEM 6.4)

### Workflow Flow — Complete Step Sequence

```
 1. Log Start Workflow                          [LogWorkflowHandler - auto-advance]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - auto-advance]
 3. Select Web Operations Team                  [Dialog Participant Step → lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata      [DialogParameterHandler - auto-advance]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - conditional]
 6. Log Rework Content                          [LogWorkflowHandler - auto-advance]
 7. Send Rework Notification Email              [EmailNotificationsHandler - auto-advance]
 8. Rework Content Step                         [Dynamic Participant → ECMA initiator-chooser]
 9. OR Split: Done with Rework / Cancel
    ├─ Branch 1: Log "Done" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
10. Set steppedBack=true                        [PlaceParametersHandler - auto-advance]
11. Report Metadata                             [LogWorkflowHandler - auto-advance]
12. Replicate to Preview                        [ReplicateToAgentHandler → push-to-preview]
13. Cache Flush Preview                         [ReplicateToAgentHandler → flush-preview]
14. Notify Design Reviewer Email                [EmailNotificationsHandler - auto-advance]
15. Review: UX / Design                         [Participant Step → lt-wf-design-reviewer]
16. OR Split: Approve / Reject / Cancel
    ├─ Branch 1: Log "Approve" → continue
    ├─ Branch 2: Log "Reject" → Jump back to step 6 (Rework)
    └─ Branch 3: Log "Cancel" → Jump to Cancel Processing
17. Log Start Editorial Review                  [LogWorkflowHandler - auto-advance]
18. Replicate to Preview                        [ReplicateToAgentHandler → push-to-preview]
19. Cache Flush Preview                         [ReplicateToAgentHandler → flush-preview]
20. Notify Editorial Reviewer Email             [EmailNotificationsHandler - auto-advance]
21. Review: Editorial                           [Participant Step → lt-wf-editorial-reviewer]
22. OR Split: Approve / Cancel
    ├─ Branch 1: Log "Approve" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
23. Log Start Final Production Review           [LogWorkflowHandler - auto-advance]
24. Replicate to Preview                        [ReplicateToAgentHandler → push-to-preview]
25. Cache Flush Preview                         [ReplicateToAgentHandler → flush-preview]
26. Notify Final Production Reviewer Email      [EmailNotificationsHandler - auto-advance]
27. Review: Final Production                    [Dynamic Participant → PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay for delayed release date selection)
28. OR Split: Approve / Cancel / Send to Translation
    ├─ Branch 1: Log "Approve" + Place Delayed Release Date → continue to step 29
    ├─ Branch 2: Log "Cancel" → Jump to Cancel Processing
    └─ Branch 3: Send to Translation sub-flow:
        a. Log "Send to Translation"
        b. Place Delayed Release Date
        c. Email Web-Ops about Translation
        d. Waiting for Translation              [Dynamic Participant → PropertiesParticipantChooser]
        e. OR Split: Translation Done / Cancel Translation
           ├─ Branch 1: Log "Done" → continue to step 29
           └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
29. Log Start Production Run                    [LogWorkflowHandler - auto-advance]
30. Replicate to Preview                        [ReplicateToAgentHandler → push-to-preview]
31. Cache Flush Preview                         [ReplicateToAgentHandler → flush-preview]
32. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - auto-advance]
33. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - auto-advance]
34. Send Pending Publication Email              [EmailNotificationsHandler - auto-advance]
35. Waiting to Publish                          [Dynamic Participant → AbsoluteTimeAutoAdvancer timeout]
36. OR Split: Force Deploy / Cancel
    ├─ Branch 1: Log "Force Deploy" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
37. Replicate to Production                     [ReplicateToAgentHandler → push-to-production]
38. Cache Flush Production                      [ReplicateToAgentHandler → flush-production]
39. Send Finished Publication Email             [EmailNotificationsHandler - auto-advance]
40. Jump to End (skip cancel processing)        [JumpToNodeHandler - auto-advance]
41. Cancel Workflow Processing                  [LogWorkflowHandler - auto-advance]
42. Inform Content Author about Cancellation    [EmailNotificationsHandler - auto-advance]
43. Log End of Workflow                         [LogWorkflowHandler - auto-advance]
```

### Participants

| Role | AEM Group | Workflow Step(s) |
|------|-----------|-----------------|
| Content Author / Initiator | lt-wf-author | Step 3 (Select Web Ops), Step 8 (Rework) |
| Design / UX Reviewer | lt-wf-design-reviewer | Step 15 |
| Editorial Reviewer | lt-wf-editorial-reviewer | Step 21 |
| Final Production Reviewer (Web Ops) | Dynamic (PropertiesParticipantChooser) | Step 27 |
| Rejection Notification Recipients | lt-wf-rejection-notification | Step 7 (email only) |
| Major Notification Recipients | lt-wf-major-notification | Steps 34, 39 (email only) |

### Custom Java Handlers (9 classes)

| Handler | Purpose | Used in Steps |
|---------|---------|---------------|
| `LogWorkflowHandler` | Logs messages with placeholder substitution | 1, 6, 9, 10, 11, 16, 17, 22, 23, 28, 29, 36, 41, 43 |
| `PlaceParametersHandler` | Sets key-value pairs on workflow metadata | 2, 10 |
| `DialogParameterHandler` | Reads dialog properties into workflow metadata | 4, 28a, 28b |
| `JumpToNodeHandler` | Unconditional jump to named workflow node | 9, 16, 22, 28, 36, 40 |
| `JumpToNodeIfSetHandler` | Conditional jump based on property existence | 5 |
| `EmailNotificationsHandler` | Sends email notifications to participants | 7, 14, 20, 26, 28c, 34, 39, 42 |
| `ReplicateToAgentHandler` | Replicates content to specific agents | 12, 13, 18, 19, 24, 25, 30, 31, 37, 38 |
| `CheckDelayedReleaseDateHandler` | Validates/sets delayed release timestamp | 32 |
| `SetDelayedReleaseAsCommentHandler` | Reports delayed release date in inbox | 33 |

### Workflow Dialogs (4 components)

| Dialog | Type | Used in Step | Status |
|--------|------|-------------|--------|
| Absolute Timer Delay | Touch UI (Coral) | Step 27 (delayed release date picker) | Clean, Cloud-ready |
| Dynamic Participant Dialog | Both Classic + Touch UI | Step 27 (Final Production review with dialog) | Touch UI works, Classic artifacts must be removed |
| Regulatory Select Product Division | Classic UI only | Not in Major Review (may be in other workflows) | Must rebuild in Touch UI |
| Web Operations Team Selection | Empty stub | Step 3 (referenced but no fields) | Verify if functional |

---

## 2. Future Workflow Definition (AEM Cloud Service + EDS xWalk)

### Architecture: Single Main Workflow + Separate Translation Workflow

The main review-approve-publish workflow remains as a single workflow. Only the translation branch is separated out because long-running "Waiting for Translation" steps (days/weeks) are not recommended in AEM Cloud Service due to instance restart policies, workflow purge schedules, and memory management.

### Main Workflow: Content Review, Approval & Publish

This is the same workflow as today — with technical changes to replication, ECMA scripts, and annotations — but the same flow, participants, and transitions.

```
 1. Log Start Workflow                          [LogWorkflowHandler - refactored]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - refactored]
 3. Select Web Operations Team                  [Dialog Participant Step → lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata      [DialogParameterHandler - refactored]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - refactored]
 6. Log Rework Content                          [LogWorkflowHandler - refactored]
 7. Send Rework Notification Email              [EmailNotificationsHandler - refactored]
 8. Rework Content Step                         [Dynamic Participant → Java InitiatorParticipantChooser] ← CHANGED (ECMA → Java)
 9. OR Split: Done with Rework / Cancel
    ├─ Branch 1: Log "Done" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
10. Set steppedBack=true                        [PlaceParametersHandler - refactored]
11. Report Metadata                             [LogWorkflowHandler - refactored]
12. Publish to Preview Tier                     [Cloud Sling Content Distribution API] ← CHANGED (replaces agent-based replication)
    (Cache Flush step REMOVED — EDS CDN handles automatically)
13. Notify Design Reviewer Email                [EmailNotificationsHandler - refactored]
14. Review: UX / Design                         [Participant Step → lt-wf-design-reviewer]
15. OR Split: Approve / Reject / Cancel
    ├─ Branch 1: Log "Approve" → continue
    ├─ Branch 2: Log "Reject" → Jump back to step 6 (Rework)
    └─ Branch 3: Log "Cancel" → Jump to Cancel Processing
16. Log Start Editorial Review                  [LogWorkflowHandler - refactored]
17. Publish to Preview Tier                     [Cloud Sling Content Distribution API] ← CHANGED
18. Notify Editorial Reviewer Email             [EmailNotificationsHandler - refactored]
19. Review: Editorial                           [Participant Step → lt-wf-editorial-reviewer]
20. OR Split: Approve / Cancel
    ├─ Branch 1: Log "Approve" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
21. Log Start Final Production Review           [LogWorkflowHandler - refactored]
22. Publish to Preview Tier                     [Cloud Sling Content Distribution API] ← CHANGED
23. Notify Final Production Reviewer Email      [EmailNotificationsHandler - refactored]
24. Review: Final Production                    [Dynamic Participant → PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay for delayed release date selection)
25. OR Split: Approve / Cancel / Send to Translation
    ├─ Branch 1: Approve + Place Delayed Release Date → continue to step 26
    ├─ Branch 2: Cancel → Jump to Cancel Processing
    └─ Branch 3: Send to Translation → Submit to Wordbee API → END ← CHANGED
        (Main workflow ENDS here for translation branch)
        (Separate Translation Completion Workflow triggered by Wordbee webhook)
26. Log Start Production Run                    [LogWorkflowHandler - refactored]
27. Publish to Preview Tier                     [Cloud Sling Content Distribution API] ← CHANGED
28. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - refactored]
29. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - refactored]
30. Send Pending Publication Email              [EmailNotificationsHandler - refactored]
31. Waiting to Publish                          [Dynamic Participant → AbsoluteTimeAutoAdvancer]
32. OR Split: Force Deploy / Cancel
    ├─ Branch 1: Log "Force Deploy" → continue
    └─ Branch 2: Log "Cancel" → Jump to Cancel Processing
33. Publish to Production                       [Cloud Sling Content Distribution API] ← CHANGED
    (Publish action → xWalk pipeline → Content Bus → EDS CDN)
    (Cache Flush REMOVED — EDS CDN invalidates automatically)
34. Send Finished Publication Email             [EmailNotificationsHandler - refactored]
35. Jump to End (skip cancel processing)        [JumpToNodeHandler - refactored]
36. Cancel Workflow Processing                  [LogWorkflowHandler - refactored]
37. Inform Content Author about Cancellation    [EmailNotificationsHandler - refactored]
38. Log End of Workflow                         [LogWorkflowHandler - refactored]
```

**Key:** The non-translation branches (Approve and Cancel) follow the same flow as today. Only the "Send to Translation" branch changes — it ends the main workflow and delegates to an external process.

### Separate Workflow: Translation Completion (Event-Driven)

This replaces the current "Waiting for Translation" participant step that keeps the main workflow open for days/weeks.

**Trigger:** Wordbee webhook calls AEM Cloud endpoint on translation completion. An AEM Event Listener automatically starts this workflow on the translated page.

```
Wordbee completes translation
  → Sends webhook to AEM: POST /bin/lifetech/translation-complete
  → AEM Event Listener starts Translation Completion Workflow

Translation Completion Workflow:
 1. Log Translation Complete                    [LogWorkflowHandler - refactored]
 2. Validate Translated Content                 [Optional - new step if needed]
 3. Continue to Production Run:
    a. Publish to Preview Tier                  [Cloud Sling Content Distribution API]
    b. Check Delayed Release Date               [CheckDelayedReleaseDateHandler - refactored]
    c. Report Delayed Release as Comment        [SetDelayedReleaseAsCommentHandler - refactored]
    d. Send Pending Publication Email           [EmailNotificationsHandler - refactored]
    e. Waiting to Publish                       [Dynamic Participant → AbsoluteTimeAutoAdvancer]
    f. OR Split: Force Deploy / Cancel
    g. Publish to Production                    [Cloud Sling Content Distribution API]
    h. Send Finished Publication Email          [EmailNotificationsHandler - refactored]
 4. Log End of Workflow                         [LogWorkflowHandler - refactored]
```

**Why only translation is separated:**
- The "Waiting for Translation" step can keep a workflow open for **days or weeks** — this is problematic in AEM Cloud Service where instances restart regularly
- All other waits (review steps, delayed release timer) are **short-lived** (hours, not days) and safe to keep in the main workflow
- The `AbsoluteTimeAutoAdvancer` for delayed release typically waits hours to a day, which is acceptable

---

## 3. Detailed Change Analysis — Step by Step

### 3.1 Steps RETAINED (No Functional Change — Code Refactoring Only)

These steps preserve identical behavior. Only OSGi annotation migration required.

| Step | Handler | Change Required |
|------|---------|----------------|
| Log Start/End/Phase Messages | `LogWorkflowHandler` | Migrate Felix SCR → OSGi DS annotations |
| Set/Place Parameters | `PlaceParametersHandler` | Migrate annotations + replace deprecated `new Long()` → `Long.valueOf()` |
| Dialog Parameter Handler | `DialogParameterHandler` | Migrate annotations |
| Jump to Node | `JumpToNodeHandler` | Migrate annotations |
| Jump to Node If Set | `JumpToNodeIfSetHandler` | Migrate annotations |
| Check Delayed Release Date | `CheckDelayedReleaseDateHandler` | Migrate annotations |
| Report Delayed Release | `SetDelayedReleaseAsCommentHandler` | Migrate annotations |
| All Participant Steps | OOTB Participant/OR Split | No change |
| Absolute Timer Delay Dialog | Touch UI dialog | No change — already Cloud-ready |
| All OR Splits | OOTB OR Split | No change |

### 3.2 Steps MODIFIED (Behavior Change Required)

| Current Step | Current Implementation | Future Implementation | Reason |
|-------------|----------------------|----------------------|--------|
| **Rework Content Step** | Dynamic Participant using ECMA script: `/libs/workflow/scripts/initiator-participant-chooser.ecma` | Java `ParticipantStepChooser` implementation: `InitiatorParticipantChooser` | ECMA workflow scripts are removed in AEM Cloud Service |
| **Replicate to Preview** (×4) | `ReplicateToAgentHandler` → `push-to-preview` agent | AEM Cloud Sling Content Distribution API: `distributor.distribute(resolver, new SimpleDistributionRequest(ADD, path))` | Agent-based replication replaced by Cloud Distribution in AEM Cloud Service |
| **Replicate to Production** (×1) | `ReplicateToAgentHandler` → `push-to-production` agent | AEM Cloud Publish action → xWalk pipeline → Content Bus → EDS CDN (aem.live) | EDS delivery replaces traditional publish instance + Dispatcher |
| **Email Notifications** (×8) | `EmailNotificationsHandler` with templates at `/etc/workflow/notification/email/lifetech/...` | Same handler (refactored) with templates relocated to `/conf/<project>/settings/workflow/notification/email/...` | `/etc/` paths deprecated in AEM Cloud Service |
| **Email — Cloudwords Integration** | `EmailNotificationsHandler` references `CloudwordsManager` for translation emails | Replace `CloudwordsManager` references with **Wordbee API/connector** | Cloudwords is legacy; client uses Wordbee |
| **Waiting for Translation** | Participant step that waits indefinitely for human completion | **Removed from main workflow** — translation handled externally via Wordbee webhook → Event Listener → triggers separate Translation Completion Workflow | Long-running wait (days/weeks) not recommended in AEM Cloud Service |
| **Dynamic Participant Dialog** | Has Classic UI `dialog.xml`, `_cq_editConfig.xml`, `tab_advanced.xml`, `details.jsp` | Remove all Classic UI artifacts; retain Touch UI `_cq_dialog`; rewrite `details.jsp` as Sling Model + HTL | Classic UI not available in Cloud Service |
| **Regulatory Select Product Division Dialog** | Classic UI only (`dialog.xml` with ExtJS, inline CQ5 JavaScript) — no Touch UI | **Rebuild entirely** as Touch UI Coral dialog with Granite datasources; migrate data from `/etc/` to `/conf/` | Classic UI not available; Touch UI dialog missing |

### 3.3 Steps REMOVED

| Current Step | Reason for Removal | How Use Case Is Addressed |
|-------------|-------------------|--------------------------|
| **Cache Flush Preview** (×4 instances) | AEM Cloud Service + EDS CDN handle cache invalidation automatically on publish | No replacement needed — cache invalidation is built into the EDS delivery pipeline |
| **Cache Flush Production** (×1 instance) | Same as above — EDS CDN (Fastly) uses push-based invalidation | No replacement needed |
| **SDL Retry Send Parameters** (place + remove steps) | Legacy SDL/Cloudwords translation integration | Replaced by Wordbee integration (if retry logic needed, implement in Wordbee connector) |
| **Waiting for Translation participant step** | Long-running workflow step (days/weeks) not recommended in Cloud | Replaced by event-driven pattern: Wordbee webhook → AEM Event Listener → triggers separate Translation Completion Workflow |

### 3.4 Functionality Being Removed and Replacement

| Removed Functionality | Current Behavior | Replacement in New Architecture |
|----------------------|-----------------|-------------------------------|
| **ECMA Workflow Scripts** | `initiator-participant-chooser.ecma` assigns rework task to workflow initiator | Java `ParticipantStepChooser` service with `@Component` annotation — same behavior, different implementation |
| **Agent-Based Replication** | `ReplicateToAgentHandler` with named agents (`push-to-preview`, `flush-preview`, `push-to-production`, `flush-production`) | AEM Cloud Sling Content Distribution API — tier-based (preview/publish) without named agents; EDS CDN handles cache |
| **Dispatcher Cache Flush** | Explicit flush via replication to `flush-*` agents | **Eliminated entirely** — EDS CDN uses push-based invalidation triggered automatically on publish |
| **Cloudwords Translation Manager** | `CloudwordsManager` in `EmailNotificationsHandler` and `ReplicateToAgentHandler` for translation path resolution and per-language emails | **Wordbee API/connector** — confirm Wordbee AEM Cloud Service compatibility; implement webhook-based notification |
| **Classic UI Workflow Dialogs** | ExtJS `cq:Dialog` with inline JavaScript (`CQ.utils.HTTP`, `CQ.Util.eval`) | Touch UI Coral dialogs with Granite datasources — no inline JavaScript |
| **`/etc/` Content Paths** | Email templates at `/etc/workflow/notification/email/lifetech/...`; product divisions at `/etc/lifetech/lists/product-divisions/` | Relocate to `/conf/<project>/settings/workflow/notification/email/...` and `/conf/<project>/lists/product-divisions/` |
| **`/libs` JSP Overlays** | README.txt instructs overlaying `/libs/cq/workflow/components/inbox/list/json.jsp` | Use Sling Resource Merger with `/apps` overlay if needed; or eliminate with Cloud Inbox UI |
| **Bulk Activation Macro** | Custom bulk activation via replication agents | See Section 5 below |

---

## 4. Custom Step Support Summary

| Custom Step | Supported in Cloud? | Changes Expected |
|------------|-------------------|-----------------|
| `LogWorkflowHandler` | Yes | Migrate Felix SCR → OSGi DS annotations |
| `PlaceParametersHandler` | Yes | Migrate annotations + fix deprecated constructors |
| `DialogParameterHandler` | Yes | Migrate annotations |
| `JumpToNodeHandler` | Yes | Migrate annotations |
| `JumpToNodeIfSetHandler` | Yes | Migrate annotations |
| `EmailNotificationsHandler` | Yes (with changes) | Migrate annotations + relocate email templates + replace Cloudwords → Wordbee |
| `ReplicateToAgentHandler` | **No** | **Replace with Cloud Distribution API** |
| `CheckDelayedReleaseDateHandler` | Yes | Migrate annotations |
| `SetDelayedReleaseAsCommentHandler` | Yes | Migrate annotations |
| `PropertiesParticipantChooser` | Yes | Migrate annotations; verify non-deprecated API usage |
| ECMA `initiator-participant-chooser` | **No** | **Replace with Java `ParticipantStepChooser`** |
| Absolute Timer Delay Dialog | **Yes** | No changes — Touch UI already Cloud-ready |
| Dynamic Participant Dialog | Partially | Remove Classic UI artifacts; retain Touch UI; rewrite JSP → HTL |
| Regulatory Select Product Division | **No** | **Rebuild entirely** — Touch UI missing, Classic UI only |
| Web Operations Team Selection | Verify | Empty stub — confirm if functional or can be removed |

### Edge Cases

| Edge Case | Current Behavior | Future Behavior |
|-----------|-----------------|----------------|
| Rework loop (reject → rework → re-submit) | Jump nodes cycle between rework and review steps | **Preserved** — jump node logic unchanged |
| First-loop rework skip | `JumpToNodeIfSetHandler` checks `steppedBack` flag to skip rework on first pass | **Preserved** — handler logic unchanged |
| Delayed release with no date set | `CheckDelayedReleaseDateHandler` auto-sets date to NOW + 1 day | **Preserved** — handler logic unchanged |
| Force deploy before timeout | OR Split allows "Force Deploy" to bypass `AbsoluteTimeAutoAdvancer` | **Preserved** — timeout mechanism unchanged in Cloud |
| Translation branch with cancel | Jump to Cancel Processing from any point in translation sub-flow | **Changed** — main workflow ends when translation is submitted; cancel during translation handled in Wordbee; if translation is cancelled in Wordbee, the webhook can trigger a cancellation notification workflow instead of the completion workflow |
| Multiple OR Split branches | Nested OR Splits with jump nodes to avoid nesting complexity | **Preserved** — OR Split and jump node behavior unchanged |

---

## 5. Bulk Activation and Scheduled Publishing

### Current Approach

- **Bulk activation**: Custom `ReplicateToAgentHandler` replicates multiple paths to named agents (`push-to-production`). May also use a custom bulk activation macro/script that triggers replication for page trees.
- **Scheduled publishing**: `AbsoluteTimeAutoAdvancer` timeout handler on a participant step. Workflow waits until the configured date/time, then auto-advances to replicate.

### Future Approach — AEM Cloud Service + EDS

#### Bulk Activation

| Approach | Description | Best For |
|----------|-------------|---------|
| **AEM Cloud Manage Publication** | OOTB "Manage Publication" wizard in Sites Console — select multiple pages, schedule, publish. Supports tree activation. | Authors publishing page trees through UI |
| **Sling Content Distribution API** | Programmatic: `distributor.distribute(resolver, new SimpleDistributionRequest(ADD, paths))` — can process multiple paths in one call | Custom workflows, automated bulk publish |
| **Custom Bulk Publish Servlet** | Deploy a servlet that accepts page paths/tree root and publishes via Distribution API | Regional/off-hours bulk activation scheduled via cron |
| **Cloud Manager Content Transfer** | For initial migration bulk activation | One-time migration events |

**For regional/off-hours needs:**

```java
@Component(service = Servlet.class)
@SlingServletPaths("/bin/lifetech/bulk-publish")
public class BulkPublishServlet extends SlingAllMethodsServlet {

    @Reference
    private Distributor distributor;

    @Override
    protected void doPost(SlingHttpServletRequest request,
                          SlingHttpServletResponse response) {
        String rootPath = request.getParameter("rootPath");
        boolean includeChildren = Boolean.parseBoolean(request.getParameter("deep"));

        // Collect paths
        List<String> paths = collectPaths(rootPath, includeChildren, request.getResourceResolver());

        // Distribute all paths
        DistributionRequest distRequest = new SimpleDistributionRequest(
            DistributionRequestType.ADD,
            paths.toArray(new String[0])
        );

        distributor.distribute(request.getResourceResolver(), distRequest);
    }
}
```

This servlet can be called via cron job or scheduled task for off-hours bulk activation:

```bash
# Scheduled via external scheduler (e.g., Adobe I/O Runtime cron, AWS CloudWatch Events)
curl -X POST "https://author-pXXX-eYYY.adobeaemcloud.com/bin/lifetech/bulk-publish" \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "rootPath=/content/lifetech/us/en/products" \
  -F "deep=true"
```

#### Scheduled Publishing

| Approach | Description | Best For |
|----------|-------------|---------|
| **Workflow with AbsoluteTimeAutoAdvancer** | Same as current — timer-based auto-advance. Works in Cloud. | Individual page scheduled publish within a review workflow |
| **Manage Publication — Schedule** | OOTB "Manage Publication" → select "Later" → set date/time | Author-scheduled publish for one or many pages |
| **AEM Cloud Scheduled Jobs** | Sling Scheduler OSGi service with cron expression | Recurring bulk publish (e.g., "every Friday at 6pm EST for LATAM region") |

**For regional off-hours scheduling:**

```java
@Component(service = Runnable.class)
@Designate(ocd = RegionalPublishScheduler.Config.class)
public class RegionalPublishScheduler implements Runnable {

    @ObjectClassDefinition(name = "Regional Publish Scheduler")
    @interface Config {
        @AttributeDefinition(name = "Cron Expression")
        String scheduler_expression() default "0 0 18 ? * FRI"; // Fridays 6pm

        @AttributeDefinition(name = "Root Path")
        String rootPath() default "/content/lifetech/latin-america/en";
    }

    @Reference
    private Distributor distributor;

    @Override
    public void run() {
        // Bulk publish for the configured region
        distributor.distribute(resolver,
            new SimpleDistributionRequest(DistributionRequestType.ADD, config.rootPath()));
    }
}
```

### EDS-Specific Publishing Behavior

After publish from AEM Cloud, the content flows through the EDS pipeline:

```
AEM Cloud Publish Action
  → Sling Content Distribution → Content Bus
  → xWalk Pipeline renders JCR → HTML
  → EDS CDN (aem.live / Fastly) → cache invalidation automatic
  → Content available at production URL within seconds
```

No Dispatcher flush needed. No separate cache invalidation step. The EDS CDN handles everything automatically.

---

## 6. Migration Effort Summary

| Category | Items | Effort | Priority |
|----------|-------|--------|----------|
| **OSGi Annotation Migration** (all 9 Java classes) | Felix SCR → OSGi DS `@Component`, `@Reference`, `@Activate` | Low | P1 — Required for Cloud deployment |
| **Replace ReplicateToAgentHandler** | 10 step instances → Cloud Distribution API | High | P1 — Core publish mechanism |
| **Replace ECMA Script** | 1 step → Java `InitiatorParticipantChooser` | Low | P1 — Required for Cloud |
| **Relocate Email Templates** | 8 templates from `/etc/` → `/conf/` | Low | P1 — Templates won't resolve from `/etc/` |
| **Remove Cache Flush Steps** | 5 step instances → remove entirely | Low | P1 — Steps will fail (no agents exist) |
| **Replace Cloudwords → Wordbee** | 2 Java files + translation sub-flow | Medium-High | P1 — Translation integration |
| **Separate Translation Branch** | Extract "Waiting for Translation" into separate event-driven workflow triggered by Wordbee webhook | Medium | P2 — Recommended; main workflow stays as single workflow |
| **Rebuild Regulatory Dialog** | Create Touch UI from scratch | Medium | P2 — Only if used in active workflows |
| **Clean Dynamic Participant Dialog** | Remove Classic UI artifacts, rewrite JSP → HTL | Medium | P2 — Touch UI already works |
| **Fix Deprecated Constructors** | `new Long()` → `Long.valueOf()` in `PlaceParametersHandler` | Low | P3 — Warning fix |
| **Remove SDL Steps** | 2 steps (place/remove SDL retry params) | Low | P3 — Confirm unused |
| **Bulk Publish Servlet** | New development for regional/off-hours bulk activation | Medium | P2 — Based on operational needs |

---

## 7. Risk Assessment

| Risk | Level | Mitigation |
|------|-------|-----------|
| Replication handler replacement — core publishing changes fundamentally | **High** | Thorough testing on Cloud dev/stage; verify xWalk pipeline delivers correctly |
| Wordbee Cloud Service compatibility — vendor dependency | **Medium** | Early engagement with Wordbee; confirm AEM Cloud connector exists or plan custom integration |
| Long-running translation wait in Cloud — instance restarts | **Medium** | Only translation branch separated into event-driven workflow; `AbsoluteTimeAutoAdvancer` for delayed release is short-lived (hours) and acceptable |
| Classic UI dialog removal — functional regression | **Low** | Touch UI dialogs exist for critical steps; rebuild missing Regulatory dialog |
| Email template relocation — path references throughout code | **Low** | Search-and-replace `/etc/workflow/notification/` → `/conf/<project>/settings/workflow/notification/` |
| ECMA script removal — single step impact | **Low** | Simple Java replacement with identical logic |
