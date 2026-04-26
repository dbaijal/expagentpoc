# Content Review Workflows -- Migration Analysis (Major Review & Simple Review)

**Document Version:** 2.0
**Date:** April 26, 2026
**Project:** AEM 6.4 to AEM as a Cloud Service + EDS (xWalk)
**Workflows Covered:** Major Review Publication, Simple Review Publication
**Status:** Draft

---

## Table of Contents

1. Executive Summary
2. Current Workflow Definitions (AEM 6.4)
3. Future Workflow Definitions (AEM Cloud Service + EDS)
4. Business Functionality Assessment
5. Technical Change Details
6. Workflow Dialog Compatibility
7. Post-Publish Delivery Path (EDS)
8. Open Items and Clarifications Needed

---

## 1. Executive Summary

### 1.1 Purpose

The site uses two content review workflow variants -- **Major Review** and **Simple Review** -- that share the same custom handler codebase, the same infrastructure (replication, email, scheduling), and the same publication pipeline. They differ only in the number of approval gates before content is published.

| Variant | Approval Gates | Use Case |
|---|---|---|
| **Major Review** | 3 gates: Design/UX -> Editorial -> Final Production | Content requiring multi-team review (major site changes, campaigns, new pages) |
| **Simple Review** | 1 gate: Final Production only | Content requiring only Web Ops approval (minor updates, corrections, routine changes) |

Both workflows share the same rework loop, delayed release mechanism, email notification system, cancellation handling, and publication steps. The Simple Review is a **subset** of the Major Review -- it is the same workflow with the Design/UX and Editorial review gates removed.

### 1.2 Migration Impact -- At a Glance

The business logic of both workflows is **fully preserved** in AEM Cloud Service. No approval gates are removed, no participant roles change, and no business rules are altered.

The changes are entirely at the **infrastructure level** and apply equally to both variants:
- **How content is published:** Sling Content Distribution replaces agent-based replication
- **How emails are sent:** Cloud-compatible email service replaces Day CQ MailService (SMTP)
- **How cache is invalidated:** EDS CDN handles automatically (Dispatcher flush steps removed)
- **How custom code is registered:** OSGi Declarative Services annotations replace deprecated Felix SCR annotations
- **How workflow routes:** OOTB Goto Step replaces custom jump handler for unconditional routing

The workflow experience for **content authors, reviewers, and web operations teams remains the same** for both variants.

**All custom handlers are shared between both workflows.** Refactoring a handler once applies to both variants -- there is no duplicate migration effort.

### 1.3 Key Characteristics

| Characteristic | Major Review | Simple Review |
|---|---|---|
| Review Stages | 3 (Design/UX, Editorial, Final Production) | 1 (Final Production only) |
| Rework Loop | Reject at Design/UX -> rework | Reject at Final Production -> rework |
| Scheduled Release | Yes -- reviewer sets activation date | Yes -- same mechanism |
| Force Deploy | Yes -- bypass scheduled wait | Yes -- same mechanism |
| Email Notifications | 8 notification points | 5 notification points |
| Cancellation | From any review stage | From any review stage |
| Team Routing | Web Ops team selected by author at start | Same |
| Total Steps (Current) | 43 | 29 |

### 1.4 References

**Note:** Both workflows were analyzed on QA. Steps documented are as per the workflows on QA. Translation was confirmed to not be part of these workflows and is not considered in this analysis.

Major Review QA Reference: `http://tfaem-author-qa1-use1-0.aemqa.thermofisher.net:4502/conf/global/settings/workflow/models/lifetech/lifetech-major-review-publication.html`

Simple Review QA Reference: `http://tfaem-author-qa1-use1-0.aemqa.thermofisher.net:4502/conf/global/settings/workflow/models/lifetech/lifetech-simple-review-publication.html`

---

## 2. Current Workflow Definitions (AEM 6.4)

### 2.1 Workflow Structure Comparison

Both workflows share the same structure for shared phases. The diagram below shows where Simple Review diverges:

