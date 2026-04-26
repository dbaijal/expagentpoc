# Major Review Publication Workflow -- Migration Analysis

**Document Version:** 1.0
**Date:** April 26, 2026
**Project:** AEM 6.4 to AEM as a Cloud Service + EDS (xWalk)
**Workflow:** Major Review Publication Workflow
**Status:** Draft

---

## Table of Contents

1. Executive Summary
2. Current Workflow Definition (AEM 6.4)
3. Future Workflow Definition (AEM Cloud Service + EDS)
4. Business Functionality Assessment
5. Technical Change Details
6. Workflow Dialog Compatibility
7. Post-Publish Delivery Path (EDS)
8. Open Items and Clarifications Needed

---

## 1. Executive Summary

### 1.1 Purpose

The Major Review Publication Workflow is a custom, multi-stage content approval and publication pipeline used across the site. It provides governed content publishing through sequential review gates, a rework loop for rejected content, scheduled/delayed release capability, and email notifications at each workflow transition.

### 1.2 Migration Impact -- At a Glance

The workflow's business logic -- multi-stage approval, rework loop, delayed release, email notifications, and cancellation handling -- is **fully preserved** in AEM Cloud Service. No approval gates are removed, no participant roles change, and no business rules are altered.

The changes are entirely at the **infrastructure level**:
- **How content is published:** Sling Content Distribution replaces agent-based replication
- **How emails are sent:** Cloud-compatible email service replaces Day CQ MailService (SMTP)
- **How cache is invalidated:** EDS CDN handles automatically (Dispatcher flush steps removed)
- **How custom code is registered:** OSGi Declarative Services annotations replace deprecated Felix SCR annotations
- **How workflow routes:** OOTB Goto Step replaces custom jump handler for unconditional routing

The workflow experience for **content authors, reviewers, and web operations teams remains the same**.

### 1.3 Key Characteristics

| Characteristic | Details |
|---|---|
| Review Stages | 3 approval gates (Design/UX, Editorial, Final Production) |
| Rework Loop | Rejected content returns to author for revision |
| Scheduled Release | Reviewer can set a future activation date |
| Force Deploy | Option to bypass scheduled wait and publish immediately |
| Email Notifications | 8 notification points across the workflow lifecycle |
| Cancellation | Workflow can be cancelled from any review stage |
| Team Routing | Web Operations team selected by author at workflow start |

### 1.4 Reference

**Note:** This workflow was analyzed on QA. Steps documented are as per the workflow on QA. Translation was confirmed to not be part of this workflow and is not considered in this analysis.

QA Reference: `http://tfaem-author-qa1-use1-0.aemqa.thermofisher.net:4502/conf/global/settings/workflow/models/lifetech/lifetech-major-review-publication.html`

---

## 2. Current Workflow Definition (AEM 6.4)

### 2.1 Workflow Flow -- Complete Step Sequence

