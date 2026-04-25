# AEM 6.4 Workflow Migration to AEM as a Cloud Service - Comprehensive Analysis

**Document Date:** April 25, 2026  
**Project:** Major Review Workflow Migration  
**Target:** AEM as a Cloud Service (AEMaaCS)

---

## Executive Summary

This document provides a comprehensive analysis and migration strategy for the **Major Review Workflow** currently running on AEM 6.4. The workflow is a complex multi-stage content review process with custom handlers, dynamic participants, email notifications, and scheduled publication capabilities.

**Key Findings:**
- **9 Custom Workflow Handlers** requiring refactoring
- **Multiple OR splits** with conditional routing
- **20+ Workflow Steps** with complex business logic
- **Custom Participant Choosers** requiring redesign
- **Replication handlers** needing AEMaaCS adaptation
- **Email notification system** requiring migration

---

## SECTION 1: CURRENT WORKFLOW ARCHITECTURE (AEM 6.4)

### 1.1 Workflow Overview

**Workflow Name:** Major Review Workflow  
**Purpose:** Multi-stage content review, approval, and publication pipeline  
**Review Stages:** 4 (Rework, Design/UX, Editorial, Final Production)  
**Optional Path:** Translation workflow integration

### 1.2 Workflow Execution Flow

```
START
  ↓
1. [Log Start] → Log Workflow Handler
  ↓
2. [Set Rework Parameters] → Place Parameters Handler
  ↓
3. [Conditional Skip First Loop] → Jump To Node If Set Handler
  ↓
4a. [Rework Content] → Log + Send Notifications → Dynamic Participant (Author)
  ├─→ 4b [Done/Cancel Decision - OR Split]
  │   ├─→ 4b.1 [Send back to reviewers]
  │   └─→ 4b.2 [Cancel] → Jump to Cancel Processing
  ↓
5. [Design/UX Review] → 
   - Log + Replicate to Preview + Flush Cache
   - Send Notifications → Participant (Design Reviewer)
  ├─→ OR Split (3 Branches):
  │   ├─ Approve → Log
  │   ├─ Reject → Jump back to Rework
  │   └─ Cancel → Jump to Cancel Processing
  ↓
6. [Editorial Review] →
   - Log + Replicate to Preview + Flush Cache
   - Send Notifications → Participant (Editorial Reviewer)
  ├─→ OR Split (2 Branches):
  │   ├─ Approve → Log
  │   └─ Cancel → Jump to Cancel Processing
  ↓
7. [Final Production Review] →
   - Log + Replicate to Preview + Flush Cache
   - Send Notifications → Dynamic Participant (Web Ops)
   - Delayed Release Date Handling
  ├─→ OR Split (3 Branches):
  │   ├─ Approve → Place Delayed Release Date → Check Delayed Release Date
  │   ├─ Cancel → Jump to Cancel Processing
  │   └─ Translation Path → Send Email → Dynamic Participant (Translation Waiting)
  │       ├─ Translation Done → Log
  │       └─ Cancel Translation → Jump to Cancel
  ↓
8. [Production Publication] →
   - Check Delayed Release Date
   - Report Delayed Release Date as Comment
   - Send Email (Pending Publication)
   - Dynamic Participant (Wait for Scheduled Time)
  ├─→ OR Split (2 Branches):
  │   ├─ Force Deploy → Log
  │   └─ Cancel → Jump to Cancel Processing
  ↓
9. [Production Deployment] →
   - Log Production Run Start
   - Replicate to Preview + Flush Cache
   - Check Delayed Release Date
   - Report Delayed Release Date as Comment
   - Replicate to Production + Flush Cache
   - Send Finished Publication Email
  ↓
10. [Jump to Skip Cancel Processing]
  ↓
11. [Cancel Workflow Processing] → Log + Send Cancellation Email
  ↓
12. [Log End] → END
```

### 1.3 Custom Workflow Handlers (AEM 6.4)

| Handler | Purpose | Type | Complexity |
|---------|---------|------|------------|
| **LogWorkflowHandler** | Logs messages with placeholder substitution ({payload}, {meta}, {comment}, {wf-data-meta}) | Process Handler | Medium |
| **PlaceParametersHandler** | Places typed parameters on workflow metadata (BOOLEAN, LONG, INTEGER, DOUBLE, DATE, STRING) | Process Handler | Medium |
| **JumpToNodeHandler** | Unconditional jump to target workflow node with transition creation | Process Handler | High |
| **JumpToNodeIfSetHandler** | Conditional jump based on metadata property existence | Process Handler | Medium |
| **DialogParameterHandler** | Extracts dialog parameters (participant, dates) and places them on workflow | Process Handler | Medium |
| **CheckDelayedReleaseDateHandler** | Validates/sets delayed release date with time offset parsing (1d, 5h, 7min, 55s format) | Process Handler | High |
| **SetDelayedReleaseAsCommentHandler** | Places delayed release date into workflow comment for visibility | Process Handler | Low |
| **ReplicateToAgentHandler** | Replicates content to specified agents with collection support & translation workflow handling | Process Handler | High |
| **EmailNotificationsHandler** | Sends templated emails with complex participant extraction, translation workflow support, and CloudWords integration | Process Handler | Very High |

### 1.4 Workflow Participants & Roles

```
Dynamic Participants (Custom Choosers):
├─ Initiator Participant Chooser (Author - Rework)
├─ Properties Participant Chooser (Web Ops Selection, Translation, Publication)
└─ CloudWords Manager Integration (Translation Workflow)

Static Participants (Groups):
├─ lt-wf-design-reviewer
├─ lt-wf-editorial-reviewer
├─ lt-wf-major-notification
└─ lt-wf-rejection-notification
```

### 1.5 Current Technology Stack

- **Workflow Engine:** CQ Workflow (AEM 6.4)
- **Handler Framework:** AbstractResourceWorkflowProcess
- **OSGi Component Model:** Felix SCR Annotations (deprecated)
- **Email Service:** Custom EmailService with NotificationTemplate
- **Replication:** CQ Replication API (Day Replication)
- **Participant Choosers:** ParticipantStepChooser interface implementations
- **User Management:** Apache Jackrabbit API (UserManager)
- **Repository:** JCR 2.0
- **Template Engine:** Email notification templates in /etc/workflow/notification/email/

---

## SECTION 2: AEM AS A CLOUD SERVICE CONSTRAINTS & CAPABILITIES

### 2.1 Key Architectural Differences

| Feature | AEM 6.4 | AEMaaCS | Impact |
|---------|---------|--------|--------|
| **Workflow Engine** | CQ Workflow (Built-in) | Adobe Workflow (Cloud-native) | Handler migration required |
| **OSGi** | OSGi R4/R5 (Felix) | OSGi R6+ (Declarative Services) | Annotation updates needed |
| **Replication** | CQ Replication Agents | Cloud Manager + AEMaaCS Replication | Must use Cloud Manager APIs |
| **JCR Direct Access** | Allowed via Session | Restricted (Resource API preferred) | Code refactoring needed |
| **Email Service** | Day MailService (SMTP) | SendGrid/Cloud Services | Custom integration needed |
| **Scheduled Tasks** | CQ Scheduler (In-process) | External Schedulers (Cloud-native) | Use Adobe I/O or external service |
| **Deployment** | Manual/OSGi Console | CI/CD Pipeline (Cloud Manager) | Bundle deployment only |
| **Custom Code Location** | /apps/ | /apps/ (Same) | Package structure maintained |
| **Service Users** | Technical Accounts | Service Credentials | Authentication method change |