```
SHARED: Setup Phase
  1. Log Start Workflow
  2. Set Rework Parameters
  3. Select Web Operations Team
  4. Place Web Ops Team on Metadata
  5. Jump Over Rework if First Loop

SHARED: Rework Phase
  6. Rework Notification Email
  7. Rework Content Step (author)
  8. OR Split: Done / Cancel

MAJOR REVIEW ONLY: Design/UX Review Phase
  9.  Set steppedBack=true
  10. Report Metadata
  11. Replicate to Preview + Cache Flush
  12. Notify Design Reviewer
  13. Review: UX / Design
  14. OR Split: Approve / Reject (->Rework) / Cancel

MAJOR REVIEW ONLY: Editorial Review Phase
  15. Replicate to Preview + Cache Flush
  16. Notify Editorial Reviewer
  17. Review: Editorial
  18. OR Split: Approve / Cancel

SHARED: Final Production Review Phase
  - Replicate to Preview + Cache Flush
  - Notify Final Production Reviewer
  - Review: Final Production (+ delayed release date)
  - OR Split: Approve / Cancel
    * Simple Review adds: Reject branch (->Rework)

SHARED: Publication Phase
  - Replicate to Preview + Cache Flush
  - Check Delayed Release Date
  - Report Delayed Release as Comment
  - Send Pending Publication Email
  - Wait to Publish (AbsoluteTimeAutoAdvancer)
  - OR Split: Force Deploy / Cancel
  - Replicate to Production + Cache Flush Production
  - Send Finished Publication Email

SHARED: End Phase
  - Jump to End (skip cancel processing)
  - Cancel Workflow Processing
  - Cancellation Email
  - Log End
```

### 2.2 Major Review -- Complete Step Sequence (43 steps)

```
 1. Log Start Workflow                          [LogWorkflowHandler - auto-advance]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - auto-advance]
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - auto-advance]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler -> "Design/UX Review"]
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

### 2.3 Simple Review -- Complete Step Sequence (29 steps)

The Simple Review workflow uses the same handlers as Major Review. Steps marked with (=Major) are identical to the corresponding Major Review steps. Differences are marked with **[DIFFERS]**.

```
 1. Log Start Workflow                          [LogWorkflowHandler - auto-advance]               (=Major Step 1)
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - auto-advance]            (=Major Step 2)
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]           (=Major Step 3)
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - auto-advance]             (=Major Step 4)
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler -> "Final Production"]     (=Major Step 5)
                                                 **[DIFFERS]** Target: "Start the Final Production Review"
                                                 (Major targets "Design/UX Review")
 6. Rework Content (Email Notification)         [EmailNotificationsHandler - auto-advance]          (=Major Step 7)
                                                 **[DIFFERS]** Template: .../simple/review/rejected/en.txt
                                                 (Major uses .../major/review/rejected/en.txt)
 7. Rework Content Step                         [Dynamic Participant -> ECMA initiator-chooser]     (=Major Step 8)
 8. OR Split: Done / Cancel                                                                        (=Major Step 9)
    +-- Branch 1: Log "Done" -> continue
    +-- Branch 2: Log "Cancel" -> Jump to Cancel Processing

    (NO Design/UX Review -- steps 10-16 of Major are ABSENT)
    (NO Editorial Review -- steps 17-22 of Major are ABSENT)
    (NO Set steppedBack=true -- Major Step 10 is ABSENT)                                           **[DIFFERS]**

 9. Log Start Final Production Review           [LogWorkflowHandler - auto-advance]                (=Major Step 23)
10. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]       (=Major Step 24)
11. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]          (=Major Step 25)
12. Notify Final Production Reviewer            [EmailNotificationsHandler - auto-advance]          (=Major Step 26)
                                                 **[DIFFERS]** Template: .../simple/review/webops/en.txt
                                                 Notification group: lt-wf-simple-notification (Major: lt-wf-major-notification)
13. Review: Final Production                    [Dynamic Participant -> PropertiesParticipantChooser] (=Major Step 27)
    (Dialog: Absolute Timer Delay for delayed release date selection)
14. OR Split: Approve / Cancel / Reject                                                            **[DIFFERS]**
    +-- Branch 1: Approve + Place Delayed Release Date -> continue
    +-- Branch 2: Cancel -> Jump to Cancel Processing
    +-- Branch 3: **Reject -> Jump back to Rework Content**                                        **[DIFFERS]**
                  (Major does NOT have reject at Final Production -- reject is at Design/UX)
15. Log Start Production Run                    [LogWorkflowHandler - auto-advance]                (=Major Step 29)
16. Replicate to Preview                        [ReplicateToAgentHandler -> push-to-preview]       (=Major Step 30)
17. Cache Flush Preview                         [ReplicateToAgentHandler -> flush-preview]          (=Major Step 31)
18. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - auto-advance]    (=Major Step 32)
19. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - auto-advance] (=Major Step 33)
20. Send Pending Publication Email              [EmailNotificationsHandler - auto-advance]          (=Major Step 34)
                                                 **[DIFFERS]** Notification group: lt-wf-simple-notification
                                                 Template: .../simple/pending-publication/en.txt
