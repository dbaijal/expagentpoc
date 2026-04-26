package com.lifetechnologies.services.workflow;

import com.lifetechnologies.services.osgi.config.WorkflowConfigurationService;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.ParticipantStepChooser;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;

import static com.lifetechnologies.services.util.Constants.*;

/**
 * Participant Chooser that relies on the Property with the Name: 'participantStepAuthorizableId'
 * to be placed on the CQ Workflow instance's Metadata Map
 */
@Component
@Service
@Properties(
    {
        @Property( name = Constants.SERVICE_DESCRIPTION, value = "Takes the Extracted Authorizable Id and returns it as Participant." ),
        @Property( name = ParticipantStepChooser.SERVICE_PROPERTY_LABEL, value = "Lifetech: Participant Chooser based on Payload Property" )
    }
)
public class PropertiesParticipantChooser
    implements ParticipantStepChooser
{
    Logger mLogger = LoggerFactory.getLogger( this.getClass() );

    @Reference
    WorkflowConfigurationService mWorkflowConfigurationService;

    public String getParticipant( WorkItem pWorkItem, WorkflowSession pWorkflowSession, MetaDataMap pArguments )
        throws WorkflowException
    {
        String lReturn = "administrators";
        Arguments lArguments = Arguments.parse( pWorkItem.getWorkflowData().getMetaDataMap() );
        mLogger.trace( "start get participant for WF Item: '{}', arguments: '{}", pWorkItem, lArguments );
        if( lArguments.containsKey( PARTICIPANT_CHOOSER_ID_PROPERTY_NAME ) ) {
            lReturn = lArguments.get( PARTICIPANT_CHOOSER_ID_PROPERTY_NAME );
        }
        AbstractResourceWorkflowProcess.LOG_LEVEL lLogLevel = AbstractResourceWorkflowProcess.obtainLogLevel( pWorkItem, mWorkflowConfigurationService, null, mLogger );
        if( lLogLevel != null ) {
            logMessage( lLogLevel, "return participant: '{}'", lReturn );
        }
        return lReturn;
    }

    protected void logMessage( AbstractResourceWorkflowProcess.LOG_LEVEL pLogLevel, String pMessage, Object pArgument1 ) {
        switch( pLogLevel ) {
            case error:
                mLogger.error( pMessage, pArgument1 );
                break;
            case warn:
                mLogger.warn( pMessage, pArgument1 );
                break;
            case info:
                mLogger.info( pMessage, pArgument1 );
                break;
            case debug:
                mLogger.debug( pMessage, pArgument1 );
                break;
            case trace:
                mLogger.trace( pMessage, pArgument1 );
                break;
        }
    }
}