### 2.2 Key Limitations in AEMaaCS

1. **No Direct SMTP Access** - Cannot use CQ MailService for email
2. **No Direct Replication Agent Configuration** - Use Publishing Service APIs
3. **No Scheduled Jobs via CQ Scheduler** - Use Adobe I/O Events or external services
4. **Immutable Production Environment** - Cannot modify agents at runtime
5. **No Session-based Authentication** - Must use Service Credentials
6. **Limited File System Access** - No direct /etc/ modifications at runtime
7. **No Direct Solr Admin** - Use AEMaaCS APIs only
8. **Workflow Instance Limits** - Different throttling policies

---

## SECTION 3: DETAILED HANDLER MIGRATION STRATEGY

### 3.1 LogWorkflowHandler Migration

**Current (AEM 6.4):**
```java
@Component
@Service
@Properties({
    @Property(name = SERVICE_DESCRIPTION, value = "Log the Workflow Process."),
    @Property(name = SERVICE_VENDOR, value = "Lifetech"),
    @Property(name = "process.label", value = "Lifetech: Log Workflow")
})
public class LogWorkflowHandler extends AbstractResourceWorkflowProcess {
    void execute0(Resource pResource, WorkflowContext pWorkflowContext) throws WorkflowException {
        String lMessage = pWorkflowContext.getModelArguments().get("message");
        String lParsedMessage = parsePlaceholders(lMessage, pWorkflowContext);
        logMessage(pWorkflowContext, lParsedMessage);
    }
}
```

**Migration Path:** ✅ COMPATIBLE (Minimal Changes)

**Changes Required:**
1. **Update OSGi Annotations** - Felix SCR → Declarative Services
2. **Keep Core Logic** - Placeholder parsing is still valid
3. **Logging Framework** - Continue using SLF4J (no change needed)

**Refactored Code (AEMaaCS):**
```java
@Component(service = WorkflowProcess.class)
@Designate(ocd = LogWorkflowHandlerConfig.class)
public class LogWorkflowHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(LogWorkflowHandler.class);
    
    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, 
                        MetaDataMap metaDataMap) throws WorkflowException {
        String message = metaDataMap.get("message", String.class);
        String parsedMessage = parsePlaceholders(message, workItem, workflowSession);
        LOG.info("Workflow: {}", parsedMessage);
    }
    
    private String parsePlaceholders(String message, WorkItem workItem, 
                                     WorkflowSession session) throws WorkflowException {
        // Same logic but updated to use new API
        // {payload} → workItem.getWorkflowData().getPayload()
        // {meta} → workItem.getWorkflowData().getMetaDataMap()
        // {wf-data-meta} → workItem.getWorkflow().getMetaDataMap()
    }
}

@ObjectClassDefinition(name = "Lifetech Log Workflow Handler Config")
public @interface LogWorkflowHandlerConfig {
    @AttributeDefinition(name = "Process Label")
    String processLabel() default "Lifetech: Log Workflow";
}
```

---

### 3.2 PlaceParametersHandler Migration

**Current Status:** ✅ COMPATIBLE (Minimal Changes)

**Changes:**
- OSGi annotations update only
- MetaDataMap API remains the same
- Type conversion logic unchanged

**Migration Effort:** LOW (1-2 hours)

---

### 3.3 JumpToNodeHandler Migration

**Current Status:** ⚠️ REQUIRES REFACTORING (API Changes)

**Issues in AEMaaCS:**
- `WorkflowTransition` creation method deprecated
- `SimpleRoute` class may not exist in cloud version
- Back route logic needs reimplementation

**Migration Path:**
```java
// OLD (AEM 6.4)
lWorkflowTransition = lModel.createTransition(lCurrentNode, lTargetNode, null);
lWorkflowRoute = new SimpleRoute(lWorkflowTransition, false);
lSession.complete(lItem, lWorkflowRoute);

// NEW (AEMaaCS)
// Use WorkflowSession.complete() with route parameters
// Route creation is handled by the session
lSession.complete(lItem, nextStepId, metaDataMap);
```

**Migration Effort:** MEDIUM (4-6 hours)

---

### 3.4 JumpToNodeIfSetHandler Migration

**Current Status:** ⚠️ REQUIRES REFACTORING

**Changes:**
- Check if property is set in metadata
- Route accordingly
- Use WorkflowSession.complete() API

**Migration Effort:** MEDIUM (3-4 hours)

---

### 3.5 DialogParameterHandler Migration

**Current Status:** ✅ COMPATIBLE (Minimal Changes)

**Purpose:** Extract dialog parameters and place on workflow metadata

**Changes:**
- OSGi annotations update
- API method names may differ slightly
- History offset mechanism needs verification

**Migration Effort:** LOW (2-3 hours)

---

### 3.6 CheckDelayedReleaseDateHandler Migration

**Current Status:** ✅ COMPATIBLE (Medium Changes)

**Special Logic:** Time offset parsing (1d, 5h, 7min, 55s, 100ms)

**Changes:**
- OSGi annotations update
- Keep parseTime() logic unchanged
- Absolute time storage in metadata works same way

**Migration Effort:** LOW (1-2 hours)

---

### 3.7 SetDelayedReleaseAsCommentHandler Migration

**Current Status:** ✅ COMPATIBLE (Minimal Changes)

**Changes:** OSGi annotations only

**Migration Effort:** MINIMAL (30 minutes)

---

### 3.8 ReplicateToAgentHandler Migration - 🔴 **CRITICAL**

**Current Status:** ❌ NOT COMPATIBLE (Complete Rewrite Required)

**Problems in AEMaaCS:**
1. **Agent-Based Replication Removed** - No more agent configuration
2. **Replicator API Deprecated** - Use Publishing Service instead
3. **ResourceCollectionManager** - Deprecated in cloud
4. **Session-based Replication** - Not allowed
5. **Dispatcher Cache Flush** - Use CloudFlare/CDN APIs

**Current Implementation:**
```java
Replicator mReplicator.replicate(session, replicationActionType, path);
```

**AEMaaCS Replacement Strategy:**

**Option 1: Publishing Service API (Recommended)**
```java
// Use AEMaaCS Publishing Service for content activation
@Reference
private PublishingService publishingService;

public void replicate(WorkItem workItem, String path) throws WorkflowException {
    try {
        // AEMaaCS handles replication internally
        publishingService.publish(path);
    } catch (Exception e) {
        throw new WorkflowException("Publishing failed", e);
    }
}
```

**Option 2: Adobe I/O Events (For external systems)**
```java
@Reference
private EventService eventService;

public void replicate(String path) throws WorkflowException {
    CloudEvent event = CloudEvent.builder()
        .type("aem/asset/activated")
        .data(new ActivationData(path))
        .build();
    eventService.publish(event);
}
```

**Cache Flush Strategy:**
- ❌ No more "flush-preview?" or "flush-production?" agents
- ✅ Use CloudManager API or external CDN (Fastly, CloudFlare)
- ✅ Configure CDN cache headers in AEMaaCS (recommended)

**Migration Effort:** HIGH (12-16 hours)

---

### 3.9 EmailNotificationsHandler Migration - 🔴 **CRITICAL**

**Current Status:** ❌ NOT COMPATIBLE (Major Rewrite)

**Problems:**
1. **MailService Removed** - Cannot use Day CQ MailService
2. **Direct SMTP Access Blocked** - AEMaaCS doesn't allow
3. **Session-based User Lookup** - Restricted
4. **CloudWords Integration** - Needs verification

