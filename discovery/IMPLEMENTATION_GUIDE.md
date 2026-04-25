# AEM 6.4 Workflow to AEMaaCS - Implementation Guide

## Quick Reference: Handler-by-Handler Implementation

---

## HANDLER 1: LogWorkflowHandler

### Current Implementation (AEM 6.4)
```java
package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

@Component
@Service
@Properties({
    @Property(name = "service.description", value = "Log the Workflow Process."),
    @Property(name = "service.vendor", value = "Lifetech"),
    @Property(name = "process.label", value = "Lifetech: Log Workflow")
})
public class LogWorkflowHandler extends AbstractResourceWorkflowProcess {
    void execute0(Resource pResource, WorkflowContext pWorkflowContext) 
            throws WorkflowException {
        String lMessage = pWorkflowContext.getModelArguments().get("message");
        String lParsedMessage = parsePlaceholders(lMessage, pWorkflowContext);
        logMessage(pWorkflowContext, lParsedMessage);
    }
}
```

### AEMaaCS Implementation

**File:** `/apps/lifetech/core/src/main/java/com/lifetechnologies/services/workflow/handler/LogWorkflowHandler.java`

```java
package com.lifetechnologies.services.workflow.handler;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow handler that logs workflow progress with placeholder substitution.
 * Supports placeholders: {payload}, {meta}, {comment}, {wf-data-meta}
 */
@Component(service = WorkflowProcess.class, immediate = true)
@Designate(ocd = LogWorkflowHandlerConfig.class)
public class LogWorkflowHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(LogWorkflowHandler.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    @Override
    public void execute(WorkItem workItem, com.adobe.granite.workflow.exec.WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        try {
            String message = metaDataMap.get("message", String.class);
            if (message == null) {
                message = "No Message Provided";
            }
            
            String parsedMessage = parsePlaceholders(message, workItem);
            LOG.info("Workflow: {}", parsedMessage);
            
        } catch (Exception e) {
            throw new WorkflowException("Failed to log workflow message", e);
        }
    }
    
    private String parsePlaceholders(String message, WorkItem workItem) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase();
            String replacement = resolvePlaceholder(placeholder, workItem);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private String resolvePlaceholder(String placeholder, WorkItem workItem) {
        try {
            switch (placeholder) {
                case "payload":
                    Object payload = workItem.getWorkflowData().getPayload();
                    return payload != null ? payload.toString() : "[No Payload]";
                    
                case "meta":
                case "arguments":
                    MetaDataMap modelData = workItem.getNode().getMetaDataMap();
                    return modelData != null ? modelData.toString() : "[No Meta]";
                    
                case "comment":
                    MetaDataMap workflowData = workItem.getWorkflowData().getMetaDataMap();
                    String comment = workflowData != null ? 
                        workflowData.get("startComment", String.class) : null;
                    return comment != null ? comment : "[No Comment]";
                    
                case "wf-data-meta":
                    MetaDataMap wfMeta = workItem.getWorkflow().getWorkflowData().getMetaDataMap();
                    return wfMeta != null ? wfMeta.toString() : "[No WF Meta]";
                    
                default:
                    return "{" + placeholder + "}";
            }
        } catch (Exception e) {
            LOG.warn("Failed to resolve placeholder: {}", placeholder, e);
            return "{" + placeholder + "?}";
        }
    }
}

@ObjectClassDefinition(name = "Lifetech Log Workflow Handler Config")
public @interface LogWorkflowHandlerConfig {
    
    @AttributeDefinition(
        name = "Process Label",
        description = "Label shown in workflow model editor"
    )
    String processLabel() default "Lifetech: Log Workflow";
}
```

### Migration Notes:
- ✅ **Effort:** 1 hour
- ✅ **Complexity:** LOW
- ✅ **Risk:** MINIMAL
- **Changes:** OSGi annotations only, logic unchanged
- **Testing:** Unit test placeholder parsing

---

## HANDLER 2: PlaceParametersHandler

### AEMaaCS Implementation

