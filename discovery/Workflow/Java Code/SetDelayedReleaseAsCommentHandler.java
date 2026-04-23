package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.lifetechnologies.services.util.*;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * This Workflow Process is used as a timeout handler which can be used to advance
 * in a Participant Step if a given Property is set or not set in the Workflows
 * Meta Data.
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Places Comment on Workflow about the Delayed Release." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Delayed Release Reporter" )
    }
)
public class SetDelayedReleaseAsCommentHandler
    extends AbstractResourceWorkflowProcess
{
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat( "MM/dd/yyyy 'at' HH:mm:ss" );

    void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
    	// WorkItem lWorkItem = pWorkflowContext.getWorkItem();
        MetaDataMap lTarget = pWorkflowContext.getWorkflowDataMap();
        Long lAbsoluteTime = lTarget.get( Constants.ABSOLUTE_TIME, Long.class );
        logMessage( pWorkflowContext, "Raw Absolute Time: {}", lAbsoluteTime );
        
        // Update inTranslation flag as false to each page for translation project.
        PlaceParameterFromConfigurationService.updateTransFlag(pResource, pWorkflowContext, false);
        
        if( lAbsoluteTime != null && lAbsoluteTime > System.currentTimeMillis() ) {
        	// logMessage( pWorkflowContext, "Contained Absolute Time" );
            String lDateFormatted = DATE_FORMAT.format( new Date( lAbsoluteTime ) );
            logMessage( pWorkflowContext, "Got Absolute Time: {} onto target: '{}'", lDateFormatted, lTarget );
            lTarget.put( "comment", "Workflow Delayed for: " + lDateFormatted );
            lTarget.put( "delayed.release.date", lDateFormatted );
            logMessage( pWorkflowContext, "Set it on Target" );
        } else {
            lTarget.put( "delayed.release.date", "" );
        }
    }
   
}
