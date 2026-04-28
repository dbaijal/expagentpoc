# Deactivation Workflow -- Migration Analysis

**Document Version:** 1.0
**Date:** April 26, 2026
**Project:** AEM 6.4 to AEM as a Cloud Service + EDS (xWalk)
**Workflow:** Lifetech - Deactivation Publication
**Status:** Draft

**Related Document:** Content Review Workflows -- Migration Analysis (Major Review & Simple Review). Handler implementation details, refactoring patterns, and Cloud compatibility analysis for shared handlers are documented there and referenced from this document.

---

## Table of Contents

1. Executive Summary
2. Current Workflow Definition (AEM 6.4)
3. Future Workflow Definition (AEM Cloud Service + EDS)
4. Business Functionality Assessment
5. Technical Change Details
6. Deactivation in EDS -- Content Removal Path
7. Open Items and Clarifications Needed

---

## 1. Executive Summary

### 1.1 Purpose

The Deactivation Workflow is a governed content removal pipeline. When content needs to be unpublished from the live site, this workflow ensures it goes through Web Operations team approval before deactivation, with optional scheduling for timed content removal.

Unlike the Content Review Workflows (Major/Simple Review) which handle content activation through multiple approval gates, the Deactivation Workflow has a **single approval gate** (Web Ops review) and **no rework loop** -- the reviewer either approves the deactivation or cancels the workflow.

### 1.2 How It Differs from Content Review Workflows

| Aspect | Content Review (Major/Simple) | Deactivation Workflow |
|---|---|---|
| Action | Publish content (ACTIVATE) | Unpublish content (DEACTIVATE) |
| Approval gates | 3 (Major) or 1 (Simple) | 1 (Web Ops only) |
| Rework loop | Yes -- reject sends back to author | No -- approve or cancel only |
| Design/UX Review | Major only | Not applicable |
| Editorial Review | Major only | Not applicable |
| Scheduled release | Yes -- delayed activation date | Yes -- delayed deactivation date |
| Force option | Yes -- force immediate publish | Yes -- force immediate deactivation |
| Default delay if no date set | +1 day (activation) | +1 day (deactivation) |

### 1.3 Migration Impact -- At a Glance

The deactivation workflow's business logic -- Web Ops approval, scheduled deactivation, force deactivation, email notifications, and cancellation -- is **fully preserved** in AEM Cloud Service. The same infrastructure-level changes that apply to the Content Review Workflows apply here.

All custom handlers used by this workflow are **shared with the Content Review Workflows**. Refactoring each handler once covers all three workflows (Major Review, Simple Review, Deactivation). There is no additional handler development for this workflow.

### 1.4 Key Characteristics

| Characteristic | Detail |
|---|---|
| Review Stages | 1 (Final Production Deactivation Review) |
| Rework Loop | None |
| Scheduled Deactivation | Reviewer can set a future deactivation date |
| Force Deactivation | Option to bypass scheduled wait and deactivate immediately |
| Default Delay | +1 day if no date is set by reviewer |
| Email Notifications | 4 notification points |
| Cancellation | From review stage or waiting stage |
| Team Routing | Web Operations team selected by author at start |
| Replication Type | DEACTIVATE (all replication steps) |
| Total Steps (Current) | 21 |

---

## 2. Current Workflow Definition (AEM 6.4)

### 2.1 Workflow Flow -- Complete Step Sequence