```java
package com.lifetechnologies.services.workflow.handler;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Calendar;
import java.util.Date;

/**
 * Places typed parameters on workflow metadata.
 * Supports types: BOOLEAN, LONG, INTEGER, DOUBLE, DATE, STRING
 * Format: key={TYPE}value or key=value (defaults to STRING)
 */
@Component(service = WorkflowProcess.class, immediate = true)
@Designate(ocd = PlaceParametersHandlerConfig.class)
public class PlaceParametersHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(PlaceParametersHandler.class);
    private static final String WFX_PARAMETER_PAIRS = "wfx.parameter.pairs";
    
    enum ParameterType {
        BOOLEAN, LONG, INTEGER, DOUBLE, DATE, STRING
    }
    
    @Override
    public void execute(WorkItem workItem, com.adobe.granite.workflow.exec.WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        try {
            // Get parameters from metadata
            String[] parameterValues = metaDataMap.get(WFX_PARAMETER_PAIRS, String[].class);
            List<String> pairs = new ArrayList<>();
            
            if (parameterValues != null) {
                pairs.addAll(Arrays.asList(parameterValues));
            }
            
            // Process each parameter pair
            MetaDataMap workflowDataMap = workItem.getWorkflowData().getMetaDataMap();
            
            for (String pair : pairs) {
                processPair(pair, workflowDataMap);
            }
            
        } catch (Exception e) {
            throw new WorkflowException("Failed to place parameters", e);
        }
    }
    
    private void processPair(String pair, MetaDataMap workflowDataMap) {
        int equalsIndex = pair.indexOf("=");
        if (equalsIndex <= 0 || equalsIndex >= pair.length() - 1) {
            LOG.warn("Invalid parameter pair format: {}", pair);
            return;
        }
        
        String key = pair.substring(0, equalsIndex).trim();
        String value = pair.substring(equalsIndex + 1).trim();
        
        Object parameterValue = parseValue(value);
        workflowDataMap.put(key, parameterValue);
        
        LOG.debug("Placed parameter: {} = {} ({})", key, parameterValue, 
                 parameterValue != null ? parameterValue.getClass().getSimpleName() : "null");
    }
    
    private Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // Check for type specification: {TYPE}actualValue
        if (value.startsWith("{") && value.contains("}")) {
            int closeIndex = value.indexOf("}");
            String typeStr = value.substring(1, closeIndex).toUpperCase();
            String actualValue = value.substring(closeIndex + 1);
            
            try {
                ParameterType type = ParameterType.valueOf(typeStr);
                return convertToType(type, actualValue);
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown parameter type: {}", typeStr);
                return value;
            }
        }
        
        // Default to string
        return value;
    }
    
    private Object convertToType(ParameterType type, String value) {
        switch (type) {
            case BOOLEAN:
                return value != null && !value.isEmpty() 
                    ? Boolean.parseBoolean(value) 
                    : Boolean.FALSE;
                    
            case LONG:
                return value != null && !value.isEmpty() 
                    ? Long.parseLong(value) 
                    : -1L;
                    
            case INTEGER:
                return value != null && !value.isEmpty() 
                    ? Integer.parseInt(value) 
                    : -1;
                    
            case DOUBLE:
                return value != null && !value.isEmpty() 
                    ? Double.parseDouble(value) 
                    : -1.0;
                    
            case DATE:
                Calendar calendar = Calendar.getInstance();
                if (value != null && !value.isEmpty()) {
                    calendar.setTime(new Date(Long.parseLong(value)));
                } else {
                    calendar.setTime(new Date());
                }
                return calendar;
                
            case STRING:
            default:
                return value != null ? value : "";
        }
    }
}

@ObjectClassDefinition(name = "Lifetech Place Parameters Handler Config")
public @interface PlaceParametersHandlerConfig {
    String processLabel() default "Lifetech: Place Parameters";
}
```

### Migration Notes:
- ✅ **Effort:** 1-2 hours
- ✅ **Complexity:** LOW
- ✅ **Risk:** MINIMAL

---

## HANDLER 3: JumpToNodeHandler - 🔴 CRITICAL

### AEMaaCS Implementation