21. Wait to Publish                             [Dynamic Participant -> AbsoluteTimeAutoAdvancer]   (=Major Step 35)
22. OR Split: Force Deploy / Cancel                                                                (=Major Step 36)
    +-- Branch 1: Force Deploy -> continue
    +-- Branch 2: Cancel -> Jump to Cancel Processing
23. Replicate to Production                     [ReplicateToAgentHandler -> push-to-production]    (=Major Step 37)
24. Cache Flush Production                      [ReplicateToAgentHandler -> flush-production]       (=Major Step 38)
25. Send Finished Publication Email             [EmailNotificationsHandler - auto-advance]          (=Major Step 39)
                                                 **[DIFFERS]** Notification group: lt-wf-simple-notification
                                                 Template: .../simple/finished-publication/en.txt
26. Jump to End (skip cancel processing)        [JumpToNodeHandler]                                (=Major Step 40)
27. Cancel Workflow Processing                  [LogWorkflowHandler - auto-advance]                (=Major Step 41)
28. Inform Content Author about Cancellation    [EmailNotificationsHandler - auto-advance]         (=Major Step 42)
                                                 **[DIFFERS]** Template: .../simple/cancelled/en.txt
29. Log End of Workflow                         [LogWorkflowHandler - auto-advance]                (=Major Step 43)
```

### 2.4 Key Differences Between Variants

| Difference | Major Review | Simple Review |
|---|---|---|
| **Approval gates** | 3 (Design/UX, Editorial, Final Production) | 1 (Final Production only) |
| **Reject-to-rework location** | At Design/UX Review (Step 16, Branch 2) | At Final Production Review (Step 14, Branch 3) |
| **Final Production OR Split** | 2 branches (Approve / Cancel) | 3 branches (Approve / Cancel / **Reject**) |
| **First-loop skip target** | "Hand over to the Design / UX Review" | "Start the Final Production Review" |
| **Set steppedBack=true step** | Present (Step 10) after rework OR Split | **Absent** -- no explicit steppedBack=true between rework and Final Production |
| **Email template path** | `/etc/.../lifetech/major/...` | `/etc/.../lifetech/simple/...` |
| **Notification group (pending/finished)** | `lt-wf-major-notification` | `lt-wf-simple-notification` |
| **Total steps** | 43 | 29 |
| **Total email notifications** | 8 | 5 |
| **Design/UX Review** | Steps 10-16 | Not present |
| **Editorial Review** | Steps 17-22 | Not present |

### 2.5 Participants and Roles

| Role | AEM Group | Major Review | Simple Review |
|---|---|---|---|
| Content Author / Initiator | lt-wf-author | Step 3 (Select Web Ops), Step 8 (Rework) | Step 3 (Select Web Ops), Step 7 (Rework) |
| Design / UX Reviewer | lt-wf-design-reviewer | Step 15 | Not used |
| Editorial Reviewer | lt-wf-editorial-reviewer | Step 21 | Not used |
| Final Production Reviewer (Web Ops) | Dynamic (PropertiesParticipantChooser) | Step 27 | Step 13 |
| Rejection Notification Recipients | lt-wf-rejection-notification | Step 7 (email) | Step 6 (email) |
| Major Notification Recipients | lt-wf-major-notification | Steps 34, 39 (email) | Not used |
| Simple Notification Recipients | lt-wf-simple-notification | Not used | Steps 20, 25 (email) |

### 2.6 Custom Handlers Summary (Shared Across Both Variants)

All custom handlers are **shared** between both workflows. The same Java classes are used by both variants.

| Handler | Purpose | Major Review Instances | Simple Review Instances |
|---|---|---|---|
| LogWorkflowHandler | Logs workflow progress with placeholder substitution | 14 | 8 |
| PlaceParametersHandler | Sets typed parameters on workflow metadata | 2 | 1 |
| DialogParameterHandler | Extracts dialog properties into workflow metadata | 2 | 2 |
| JumpToNodeHandler | Unconditional jump to a named workflow node | 8 | 4 |
| JumpToNodeIfSetHandler | Conditional jump based on metadata property state | 1 | 1 |
| ReplicateToAgentHandler | Replicates content to specific replication agents | 10 | 6 |
| EmailNotificationsHandler | Sends templated email notifications to participants | 8 | 5 |
| CheckDelayedReleaseDateHandler | Validates or sets the delayed release timestamp | 1 | 1 |
| SetDelayedReleaseAsCommentHandler | Reports delayed release date in workflow inbox comment | 1 | 1 |

---

## 3. Future Workflow Definitions (AEM Cloud Service + EDS)

### 3.1 Architecture Approach

Both workflows are migrated preserving the same business logic, participants, and transitions. Changes are technical (infrastructure-level) only and **apply equally to both variants** since they share the same handler codebase. Refactoring a handler once applies to both workflows.

### 3.2 Major Review -- Future Step Sequence (38 steps)

```
 1. Log Start Workflow                          [LogWorkflowHandler - refactored]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - refactored]
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - refactored]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - refactored]
 6. Log Rework Content                          [LogWorkflowHandler - refactored]
 7. Send Rework Notification Email              [EmailNotificationsHandler - refactored]
 8. Rework Content Step                         [Dynamic Participant -> Java InitiatorParticipantChooser]
                                                 <-- CHANGED: ECMA -> Java ParticipantStepChooser
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
    +-- Branch 2: Log "Reject" -> Goto step 6 (Rework)         <-- CHANGED: OOTB Goto Step
    +-- Branch 3: Log "Cancel" -> Goto Cancel Processing        <-- CHANGED: OOTB Goto Step