```
 1. Log Start Workflow                          [LogWorkflowHandler - auto-advance]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - auto-advance]
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - auto-advance]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - conditional]
 6. Log Rework Content                          [LogWorkflowHandler - auto-advance]
 7. Send Rework Notification Email              [EmailNotificationsHandler - auto-advance]
 8. Rework Content Step                         [Dynamic Participant -> ECMA initiator-chooser]
 9. OR Split: Done with Rework / Cancel
    +-- Branch 1: Log "Done" -> continue
    +-- Branch 2: Log "Cancel" -> Jump to Cancel Processing
10. Set steppedBack=true                        [PlaceParametersHandler - auto-advance]
11. Report Metadata                             [LogWorkflowHandler - auto-advance]
12. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]
13. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]
14. Notify Design Reviewer Email                [EmailNotificationsHandler - auto-advance]
15. Review: UX / Design                         [Participant Step -> lt-wf-design-reviewer]
16. OR Split: Approve / Reject / Cancel
    +-- Branch 1: Log "Approve" -> continue
    +-- Branch 2: Log "Reject" -> Jump back to step 6 (Rework)
    +-- Branch 3: Log "Cancel" -> Jump to Cancel Processing
17. Log Start Editorial Review                  [LogWorkflowHandler - auto-advance]
18. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]
19. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]
20. Notify Editorial Reviewer Email             [EmailNotificationsHandler - auto-advance]
21. Review: Editorial                           [Participant Step -> lt-wf-editorial-reviewer]
22. OR Split: Approve / Cancel
    +-- Branch 1: Log "Approve" -> continue
    +-- Branch 2: Log "Cancel" -> Jump to Cancel Processing
23. Log Start Final Production Review           [LogWorkflowHandler - auto-advance]
24. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]
25. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]
26. Notify Final Production Reviewer Email      [EmailNotificationsHandler - auto-advance]
27. Review: Final Production                    [Dynamic Participant -> PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay for delayed release date selection)
28. OR Split: Approve / Cancel
    +-- Branch 1: Log "Approve" + Place Delayed Release Date -> continue to step 29
    +-- Branch 2: Log "Cancel" -> Jump to Cancel Processing
29. Log Start Production Run                    [LogWorkflowHandler - auto-advance]
30. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]
31. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]
32. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - auto-advance]
33. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - auto-advance]
34. Send Pending Publication Email              [EmailNotificationsHandler - auto-advance]
35. Waiting to Publish                          [Dynamic Participant -> AbsoluteTimeAutoAdvancer timeout]
36. OR Split: Force Deploy / Cancel
    +-- Branch 1: Log "Force Deploy" -> continue
    +-- Branch 2: Log "Cancel" -> Jump to Cancel Processing
37. Replicate to Production                     [ReplicateToAgentHandler -> push-to-production]
38. Cache Flush Production                      [ReplicateToAgentHandler -> flush-production]
39. Send Finished Publication Email             [EmailNotificationsHandler - auto-advance]
40. Jump to End (skip cancel processing)        [JumpToNodeHandler - auto-advance]
41. Cancel Workflow Processing                  [LogWorkflowHandler - auto-advance]
42. Inform Content Author about Cancellation    [EmailNotificationsHandler - auto-advance]
43. Log End of Workflow                         [LogWorkflowHandler - auto-advance]
```

### 2.2 Participants and Roles

| Role | AEM Group | Used In |
|---|---|---|
| Content Author / Initiator | lt-wf-author | Step 3 (Select Web Ops), Step 8 (Rework) |
| Design / UX Reviewer | lt-wf-design-reviewer | Step 15 |
| Editorial Reviewer | lt-wf-editorial-reviewer | Step 21 |
| Final Production Reviewer (Web Ops) | Dynamic (PropertiesParticipantChooser) | Step 27 |
| Rejection Notification Recipients | lt-wf-rejection-notification | Step 7 (email only) |
| Major Notification Recipients | lt-wf-major-notification | Steps 34, 39 (email only) |

### 2.3 Custom Handlers Summary

| Handler | Purpose | Instances |
|---|---|---|
| LogWorkflowHandler | Logs workflow progress with placeholder substitution | 14 |
| PlaceParametersHandler | Sets typed key-value parameters on workflow metadata | 2 |
| DialogParameterHandler | Extracts dialog properties into workflow metadata | 2 |
| JumpToNodeHandler | Unconditional jump to a named workflow node | 8 |
| JumpToNodeIfSetHandler | Conditional jump based on metadata property state | 1 |
| ReplicateToAgentHandler | Replicates content to specific replication agents | 10 |
| EmailNotificationsHandler | Sends templated email notifications to participants | 8 |
| CheckDelayedReleaseDateHandler | Validates or sets the delayed release timestamp | 1 |
| SetDelayedReleaseAsCommentHandler | Reports delayed release date in workflow inbox comment | 1 |

---

## 3. Future Workflow Definition (AEM Cloud Service + EDS)

### 3.1 Architecture Approach

The workflow is migrated as a **single workflow** preserving the same business logic, participants, and transitions. Changes are technical (infrastructure-level) only. The step sequence below marks all changes with an arrow (CHANGED or REMOVED).

### 3.2 Future Workflow Flow -- Complete Step Sequence