```
 1. Log Start Workflow                                    [LogWorkflowHandler - auto-advance]
    "Workflow for {payload} Deactivation started."

 2. Select Web Operations Team for Deactivation           [Dialog Participant Step -> lt-wf-author]
    Author selects Web Ops team from dialog

 3. Place Web Ops Team on Workflow Metadata                [DialogParameterHandler - auto-advance]
    Extracts selected-web-ops -> participantStepAuthorizableId

 4. Log Start Final Production Deactivation Review        [LogWorkflowHandler - auto-advance]
    "Start the Final Production Deactivation on {payload}"

 5. Replicate Deactivation to Preview                     [ReplicateToAgentHandler - auto-advance]
    activationType: DEACTIVATE, agentId: push-to-preview

 6. Cache Flush Deactivation Preview                      [ReplicateToAgentHandler - auto-advance]
    activationType: DEACTIVATE, agentId: flush-preview

 7. Notify about Final Production Review                  [EmailNotificationsHandler - auto-advance]
    Template: .../deactivation/review/webops/en.txt
    Participant chooser: PropertiesParticipantChooser

 8. Perform Final Production Deactivation Review          [Dynamic Participant -> PropertiesParticipantChooser]
    Dialog: Absolute Timer Delay (delayed deactivation date picker)
    Reviewer inspects preview and approves or cancels deactivation

 9. OR Split: Approve / Cancel
    +-- Branch 1: Approve the Deactivation
    |   +-- Log "Final Production Reviewer Accepted Deactivation on {payload}"
    |   +-- DialogParameterHandler: extracts delayed-release-date -> absoluteTime
    +-- Branch 2: Cancel the Deactivation Workflow
        +-- Log "Terminated the Deactivation Workflow on {payload}"
        +-- JumpToNodeHandler -> "Cancel Workflow Processing"

10. Log Start Production Deactivation Run                 [LogWorkflowHandler - auto-advance]
    "Start the Production Deactivation Run for {payload}"

11. Check Delayed Release Date                            [CheckDelayedReleaseDateHandler - auto-advance]
    delayOffset: "1d"
    If no date set by reviewer -> defaults to NOW + 1 day

12. Reports Delayed Deactivation Date                     [SetDelayedReleaseAsCommentHandler - auto-advance]
    Sets deactivation date as workflow comment visible in AEM Inbox

13. Send Email about Pending Deactivation                 [EmailNotificationsHandler - auto-advance]
    Template: .../deactivation/pending-publication/en.txt
    Recipients: lt-wf-deactivation-notification

14. Wait to Deactivate                                    [Dynamic Participant -> AbsoluteTimeAutoAdvancer]
    timeoutHandler: com.day.cq.workflow.timeout.autoadvance.AbsoluteTimeAutoAdvancer
    Waits until absoluteTime is reached, then auto-advances

15. OR Split: Force Deactivation / Cancel
    +-- Branch 1: Force the Deactivation -> continue
    +-- Branch 2: Cancel the Deactivation Workflow
        +-- JumpToNodeHandler -> "Cancel Workflow Processing"

16. Replicate Deactivation to Production                  [ReplicateToAgentHandler - auto-advance]
    activationType: DEACTIVATE, agentId: push-to-production

17. Cache Flush Deactivation Production                   [ReplicateToAgentHandler - auto-advance]
    activationType: DEACTIVATE, agentId: flush-production

18. Send Email about Finished Deactivation                [EmailNotificationsHandler - auto-advance]
    Template: .../deactivation/finished-publication/en.txt
    Recipients: lt-wf-deactivation-notification

19. Jump to End (skip cancel processing)                  [JumpToNodeHandler -> "Log End of Workflow"]

20. Cancel Workflow Processing                            [LogWorkflowHandler - auto-advance]
    + Inform Content Author about Cancellation            [EmailNotificationsHandler]
    Template: .../deactivation/cancelled/en.txt

21. Log End of Workflow                                   [LogWorkflowHandler - auto-advance]
    "Workflow has Ended for {payload}"
```

### 2.2 Participants and Roles

| Role | AEM Group | Used In |
|---|---|---|
| Content Author / Initiator | lt-wf-author | Step 2 (Select Web Ops team) |
| Final Production Reviewer (Web Ops) | Dynamic (PropertiesParticipantChooser) | Step 8 (Deactivation review) |
| Deactivation Notification Recipients | lt-wf-deactivation-notification | Steps 13, 18 (email only) |

### 2.3 Scheduling and Delay Logic

The deactivation timing works as follows:

```
Step 8:  Reviewer gets Absolute Timer Delay dialog
         -> Optionally picks a future deactivation date
         -> If picked: DialogParameterHandler stores as absoluteTime

Step 11: CheckDelayedReleaseDateHandler
         -> IF absoluteTime set and valid -> use reviewer's date
         -> IF NOT set -> apply default: NOW + 1 day (delayOffset="1d")

Step 14: AbsoluteTimeAutoAdvancer
         -> Waits until absoluteTime
         -> Auto-advances workflow when time is reached

Step 15: OR Split
         -> Force: bypass timer, deactivate immediately
         -> Cancel: abort deactivation entirely
```