16. Log Start Editorial Review                  [LogWorkflowHandler - refactored]
17. Publish to Preview Tier                     [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED)
18. Notify Editorial Reviewer Email             [EmailNotificationsHandler - refactored]
19. Review: Editorial                           [Participant Step -> lt-wf-editorial-reviewer]
20. OR Split: Approve / Cancel
    +-- Branch 1: Log "Approve" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing        <-- CHANGED: OOTB Goto Step
21. Log Start Final Production Review           [LogWorkflowHandler - refactored]
22. Publish to Preview Tier                     [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED)
23. Notify Final Production Reviewer Email      [EmailNotificationsHandler - refactored]
24. Review: Final Production                    [Dynamic Participant -> PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay -- NO CHANGE)
25. OR Split: Approve / Cancel
    +-- Branch 1: Approve + Place Delayed Release Date -> continue
    +-- Branch 2: Cancel -> Goto Cancel Processing              <-- CHANGED: OOTB Goto Step
26. Log Start Production Run                    [LogWorkflowHandler - refactored]
27. Publish to Preview Tier                     [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED)
28. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - refactored]
29. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - refactored]
30. Send Pending Publication Email              [EmailNotificationsHandler - refactored]
31. Waiting to Publish                          [Dynamic Participant -> AbsoluteTimeAutoAdvancer]
                                                 (NO CHANGE -- OOTB, backed by Sling Jobs)
32. OR Split: Force Deploy / Cancel
    +-- Branch 1: Log "Force Deploy" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing        <-- CHANGED: OOTB Goto Step
33. Publish to Production                       [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED -- EDS CDN invalidates automatically)
34. Send Finished Publication Email             [EmailNotificationsHandler - refactored]
35. Goto End (skip cancel processing)           [OOTB Goto Step]                     <-- CHANGED
36. Cancel Workflow Processing                  [LogWorkflowHandler - refactored]
37. Inform Content Author about Cancellation    [EmailNotificationsHandler - refactored]
38. Log End of Workflow                         [LogWorkflowHandler - refactored]
```

### 3.3 Simple Review -- Future Step Sequence (25 steps)

All changes below are the **same type of changes** as Major Review (same handlers refactored, same OOTB replacements). No additional migration effort.

```
 1. Log Start Workflow                          [LogWorkflowHandler - refactored]
 2. Set Rework Parameters (steppedBack=false)   [PlaceParametersHandler - refactored]
 3. Select Web Operations Team                  [Dialog Participant Step -> lt-wf-author]
 4. Place Web Ops Team on Workflow Metadata     [DialogParameterHandler - refactored]
 5. Jump Over Rework if First Loop              [JumpToNodeIfSetHandler - refactored]
 6. Rework Content (Email Notification)         [EmailNotificationsHandler - refactored]
 7. Rework Content Step                         [Dynamic Participant -> Java InitiatorParticipantChooser]
                                                 <-- CHANGED: ECMA -> Java ParticipantStepChooser
 8. OR Split: Done / Cancel
    +-- Branch 1: Log "Done" -> continue
    +-- Branch 2: Log "Cancel" -> Goto Cancel Processing        <-- CHANGED: OOTB Goto Step
 9. Log Start Final Production Review           [LogWorkflowHandler - refactored]