```
 1. Log Start Workflow                          [LogWorkflowHandler - refactored]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - refactored]
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - refactored]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - refactored]
 6. Log Rework Content                          [LogWorkflowHandler - refactored]
 7. Send Rework Notification Email              [EmailNotificationsHandler - refactored]
 8. Rework Content Step                         [Dynamic Participant -> Java InitiatorParticipantChooser]
                                                 <-- CHANGED: ECMA scripts not supported in AEM Cloud;
                                                     replaced with Java ParticipantStepChooser
 9. OR Split: Done with Rework / Cancel
    +-- Branch 1: Log "Done" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing
                                                 <-- CHANGED: OOTB Goto Step replaces JumpToNodeHandler
10. Set steppedBack=true                        [PlaceParametersHandler - refactored]
11. Report Metadata                             [LogWorkflowHandler - refactored]
12. Publish to Preview Tier                     [Sling Content Distribution API]
                                                 <-- CHANGED: Replaces agent-based ReplicateToAgentHandler
    (Cache Flush step REMOVED -- EDS CDN handles cache invalidation automatically)
13. Notify Design Reviewer Email                [EmailNotificationsHandler - refactored]
14. Review: UX / Design                         [Participant Step -> lt-wf-design-reviewer]
15. OR Split: Approve / Reject / Cancel
    +-- Branch 1: Log "Approve" -> continue
    +-- Branch 2: Log "Reject" -> Goto step 6 (Rework)
                                                 <-- CHANGED: OOTB Goto Step replaces JumpToNodeHandler
    +-- Branch 3: Log "Cancel" -> Goto Cancel Processing
                                                 <-- CHANGED: OOTB Goto Step replaces JumpToNodeHandler
16. Log Start Editorial Review                  [LogWorkflowHandler - refactored]
17. Publish to Preview Tier                     [Sling Content Distribution API]
                                                 <-- CHANGED: Replaces agent-based replication
    (Cache Flush REMOVED)
18. Notify Editorial Reviewer Email             [EmailNotificationsHandler - refactored]
19. Review: Editorial                           [Participant Step -> lt-wf-editorial-reviewer]
20. OR Split: Approve / Cancel
    +-- Branch 1: Log "Approve" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing
                                                 <-- CHANGED: OOTB Goto Step
21. Log Start Final Production Review           [LogWorkflowHandler - refactored]
22. Publish to Preview Tier                     [Sling Content Distribution API]
                                                 <-- CHANGED: Replaces agent-based replication
    (Cache Flush REMOVED)
23. Notify Final Production Reviewer Email      [EmailNotificationsHandler - refactored]
24. Review: Final Production                    [Dynamic Participant -> PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay for delayed release date selection -- NO CHANGE)
25. OR Split: Approve / Cancel
    +-- Branch 1: Approve + Place Delayed Release Date -> continue to step 26
    +-- Branch 2: Cancel -> Goto Cancel Processing
                                                 <-- CHANGED: OOTB Goto Step
26. Log Start Production Run                    [LogWorkflowHandler - refactored]
27. Publish to Preview Tier                     [Sling Content Distribution API]
                                                 <-- CHANGED: Replaces agent-based replication
    (Cache Flush REMOVED)
28. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - refactored]
29. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - refactored]
30. Send Pending Publication Email              [EmailNotificationsHandler - refactored]
31. Waiting to Publish                          [Dynamic Participant -> AbsoluteTimeAutoAdvancer]
                                                 (NO CHANGE -- OOTB, backed by Sling Jobs, survives restarts)
32. OR Split: Force Deploy / Cancel
    +-- Branch 1: Log "Force Deploy" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing
                                                 <-- CHANGED: OOTB Goto Step
33. Publish to Production                       [Sling Content Distribution API]
                                                 <-- CHANGED: Replaces agent-based replication
    (Cache Flush REMOVED -- EDS CDN invalidates automatically)
34. Send Finished Publication Email             [EmailNotificationsHandler - refactored]
35. Goto End (skip cancel processing)           [OOTB Goto Step]
                                                 <-- CHANGED: Replaces JumpToNodeHandler
36. Cancel Workflow Processing                  [LogWorkflowHandler - refactored]
37. Inform Content Author about Cancellation    [EmailNotificationsHandler - refactored]
38. Log End of Workflow                         [LogWorkflowHandler - refactored]
```