**The +1 day default delay applies to deactivation.** `CheckDelayedReleaseDateHandler` uses the same code path for both activation and deactivation workflows -- it has no awareness of the replication type. If the reviewer does not set a date, deactivation is delayed by 1 day by default.

### 2.4 Custom Handlers Used

All handlers in this workflow are **shared with the Content Review Workflows** (Major Review & Simple Review). They are the same Java classes.

| Handler | Instances in This Workflow | Also Used In |
|---|---|---|
| LogWorkflowHandler | 6 | Major (14), Simple (8) |
| DialogParameterHandler | 2 | Major (2), Simple (2) |
| ReplicateToAgentHandler | 4 (all DEACTIVATE) | Major (10, all ACTIVATE), Simple (6, all ACTIVATE) |
| EmailNotificationsHandler | 4 | Major (8), Simple (5) |
| CheckDelayedReleaseDateHandler | 1 | Major (1), Simple (1) |
| SetDelayedReleaseAsCommentHandler | 1 | Major (1), Simple (1) |
| JumpToNodeHandler | 3 | Major (8), Simple (4) |

**For handler implementation details, Cloud compatibility issues, and refactoring approach, refer to the Content Review Workflows -- Migration Analysis document (Section 5: Technical Change Details).** The same changes apply.

### 2.5 Email Templates

| Template | Path |
|---|---|
| Deactivation review notification | `/etc/workflow/notification/email/lifetech/deactivation/review/webops/en.txt` |
| Pending deactivation | `/etc/workflow/notification/email/lifetech/deactivation/pending-publication/en.txt` |
| Finished deactivation | `/etc/workflow/notification/email/lifetech/deactivation/finished-publication/en.txt` |
| Cancellation | `/etc/workflow/notification/email/lifetech/deactivation/cancelled/en.txt` |

---

## 3. Future Workflow Definition (AEM Cloud Service + EDS)

### 3.1 Architecture Approach

The workflow is migrated preserving the same business logic, participants, and transitions. Changes are infrastructure-level only. All handler refactoring is shared with the Content Review Workflows -- no additional handler development is required.

The key difference from the activation workflows: replication steps use `DistributionRequestType.DELETE` instead of `DistributionRequestType.ADD` to unpublish content rather than publish it.

### 3.2 Future Workflow Flow -- Complete Step Sequence (17 steps)

```
 1. Log Start Workflow                                    [LogWorkflowHandler - refactored]

 2. Select Web Operations Team for Deactivation           [Dialog Participant Step -> lt-wf-author]
                                                           (NO CHANGE)

 3. Place Web Ops Team on Workflow Metadata                [DialogParameterHandler - refactored]

 4. Log Start Final Production Deactivation Review        [LogWorkflowHandler - refactored]

 5. Unpublish from Preview Tier                           [Sling Content Distribution -> DELETE]
                                                           <-- CHANGED: Replaces DEACTIVATE to push-to-preview agent
    (Cache Flush step REMOVED -- EDS CDN handles automatically)

 6. Notify about Final Production Review                  [EmailNotificationsHandler - refactored]

 7. Perform Final Production Deactivation Review          [Dynamic Participant -> PropertiesParticipantChooser]
    (Dialog: Absolute Timer Delay -- NO CHANGE)

 8. OR Split: Approve / Cancel
    +-- Branch 1: Approve + Place Delayed Release Date -> continue
    +-- Branch 2: Cancel -> Goto Cancel Processing
                                                           <-- CHANGED: OOTB Goto Step replaces JumpToNodeHandler

 9. Log Start Production Deactivation Run                 [LogWorkflowHandler - refactored]

10. Check Delayed Release Date                            [CheckDelayedReleaseDateHandler - refactored]
                                                           (Same +1 day default if no date set)

11. Reports Delayed Deactivation Date                     [SetDelayedReleaseAsCommentHandler - refactored]

12. Send Email about Pending Deactivation                 [EmailNotificationsHandler - refactored]

13. Wait to Deactivate                                    [Dynamic Participant -> AbsoluteTimeAutoAdvancer]
                                                           (NO CHANGE -- OOTB, backed by Sling Jobs)

14. OR Split: Force Deactivation / Cancel
    +-- Branch 1: Force -> continue
    +-- Branch 2: Cancel -> Goto Cancel Processing
                                                           <-- CHANGED: OOTB Goto Step

15. Unpublish from Production                             [Sling Content Distribution -> DELETE]
                                                           <-- CHANGED: Replaces DEACTIVATE to push-to-production agent
    (Cache Flush REMOVED -- EDS CDN invalidates automatically)

16. Send Email about Finished Deactivation                [EmailNotificationsHandler - refactored]

17. Goto End / Cancel Processing / Cancellation Email / Log End
                                                           <-- CHANGED: OOTB Goto Step replaces JumpToNodeHandler
```