10. Publish to Preview Tier                     [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED)
11. Notify Final Production Reviewer            [EmailNotificationsHandler - refactored]
12. Review: Final Production                    [Dynamic Participant -> PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay -- NO CHANGE)
13. OR Split: Approve / Cancel / Reject
    +-- Branch 1: Approve + Place Delayed Release Date -> continue
    +-- Branch 2: Cancel -> Goto Cancel Processing              <-- CHANGED: OOTB Goto Step
    +-- Branch 3: Reject -> Goto Rework Content                 <-- CHANGED: OOTB Goto Step
14. Log Start Production Run                    [LogWorkflowHandler - refactored]
15. Publish to Preview Tier                     [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED)
16. Check Delayed Release Date                  [CheckDelayedReleaseDateHandler - refactored]
17. Report Delayed Release as Comment           [SetDelayedReleaseAsCommentHandler - refactored]
18. Send Pending Publication Email              [EmailNotificationsHandler - refactored]
19. Wait to Publish                             [Dynamic Participant -> AbsoluteTimeAutoAdvancer]
                                                 (NO CHANGE -- OOTB, backed by Sling Jobs)
20. OR Split: Force Deploy / Cancel
    +-- Branch 1: Force Deploy -> continue
    +-- Branch 2: Cancel -> Goto Cancel Processing              <-- CHANGED: OOTB Goto Step
21. Publish to Production                       [Sling Content Distribution API]     <-- CHANGED
    (Cache Flush REMOVED -- EDS CDN invalidates automatically)