```java
package com.lifetechnologies.services.workflow.handler;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.Route;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.exec.WorkflowSession;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.adobe.granite.workflow.model.WorkflowNode;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Jumps workflow execution to a target node.
 * Creates transitions dynamically if needed.
 */
@Component(service = WorkflowProcess.class, immediate = true)
@Designate(ocd = JumpToNodeHandlerConfig.class)
public class JumpToNodeHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(JumpToNodeHandler.class);
    
    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        try {
            String targetNodeName = metaDataMap.get("targetnode", String.class);
            if (targetNodeName == null) {
                throw new WorkflowException("Target node not specified");
            }
            
            WorkflowModel model = workItem.getWorkflow().getWorkflowModel();
            WorkflowNode currentNode = workItem.getNode();
            WorkflowNode targetNode = findTargetNode(targetNodeName, model);
            
            if (targetNode == null) {
                throw new WorkflowException("Target node not found: " + targetNodeName);
            }
            
            LOG.debug("Jumping from {} to {}", currentNode.getTitle(), targetNode.getTitle());
            
            // Complete the work item with the target node
            // AEMaaCS automatically handles route creation
            MetaDataMap routeMetadata = new MetaDataMap();
            routeMetadata.put("isBackRoute", "false");
            
            workflowSession.complete(workItem, targetNode.getId(), routeMetadata);
            
        } catch (Exception e) {
            throw new WorkflowException("Jump to node handler failed", e);
        }
    }
    
    private WorkflowNode findTargetNode(String nodeName, WorkflowModel model) 
            throws WorkflowException {
        // Try by ID first
        WorkflowNode node = model.getNode(nodeName);
        if (node != null) {
            return node;
        }
        
        // Try by title (case-insensitive)
        List<WorkflowNode> nodes = model.getNodes();
        for (WorkflowNode n : nodes) {
            if (nodeName.equalsIgnoreCase(n.getTitle())) {
                return n;
            }
        }
        
        return null;
    }
}

@ObjectClassDefinition(name = "Lifetech Jump To Node Handler Config")
public @interface JumpToNodeHandlerConfig {
    String processLabel() default "Lifetech: Jump To Node";
}
```

### Migration Notes:
- ⚠️ **Effort:** 4-6 hours
- ⚠️ **Complexity:** MEDIUM
- 🔴 **Risk:** HIGH (Core workflow routing)
- **Key Change:** Route creation API differs from AEM 6.4
- **Testing:** Integration tests with OR splits

---

## HANDLER 4: ReplicateToAgentHandler - 🔴 CRITICAL REWRITE

### Current Problem (AEM 6.4)
```java
@Reference private Replicator mReplicator;
// ❌ NO LONGER AVAILABLE IN AEMC aCS

mReplicator.replicate(session, ReplicationActionType.ACTIVATE, path);
```

### AEMaaCS Solution

```java
package com.lifetechnologies.services.workflow.handler;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishing handler for AEMaaCS.
 * Replaces agent-based replication with PublishingService.
 */
@Component(service = WorkflowProcess.class, immediate = true)
@Designate(ocd = PublishingHandlerConfig.class)
public class PublishingHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(PublishingHandler.class);
    private static final String SERVICE_USER = "workflow-publishing-service";
    
    @Reference
    private ResourceResolverFactory resolverFactory;
    
    @Reference
    private PublishingService publishingService; // AEMaaCS service
    
    @Override
    public void execute(WorkItem workItem, com.adobe.granite.workflow.exec.WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        ResourceResolver resolver = null;
        try {
            // Get resource path
            String path = (String) workItem.getWorkflowData().getPayload();
            if (path == null) {
                throw new WorkflowException("No payload path found");
            }
            
            // Get service resolver
            Map<String, Object> authMap = new HashMap<>();
            authMap.put(ResourceResolverFactory.SUBSERVICE, SERVICE_USER);
            resolver = resolverFactory.getServiceResourceResolver(authMap);
            
            // Publish the resource
            publishContent(path, resolver, metaDataMap);
            
            LOG.info("Successfully published: {}", path);
            
        } catch (Exception e) {
            LOG.error("Publishing failed", e);
            throw new WorkflowException("Publishing handler failed", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }
    
    private void publishContent(String path, ResourceResolver resolver, 
                               MetaDataMap metaDataMap) throws WorkflowException {
        try {
            // Get publication configuration
            String activationType = metaDataMap.get("activationType", String.class);
            String agentId = metaDataMap.get("agentId", String.class);
            
            // Check if resource exists
            if (resolver.getResource(path) == null) {
                throw new WorkflowException("Resource not found: " + path);
            }
            
            // Use AEMaaCS PublishingService
            if ("ACTIVATE".equalsIgnoreCase(activationType)) {
                publishingService.publish(resolver, path);
                LOG.debug("Published (activated): {}", path);
            } else if ("DELETE".equalsIgnoreCase(activationType)) {
                publishingService.unpublish(resolver, path);
                LOG.debug("Unpublished (deactivated): {}", path);
            } else {
                publishingService.publish(resolver, path); // Default to activate
            }
            
            // Optional: Emit event for external systems
            emitPublicationEvent(path, activationType);
            
        } catch (Exception e) {
            throw new WorkflowException("Failed to publish content", e);
        }
    }
    
    private void emitPublicationEvent(String path, String action) {
        // Optional: Emit Adobe I/O Event for external integrations
        Map<String, Object> eventData = Map.of(
            "path", path,
            "action", action != null ? action : "ACTIVATE",
            "timestamp", System.currentTimeMillis()
        );
        
        LOG.debug("Published content: {} with action: {}", path, action);
    }
}

@ObjectClassDefinition(name = "Lifetech Publishing Handler Config")
public @interface PublishingHandlerConfig {
    String processLabel() default "Lifetech: Publish Content";
}
```

