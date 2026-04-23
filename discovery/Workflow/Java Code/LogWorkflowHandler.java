package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.Workflow;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.Value;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * Workflow process that deletes the given Node.
 *
 * @author Andreas Schaefer
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Log the Workflow Process." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Log Workflow" )
    }
)
public class LogWorkflowHandler
    extends AbstractResourceWorkflowProcess
{
    private static final Logger LOG = LoggerFactory.getLogger( LogWorkflowHandler.class );

    @Reference
    private SlingSettingsService settingsService;

    @Override
    void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        String lMessage = pWorkflowContext.getModelArguments().get( "message" );
        if( lMessage == null ) { lMessage = "No Message Provided"; }
        String lParsedMessage = parsePlaceholders( lMessage, pWorkflowContext );
        logMessage( pWorkflowContext, lParsedMessage );
    }

    private String parsePlaceholders( String pMessage, WorkflowContext pWorkflowContext ) {
        WorkItem lItem = pWorkflowContext.getWorkItem();
        int lStart = -1;
        String lReturn = "";
        int lEnd = -1;
        while( true ) {
            lStart = pMessage.indexOf( "{", lStart + 1 );
            if( lStart >= 0 ) {
                lReturn += pMessage.substring( lEnd + 1, lStart );
                lEnd = pMessage.indexOf( "}", lStart );
                if( lEnd > lStart ) {
                    String lName = pMessage.substring( lStart + 1, lEnd ).toLowerCase();
                    String lValue = null;
                    if( "payload".equals( lName ) ) {
                        WorkflowData lWorkflowData = lItem.getWorkflowData();
                        Object lPayload = lWorkflowData != null ? lWorkflowData.getPayload() : null;
                        if( lPayload != null ) {
                            lValue = "Payload: " + lPayload;
                        } else {
                            lValue = "Payload: is not found";
                        }
                    } else if( "arguments".equals( lName ) ) {
                        lValue = "Arguments: " + toString( pWorkflowContext.getModelDataMap() );
                    } else if( "meta".equals( lName ) ) {
                        lValue = "Meta: " + toString( pWorkflowContext.getModelDataMap() );
                    } else if( "comment".equals( lName ) ) {
                        if( pWorkflowContext.getWorkflowDataMap() != null ) {
                            lValue = "Comment: " + pWorkflowContext.getWorkflowDataMap().get( "startComment", String.class );
                        } else {
                            lValue = "Comment: No Meta Data Map found";
                        }
                    } else if( "wf-data-meta".equals( lName ) ) {
                        Workflow lWorkflow = lItem.getWorkflow();
                        WorkflowData lWorkflowData = lWorkflow != null ? lWorkflow.getWorkflowData() : null;
                        MetaDataMap lMetaDataMap = lWorkflowData != null ? lWorkflowData.getMetaDataMap() : null;
                        if( lMetaDataMap != null ) {
                            lValue = "WF Data Meta: " + toString( pWorkflowContext.getWorkflowDataMap() );
                        } else {
                            lValue = "WF Data Meta: No Meta Data Map found";
                        }
                    }
                    LOG.trace( "Value: " + lValue );
                    if( lValue != null ) {
                        lReturn += lValue;
                    } else {
                        lReturn += "'placeholder unknown'";
                    }
                } else {
                    // No End Bracket found so add the rest and exit
                    lReturn += pMessage.substring( lStart );
                    break;
                }
            } else {
                // No Start Bracket found so add the rest and exit
                if( pMessage.length() > lEnd ) {
                    lReturn += pMessage.substring( lEnd + 1 );
                }
                break;
            }
        }
        return lReturn;
    }

    private String toString( MetaDataMap pArguments ) {
        String lReturn = "{ ";
        for( String lKey: pArguments.keySet() ) {
            Object lTemp = pArguments.get( lKey, Object.class );
            String lClass = "";
            String lValue = null;
            if( lTemp != null ) {
                lClass = ( lTemp instanceof Value ) ?
                    "type: " + PropertyType.nameFromValue( ((Value) lTemp).getType() ) :
                    "class: " + lTemp.getClass().getName();
                lValue = pArguments.get(lKey, String.class);
            }
            // Just in case the Value is null (should not but could)
            if( lValue != null ) {
                while( true ) {
                    int lIndex = lValue.indexOf( "{" );
                    if( lIndex >= 0 ) {
                        if( lIndex < ( lValue.length() - 1 ) ) {
                            lValue = lValue.substring( 0, lIndex ) + lValue.substring( lIndex + 1 );
                        } else {
                            lValue = lValue.substring( 0, lIndex );
                        }
                    } else {
                        break;
                    }
                }
                lReturn += "\n"  + lKey + " = '" + pArguments.get( lKey, String.class ) +
                    "', " + lClass;
            } else {
                lReturn += "\n" + lKey + " is NULL";
            }
        }
        lReturn += " }";

        return lReturn;
    }
}