22. Send Finished Publication Email             [EmailNotificationsHandler - refactored]
23. Goto End (skip cancel processing)           [OOTB Goto Step]                     <-- CHANGED
24. Cancel Workflow Processing + Cancellation Email
25. Log End of Workflow                         [LogWorkflowHandler - refactored]
```

### 3.4 Step Count Changes

| Metric | Major Current | Major Future | Simple Current | Simple Future |
|---|---|---|---|---|
| Total steps | 43 | 38 | 29 | 25 |
| Review gates | 3 | 3 | 1 | 1 |
| Steps removed (cache flush) | -- | -5 | -- | -4 |
| Email notifications | 8 | 8 | 5 | 5 |

---

## 4. Business Functionality Assessment

### 4.1 Functionality Preservation Status (Both Variants)

| Capability | Current Behavior | Future Behavior | Major | Simple |
|---|---|---|---|---|
| Multi-stage approval | Sequential gates with OR Splits | Same | Fully Preserved | Fully Preserved |
| Rework loop (reject -> rework -> re-review) | Workflow jumps back to rework step | Same (OOTB Goto Step) | Fully Preserved | Fully Preserved |
| First-loop rework skip | Custom handler skips rework on initial submission | Refactored handler, same logic | Fully Preserved | Fully Preserved |
| Web Ops team selection | Author selects from dialog dropdown | Same dialog | Fully Preserved | Fully Preserved |
| Delayed/scheduled release | Reviewer sets date via datepicker | Same mechanism (AbsoluteTimeAutoAdvancer) | Fully Preserved | Fully Preserved |
| Force deploy | OR Split allows immediate publication | Same OR Split | Fully Preserved | Fully Preserved |
| Email notifications | Templated emails to participants | Same handler, new email backend | Fully Preserved | Fully Preserved |
| Cancel from any stage | Jump to shared cancel handler | OOTB Goto Step to cancel handler | Fully Preserved | Fully Preserved |
| Comment history in emails | Assembled from workflow history | Same logic | Fully Preserved | Fully Preserved |
| Participant roles and groups | AEM user groups | Same groups | Fully Preserved | Fully Preserved |
| Preview before review | Replicated to preview agent | Published to Cloud preview tier | Fully Preserved | Fully Preserved |
| Production publish | Replicated to production agent | Published via Cloud Distribution -> EDS CDN | Fully Preserved | Fully Preserved |
| Inbox delayed release visibility | Comment shows release date | Same mechanism | Fully Preserved | Fully Preserved |

### 4.2 Impact by Role

| Role | What Changes | What Stays the Same | Applies To |
|---|---|---|---|
| **Content Author** | Nothing | Initiates workflow, selects Web Ops, reworks on rejection | Both |
| **Design/UX Reviewer** | Preview URL changes to EDS (.aem.page) | Reviews, approves/rejects/cancels from Inbox | Major only |
| **Editorial Reviewer** | Preview URL changes to EDS (.aem.page) | Reviews, approves/cancels from Inbox | Major only |
| **Web Ops (Final Production)** | Preview URL changes to EDS (.aem.page) | Sets release date, approves/cancels/forces deploy | Both |
| **All Participants** | Email links point to Cloud author + EDS preview URLs | Notifications, comment history, Inbox experience | Both |

### 4.3 What Is NOT Changing (Both Variants)

- Workflow initiation process
- Number and sequence of approval gates (per variant)
- Participant assignment logic
- OR Split branching options
- Rework loop behavior
- Delayed release date selection and timer
- Force deploy option
- Email notification content and recipients
- Workflow cancellation flow
- AEM Inbox experience
- Variant selection (authors still choose between Major and Simple Review when initiating)

---

## 5. Technical Change Details

**All technical changes documented below apply equally to both the Major Review and Simple Review workflows**, as they share the same custom handler codebase.

### 5.1 Steps Removed (No Replacement Needed)

| Step | Major Instances | Simple Instances | Current Behavior | Why Removed |
|---|---|---|---|---|
| Cache Flush Preview | 4 | 2 | Replicates to `flush-preview` agent | EDS CDN handles cache invalidation automatically. No Dispatcher in EDS. |
| Cache Flush Production | 1 | 1 | Replicates to `flush-production` agent | Same -- EDS CDN push-based invalidation. |

**Total steps removed: 5 (Major), 3 (Simple)**

### 5.2 Steps Replaced with OOTB AEM Cloud Features

| Current Step | Major Instances | Simple Instances | Replacement | Why |
|---|---|---|---|---|
| Replicate to Preview | 4 | 2 | **Sling Content Distribution API** | Agent-based replication not available in Cloud. Reference: [AEM Cloud Replication](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/operations/replication) |
| Replicate to Production | 1 | 1 | **Sling Content Distribution API** | Same -- content flows through xWalk pipeline to EDS CDN |
| JumpToNodeHandler (unconditional jumps) | 8 | 4 | **OOTB Goto Step** (`com.day.cq.workflow.impl.process.GotoStep`) | `createTransition()` modifies workflow model at runtime -- incompatible with Cloud's immutable infrastructure |
| ECMA initiator-participant-chooser | 1 | 1 | **Java `ParticipantStepChooser`** | ECMA workflow scripts not supported in AEM Cloud Service |

### 5.3 Steps Refactored (Same Behavior, Updated Code)

#### 5.3.1 LogWorkflowHandler

**Instances:** Major: 14, Simple: 8

**Changes:** Felix SCR -> OSGi DS annotations; remove `AbstractResourceWorkflowProcess` base class.

**Recommendation:** AEM Cloud's workflow engine automatically tracks step execution, participant actions, and comments. Consider reducing to 2-3 instances per workflow where metadata debugging context ({wf-data-meta}) adds genuine value.

**Business impact:** None.

#### 5.3.2 PlaceParametersHandler

**Instances:** Major: 2, Simple: 1

**Note:** Major Review has `PlaceParametersHandler` setting `steppedBack=true` after the rework OR Split (Step 10). Simple Review **does not have this step** -- see Open Item 8.5.

**Changes:** Felix SCR -> OSGi DS; fix deprecated `new Long()` -> `Long.valueOf()`.

**Business impact:** None.

#### 5.3.3 DialogParameterHandler

**Instances:** Major: 2, Simple: 2

**Changes:** Felix SCR -> OSGi DS annotations.

**Business impact:** None.

#### 5.3.4 JumpToNodeIfSetHandler

**Instances:** Major: 1, Simple: 1

**What it does:** Checks `steppedBack` property to skip rework on first loop. Jump target differs per variant:
- Major: targets "Hand over to the Design / UX Review"
- Simple: targets "Start the Final Production Review"

**Changes:** Implement `WorkflowProcess` directly; remove `createTransition()` and `SimpleRoute`; use pre-defined routes; Felix SCR -> OSGi DS. Add pre-defined transition in both workflow models from this step to its respective jump target.

**Business impact:** None -- same conditional logic in both variants.

#### 5.3.5 EmailNotificationsHandler

**Instances:** Major: 8, Simple: 5

**Why it cannot be used as-is:**

| Issue | Detail | Severity |
|---|---|---|
| `com.day.cq.mailer.MailService` removed | AEM Cloud Service has no direct SMTP access | Critical |
| Felix SCR annotations | Must migrate to OSGi DS | High |
| JCR Session as instance variable | Unsafe in Cloud -- instances restart frequently | High |
| Dynamic `@Reference` for `ParticipantStepChooser` | Felix bind/unbind must be rewritten to OSGi DS volatile List | High |
| Email templates at `/etc/` | Deprecated content path | Medium |
| Hardcoded host URLs | `http://author1.lifetechnologies.com`, `http://preview.lifetechnologies.com` | Medium |