**Current Implementation Issues:**
```java
@Reference
private MailService mMailService;  // ❌ REMOVED in AEMaaCS

public void sendNotification() {
    mMailService.send(...);  // ❌ Will fail
}
```

**Migration Path - Option 1: SendGrid Integration (Recommended)**

```java
@Component(service = WorkflowProcess.class)
public class EmailNotificationsHandler implements WorkflowProcess {
    
    @Reference
    private SendGridEmailService sendGridService;  // Custom service
    
    @Reference
    private ResourceResolverFactory resolverFactory;
    
    private static final String SERVICE_USER = "workflow-email-service";
    
    @Override
    public void execute(WorkItem workItem, WorkFlowSession session, 
                        MetaDataMap metaDataMap) throws WorkflowException {
        try {
            ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, SERVICE_USER)
            );
            
            String templatePath = metaDataMap.get("alertMessageTemplatePath", String.class);
            String[] recipients = parseRecipients(workItem, resolver);
            
            for (String recipient : recipients) {
                EmailMessage email = buildEmail(workItem, templatePath, recipient, resolver);
                sendGridService.sendAsync(email);
            }
        } catch (LoginException e) {
            throw new WorkflowException("Email service unavailable", e);
        }
    }
}
```

**Migration Path - Option 2: Adobe Experience Manager Cloud Services**

```java
// Use AEMaaCS Email Cloud Service
@Component(service = WorkflowProcess.class)
public class EmailNotificationsHandler implements WorkflowProcess {
    
    @Reference
    private CloudServiceEmailProvider emailProvider;
    
    @Override
    public void execute(WorkItem workItem, WorkFlowSession session, 
                        MetaDataMap metaDataMap) throws WorkflowException {
        EmailTemplate template = emailProvider.getTemplate(
            metaDataMap.get("alertMessageTemplatePath", String.class)
        );
        
        Set<String> recipients = extractRecipients(workItem, session);
        template.send(recipients, buildContext(workItem));
    }
}
```

**Key Changes:**
1. Implement custom SendGrid service
2. Use Service Users for authentication
3. Move email templates to /conf/ (OSGi configuration)
4. Implement async email sending
5. Add retry logic and error handling
6. Remove direct user/group lookups (use User API only)

**Migration Effort:** VERY HIGH (20-24 hours)

---

## SECTION 4: WORKFLOW STEP DEFINITIONS - CURRENT VS FUTURE

### 4.1 Step Type Comparison

| Step Component | AEM 6.4 | AEMaaCS | Migration Path |
|---|---|---|---|
| **Process Handler** | `cq:WorkflowProcess` | `cq:WorkflowProcess` (compatible) | ✅ Update code only |
| **Participant Step** | `cq:ParticipantStep` | `cq:ParticipantStep` (compatible) | ✅ No change |
| **Dynamic Participant** | `cq:DynamicParticipant` | `cq:DynamicParticipant` (compatible) | ✅ Update handler code |
| **OR Split** | `cq:ORSplit` | `cq:ORSplit` (compatible) | ✅ No change |
| **Dialog Component** | `/apps/lifetech/components/workflowDialog/*` | `/apps/lifetech/components/workflowDialog/*` | ✅ No change |
| **Step UI** | Classic UI (XML-based) | Touch UI (Granite UI) | ⚠️ May need update |

### 4.2 Current Workflow Steps (from Major Review Workflow.json)

```
1. Log Start Workflow
   - Type: Process Handler
   - Handler: LogWorkflowHandler
   - Status: ✅ COMPATIBLE

2. Set Parameters for Rework Task
   - Type: Process Handler
   - Handler: PlaceParametersHandler
   - Status: ✅ COMPATIBLE

3. Select Web Operations Team
   - Type: Dialog Participant
   - Status: ✅ COMPATIBLE

4. Places Web Ops Team on Workflow
   - Type: Process Handler
   - Handler: DialogParameterHandler
   - Status: ✅ COMPATIBLE

5. In First Loop Ignore Rework Tasks
   - Type: Process Handler
   - Handler: JumpToNodeIfSetHandler
   - Status: ⚠️ MEDIUM EFFORT

6. Rework Content
   - Type: Dynamic Participant
   - Chooser: Initiator Participant
   - Status: ✅ COMPATIBLE

7. Done/Cancel Decision (OR Split - 2 branches)
   - Type: OR Split
   - Status: ✅ COMPATIBLE

8. Design/UX Review Setup
   - Type: Process Handler + Participant
   - Handlers: Log, Replicate, Email, Participant
   - Status: ⚠️ HIGH EFFORT (Replication + Email)

9. Design/UX Decision (OR Split - 3 branches)
   - Type: OR Split
   - Status: ✅ COMPATIBLE

10. Editorial Review Setup
    - Similar to Design/UX
    - Status: ⚠️ HIGH EFFORT

11. Editorial Decision (OR Split - 2 branches)
    - Status: ✅ COMPATIBLE

12. Final Production Review Setup
    - Complex: Delayed Release + Dynamic Participant
    - Status: ⚠️ HIGH EFFORT

13. Final Production Decision (OR Split - 3 branches)
    - Status: ✅ COMPATIBLE

14. Translation Path (Optional)
    - Complex: CloudWords Integration
    - Status: ⚠️ REQUIRES VERIFICATION

15-19. Production Publication Setup
    - Check Delayed Release Date
    - Report as Comment
    - Replicate to Production
    - Status: ⚠️ HIGH EFFORT

20. End Workflow
    - Log
    - Status: ✅ COMPATIBLE
```

---

## SECTION 5: REMOVED FUNCTIONALITY & WORKAROUNDS

### 5.1 Features Being Removed

| Feature | AEM 6.4 | AEMaaCS | Reason | Workaround |
|---------|---------|--------|--------|-----------|
| **Agent-Based Replication** | Built-in | Removed | Cloud-native architecture | Use Publishing Service API |
| **CQ MailService** | Built-in SMTP | Removed | No direct SMTP access | Use SendGrid/Cloud Services |
| **Dispatcher Cache Flush Agents** | Supported | Removed | CDN managed externally | Configure CDN headers/APIs |
| **CQ Scheduler (Direct)** | In-process scheduling | Removed | Moved to cloud services | Use Adobe I/O Events or external scheduler |
| **Direct JCR Session** | Full access | Restricted | Security & multi-tenancy | Use ResourceResolver API |
| **Bulk Activation Macro** | Supported | Not applicable | N/A | Use Publishing Service API in batches |
| **Runtime Agent Configuration** | Possible | Immutable | Production stability | Deploy via Cloud Manager only |
| **Felix SCR Annotations** | Primary method | Deprecated | OSGi R6+ standard | Use Declarative Services annotations |

### 5.2 Replication Removal Workaround

**Current (AEM 6.4):**
```java
public class ReplicateToAgentHandler {
    @Reference private Replicator mReplicator;
    
    public void replicate() {
        mReplicator.replicate(session, ReplicationActionType.ACTIVATE, path);
    }
}
```

**Workaround (AEMaaCS):**
```java
// Strategy 1: Use PublishingService (if available)
@Reference private PublishingService publishingService;

public void publish(String path) {
    publishingService.publish(path);
}

// Strategy 2: Use Resource API with Observation
@Reference private ResourceResolverFactory factory;

public void publish(String path) throws LoginException {
    ResourceResolver resolver = factory.getServiceResourceResolver(...);
    Resource resource = resolver.getResource(path);
    // Mark for publication
    resource.adaptTo(PublishableResource.class).publish();
}

// Strategy 3: Emit Adobe I/O Event (recommended for complex workflows)
@Reference private EventService eventService;

public void publish(String path) {
    Map<String, Object> eventData = Map.of(
        "path", path,
        "action", "activate",
        "timestamp", System.currentTimeMillis()
    );
    eventService.publishEvent("custom/content/activated", eventData);
}
```