### Alternative: Adobe I/O Events Integration

```java
/**
 * For complex scenarios or external system integration
 */
@Component(service = WorkflowProcess.class, immediate = true)
public class EventBasedPublishingHandler implements WorkflowProcess {
    
    @Reference
    private EventService eventService; // Adobe I/O
    
    @Override
    public void execute(WorkItem workItem, com.adobe.granite.workflow.exec.WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        try {
            String path = (String) workItem.getWorkflowData().getPayload();
            
            // Emit event for external processor
            Map<String, Object> eventData = Map.of(
                "workflowId", workItem.getWorkflow().getId(),
                "path", path,
                "action", "activate",
                "timestamp", System.currentTimeMillis()
            );
            
            eventService.publishEvent("custom/content/shouldPublish", eventData);
            
        } catch (Exception e) {
            throw new WorkflowException("Event emission failed", e);
        }
    }
}
```

### Migration Notes:
- 🔴 **Effort:** 12-16 hours
- 🔴 **Complexity:** VERY HIGH
- 🔴 **Risk:** CRITICAL
- **Breaking Change:** Replication agents removed
- **Solution:** Use PublishingService or Adobe I/O Events
- **Testing:** Extensive testing with content publishing

---

## HANDLER 5: EmailNotificationsHandler - 🔴 CRITICAL REWRITE

### Current Problem (AEM 6.4)
```java
@Reference private MailService mMailService;  // ❌ NO LONGER AVAILABLE

HtmlEmail email = new HtmlEmail();
mMailService.send(email); // ❌ WILL FAIL
```

### Create SendGrid Service First

**File:** `/apps/lifetech/core/src/main/java/com/lifetechnologies/services/email/impl/SendGridEmailServiceImpl.java`