**Changes:**
1. Replace `MailService` with cloud-compatible email service (e.g., SendGrid)
2. Migrate to OSGi DS annotations
3. Remove Session instance variable -- use method-local ResourceResolver
4. Relocate email templates from `/etc/` to `/conf/`
5. Make host URLs configurable via OSGi

**Email templates requiring relocation:**

| Template | Major Review Path | Simple Review Path |
|---|---|---|
| Rework rejection | `.../major/review/rejected/en.txt` | `.../simple/review/rejected/en.txt` |
| Design review notification | `.../major/review/design/en.txt` | N/A (not used) |
| Editorial review notification | `.../major/review/editorial/en.txt` | N/A (not used) |
| Final production notification | `.../major/review/webops/en.txt` | `.../simple/review/webops/en.txt` |
| Pending publication | `.../major/pending-publication/en.txt` | `.../simple/pending-publication/en.txt` |
| Finished publication | `.../major/finished-publication/en.txt` | `.../simple/finished-publication/en.txt` |
| Cancellation | `.../major/cancelled/en.txt` | `.../simple/cancelled/en.txt` |

**Total templates to relocate: 7 (Major) + 5 (Simple) = 12 templates**

**Business impact:** None -- same emails, same recipients, same content.

#### 5.3.6 CheckDelayedReleaseDateHandler

**Instances:** 1 in each variant.

**Changes:** Felix SCR -> OSGi DS; `SimpleDateFormat` -> `DateTimeFormatter` (thread-safety).

**Business impact:** None.

#### 5.3.7 SetDelayedReleaseAsCommentHandler

**Instances:** 1 in each variant.

**Changes:** Felix SCR -> OSGi DS; `SimpleDateFormat` -> `DateTimeFormatter`; review `updateTransFlag()` static call.

**Business impact:** None.

### 5.4 Handler Change Summary Table

| Handler | Change Type | Cloud Risk | Effort | Applies To |
|---|---|---|---|---|
| LogWorkflowHandler | Annotation refactoring | None | Low (1-2 hrs) | Both |
| PlaceParametersHandler | Annotations + deprecated fix | None | Low (1-2 hrs) | Both |
| DialogParameterHandler | Annotation refactoring | None | Low (1-2 hrs) | Both |
| JumpToNodeHandler | **Replace with OOTB Goto Step** | None (OOTB) | Low (2-3 hrs) | Both |
| JumpToNodeIfSetHandler | **Refactor** -- remove createTransition() | Low | Medium (3-4 hrs) | Both |
| ReplicateToAgentHandler | **Replace with Sling Content Distribution** | Low | High (12-16 hrs) | Both |
| EmailNotificationsHandler | **Major refactor** -- replace MailService | Medium | High (20-24 hrs) | Both |
| CheckDelayedReleaseDateHandler | Annotations + thread-safety | None | Low (1-2 hrs) | Both |
| SetDelayedReleaseAsCommentHandler | Annotations + thread-safety | None | Low (1-2 hrs) | Both |
| ECMA initiator-participant-chooser | **Replace with Java ParticipantStepChooser** | None | Low (1-2 hrs) | Both |
| Cache Flush steps | **Remove entirely** | None | None | Both |

**Important:** All handler changes are done **once** and apply to both workflow variants. There is **no duplicate migration effort** for the Simple Review workflow.

### 5.5 Scheduling and Release Logic (Both Variants)

Both variants use the identical delayed release pattern, which is architecturally aligned with AEM Cloud's internal scheduled publishing mechanism.