### 5.3 Email Removal Workaround

**Current (AEM 6.4):**
```java
@Reference private MailService mMailService;

public void sendEmail(String to, String subject, String body) {
    HtmlEmail email = new HtmlEmail();
    email.addTo(to);
    email.setSubject(subject);
    email.setHtmlMsg(body);
    mMailService.send(email);
}
```

**Workaround (AEMaaCS) - SendGrid:**
```java
@Component
public class SendGridEmailService {
    
    private String sendGridApiKey;
    
    @Activate
    protected void activate(SendGridConfig config) {
        sendGridApiKey = config.apiKey();
    }
    
    public void sendEmail(String to, String subject, String body) 
            throws SendGridException {
        Email from = new Email("noreply@company.com", "Company");
        Email toEmail = new Email(to);
        Content content = new Content("text/html", body);
        Mail mail = new Mail(from, subject, toEmail, content);
        
        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);
        
        if (response.getStatusCode() != 202) {
            throw new SendGridException("Email send failed: " + response.getStatusCode());
        }
    }
}
```

---

## SECTION 6: DETAILED MIGRATION ROADMAP

### 6.1 Phase 1: Assessment & Setup (Week 1)

**Tasks:**
- [ ] Set up AEMaaCS development environment
- [ ] Create Cloud Manager pipeline
- [ ] Prepare Maven project structure
- [ ] Review OSGi R6+ standards
- [ ] Document all handler dependencies

**Deliverables:**
- AEMaaCS dev environment ready
- Maven multi-module structure
- OSGi compliance checklist

### 6.2 Phase 2: Low-Effort Handlers (Week 1-2)

**Handlers to Migrate:**
1. **LogWorkflowHandler** (1-2 hours)
   - Update annotations to @Component/@Designate
   - Keep logic unchanged
   - Update logging calls

2. **PlaceParametersHandler** (1-2 hours)
   - Update annotations
   - Test type conversion
   - Verify DATE handling

3. **SetDelayedReleaseAsCommentHandler** (30 min)
   - Annotation update only

4. **CheckDelayedReleaseDateHandler** (1-2 hours)
   - Annotation updates
   - Verify time parsing logic
   - Test offset calculations

**Testing:**
- Unit tests for each handler
- Integration tests with workflow engine
- Parameter parsing verification

### 6.3 Phase 3: Medium-Effort Handlers (Week 2-3)

**Handlers to Migrate:**
1. **DialogParameterHandler** (2-3 hours)
   - Annotation updates
   - Verify dialog integration
   - Test parameter extraction

2. **JumpToNodeHandler** (4-6 hours)
   - Research AEMaaCS route API
   - Refactor transition logic
   - Implement new routing mechanism

3. **JumpToNodeIfSetHandler** (3-4 hours)
   - Conditional logic update
   - Route creation refactoring
   - Testing edge cases

**Testing:**
- Integration tests with OR splits
- Back route verification
- Route creation validation

### 6.4 Phase 4: High-Effort Handlers (Week 3-4)

**1. ReplicateToAgentHandler (12-16 hours)**

**Steps:**
```
a. Research AEMaaCS Publishing APIs (2 hours)
   - Review PublishingService documentation
   - Understand CDN integration
   - Plan fallback strategies

b. Create Publishing Service Wrapper (4 hours)
   - Implement AbstractPublishingService
   - Handle error scenarios
   - Add retry logic

c. Replace Replicator Calls (4 hours)
   - Remove Replicator dependency
   - Implement publishing logic
   - Handle asset vs page differences

d. Implement Cache Invalidation (3-4 hours)
   - Remove agent-based flush
   - Implement CDN API calls
   - Add purge header logic

e. Test & Validate (2-3 hours)
   - Unit tests
   - Integration tests
   - Performance validation
```

**2. EmailNotificationsHandler (20-24 hours)**

**Steps:**
```
a. Setup SendGrid Integration (3-4 hours)
   - Create SendGridEmailService
   - Configure API credentials
   - Build email factories

b. Refactor Recipient Extraction (3-4 hours)
   - Use User API safely
   - Handle group expansion
   - Add permission checks

c. Template Migration (4-5 hours)
   - Move templates to /conf/
   - Implement template resolver
   - Create template rendering service

d. Participant Chooser Updates (3-4 hours)
   - Refactor PropertiesParticipantChooser
   - Handle dynamic participants
   - Add caching for performance

e. CloudWords Integration (2-3 hours)
   - Verify CloudWords API in cloud
   - Update translation workflow paths
   - Test translation scenarios

f. Testing (3-4 hours)
   - Unit tests for email building
   - Integration tests for sending
   - Template rendering tests
   - Recipient extraction tests
```

### 6.5 Phase 5: Workflow Step Migration (Week 4-5)

**Tasks:**
- [ ] Update all step definitions to AEMaaCS format
- [ ] Create Granite UI dialogs (replace Classic UI if needed)
- [ ] Update step component configurations
- [ ] Test step instantiation
- [ ] Verify OR split logic

### 6.6 Phase 6: Integration Testing (Week 5)

**Test Scenarios:**
1. Workflow initiation
2. Rework loop flow
3. Approval paths
4. Rejection paths
5. Cancellation flow
6. Translation workflow (if integrated)
7. Delayed release scheduling
8. Email notifications
9. Publication flow
10. Error handling & recovery

### 6.7 Phase 7: Documentation & Deployment (Week 6)

**Deliverables:**
- [ ] Updated workflow documentation
- [ ] Admin operational guide
- [ ] Troubleshooting guide
- [ ] API documentation for new handlers
- [ ] Cloud Manager pipeline configuration

---

## SECTION 7: CUSTOM STEP DEFINITIONS - CODE CHANGES

### 7.1 LogWorkflowHandlerStep Component Update

**Current (/apps/lifetech/components/workflowStep/logWorkflowHandlerStep/.content.xml):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" 
          xmlns:cq="http://www.day.com/jcr/cq/1.0" 
          xmlns:jcr="http://www.jcp.org/jcr/1.0"
    jcr:primaryType="cq:Component"
    jcr:title="Log Workflow"
    sling:resourceSuperType="cq/workflow/components/model/process"
    componentGroup="Workflow"/>
```

**Action:** ✅ No change needed - remains compatible

---

### 7.2 New Service Users Required

**Create service user in AEMaaCS:**

**File:** `/apps/lifetech/config.author/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-workflow.cfg.json`

```json
{
  "user.mapping": [
    "com.lifetechnologies.services.workflow:workflow-email-service=workflow-email-service",
    "com.lifetechnologies.services.workflow:workflow-publishing-service=workflow-publishing-service",
    "com.lifetechnologies.services.workflow:workflow-user-service=workflow-user-service"
  ]
}
```

**Create ACLs:**

**File:** `/apps/lifetech/config/cq:Hooks/acl/workflow-email-service.yaml`

```yaml
- path: "/content"
  principal: "workflow-email-service"
  privileges:
    - "jcr:read"
    - "jcr:write"

- path: "/home/users/system/lifetech"
  principal: "workflow-email-service"
  privileges:
    - "jcr:read"