### 3.3 Summary of Step Count Changes

| Metric | Current (AEM 6.4) | Future (AEM Cloud) | Reason |
|---|---|---|---|
| Total steps | 43 | 38 | Cache flush steps removed (5 steps) |
| Review gates | 3 | 3 | No change |
| OR Splits | 6 | 6 | No change |
| Email notifications | 8 | 8 | No change |
| Participant steps | 4 | 4 | No change |

---

## 4. Business Functionality Assessment

### 4.1 Functionality Preservation Status

| Capability | Current Behavior | Future Behavior | Status |
|---|---|---|---|
| Multi-stage approval (Design, Editorial, Production) | 3 sequential approval gates with OR Splits | Same -- 3 gates, same OR Splits | Fully Preserved |
| Rework loop (reject, author reworks, re-review) | Reviewer rejects, workflow jumps back to rework step | Same -- OOTB Goto Step routes to rework step | Fully Preserved |
| First-loop rework skip | Custom handler skips rework on initial submission | Refactored handler -- same conditional logic | Fully Preserved |
| Web Ops team selection at workflow start | Author selects from dialog dropdown | Same dialog, same selection | Fully Preserved |
| Delayed/scheduled release date | Reviewer sets date via datepicker in Final Production step | Same datepicker, same timer mechanism (AbsoluteTimeAutoAdvancer) | Fully Preserved |
| Force deploy (bypass scheduled wait) | OR Split allows immediate publication | Same OR Split | Fully Preserved |
| Email notifications at each stage | Custom handler sends templated emails to participants | Same handler (refactored), same templates, same recipients | Fully Preserved |
| Cancel workflow from any stage | Jump to shared cancel handler from any OR Split | OOTB Goto Step to same cancel handler | Fully Preserved |
| Workflow comment history in emails | Assembled from workflow history, included in email body | Same logic | Fully Preserved |
| Participant roles and groups | AEM user groups (lt-wf-design-reviewer, etc.) | Same groups | Fully Preserved |
| Preview before each review stage | Content replicated to preview for reviewer to inspect | Content published to Cloud preview tier (.aem.page) | Fully Preserved (different mechanism, same result) |
| Production publish after final approval | Content replicated to production via agents | Content published via Cloud Distribution to EDS CDN | Fully Preserved (different mechanism, same result) |
| Inbox visibility of delayed release date | Release date shown as workflow comment in AEM Inbox | Same -- SetDelayedReleaseAsCommentHandler sets comment | Fully Preserved |

### 4.2 Impact by Role

| Role | What Changes for This Role | What Stays the Same |
|---|---|---|
| **Content Author** | Nothing changes | Initiates workflow, selects Web Ops team, reworks content on rejection, receives cancellation emails |
| **Design/UX Reviewer** | Preview URL changes from Dispatcher to EDS preview (.aem.page) | Reviews content on preview, approves/rejects/cancels from AEM Inbox |
| **Editorial Reviewer** | Preview URL changes from Dispatcher to EDS preview (.aem.page) | Reviews content on preview, approves/cancels from AEM Inbox |
| **Web Ops (Final Production)** | Preview URL changes from Dispatcher to EDS preview (.aem.page) | Sets delayed release date, approves/cancels/forces deployment from AEM Inbox |
| **All Participants** | Email notification links will point to Cloud author and EDS preview URLs | Email notifications, comment history, workflow inbox experience |

### 4.3 What Is NOT Changing

- Workflow initiation process
- Number and sequence of approval gates
- Participant assignment logic
- OR Split branching (approve/reject/cancel options)
- Rework loop behavior
- Delayed release date selection and timer mechanism
- Force deploy option
- Email notification content and recipients
- Workflow cancellation flow
- AEM Inbox experience

---

## 5. Technical Change Details

### 5.1 Steps Removed (No Replacement Needed)

| Step | Instances | Current Behavior | Why Removed |
|---|---|---|---|
| Cache Flush Preview | 4 | Replicates to `flush-preview` agent to clear Dispatcher cache | EDS CDN handles cache invalidation automatically on publish. No Dispatcher exists in EDS architecture. |
| Cache Flush Production | 1 | Replicates to `flush-production` agent to clear Dispatcher cache | Same -- EDS CDN uses push-based invalidation triggered automatically on publish. |