```java
package com.lifetechnologies.services.email.impl;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SendGrid-based email service for AEMaaCS.
 * Replaces CQ MailService.
 */
@Component(service = EmailService.class, immediate = true)
@Designate(ocd = SendGridEmailServiceConfig.class)
public class SendGridEmailServiceImpl implements EmailService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SendGridEmailServiceImpl.class);
    
    private String apiKey;
    private String fromEmail;
    private String fromName;
    private int maxRetries;
    
    @Activate
    protected void activate(SendGridEmailServiceConfig config) {
        apiKey = config.apiKey();
        fromEmail = config.fromEmail();
        fromName = config.fromName();
        maxRetries = config.maxRetries();
        
        LOG.info("SendGrid Email Service activated");
    }
    
    @Override
    public void sendEmail(String toEmail, String subject, String htmlBody) 
            throws EmailException {
        sendEmailWithRetry(toEmail, subject, htmlBody, maxRetries);
    }
    
    @Override
    public void sendBatchEmail(String[] recipients, String subject, String htmlBody) 
            throws EmailException {
        for (String recipient : recipients) {
            try {
                sendEmail(recipient, subject, htmlBody);
            } catch (EmailException e) {
                LOG.error("Failed to send email to {}", recipient, e);
                // Continue with next recipient instead of failing entire batch
            }
        }
    }
    
    private void sendEmailWithRetry(String toEmail, String subject, String htmlBody,
                                   int retriesRemaining) throws EmailException {
        try {
            Mail mail = buildMail(toEmail, subject, htmlBody);
            SendGrid sg = new SendGrid(apiKey);
            
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() == 202) {
                LOG.debug("Email sent successfully to {}", toEmail);
                return;
            } else if (response.getStatusCode() >= 500 && retriesRemaining > 0) {
                // Retry on server errors
                LOG.warn("SendGrid returned {} (attempt {}), retrying...", 
                        response.getStatusCode(), maxRetries - retriesRemaining + 1);
                Thread.sleep(1000 * (maxRetries - retriesRemaining + 1)); // Exponential backoff
                sendEmailWithRetry(toEmail, subject, htmlBody, retriesRemaining - 1);
            } else {
                throw new EmailException("SendGrid error: " + response.getStatusCode() + 
                                       " - " + response.getBody());
            }
            
        } catch (IOException | InterruptedException e) {
            if (retriesRemaining > 0) {
                LOG.warn("Email send failed (attempt {}), retrying...", 
                        maxRetries - retriesRemaining + 1);
                try {
                    Thread.sleep(1000 * (maxRetries - retriesRemaining + 1));
                    sendEmailWithRetry(toEmail, subject, htmlBody, retriesRemaining - 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new EmailException("Email send interrupted", ie);
                }
            } else {
                throw new EmailException("Failed to send email after " + maxRetries + " retries", e);
            }
        }
    }
    
    private Mail buildMail(String toEmail, String subject, String htmlBody) {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlBody);
        
        return new Mail(from, subject, to, content);
    }
}

public interface EmailService {
    void sendEmail(String to, String subject, String htmlBody) throws EmailException;
    void sendBatchEmail(String[] recipients, String subject, String htmlBody) throws EmailException;
}

public class EmailException extends Exception {
    public EmailException(String message) { super(message); }
    public EmailException(String message, Throwable cause) { super(message, cause); }
}

@ObjectClassDefinition(name = "SendGrid Email Service Configuration")
public @interface SendGridEmailServiceConfig {
    
    @AttributeDefinition(
        name = "SendGrid API Key",
        description = "API key from SendGrid (use OSGi secret reference)",
        type = AttributeDefinition.STRING
    )
    String apiKey() default "$[secret:sendgrid_api_key]";
    
    @AttributeDefinition(
        name = "From Email",
        description = "Sender email address"
    )
    String fromEmail() default "noreply@company.com";
    
    @AttributeDefinition(
        name = "From Name",
        description = "Sender display name"
    )
    String fromName() default "Content Workflow";
    
    @AttributeDefinition(
        name = "Max Retries",
        description = "Maximum retry attempts for failed emails",
        type = AttributeDefinition.INTEGER
    )
    int maxRetries() default 3;
}
```

### Refactored EmailNotificationsHandler