### 3.3 Step Count Changes

| Metric | Current (AEM 6.4) | Future (AEM Cloud) | Change |
|---|---|---|---|
| Total steps | 21 | 17 | -4 (cache flush steps removed) |
| Approval gates | 1 | 1 | No change |
| OR Splits | 2 | 2 | No change |
| Email notifications | 4 | 4 | No change |
| Replication type | DEACTIVATE (agent-based) | DELETE (Sling Content Distribution) | Mechanism changed, action preserved |

---

## 4. Business Functionality Assessment

### 4.1 Functionality Preservation Status

| Capability | Current Behavior | Future Behavior | Status |
|---|---|---|---|
| Web Ops approval for deactivation | Single approval gate with OR Split | Same | Fully Preserved |
| Web Ops team selection at start | Author selects from dialog | Same dialog | Fully Preserved |
| Scheduled deactivation date | Reviewer sets date via datepicker | Same mechanism (AbsoluteTimeAutoAdvancer) | Fully Preserved |
| Default +1 day delay if no date | CheckDelayedReleaseDateHandler applies offset | Same handler, same offset | Fully Preserved |
| Force deactivation (bypass timer) | OR Split allows immediate deactivation | Same OR Split | Fully Preserved |
| Email: deactivation review notification | Sent to Web Ops reviewer | Same (refactored handler) | Fully Preserved |
| Email: pending deactivation | Sent to notification group | Same | Fully Preserved |
| Email: finished deactivation | Sent to notification group | Same | Fully Preserved |
| Email: cancellation notification | Sent to initiating author | Same | Fully Preserved |
| Cancel from review stage | Jump to cancel processing | OOTB Goto Step to cancel processing | Fully Preserved |
| Cancel from waiting stage | Jump to cancel processing | OOTB Goto Step to cancel processing | Fully Preserved |
| Preview deactivation before review | Content deactivated on preview for reviewer inspection | Content unpublished from Cloud preview tier (.aem.page) | Fully Preserved (different mechanism, same result) |
| Production deactivation after approval | Content deactivated via production agent | Content unpublished via Cloud Distribution -> EDS CDN removal | Fully Preserved (different mechanism, same result) |
| Inbox visibility of deactivation date | Date shown as workflow comment | Same mechanism | Fully Preserved |

### 4.2 Impact by Role

| Role | What Changes | What Stays the Same |
|---|---|---|
| **Content Author** | Nothing | Initiates deactivation workflow, selects Web Ops team |
| **Web Ops Reviewer** | Preview URL changes from Dispatcher to EDS preview (.aem.page) | Reviews deactivation on preview, sets deactivation date or approves immediately, can force or cancel |
| **Notification Recipients** | Email links point to Cloud author + EDS URLs | Receive pending/finished/cancellation notifications |

### 4.3 What Is NOT Changing

- Workflow initiation process
- Web Ops team selection and routing
- Single approval gate (approve or cancel)
- Scheduled deactivation mechanism
- Force deactivation option
- Default +1 day delay behavior
- Email notification content and recipients
- Cancellation flow
- AEM Inbox experience

---

## 5. Technical Change Details

### 5.1 Shared Handler Disclaimer

All custom handlers in this workflow are shared with the Content Review Workflows (Major Review & Simple Review). The handler refactoring documented in the **Content Review Workflows -- Migration Analysis (Section 5)** covers all changes needed for this workflow as well.

**No additional handler development is required for the Deactivation Workflow.**