**Total steps removed: 5**

### 5.2 Steps Replaced with OOTB AEM Cloud Features

| Current Step | Instances | Current Implementation | Replacement | Why |
|---|---|---|---|---|
| Replicate to Preview | 4 | `ReplicateToAgentHandler` with `push-to-preview` agent | **Sling Content Distribution API** -- publish to Cloud preview tier | Agent-based replication is not available in AEM Cloud Service. AEM Cloud uses Sling Content Distribution for content publishing. Reference: [AEM Cloud Replication](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/operations/replication) |
| Replicate to Production | 1 | `ReplicateToAgentHandler` with `push-to-production` agent | **Sling Content Distribution API** -- publish to live tier | Same -- content flows through xWalk pipeline to EDS CDN |
| Jump to Cancel Processing | 7 | `JumpToNodeHandler` -- creates workflow transitions at runtime using `WorkflowModel.createTransition()` | **OOTB Goto Step** (`com.day.cq.workflow.impl.process.GotoStep`) | `createTransition()` performs runtime model modification which goes against AEM Cloud Service's immutable infrastructure principle. OOTB Goto Step uses pre-defined transitions and is fully Cloud-supported. |
| Jump to End (skip cancel) | 1 | `JumpToNodeHandler` | **OOTB Goto Step** | Same reason as above |
| Rework Content (participant assignment) | 1 | Dynamic Participant using ECMA script: `/libs/workflow/scripts/initiator-participant-chooser.ecma` | **Java `ParticipantStepChooser`** implementation | ECMA workflow scripts are not supported in AEM Cloud Service. A Java-based `ParticipantStepChooser` that returns the workflow initiator provides identical behavior. |

### 5.3 Steps Refactored (Same Behavior, Updated Code)

These handlers are functionally compatible with AEM Cloud Service. The required changes are code-level refactoring only -- no business logic changes.

#### 5.3.1 LogWorkflowHandler (14 instances)

**What it does:** Logs workflow progress messages with placeholder substitution ({payload}, {meta}, {comment}, {wf-data-meta}).

**Changes required:**
- Felix SCR annotations (`@Component`, `@Service`, `@Properties`) migrated to OSGi Declarative Services (`@Component(service = WorkflowProcess.class)`)
- Remove `AbstractResourceWorkflowProcess` base class dependency -- implement `WorkflowProcess` interface directly

**Recommendation:** AEM Cloud's workflow engine automatically tracks every step execution, participant action, and comment in the workflow instance history. Most of the 14 log steps duplicate information the platform already captures. Consider reducing to 2-3 instances where metadata debugging context ({wf-data-meta}) provides genuine value beyond what the workflow history shows.

**Business impact:** None -- logging behavior identical.

#### 5.3.2 PlaceParametersHandler (2 instances)

**What it does:** Sets typed key-value parameters (BOOLEAN, LONG, INTEGER, DOUBLE, DATE, STRING) on workflow metadata map. Used to set the `steppedBack` flag that controls the rework skip logic.

**Changes required:**
- Felix SCR to OSGi DS annotations
- Replace deprecated boxed-type constructors (`new Long(-1)` to `Long.valueOf(-1)`, `new Integer(-1)` to `Integer.valueOf(-1)`)

**Business impact:** None -- parameter handling identical.

#### 5.3.3 DialogParameterHandler (2 instances)

**What it does:** Reads a property value from workflow dialog history or payload resource, converts it to a typed value, and places it into workflow metadata. Used to capture the Web Ops team selection and delayed release date from participant step dialogs.

**Changes required:**
- Felix SCR to OSGi DS annotations

**Business impact:** None -- dialog parameter extraction identical.

#### 5.3.4 JumpToNodeIfSetHandler (1 instance)

**What it does:** Checks whether the `steppedBack` property is set in workflow metadata. On the first loop (steppedBack not set), jumps to "Design/UX Review" to skip the rework step. On subsequent loops (steppedBack is true), continues to the rework step so the author can revise content.