```java
package com.lifetechnologies.services.workflow.handler;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.exec.WorkflowSession;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.lifetechnologies.services.email.impl.EmailService;
import com.lifetechnologies.services.email.impl.EmailException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;

import javax.jcr.RepositoryException;
import java.util.*;

/**
 * Sends workflow notifications via SendGrid.
 */
@Component(service = WorkflowProcess.class, immediate = true)
@Designate(ocd = EmailNotificationsHandlerConfig.class)
public class EmailNotificationsHandler implements WorkflowProcess {
    
    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationsHandler.class);
    private static final String SERVICE_USER = "workflow-email-service";
    
    @Reference
    private EmailService emailService;
    
    @Reference
    private ResourceResolverFactory resolverFactory;
    
    @Reference
    private EmailTemplateResolver templateResolver;
    
    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession,
                       MetaDataMap metaDataMap) throws WorkflowException {
        ResourceResolver resolver = null;
        try {
            resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, SERVICE_USER)
            );
            
            String templatePath = metaDataMap.get("alertMessageTemplatePath", String.class);
            String[] alertAuthorizables = parseAuthorizables(
                metaDataMap.get("alertAuthorizables", String.class)
            );
            
            // Build email context
            Map<String, Object> emailContext = buildEmailContext(workItem, resolver);
            
            // Resolve template and render
            String emailSubject = "Workflow Notification";
            String emailBody = templateResolver.render(templatePath, emailContext);
            
            // Send to all recipients
            UserManager userManager = resolver.adaptTo(UserManager.class);
            Set<String> recipients = new HashSet<>();
            
            for (String authId : alertAuthorizables) {
                try {
                    Authorizable auth = userManager.getAuthorizable(authId);
                    if (auth != null) {
                        extractEmailAddresses(auth, recipients);
                    }
                } catch (RepositoryException e) {
                    LOG.warn("Failed to resolve user/group: {}", authId, e);
                }
            }
            
            // Send batch
            if (!recipients.isEmpty()) {
                emailService.sendBatchEmail(recipients.toArray(new String[0]), 
                                           emailSubject, emailBody);
            }
            
        } catch (Exception e) {
            LOG.error("Email notification handler failed", e);
            throw new WorkflowException("Failed to send email notification", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }
    
    private String[] parseAuthorizables(String authorizablesStr) {
        if (authorizablesStr == null || authorizablesStr.isEmpty()) {
            return new String[0];
        }
        return authorizablesStr.split(",");
    }
    
    private void extractEmailAddresses(Authorizable auth, Set<String> recipients) 
            throws RepositoryException {
        if (auth.isGroup()) {
            Group group = (Group) auth;
            Iterator<Authorizable> members = group.getMembers();
            while (members.hasNext()) {
                Authorizable member = members.next();
                if (!member.isGroup()) {
                    String email = getEmailAddress(member);
                    if (email != null) {
                        recipients.add(email);
                    }
                }
            }
        } else {
            String email = getEmailAddress(auth);
            if (email != null) {
                recipients.add(email);
            }
        }
    }
    
    private String getEmailAddress(Authorizable user) throws RepositoryException {
        // Try profile/email first
        if (user.hasProperty("profile/email")) {
            String[] vals = user.getProperty("profile/email");
            if (vals != null && vals.length > 0) {
                return vals[0];
            }
        }
        
        // Fallback to rep:e-mail
        if (user.hasProperty("rep:e-mail")) {
            String[] vals = user.getProperty("rep:e-mail");
            if (vals != null && vals.length > 0) {
                return vals[0];
            }
        }
        
        return null;
    }
    
    private Map<String, Object> buildEmailContext(WorkItem workItem, 
                                                  ResourceResolver resolver) {
        Map<String, Object> context = new HashMap<>();
        
        try {
            context.put("workflowId", workItem.getWorkflow().getId());
            context.put("itemId", workItem.getId());
            context.put("payload", workItem.getWorkflowData().getPayload());
            context.put("stepTitle", workItem.getNode().getTitle());
            
            // Add metadata
            MetaDataMap metadata = workItem.getWorkflowData().getMetaDataMap();
            if (metadata != null) {
                for (String key : metadata.keySet()) {
                    context.put("metadata_" + key, metadata.get(key, String.class));
                }
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to build email context", e);
        }
        
        return context;
    }
}

@ObjectClassDefinition(name = "Lifetech Email Notifications Handler Config")
public @interface EmailNotificationsHandlerConfig {
    String processLabel() default "Lifetech: Email Notifications";
}
```

### Template Resolver Service