The following subsections highlight only what is **specific to the Deactivation Workflow** -- primarily the use of `DEACTIVATE` replication type and the deactivation-specific email templates.

### 5.2 Steps Removed

| Step | Instances | Why Removed |
|---|---|---|
| Cache Flush Preview (DEACTIVATE) | 1 | EDS CDN handles cache invalidation automatically when content is unpublished |
| Cache Flush Production (DEACTIVATE) | 1 | Same -- EDS CDN push-based invalidation |
| Log steps (optional reduction) | Up to 4 | Workflow engine tracks step execution automatically (see Open Item 7.3) |

### 5.3 Steps Changed

| Current Step | Current Implementation | Future Implementation | Notes |
|---|---|---|---|
| Replicate Deactivation to Preview | `ReplicateToAgentHandler` with `activationType=DEACTIVATE`, `agentId=push-to-preview` | Sling Content Distribution with `DistributionRequestType.DELETE` | Same refactored handler as activation workflows, but using DELETE instead of ADD |
| Replicate Deactivation to Production | `ReplicateToAgentHandler` with `activationType=DEACTIVATE`, `agentId=push-to-production` | Sling Content Distribution with `DistributionRequestType.DELETE` | Same |
| Jump to Cancel Processing (2 instances) | `JumpToNodeHandler` with runtime transition creation | OOTB Goto Step (`com.day.cq.workflow.impl.process.GotoStep`) | Same change as Content Review Workflows -- see Content Review Migration Analysis Section 5.2 |
| Jump to End (skip cancel) | `JumpToNodeHandler` | OOTB Goto Step | Same |

### 5.4 Deactivation-Specific: ReplicateToAgentHandler Behavior

The refactored publishing handler (documented in Content Review Migration Analysis) must support **both** activation and deactivation. The current `ReplicateToAgentHandler` reads `activationType` from workflow step arguments:

```java
// Current (AEM 6.4):
String lActivationType = pWorkflowContext.getModelArguments().get("activationType");
ReplicationActionType lReplicationActionType = ReplicationActionType.valueOf(lActivationType);
// → ACTIVATE for Content Review workflows
// → DEACTIVATE for Deactivation workflow

// Future (AEM Cloud):
String activationType = metaDataMap.get("activationType", String.class);
if ("DEACTIVATE".equalsIgnoreCase(activationType)) {
    distributor.distribute(resolver,
        new SimpleDistributionRequest(DistributionRequestType.DELETE, path));
} else {
    distributor.distribute(resolver,
        new SimpleDistributionRequest(DistributionRequestType.ADD, path));
}
```

The `activationType` step argument is preserved in the workflow model. The refactored handler reads it and maps to the appropriate Sling Content Distribution request type:

| activationType (Step Argument) | AEM 6.4 Action | AEM Cloud Action |
|---|---|---|
| `ACTIVATE` | `ReplicationActionType.ACTIVATE` via agent | `DistributionRequestType.ADD` via Sling Content Distribution |
| `DEACTIVATE` | `ReplicationActionType.DEACTIVATE` via agent | `DistributionRequestType.DELETE` via Sling Content Distribution |

### 5.5 Deactivation Email Templates

Four deactivation-specific email templates must be relocated from `/etc/` to `/conf/`:

| Template | Current Path | New Path |
|---|---|---|
| Deactivation review | `/etc/.../lifetech/deactivation/review/webops/en.txt` | `/conf/<project>/.../deactivation/review/webops/en.txt` |
| Pending deactivation | `/etc/.../lifetech/deactivation/pending-publication/en.txt` | `/conf/<project>/.../deactivation/pending-publication/en.txt` |
| Finished deactivation | `/etc/.../lifetech/deactivation/finished-publication/en.txt` | `/conf/<project>/.../deactivation/finished-publication/en.txt` |
| Cancellation | `/etc/.../lifetech/deactivation/cancelled/en.txt` | `/conf/<project>/.../deactivation/cancelled/en.txt` |

These are in addition to the 12 templates from the Content Review Workflows. **Total across all workflows: 16 email templates to relocate.**

### 5.6 Migration Effort Summary (Deactivation-Specific Only)

Since all handlers are shared and refactored once for the Content Review Workflows, the deactivation-specific effort is minimal:

| Task | Effort | Notes |
|---|---|---|
| Update workflow model: replace JumpToNodeHandler with OOTB Goto Step | Low (1 hour) | 3 instances |
| Update workflow model: remove cache flush steps | Low (30 min) | 2 instances |
| Update workflow model: update replication steps to use refactored handler | Low (30 min) | 2 instances -- handler already supports DEACTIVATE |
| Relocate 4 email templates from `/etc/` to `/conf/` | Low (1 hour) | Path update in workflow model + content move |
| Verify `lt-wf-deactivation-notification` group exists in Cloud | Low (15 min) | Group membership check |
| **Total deactivation-specific effort** | **~3 hours** | Assumes shared handlers already refactored |

---

## 6. Deactivation in EDS -- Content Removal Path

When the workflow unpublishes content (Steps 5 and 15), the following removal pipeline is triggered:

```
Workflow step: "Unpublish from Preview" or "Unpublish from Production"
  (Sling Content Distribution with DistributionRequestType.DELETE)
        |
        v
AEM Cloud processes the DELETE distribution request
        |
        v
Content Bus receives the removal instruction
        |
        v
EDS Pipeline processes the removal
        |
        v
EDS CDN (aem.live / Fastly)
  - Content removed from CDN cache
  - Future requests for that URL return 404
        |
        v
Page is no longer accessible on the public site
```

**Cache invalidation is automatic.** When content is unpublished, the EDS CDN removes it from cache without any explicit flush step. This is why all Cache Flush steps are removed from the workflow.

**Preview deactivation (Step 5):** Content is removed from the Cloud preview tier (`.aem.page`). The Web Ops reviewer can verify the page returns 404 on preview before approving the production deactivation.

**Production deactivation (Step 15):** Content is removed from the live tier (`.aem.live`). After this step, the page is no longer accessible to end users.

---

## 7. Open Items and Clarifications Needed

### 7.1 Default +1 Day Delay for Deactivation

**Current behavior:** When the Web Ops reviewer does not set a deactivation date, `CheckDelayedReleaseDateHandler` applies a default delay of NOW + 1 day (`delayOffset="1d"`). Content remains live for 24 hours after approval unless someone explicitly uses "Force the Deactivation."

**Clarification needed from business:**

Is the +1 day default delay a deliberate business requirement for deactivation? Unlike activation (where a delay provides a final review window), deactivation scenarios often have urgency -- content may need to come down quickly for legal, compliance, or accuracy reasons.

**Scenarios to consider:**

| Scenario | Appropriate Delay? |
|---|---|
| Routine content retirement (end-of-campaign) | Delay may be acceptable |
| Legal/compliance content removal | Delay is counter-productive -- needs immediate removal |
| Incorrect information on live page | Delay is risky -- inaccurate content stays live 24 hours |
| Scheduled content expiry (known end date) | Reviewer sets explicit date -- default delay irrelevant |

**Recommendation:** If there is no specific business requirement for the default delay on deactivation, consider either:
- Removing the delay entirely (deactivate immediately after approval)
- Reducing the default offset (e.g., 1 hour instead of 1 day)
- Making the offset configurable per workflow instance rather than hardcoded

### 7.2 Email Service

Same dependency as Content Review Workflows -- `MailService` (SMTP) is not available in AEM Cloud Service. The cloud-compatible email service selected for the Content Review Workflows (e.g., SendGrid) will also be used by this workflow. No separate decision needed.

Refer to: Content Review Workflows -- Migration Analysis, Section 8.2.

### 7.3 LogWorkflowHandler Reduction

The workflow has 6 log steps. AEM Cloud's workflow engine tracks step execution automatically. Recommend reducing to 1-2 instances where metadata context is valuable for debugging.

Refer to: Content Review Workflows -- Migration Analysis, Section 8.4.

### 7.4 Notification Group Verification

The workflow uses `lt-wf-deactivation-notification` as the recipient group for pending and finished deactivation emails. Verify this group exists and has correct membership in the AEM Cloud environment after migration.

### 7.5 Hardcoded Host URLs

Same issue as Content Review Workflows -- email templates reference hardcoded author/preview URLs that must be updated for Cloud.

Refer to: Content Review Workflows -- Migration Analysis, Section 8.3.