**Why it cannot be used as-is:** The handler extends `JumpToNodeHandler` and inherits the `WorkflowModel.createTransition()` call that creates workflow transitions at runtime. This runtime model modification is not compatible with AEM Cloud Service's immutable infrastructure.

**Changes required:**
- Implement `WorkflowProcess` directly (instead of extending `JumpToNodeHandler`)
- Remove `createTransition()` -- use only pre-existing routes from `getBackRoutes()` and `getRoutes()`
- Remove `SimpleRoute` custom class -- use standard `Route` objects from the workflow session
- Felix SCR to OSGi DS annotations
- Add a pre-defined transition in the workflow model editor from this step to "Design/UX Review" so the handler can find the route at runtime instead of creating it dynamically

**Business impact:** None -- same conditional logic (XOR-based routing), same skip behavior on first loop, same rework behavior on subsequent loops.

#### 5.3.5 EmailNotificationsHandler (8 instances)

**What it does:** Sends email notifications at key workflow transitions -- rework rejection, design review, editorial review, final production review, pending publication, finished publication, and workflow cancellation. Resolves email recipients from workflow participants, user groups, and dynamic `ParticipantStepChooser` implementations. Loads email templates with 40+ substitution variables (payload path, participant names, workflow state, comment history, metadata). Builds comment history from workflow history for inclusion in email body.

**Why it cannot be used as-is:** Multiple Cloud-incompatible dependencies:

| Issue | Detail | Severity |
|---|---|---|
| `com.day.cq.mailer.MailService` removed | AEM Cloud Service does not provide direct SMTP access. The `MailService` dependency (`@Reference protected MailService mMailService`) will fail to resolve. | Critical |
| Felix SCR annotations | All component annotations must be migrated to OSGi Declarative Services | High |
| JCR Session as instance variable | `protected Session mSession` stored as instance variable -- unsafe in Cloud where instances restart and components are reused across invocations | High |
| Dynamic `@Reference` for `ParticipantStepChooser` | Felix SCR `@Reference(cardinality=OPTIONAL_MULTIPLE, policy=DYNAMIC)` with `bind`/`unbind` must be rewritten to OSGi DS `@Reference` with `volatile List` | High |
| Email templates at `/etc/` | Templates at `/etc/workflow/notification/email/lifetech/major/...` -- `/etc/` is deprecated for mutable content in AEM Cloud | Medium |
| Hardcoded host URLs | Default values reference `http://author1.lifetechnologies.com` and `http://preview.lifetechnologies.com` -- not valid for Cloud instances | Medium |

**Changes required:**
1. **Email delivery:** Replace `MailService` with a cloud-compatible email service (e.g., SendGrid). The existing custom `EmailService` interface can be retained -- only the underlying implementation changes.
2. **Annotations:** Migrate to OSGi DS. Replace dynamic bind/unbind with `volatile List<ParticipantStepChooser>` with `MULTIPLE` cardinality.
3. **Session handling:** Remove instance variable `mSession`. Use method-local `ResourceResolver` from `ResourceResolverFactory.getServiceResourceResolver()`.
4. **Email templates:** Relocate from `/etc/workflow/notification/email/lifetech/major/...` to `/conf/<project>/settings/workflow/notification/email/major/...`. Update all `alertMessageTemplatePath` values in the workflow model.
5. **Host URLs:** Make author/preview host URLs configurable via OSGi configuration instead of hardcoded defaults.

**Email templates requiring relocation (8 templates):**

| Current Path | Purpose |
|---|---|
| `/etc/.../major/review/rejected/en.txt` | Rework rejection notification |
| `/etc/.../major/review/design/en.txt` | Design review notification |
| `/etc/.../major/review/editorial/en.txt` | Editorial review notification |
| `/etc/.../major/review/webops/en.txt` | Final production review notification |
| `/etc/.../major/pending-publication/en.txt` | Pending publication notification |
| `/etc/.../major/finished-publication/en.txt` | Finished publication notification |
| `/etc/.../major/cancelled/en.txt` | Workflow cancellation notification |

**Business impact:** None -- same emails sent to same recipients with same template content. Changes are infrastructure-level only.

#### 5.3.6 CheckDelayedReleaseDateHandler (1 instance)