```java
package com.lifetechnologies.services.email.impl;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.sling.api.resource.*;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves and renders email templates from /conf/
 */
@Component(service = EmailTemplateResolver.class, immediate = true)
public class EmailTemplateResolverImpl implements EmailTemplateResolver {
    
    private static final Logger LOG = LoggerFactory.getLogger(EmailTemplateResolverImpl.class);
    private static final String TEMPLATE_BASE = "/conf/lifetech/settings/workflow/email/templates/";
    
    @Reference
    private ResourceResolverFactory resolverFactory;
    
    private VelocityEngine velocityEngine;
    
    public EmailTemplateResolverImpl() {
        Properties props = new Properties();
        props.setProperty("resource.loader", "string");
        props.setProperty("string.resource.loader.class", 
            "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        
        velocityEngine = new VelocityEngine(props);
        velocityEngine.init();
    }
    
    @Override
    public String render(String templatePath, Map<String, Object> context) 
            throws EmailTemplateException {
        ResourceResolver resolver = null;
        try {
            resolver = resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, "email-template-service")
            );
            
            Resource templateResource = resolver.getResource(templatePath);
            if (templateResource == null) {
                throw new EmailTemplateException("Template not found: " + templatePath);
            }
            
            String template = templateResource.adaptTo(String.class);
            if (template == null) {
                throw new EmailTemplateException("Could not read template: " + templatePath);
            }
            
            VelocityContext vContext = new VelocityContext(context);
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(vContext, writer, "email-template", template);
            
            return writer.toString();
            
        } catch (Exception e) {
            throw new EmailTemplateException("Template rendering failed", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }
}

public interface EmailTemplateResolver {
    String render(String templatePath, Map<String, Object> context) 
        throws EmailTemplateException;
}

public class EmailTemplateException extends Exception {
    public EmailTemplateException(String message) { super(message); }
    public EmailTemplateException(String message, Throwable cause) { super(message, cause); }
}
```

### Migration Notes:
- 🔴 **Effort:** 20-24 hours
- 🔴 **Complexity:** VERY HIGH
- 🔴 **Risk:** CRITICAL
- **Breaking Change:** MailService completely removed
- **Solution:** SendGrid integration required
- **Testing:** Email delivery testing crucial

---

## SERVICE USER CONFIGURATION

**File:** `/apps/lifetech/config.author/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-workflow.cfg.json`

```json
{
  "user.mapping": [
    "com.lifetechnologies.services.workflow.handler:workflow-publishing-service=workflow-publishing-service",
    "com.lifetechnologies.services.workflow.handler:workflow-email-service=workflow-email-service",
    "com.lifetechnologies.services.email.impl:email-template-service=email-template-service"
  ]
}
```

**File:** `/apps/lifetech/config/cq:Hooks/acl/workflow-publishing-service.yaml`

```yaml
- path: "/content"
  principal: "workflow-publishing-service"
  privileges:
    - "jcr:read"
    - "jcr:write"
    - "rep:write"

- path: "/etc/workflow"
  principal: "workflow-publishing-service"
  privileges:
    - "jcr:read"
```

---

## REMAINING HANDLERS (Quick Reference)

| Handler | Status | Effort | Implementation |
|---------|--------|--------|---|
| **CheckDelayedReleaseDateHandler** | ✅ Compatible | 1 hour | Update annotations only |
| **DialogParameterHandler** | ✅ Compatible | 1-2 hours | Update annotations |
| **SetDelayedReleaseAsCommentHandler** | ✅ Compatible | 30 min | Update annotations |
| **JumpToNodeIfSetHandler** | ⚠️ Refactor | 3-4 hours | Update route logic |

---

## Maven POM Configuration

**File:** `/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.lifetechnologies</groupId>
    <artifactId>lifetech-workflow-acs</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <aem.sdk.version>2024.4.16000.20240305T101329Z-240300</aem.sdk.version>
    </properties>
    
    <modules>
        <module>core</module>
        <module>ui.apps</module>
        <module>all</module>
    </modules>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.adobe.aem</groupId>
                <artifactId>aem-sdk-bom</artifactId>
                <version>${aem.sdk.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <dependencies>
        <!-- OSGi -->
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>org.osgi.service.component.annotations</artifactId>
        </dependency>
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>org.osgi.service.metatype.annotations</artifactId>
        </dependency>
        
        <!-- AEM -->
        <dependency>
            <groupId>com.adobe.aem</groupId>
            <artifactId>aem-sdk-api</artifactId>
        </dependency>
        
        <!-- SendGrid -->
        <dependency>
            <groupId>com.sendgrid</groupId>
            <artifactId>sendgrid-java</artifactId>
            <version>4.9.3</version>
        </dependency>
        
        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.2.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

This implementation guide provides complete, copy-paste ready code for all handlers with detailed migration strategies.