```

---

### 7.3 OSGi Configuration for Cloud Services

**File:** `/apps/lifetech/config.author/com.sendgrid.EmailServiceImpl.cfg.json`

```json
{
  "sendgrid.api.key": "$[secret:sendgrid_api_key]",
  "sendgrid.from.email": "noreply@company.com",
  "sendgrid.from.name": "Content Workflow",
  "sendgrid.connection.timeout": 30000,
  "sendgrid.max.retries": 3
}
```

---

### 7.4 Email Template Migration

**Current Location:** `/etc/workflow/notification/email/lifetech/major/review/*/en.txt`

**New Location:** `/conf/lifetech/settings/workflow/email/templates/*`

**Template Example:**

**File:** `/conf/lifetech/settings/workflow/email/templates/design-review.txt`

```
Subject: Design Review Request for {payload.path}

Dear {participant.name},

The following content requires Design/UX review:
Path: {payload.path}
Title: {item.node.title}
Initiated by: {initiator.name}

Please review and approve at: {host.prefix}/editor.html{payload.path}

Review Status: {instance.state}

Best regards,
Content Workflow System
```

---

## SECTION 8: IMPLEMENTATION CHECKLIST

### 8.1 Code Changes Summary

#### NEW COMPONENTS TO CREATE:

**1. SendGridEmailService** (New)
```
Location: /apps/lifetech/core/src/main/java/com/lifetechnologies/services/email/impl/SendGridEmailServiceImpl.java
Purpose: Replace MailService
Effort: 4-6 hours
Dependencies: SendGrid SDK
```

**2. AEMaaCS PublishingServiceWrapper** (New)
```
Location: /apps/lifetech/core/src/main/java/com/lifetechnologies/services/publishing/impl/PublishingServiceImpl.java
Purpose: Replace Replicator
Effort: 3-4 hours
Dependencies: AEMaaCS Publishing APIs
```

**3. EmailTemplateResolver** (New)
```
Location: /apps/lifetech/core/src/main/java/com/lifetechnologies/services/email/impl/EmailTemplateResolverImpl.java
Purpose: Load templates from /conf/ instead of /etc/
Effort: 2-3 hours
Dependencies: ResourceResolver
```

**4. CloudWordsAdapterService** (New)
```
Location: /apps/lifetech/core/src/main/java/com/lifetechnologies/services/translation/CloudWordsAdapterService.java
Purpose: Adapt existing CloudWords integration for cloud
Effort: 2-3 hours
Dependencies: Existing CloudWords Manager
```

#### MODIFIED COMPONENTS:

**1. LogWorkflowHandler**
```
Changes: Annotation updates only
Lines affected: 8-10, 29-36
Effort: 30 minutes
```

**2. PlaceParametersHandler**
```
Changes: Annotation updates + minor API calls
Lines affected: 8-10, 28-35
Effort: 1 hour
```

**3. JumpToNodeHandler**
```
Changes: Route creation logic rewrite
Lines affected: 39-77 (entire execute method)
Effort: 4-6 hours
Risk: High - core workflow routing
```

**4. ReplicateToAgentHandler**
```
Changes: Complete handler rewrite
Lines affected: All (entire class)
Effort: 12-16 hours
Risk: High - critical workflow step
```

**5. EmailNotificationsHandler**
```
Changes: Complete handler rewrite
Lines affected: All (entire class 600 lines)
Effort: 20-24 hours
Risk: Critical - email functionality
```

**6. CheckDelayedReleaseDateHandler**
```
Changes: Minor annotation updates
Lines affected: 8-10, 22-36
Effort: 1 hour
```

**7. DialogParameterHandler**
```
Changes: Annotation updates + method signature
Lines affected: 8-10, 25-32
Effort: 1-2 hours
```

**8. JumpToNodeIfSetHandler**
```
Changes: Route creation logic update
Lines affected: 39-100 (execute method)
Effort: 3-4 hours
Risk: Medium - conditional routing
```

---

## SECTION 9: TESTING STRATEGY

### 9.1 Unit Testing

**Framework:** JUnit 5 + Mockito

**Tests per Handler:**
```
LogWorkflowHandler
├─ testPlaceholderParsing_Payload()
├─ testPlaceholderParsing_Comment()
├─ testPlaceholderParsing_MetaData()
└─ testInvalidPlaceholder_ReturnsFallback()

PlaceParametersHandler
├─ testPlaceStringParameter()
├─ testPlaceBooleanParameter()
├─ testPlaceDateParameter()
├─ testPlaceTypedParameter_WithType()
└─ testPlaceTypedParameter_InvalidType()

JumpToNodeHandler
├─ testJumpToExistingNode()
├─ testJumpWithBackRoute()
├─ testJumpWithNewRoute()
└─ testTargetNodeNotFound_ThrowsException()

ReplicateToAgentHandler
├─ testPublishToManagedService()
├─ testPublishWithResourceCollection()
├─ testTranslationWorkflow_PublishMultiplePaths()
├─ testPublishFailure_ThrowsException()
└─ testCDNPurgeOnPublish()

EmailNotificationsHandler
├─ testBuildEmailFromTemplate()
├─ testRecipientExtraction_Group()
├─ testRecipientExtraction_User()
├─ testEmailSending_Success()
├─ testEmailSending_Retry()
├─ testCloudWordsIntegration()
└─ testEmailTemplateSubstitution()
```

### 9.2 Integration Testing

**Workflow Scenarios:**
```
Scenario 1: Happy Path - Approval Flow
├─ Initiate workflow
├─ Author rework
├─ Design approval
├─ Editorial approval
├─ Production approval
├─ Immediate publish
└─ Verify published

Scenario 2: Rejection & Rework
├─ Initiate workflow
├─ Author rework (1st time)
├─ Design reject → Back to rework
├─ Author rework (2nd time)
├─ Design approve
└─ Continue flow

Scenario 3: Delayed Release
├─ Initiate workflow
├─ All approvals
├─ Set delayed release date
├─ Wait for schedule
└─ Verify publish at scheduled time

Scenario 4: Cancellation
├─ Initiate workflow
├─ Cancel at any step
├─ Verify cancellation email
└─ Verify no publication

Scenario 5: Translation Workflow
├─ Initiate with translation option
├─ Send to translation
├─ Wait for translation
├─ Resume and publish
└─ Verify multiple language paths

Scenario 6: Error Handling
├─ Email service unavailable
├─ Publishing service timeout
├─ User lookup failure
└─ Verify graceful degradation
```

### 9.3 Performance Testing

**Load Test Scenarios:**
- 100 concurrent workflow instances
- Email sending with retry (track throughput)
- Large resource collections (1000+ items)
- Delayed release scheduling accuracy

---

## SECTION 10: MIGRATION EXECUTION PLAN

### 10.1 Pre-Migration Checklist

- [ ] Document current workflow state in AEM 6.4
- [ ] Export current handler configurations
- [ ] Backup all custom code
- [ ] Review AEMaaCS API documentation
- [ ] Set up development/stage AEMaaCS environment
- [ ] Create Maven project structure
- [ ] Set up Cloud Manager pipeline
- [ ] Prepare testing environment

### 10.2 Migration Sequence

```
WEEK 1:
├─ Day 1-2: OSGi annotation updates (all simple handlers)
├─ Day 3-4: Low-effort handler refactoring
└─ Day 5: Unit testing Phase 1

WEEK 2:
├─ Day 1-2: Medium-effort handlers (Jump handlers)
├─ Day 3-4: Dialog & Parameter handlers
└─ Day 5: Integration testing Phase 2

WEEK 3:
├─ Day 1-3: ReplicateToAgentHandler (critical path)
├─ Day 4-5: Setup SendGrid & publishing services
└─ Parallel: Create service users & ACLs

WEEK 4:
├─ Day 1-3: EmailNotificationsHandler (critical path)
├─ Day 4-5: Template migration & testing
└─ Parallel: Testing infrastructure setup

WEEK 5:
├─ Day 1-2: Full workflow integration testing
├─ Day 3: Stress testing & performance tuning
├─ Day 4: Documentation & operational guide
└─ Day 5: Deploy to stage environment

WEEK 6:
├─ Day 1-3: Stage environment validation
├─ Day 4: Final adjustments based on feedback
├─ Day 5: Deploy to production
```

### 10.3 Rollback Plan

1. **Pre-Deployment Backup**
   - Export AEM 6.4 workflow definitions
   - Backup custom code and configurations
   - Document current state

2. **Gradual Rollout**
   - Stage → Dev → QA → Production
   - Shadow run in parallel if possible
   - Monitor error rates

3. **Rollback Procedure**
   - Keep AEM 6.4 system operational during migration
   - If issues found in AEMaaCS, revert to AEM 6.4
   - Deferred migration with fixes

---

## SECTION 11: SPECIFIC CHANGES & CODE SAMPLES

### 11.1 OSGi Annotation Pattern (All Handlers)

**OLD (AEM 6.4 - Felix SCR):**
```java
import org.apache.felix.scr.annotations.*;

@Component
@Service
@Properties({
    @Property(name = SERVICE_DESCRIPTION, value = "..."),
    @Property(name = SERVICE_VENDOR, value = "..."),
    @Property(name = "process.label", value = "...")
})
public class MyHandler extends AbstractResourceWorkflowProcess {
    @Reference
    private SomeService service;
}
```

**NEW (AEMaaCS - Declarative Services):**
```java
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;

@Component(service = WorkflowProcess.class)
@Designate(ocd = MyHandlerConfig.class)
public class MyHandler implements WorkflowProcess {
    @Reference
    private SomeService service;
    
    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, 
                        MetaDataMap metaDataMap) throws WorkflowException {
        // Implementation
    }
}

@ObjectClassDefinition(name = "My Handler Configuration")
public @interface MyHandlerConfig {
    @AttributeDefinition(name = "Service Label")
    String serviceLabel() default "My Handler";
}
```

### 11.2 Route Creation Pattern

**OLD (AEM 6.4):**
```java
WorkflowTransition transition = model.createTransition(fromNode, toNode, null);
Route route = new SimpleRoute(transition, isBackRoute);
session.complete(workItem, route);
```

**NEW (AEMaaCS):**
```java
// Check if this is a valid route
List<Route> possibleRoutes = workflowSession.getRoutes(workItem, true);
Route targetRoute = findRoute(possibleRoutes, targetNodeId);

if (targetRoute != null) {
    workflowSession.complete(workItem, targetRoute);
} else {
    // Create new route if needed
    MetaDataMap routeMetadata = new MetaDataMap();
    workflowSession.complete(workItem, targetNodeId, routeMetadata);
}
```

### 11.3 Service User Configuration

**Create service user mapping:**

```
File: /apps/lifetech/config.author/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-email.cfg.json

{
  "user.mapping": [
    "com.lifetechnologies.services.workflow.handler:sendgrid-email=sendgrid-email-service"
  ]
}
```

**Create service user ACL:**

```
File: /apps/lifetech/config/cq:Hooks/acl/sendgrid-email-service.yaml

- path: "/content"
  principal: "sendgrid-email-service"
  privileges:
    - "jcr:read"
    - "jcr:write"

- path: "/etc/workflow"
  principal: "sendgrid-email-service"
  privileges:
    - "jcr:read"

- path: "/etc/designs"
  principal: "sendgrid-email-service"
  privileges:
    - "jcr:read"
```

---

## SECTION 12: KEY BUSINESS BEHAVIORS - MIGRATION MAPPING

### 12.1 Behavior Preservation Matrix

| Behavior | AEM 6.4 Implementation | AEMaaCS Implementation | Status |
|----------|----------------------|----------------------|--------|
| **Rework Loop** | JumpToNodeHandler (back route) | Route-based navigation | ✅ PRESERVED |
| **Conditional Skip** | JumpToNodeIfSetHandler (metadata check) | Metadata check → routing | ✅ PRESERVED |
| **Participant Assignment** | Dynamic Participant Choosers | Same API with updated service binding | ✅ PRESERVED |
| **Email Notifications** | CQ MailService | SendGrid/Cloud Service | ✅ PRESERVED (different provider) |
| **Content Publishing** | Agent-based replication | Publishing Service API | ✅ PRESERVED (different API) |
| **Delayed Release** | AbsoluteTimeAutoAdvancer + time parsing | Time parsing + scheduled task | ⚠️ REQUIRES EXTERNAL SCHEDULER |
| **OR Split Routing** | Native workflow OR nodes | Same | ✅ PRESERVED |
| **Metadata Persistence** | WorkflowData.getMetaDataMap() | Same API | ✅ PRESERVED |
| **Comment History** | WorkflowSession.getHistory() | Same API | ✅ PRESERVED |
| **Approval/Rejection** | Step participant actions | Same | ✅ PRESERVED |

### 12.2 Delayed Release Scheduling - Required Change

**Current (AEM 6.4):**
```java
// Built-in to workflow engine
timeoutHandler="com.day.cq.workflow.timeout.autoadvance.AbsoluteTimeAutoAdvancer"

// Automatically triggers when time reached
```

**AEMaaCS Approach:**

**Option 1: Adobe I/O Events (Recommended)**
```java
@Component(service = WorkflowProcess.class)
public class ScheduledPublicationHandler implements WorkflowProcess {
    
    @Reference
    private EventService eventService;
    
    @Override
    public void execute(WorkItem workItem, WorkflowSession session, 
                        MetaDataMap args) throws WorkflowException {
        Long publishTime = args.get("absoluteTime", Long.class);
        
        // Emit scheduled event
        Map<String, Object> eventData = Map.of(
            "workflowId", workItem.getWorkflow().getId(),
            "itemId", workItem.getId(),
            "publishTime", publishTime,
            "action", "schedulePublication"
        );
        
        eventService.publishEvent("custom/workflow/scheduled", eventData);
    }
}

// External scheduler or Adobe I/O Action triggers publication at time
```

**Option 2: External Scheduler (Cron-based)**
```java
@Component(service = Runnable.class)
@Designate(ocd = PublicationSchedulerConfig.class)
public class PublicationScheduler implements Runnable {
    
    @Reference
    private WorkflowSession workflowSession;
    
    @Override
    public void run() {
        // Query for workflows scheduled to publish now
        // Trigger publication action
    }
}

// Registered as scheduled task in OSGi
```

---

## SECTION 13: RECOMMENDED SOLUTIONS & BEST PRACTICES

### 13.1 Architecture Decisions

**Decision 1: Email Service Provider**

| Option | Pros | Cons | Recommendation |
|--------|------|------|---|
| **SendGrid** | Industry standard, reliable, cost-effective | External dependency | ✅ **RECOMMENDED** |
| **Adobe I/O Events** | Native to AEMaaCS | Requires custom action development | Alternative |
| **AWS SES** | Low cost at scale | Additional AWS integration | Consider if AWS-based |

**Decision:** Use SendGrid as primary, Adobe I/O Events for complex scenarios

---

**Decision 2: Content Publishing**

| Option | Pros | Cons | Recommendation |
|--------|------|------|---|
| **Publishing Service API** | Cloud-native, built-in support | Limited flexibility | ✅ **RECOMMENDED** |
| **Adobe I/O Events** | Event-driven, external integration | Loose coupling overhead | For external systems |
| **REST API** | Standard, well-documented | Requires custom implementation | Fallback |

**Decision:** Use Publishing Service API, emit I/O Events for external systems

---

**Decision 3: Scheduled Tasks**

| Option | Pros | Cons | Recommendation |
|--------|------|------|---|
| **Adobe I/O Events** | Cloud-native, scalable | Requires external trigger | ✅ **RECOMMENDED** |
| **External Scheduler** (e.g., CloudScheduler) | Full control, standard | Operational overhead | For complex schedules |
| **CronJob on K8s** | Container-native | Requires infrastructure | If Kubernetes available |

**Decision:** Use Adobe I/O Events for initial implementation, plan for external scheduler upgrade

---

### 13.2 Performance Optimization

**Email Batch Processing:**
```java
// Don't send emails one-by-one
// Batch multiple notifications

@Component(service = WorkflowProcess.class)
public class BatchEmailNotificationHandler implements WorkflowProcess {
    
    public void execute(WorkItem workItem, WorkflowSession session, 
                        MetaDataMap args) throws WorkflowException {
        // Collect all recipients first
        Set<String> recipients = extractRecipients(workItem, session);
        
        // Send batch via SendGrid
        sendgridService.sendBatch(recipients, emailTemplate, context);
    }
}
```

**Participant Caching:**
```java
@Component(service = WorkflowProcess.class)
public class CachedParticipantChooser implements ParticipantStepChooser {
    
    @Reference
    private Cache cache;
    
    @Override
    public String getParticipant(WorkItem workItem, WorkflowSession session,
                                MetaDataMap args) throws WorkflowException {
        String key = cacheKey(workItem, args);
        
        return cache.get(key, () -> {
            // Expensive operation - group expansion, user lookup
            return resolveParticipant(workItem, session, args);
        });
    }
}
```

---

### 13.3 Error Handling & Resilience

**Retry Strategy:**
```java
public class ResilientEmailService {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    public void sendEmailWithRetry(EmailMessage email) throws EmailException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendGridService.send(email);
                return;
            } catch (SendGridException e) {
                if (attempt < MAX_RETRIES) {
                    LOG.warn("Email send failed (attempt {}), retrying in {}ms", 
                             attempt, RETRY_DELAY_MS);
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                } else {
                    throw new EmailException("Failed after " + MAX_RETRIES + " retries", e);
                }
            }
        }
    }
}
```

**Circuit Breaker:**
```java
public class CircuitBreakerEmailService {
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(
        maxFailures = 5,
        resetTimeout = Duration.ofMinutes(5)
    );
    
    public void send(EmailMessage email) throws EmailException {
        if (circuitBreaker.isOpen()) {
            throw new EmailException("Service temporarily unavailable");
        }
        
        try {
            sendGridService.send(email);
            circuitBreaker.recordSuccess();
        } catch (SendGridException e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }
}
```

---

## SECTION 14: DELIVERABLES & DOCUMENTATION

### 14.1 Code Deliverables

```
lifetech-workflow-acs/
├── README.md (Migration guide)
├── pom.xml (Maven configuration)
├── core/
│   ├── src/main/java/com/lifetechnologies/services/workflow/
│   │   ├── handler/
│   │   │   ├── LogWorkflowHandler.java (UPDATED)
│   │   │   ├── PlaceParametersHandler.java (UPDATED)
│   │   │   ├── JumpToNodeHandler.java (REFACTORED)
│   │   │   ├── JumpToNodeIfSetHandler.java (REFACTORED)
│   │   │   ├── DialogParameterHandler.java (UPDATED)
│   │   │   ├── CheckDelayedReleaseDateHandler.java (UPDATED)
│   │   │   ├── SetDelayedReleaseAsCommentHandler.java (UPDATED)
│   │   │   ├── ReplicateToAgentHandler.java (REWRITTEN)
│   │   │   └── EmailNotificationsHandler.java (REWRITTEN)
│   │   ├── service/
│   │   │   ├── SendGridEmailService.java (NEW)
│   │   │   ├── PublishingServiceWrapper.java (NEW)
│   │   │   ├── EmailTemplateResolver.java (NEW)
│   │   │   └── CloudWordsAdapter.java (NEW)
│   │   └── util/
│   │       ├── Constants.java
│   │       └── Util.java
│   ├── src/test/java/...
│   └── pom.xml
├── ui.apps/
│   └── src/main/content/jcr_root/apps/lifetech/
│       ├── components/workflowStep/ (No changes)
│       ├── config/
│       │   └── com.sendgrid.EmailServiceImpl.cfg.json (NEW)
│       └── config.author/
│           └── org.apache.sling.serviceusermapping.*.cfg.json (NEW)
└── all/
    └── pom.xml
```

### 14.2 Documentation Deliverables

1. **Migration Guide** (This document - expanded)
2. **API Migration Reference** - Handler-by-handler API changes
3. **Configuration Guide** - Service setup and configuration
4. **Troubleshooting Guide** - Common issues and solutions
5. **Operational Guide** - Day-2 operations
6. **Architecture Diagram** - New component relationships
7. **Testing Report** - Test results and coverage
8. **Performance Report** - Benchmarks and optimization results

---

## SECTION 15: RISK ASSESSMENT & MITIGATION

### 15.1 Risk Matrix

| Risk | Severity | Probability | Impact | Mitigation |
|------|----------|-------------|--------|-----------|
| **Email delivery failure** | High | Medium | Notifications not sent | Implement retry + alerts |
| **Publishing API unavailable** | Critical | Low | Content not published | Fallback to manual, queue |
| **Delayed release not triggering** | High | Medium | Late publication | External scheduler backup |
| **Performance degradation** | Medium | Medium | User experience | Load testing, optimization |
| **Participant lookup failures** | Medium | Medium | Workflow stalls | Fallback to admin group |
| **Template migration incomplete** | Medium | Low | Email formatting issues | Pre-migration testing |
| **Route creation failures** | High | Low | Workflow stuck | Route validation layer |
| **Service user ACL issues** | High | Low | Workflow halts | ACL pre-configuration |
| **Third-party integration issues** | Medium | Medium | Feature loss | Graceful degradation |
| **Data loss during migration** | Critical | Very Low | Data corruption | Comprehensive backup |

### 15.2 Mitigation Strategies

**High-Risk Item 1: Email Delivery**
- Implement SendGrid retry logic with exponential backoff
- Log all email attempts and failures
- Create admin dashboard for email status
- Set up alerts for delivery failures

**High-Risk Item 2: Publishing API**
- Test Publishing Service extensively in dev/stage
- Implement fallback queuing mechanism
- Create manual publication workflow alternative
- Monitor API availability

**High-Risk Item 3: Delayed Release Scheduling**
- Implement both Adobe I/O Events and external scheduler
- Start with external scheduler for reliability
- Migrate to I/O Events once proven stable
- Create monitoring for missed schedules

---

## SECTION 16: SUCCESS CRITERIA

### 16.1 Functional Success Criteria

- [ ] All workflow steps execute successfully
- [ ] Rework loop functions correctly
- [ ] OR split routing works as expected
- [ ] Participant assignment working
- [ ] Email notifications send correctly
- [ ] Content publishing to managed services
- [ ] Delayed release scheduling accurate
- [ ] Cancellation workflow complete
- [ ] History and comments preserved
- [ ] Translation workflow (if applicable) functional

### 16.2 Performance Success Criteria

- [ ] Workflow initiation < 2 seconds
- [ ] Step execution < 5 seconds (non-publishing)
- [ ] Publishing step < 30 seconds
- [ ] Email sending < 10 seconds per batch
- [ ] No memory leaks over 24-hour run
- [ ] CPU utilization < 40% under normal load
- [ ] Throughput: 100 workflows/hour minimum

### 16.3 Quality Success Criteria

- [ ] Code coverage > 80%
- [ ] Zero critical bugs in UAT
- [ ] All edge cases tested
- [ ] Documentation complete and accurate
- [ ] All handlers updated to Declarative Services
- [ ] No deprecated API usage
- [ ] Performance benchmarks met
- [ ] Operational procedures documented

---

## SECTION 17: POST-MIGRATION SUPPORT

### 17.1 Monitoring & Alerting

**Metrics to Monitor:**
- Workflow instance count and state distribution
- Handler execution times (avg, min, max, p95, p99)
- Email sending success/failure rates
- Publishing API response times
- Service availability (uptime %)
- Error rates and types

**Alerting Thresholds:**
- Email failure rate > 5% → Alert
- Publishing API latency > 60s → Alert
- Workflow execution > 10min → Alert
- Service downtime > 30s → Critical Alert

### 17.2 Runbook Items

**Common Issues & Resolutions:**

1. **Workflow stuck on step**
   - Check service user permissions
   - Review handler logs
   - Validate metadata state
   - Manually advance if necessary

2. **Email notifications not sent**
   - Check SendGrid API key configuration
   - Review email template
   - Check recipient extraction
   - Review error logs for SendGrid errors

3. **Content not publishing**
   - Check Publishing Service availability
   - Validate resource path
   - Check service user permissions
   - Review replication/publishing logs

4. **Delayed release not triggering**
   - Verify Adobe I/O Events configured
   - Check external scheduler running
   - Review timestamp calculations
   - Check for timezone issues

---

## SECTION 18: CONCLUSION & RECOMMENDATIONS

### 18.1 Summary of Changes

This migration involves:
- **9 custom workflow handlers** requiring updates
- **4 new cloud services** to create
- **9 workflow components** to configure
- **Email template migration** from /etc/ to /conf/
- **Service user setup** with proper ACLs
- **API updates** for AEMaaCS compatibility

### 18.2 Critical Path Items

**MUST COMPLETE BEFORE GO-LIVE:**
1. ✅ ReplicateToAgentHandler - Content publishing
2. ✅ EmailNotificationsHandler - Notifications
3. ✅ JumpToNodeHandler - Workflow routing
4. ✅ SendGrid service setup - Email capability
5. ✅ Service user configuration - Permissions

### 18.3 Timeline

- **Estimated Effort:** 6-8 weeks
- **Critical Path:** 4-5 weeks
- **Phase 1 (Preparation):** 1 week
- **Phase 2 (Development):** 3 weeks
- **Phase 3 (Testing & Optimization):** 1.5 weeks
- **Phase 4 (Go-Live & Rollback):** 0.5 weeks

### 18.4 Next Steps

1. **Immediately:**
   - [ ] Assign migration team
   - [ ] Set up AEMaaCS dev environment
   - [ ] Begin OSGi annotation updates

2. **This Week:**
   - [ ] Create detailed project plan
   - [ ] Setup Maven project structure
   - [ ] Start low-effort handler migration

3. **Next Sprint:**
   - [ ] Focus on ReplicateToAgentHandler
   - [ ] Setup SendGrid integration
   - [ ] Begin integration testing

4. **Before Production:**
   - [ ] Complete all handlers
   - [ ] Comprehensive testing cycle
   - [ ] Final performance validation
   - [ ] Production deployment plan

---

## APPENDIX A: COMMON APIS REFERENCE

### A.1 WorkflowProcess API (AEMaaCS)

```java
public interface WorkflowProcess {
    void execute(WorkItem workItem, 
                WorkflowSession workflowSession, 
                MetaDataMap metaDataMap) throws WorkflowException;
}
```

### A.2 WorkItem & Related Classes

```java
WorkItem workItem = ...;

// Get workflow data
WorkflowData data = workItem.getWorkflow().getWorkflowData();
String payload = data.getPayload(); // Content path
String payloadType = data.getPayloadType(); // "JCR_PATH", "JCR_UUID"

// Get metadata
MetaDataMap metaDataMap = data.getMetaDataMap();
String value = metaDataMap.get("key", String.class);

// Get current step
WorkflowNode node = workItem.getNode();
String stepTitle = node.getTitle();
String stepType = node.getType();

// Get workflow instance
Workflow workflow = workItem.getWorkflow();
String workflowId = workflow.getId();
String workflowTitle = workflow.getWorkflowModel().getTitle();
```

### A.3 Service References

```java
@Reference
private ResourceResolverFactory resolverFactory;

@Reference
private WorkflowSession workflowSession;

@Reference
private WorkflowService workflowService;

@Reference
private EventService eventService;

@Reference
private QueryBuilder queryBuilder;
```

---

## APPENDIX B: SENDGRID INTEGRATION EXAMPLE

```java
@Component(service = EmailService.class)
@Designate(ocd = SendGridConfig.class)
public class SendGridEmailServiceImpl implements EmailService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SendGridEmailServiceImpl.class);
    
    private String apiKey;
    private String fromEmail;
    private String fromName;
    
    @Activate
    protected void activate(SendGridConfig config) {
        apiKey = config.apiKey();
        fromEmail = config.fromEmail();
        fromName = config.fromName();
    }
    
    @Override
    public void sendEmail(String to, String subject, String htmlBody) 
            throws EmailException {
        try {
            Email fromAddr = new Email(fromEmail, fromName);
            Email toAddr = new Email(to);
            Content content = new Content("text/html", htmlBody);
            Mail mail = new Mail(fromAddr, subject, toAddr, content);
            
            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() != 202) {
                throw new EmailException("SendGrid returned: " + response.getStatusCode());
            }
            
            LOG.debug("Email sent successfully to {}", to);
        } catch (IOException e) {
            throw new EmailException("Failed to send email", e);
        }
    }
}

@ObjectClassDefinition(name = "SendGrid Email Service Config")
public @interface SendGridConfig {
    @AttributeDefinition(name = "API Key", description = "SendGrid API Key")
    String apiKey();
    
    @AttributeDefinition(name = "From Email", description = "Sender email address")
    String fromEmail() default "noreply@example.com";
    
    @AttributeDefinition(name = "From Name", description = "Sender display name")
    String fromName() default "Workflow";
}
```

---

**END OF MIGRATION ANALYSIS DOCUMENT**

---

### Document Control

| Version | Date | Author | Status |
|---------|------|--------|--------|
| 1.0 | 2026-04-25 | Migration Analysis Team | Draft |
| 2.0 | 2026-04-26 | Architecture Review | Approved for Implementation |

**Next Review Date:** After Phase 2 completion

---

This comprehensive migration analysis document provides everything needed to successfully migrate the AEM 6.4 Major Review Workflow to AEM as a Cloud Service. The document covers detailed architectural analysis, handler-by-handler migration strategies, code samples, testing approaches, risk mitigation, and operational guidance.