**What it does:** Checks if a delayed release timestamp (`absoluteTime`) is already set in workflow metadata. If set and valid, proceeds. If not set, calculates a default time of NOW + configured offset (default: 1 day) and stores it in metadata.

**Changes required:**
- Felix SCR to OSGi DS annotations
- Replace `SimpleDateFormat` (thread-unsafe static instance) with `java.time.DateTimeFormatter` (thread-safe)
- Remove `AbstractResourceWorkflowProcess` base class dependency

**Business impact:** None -- same delay logic.

#### 5.3.7 SetDelayedReleaseAsCommentHandler (1 instance)

**What it does:** Reads the stored `absoluteTime` from workflow metadata. If the time is in the future, formats it as a readable date string and sets it as a workflow comment (visible in AEM Inbox) and as the `delayed.release.date` metadata property.

**Changes required:**
- Felix SCR to OSGi DS annotations
- Replace `SimpleDateFormat` with `DateTimeFormatter` (thread-safety)
- Remove `AbstractResourceWorkflowProcess` base class dependency
- Review the `PlaceParameterFromConfigurationService.updateTransFlag()` static call -- this translation flag logic should be moved to a separate step or removed if translation is not part of this workflow

**Business impact:** None -- same inbox comment behavior.

### 5.4 Handler Change Summary Table

| Handler | Instances | Change Type | Cloud Risk | Effort | Business Impact |
|---|---|---|---|---|---|
| LogWorkflowHandler | 14 | Annotation refactoring | None | Low (1-2 hrs) | None |
| PlaceParametersHandler | 2 | Annotations + deprecated constructor fix | None | Low (1-2 hrs) | None |
| DialogParameterHandler | 2 | Annotation refactoring | None | Low (1-2 hrs) | None |
| JumpToNodeHandler | 8 | **Replace with OOTB Goto Step** | None (OOTB) | Low (2-3 hrs) | None |
| JumpToNodeIfSetHandler | 1 | **Refactor** -- remove createTransition() | Low | Medium (3-4 hrs) | None |
| ReplicateToAgentHandler | 10 | **Replace with Sling Content Distribution** | Low | High (12-16 hrs) | None |
| EmailNotificationsHandler | 8 | **Major refactor** -- replace MailService, annotations, templates | Medium | High (20-24 hrs) | None |
| CheckDelayedReleaseDateHandler | 1 | Annotations + thread-safety | None | Low (1-2 hrs) | None |
| SetDelayedReleaseAsCommentHandler | 1 | Annotations + thread-safety | None | Low (1-2 hrs) | None |
| ECMA initiator-participant-chooser | 1 | **Replace with Java ParticipantStepChooser** | None | Low (1-2 hrs) | None |
| Cache Flush steps (5) | 5 | **Remove entirely** | None | None | None |

### 5.5 Scheduling and Release Logic

The existing delayed release pattern is **architecturally aligned** with how AEM Cloud Service handles scheduled publishing internally. AEM Cloud's own "Manage Publication > Later" feature uses the same mechanism -- workflow instances backed by Sling Jobs with JCR persistence.

| Component | Cloud Compatible? | Change Needed |
|---|---|---|
| CheckDelayedReleaseDateHandler | Yes (with refactoring) | Annotation migration + thread-safety fix |
| SetDelayedReleaseAsCommentHandler | Yes (with refactoring) | Annotation migration + thread-safety fix |
| AbsoluteTimeAutoAdvancer (OOTB) | Yes -- works in Cloud | None -- backed by Sling Jobs, guaranteed execution, survives instance restarts |
| Absolute Timer Delay Dialog (datepicker) | Yes -- Touch UI, Cloud-ready | None |
| Force Deploy via OR Split | Yes -- OOTB | None |

Sling Jobs persist to JCR (`/var/eventing/jobs/`) and survive instance restarts. The scheduled release time stored in workflow metadata is not lost during Cloud maintenance windows.

**Important note from Adobe's Cloud documentation:**
- Sling Jobs provide **"at-least-once" guaranteed execution** -- recommended for background tasks in Cloud
- Sling Commons Scheduler (cron-based) is **not recommended** -- "execution cannot be guaranteed"
- The current workflow pattern correctly uses Sling Jobs (via the workflow engine) rather than Sling Scheduler