| Component | Cloud Compatible? | Change Needed |
|---|---|---|
| CheckDelayedReleaseDateHandler | Yes (with refactoring) | Annotation migration + thread-safety |
| SetDelayedReleaseAsCommentHandler | Yes (with refactoring) | Annotation migration + thread-safety |
| AbsoluteTimeAutoAdvancer (OOTB) | Yes -- works in Cloud | None -- Sling Jobs guarantee execution, survives restarts |
| Absolute Timer Delay Dialog | Yes -- Touch UI, Cloud-ready | None |
| Force Deploy via OR Split | Yes -- OOTB | None |

---

## 6. Workflow Dialog Compatibility

| Dialog | UI Type | Used In | Cloud Status |
|---|---|---|---|
| **Absolute Timer Delay** | Touch UI (Coral datepicker) | Final Production step -- both variants | Cloud-ready -- no changes |
| **Web Operations Team Selection** | Touch UI | Step 3 -- both variants | Cloud-ready -- no changes |
| **Dynamic Participant Dialog** | Both Classic + Touch UI | Final Production step -- both variants | Touch UI works; remove Classic UI artifacts |

---

## 7. Post-Publish Delivery Path (EDS)

When either workflow publishes content, the following delivery pipeline is triggered automatically:

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

This applies identically to both Major Review and Simple Review. Cache invalidation is automatic -- no workflow steps needed.

---

## 8. Open Items and Clarifications Needed

### 8.1 Delayed Release Default Behavior (Both Variants)

**Current behavior:** When no activation date is entered, `CheckDelayedReleaseDateHandler` defaults to NOW + 1 day. Pages are never published immediately after approval.

**Clarification needed:** Is the 1-day delay a business requirement or a technical safeguard?

**Recommendation:** If no business requirement exists, publish immediately upon approval. Delayed release remains available when a reviewer explicitly sets a future date.

### 8.2 Email Service Selection (Both Variants)

`MailService` (SMTP) is removed in Cloud. A replacement must be selected:

| Option | Notes |
|---|---|
| **SendGrid** | Most common with AEM Cloud |
| **Adobe Campaign** | If already in Adobe stack |
| **AWS SES** | If AWS infrastructure |

### 8.3 Hardcoded Host URLs (Both Variants)

Email templates contain `http://author1.lifetechnologies.com` and `http://preview.lifetechnologies.com`. Must be updated to Cloud URLs or made OSGi-configurable.

### 8.4 LogWorkflowHandler Reduction (Both Variants)

**Recommendation:** Reduce from 14 (Major) / 8 (Simple) to 2-3 instances per variant. AEM Cloud tracks step execution automatically.

**Decision needed:** Are all log steps required for compliance/operational reasons?

### 8.5 Simple Review: Rework Loop -- steppedBack Flag Verification

**Observation:** The Simple Review workflow does **not** include an explicit `PlaceParametersHandler` step to set `steppedBack=true` after the rework OR Split. In Major Review, this is Step 10 -- it sets the flag so that `JumpToNodeIfSetHandler` allows the rework step on the second and subsequent loops.

In Simple Review, the rework loop is triggered by the **Reject branch at Final Production** (Step 14, Branch 3), which jumps back to "Rework Content" (Step 6). However, without an explicit `steppedBack=true` being set, the `JumpToNodeIfSetHandler` at Step 5 may skip rework again on re-entry if the flag was never set.

**Verification needed during testing:** Confirm that the Simple Review rework loop functions correctly when the Final Production reviewer rejects content. Specifically, verify that on the second pass through Step 5, the workflow correctly proceeds to the rework step (Steps 6-7) rather than skipping to Final Production again.

### 8.6 Simple Review Email Templates

Five Simple Review email templates must be relocated alongside the Major Review templates:

| Template | Current Path |
|---|---|
| Rework rejection | `/etc/.../simple/review/rejected/en.txt` |
| Final production notification | `/etc/.../simple/review/webops/en.txt` |
| Pending publication | `/etc/.../simple/pending-publication/en.txt` |
| Finished publication | `/etc/.../simple/finished-publication/en.txt` |
| Cancellation | `/etc/.../simple/cancelled/en.txt` |

These are in addition to the 7 Major Review templates. **Total: 12 email templates to relocate to `/conf/`.**

### 8.7 Notification Group Configuration

Both variants use separate AEM user groups for publication notifications:
- Major Review: `lt-wf-major-notification`
- Simple Review: `lt-wf-simple-notification`

**Verification needed:** Confirm these groups exist and have correct membership in the Cloud environment after migration.