---

## 6. Workflow Dialog Compatibility

| Dialog | UI Type | Used In | Cloud Status |
|---|---|---|---|
| **Absolute Timer Delay** | Touch UI (Coral datepicker) | Step 27 -- delayed release date selection | Cloud-ready -- no changes needed |
| **Web Operations Team Selection** | Touch UI | Step 3 -- Web Ops team selection by author | Cloud-ready -- no changes needed |
| **Dynamic Participant Dialog** | Both Classic + Touch UI | Step 27 -- Final Production review with dialog | Touch UI works; Classic UI artifacts (dialog.xml, _cq_editConfig.xml) should be removed as Classic UI is not available in Cloud |

---

## 7. Post-Publish Delivery Path (EDS)

When the workflow publishes content (steps 12, 17, 22, 27, 33), the following delivery pipeline is triggered automatically by AEM Cloud Service:

```
Workflow step: "Publish to Preview" or "Publish to Production"
        |
        v
Sling Content Distribution (AEM Cloud built-in)
        |
        v
xWalk Pipeline (converts JCR content to semantic HTML)
        |
        v
Content Bus (Adobe managed infrastructure)
        |
        v
EDS CDN -- Fastly (push-based cache, global edge network)
        |
        v
Content live on site (aem.page for preview, aem.live for production)
```

This replaces the previous chain of: Replication Agent -> Publish Instance -> Dispatcher -> Cache Flush. No additional workflow steps are needed -- the publish action in the workflow triggers the entire pipeline automatically.

**Cache invalidation** is handled by the EDS CDN's push-based mechanism. When content is published, the CDN cache for that content is invalidated automatically. This is why all Cache Flush workflow steps are removed.

---

## 8. Open Items and Clarifications Needed

### 8.1 Delayed Release Default Behavior

**Current behavior:** When no activation date is entered by the Final Production reviewer, `CheckDelayedReleaseDateHandler` applies a default and sets the release time to NOW + 1 day (configurable via `delayOffset` parameter). This means a page is **never published immediately** after final approval -- there is always at least a 1-day delay unless someone explicitly uses the "Force the Deployment" path.

**Clarification needed from business:**

Is the 1-day default delay a deliberate business requirement (e.g., to provide a review window before content goes live, or to coordinate with regional publishing schedules), or is it a technical safeguard that may no longer be needed?

**Recommendation:** If there is no specific business requirement to delay page activation, the recommendation is to publish immediately upon approval. The delayed release mechanism would remain fully available for cases where a reviewer explicitly sets a future activation date. This would simplify the happy path while preserving scheduling capability when needed.

### 8.2 Email Service Selection

The current `MailService` (SMTP-based) is not available in AEM Cloud Service. A cloud-compatible email delivery service must be selected. Common options:

| Option | Notes |
|---|---|
| **SendGrid** | Most commonly used with AEM Cloud. Well-documented integration pattern. |
| **Adobe Campaign** | If already part of the client's Adobe stack. |
| **AWS SES** | If the client's infrastructure is AWS-based. |

**Decision needed:** Which email delivery service will be used for workflow notifications?

### 8.3 Hardcoded Host URLs in Email Templates

Email notification templates and the `EmailNotificationsHandler` contain hardcoded references to:
- `http://author1.lifetechnologies.com` (author host)
- `http://preview.lifetechnologies.com` (preview host)

These must be updated to AEM Cloud Service instance URLs. Should these be:
- Hardcoded to Cloud URLs (simpler but less flexible)?
- Configured via OSGi configuration (recommended -- allows different values per environment)?

### 8.4 LogWorkflowHandler Usage Reduction

**Recommendation for discussion:** Reduce LogWorkflowHandler from 14 instances to 2-3 instances that log metadata context valuable for debugging. AEM Cloud's workflow engine already tracks step execution, participant actions, and comments automatically. This would reduce workflow execution overhead (14 Sling Jobs per workflow instance for logging alone).

**Decision needed:** Is the client comfortable reducing log steps, or are all 14 required for operational/compliance reasons?
