package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.HistoryItem;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Date;
import java.util.List;

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
        @Property( name = SERVICE_DESCRIPTION, value = "Takes a Node Property and adds it to the Meta Data of the Workflow." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Handles Node Properties" )
    }
)
public class DialogParameterHandler
    extends AbstractResourceWorkflowProcess
{
    private enum TYPES { STRING, DATE, BOOLEAN, LONG };

    void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        WorkItem lItem = pWorkflowContext.getWorkItem();
        WorkflowSession lSession = pWorkflowContext.getWorkflowSession();
        MetaDataMap lMetaDataMap = pWorkflowContext.getModelDataMap();
        Arguments lArguments = Arguments.parse( lMetaDataMap );
        String lPropertyPath = Util.getArgument( lArguments, "propertyPath" );
        // If the Property contains a slash it is considered to be a Payload Property based Value
        boolean lIsPayloadProperty = lPropertyPath.contains( "/" );
        logMessage( pWorkflowContext, "Property Path: '{}', Payload Property Based: {}", lPropertyPath, lIsPayloadProperty );
        if( lIsPayloadProperty ) {
            if( lPropertyPath.startsWith( "./" ) ) {
                lPropertyPath = lPropertyPath.substring( 2 );
            }
            while( lPropertyPath.startsWith( "/" ) ) {
                lPropertyPath = lPropertyPath.substring( 1 );
            }
            int lIndex = lPropertyPath.lastIndexOf( "/" );
            Resource lTargetResource = pResource;
            if( lIndex > 0 ) {
                String lResourcePath = lPropertyPath.substring( 0, lIndex );
                logMessage( pWorkflowContext, "Relative Resource Path: {}", lResourcePath );
                lPropertyPath = lPropertyPath.substring( lIndex + 1 );
                lTargetResource = pResource.getChild( lResourcePath );
                logMessage( pWorkflowContext, "Target Resource: {}", lTargetResource );
            }
            if( lTargetResource != null ) {
                ValueMap lProperties = lTargetResource.adaptTo( ValueMap.class );
                boolean lPropertyExists = false;
                int i = 0;
                do {
                    lPropertyExists = lProperties.containsKey( lPropertyPath );
                    if( !lPropertyExists ) {
                        logMessage( pWorkflowContext, "Property does not exist, loop: {}", i );
                        try {
                            i++;
                            Thread.sleep( 1000 );
                        } catch( InterruptedException e ) {
                            logMessage( pWorkflowContext, "Failed to wait", e );
                        }
                    }
                } while( !lPropertyExists && i < 10 );
                logMessage( pWorkflowContext, "Property: '{}', exists: {}", lPropertyPath, lPropertyExists );
                String lPropertyType = lArguments.get( "propertyType" );
                if( lPropertyType == null ) {
                    lPropertyType = "STRING";
                } else {
                    lPropertyType = lPropertyType.toUpperCase();
                }
                String lPropertyDefault = lArguments.get( "propertyDefault" );
                TYPES lType = TYPES.valueOf( lPropertyType );
                String lMetaDataPropertyName = Util.getArgument( lArguments, "metaDataProperty" );
                MetaDataMap lTarget = lItem.getWorkflowData().getMetaDataMap();
                logMessage( pWorkflowContext, "Property Type: '{}', default: '{}', meta data property name: '{}", new Object[] { lPropertyType, lPropertyDefault, lMetaDataPropertyName } );
                switch( lType ) {
                    case STRING:
                        lPropertyDefault = lPropertyDefault == null ?  "" : lPropertyDefault;
                        String lValue = !lPropertyExists ? lPropertyDefault : lProperties.get( lPropertyPath, String.class );
                        logMessage( pWorkflowContext, "STRING: Name: '{}', Value: '{}'", lPropertyPath, lValue );
                        lTarget.put( lMetaDataPropertyName, lValue );
                        break;
                    case DATE:
                        long lDateDefault = lPropertyDefault == null ? 0L : Long.parseLong( lPropertyDefault );
                        long lDateValue = !lPropertyExists ? lDateDefault : lProperties.get( lPropertyPath, Date.class ).getTime();
                        logMessage( pWorkflowContext, "DATE: Name: '{}', Value: '{}'", lPropertyPath, lDateValue );
                        lTarget.put( lMetaDataPropertyName, lDateValue );
                        break;
                    case BOOLEAN:
                        boolean lBooleanDefault = lPropertyDefault == null ? false : Boolean.parseBoolean( lPropertyDefault );
                        boolean lBooleanValue = lProperties.get( lPropertyPath, Boolean.class );
                        logMessage( pWorkflowContext, "BOOLEAN: Name: '{}', Value: '{}'", lPropertyPath, lBooleanValue );
                        lTarget.put( lMetaDataPropertyName, !lPropertyExists ? lBooleanDefault : lBooleanValue );
                        break;
                    case LONG:
                        long lLongDefault = lPropertyDefault == null ? 0L : Long.parseLong( lPropertyDefault );
                        long lLongValue = !lPropertyExists ? lLongDefault : lProperties.get( lPropertyPath, Long.class );
                        logMessage( pWorkflowContext, "LONG: Name: '{}', Value: '{}'", lPropertyPath, lLongValue );
                        lTarget.put( lMetaDataPropertyName, lLongValue );
                        break;
                    default:
                        throw new WorkflowException( "Given type: " + lType + " isn't supported" );
                }
                // We want to clear the property so we remove the property from the node and save the changes
                if( lPropertyExists ) {
                    Node lTargetNode = lTargetResource.adaptTo( Node.class );
                    try {
                        javax.jcr.Property lTargetProperty = lTargetNode.getProperty( lPropertyPath );
                        logMessage( pWorkflowContext, "Target Property: '{}' for Path: '{}'", lTargetProperty, lPropertyPath );
                        lTargetProperty.remove();
                        lTargetNode.getSession().save();
                    } catch( RepositoryException e ) {
                        mLogger.warn( "Could not save the removal of the Property", e );
                    }
                }
            } else {
                mLogger.warn( "Target Resource Not Found" );
            }
        } else {
            int lHistoryOffset = 1;
            try {
                lHistoryOffset = Integer.parseInt( Util.getArgument( lArguments, "historyOffset", "1" ) );
            } catch( NumberFormatException e ) {
                mLogger.warn( "History Offset is not a number: '{}", Util.getArgument( lArguments, "historyOffset" ), e );
            }
            List<HistoryItem> lHistoryItemList = lSession.getHistory( lItem.getWorkflow() );
            logMessage( pWorkflowContext, "History Item List: {}", lHistoryItemList );
            HistoryItem lHistoryItem;
            if( lHistoryOffset > 0 && lHistoryOffset < lHistoryItemList.size() ) {
                lHistoryItem = lHistoryItemList.get( lHistoryItemList.size() - lHistoryOffset );
            } else {
                lHistoryItem = lHistoryItemList.get( lHistoryItemList.size() - 1 );
            }
            logMessage( pWorkflowContext, "Last History Item: '{}'", lHistoryItem );
            // Now we can obtain the Item, then its Metadata and read out our paramter
            MetaDataMap lPreviousMetaDataMap = lHistoryItem.getWorkItem().getMetaDataMap();
            logMessage( pWorkflowContext, "Last History Item Meta Data Map: '{}'", lPreviousMetaDataMap );
            Arguments lPreviousArguments = Arguments.parse( lPreviousMetaDataMap );
            String lPropertyType = lPreviousArguments.get( lPropertyPath + "@TypeHint" );
            if( lPropertyType == null ) {
                lPropertyType = "STRING";
            } else {
                lPropertyType = lPropertyType.toUpperCase();
            }
            String lPropertyDefault = lArguments.get( "propertyDefault" );
            TYPES lType = TYPES.valueOf( lPropertyType );
            String lMetaDataPropertyName = Util.getArgument( lArguments, "metaDataProperty" );
            MetaDataMap lTarget = lItem.getWorkflowData().getMetaDataMap();
            logMessage( pWorkflowContext, "Property Type: '{}', default: '{}', meta data property name: '{}", new Object[] { lPropertyType, lPropertyDefault, lMetaDataPropertyName } );
            boolean lPropertyExists = lPreviousArguments.containsKey( lPropertyPath );
            switch( lType ) {
                case STRING:
                    String lStringDefault = lPropertyDefault == null ?  "" : lPropertyDefault;
                    String lStringActual = !lPropertyExists ? lStringDefault : lPreviousMetaDataMap.get( lPropertyPath, String.class );
                    logMessage( pWorkflowContext, "STRING: Name: '{}', Value: '{}'", lPropertyPath, lStringActual );
                    lTarget.put( lMetaDataPropertyName, lStringActual );
                    break;
                case DATE:
                    long lDateDefault = lPropertyDefault == null ?  0L : Long.parseLong( lPropertyDefault );
                    long lDateActual = !lPropertyExists ? lDateDefault : lPreviousMetaDataMap.get( lPropertyPath, Date.class ).getTime();
                    logMessage( pWorkflowContext, "DATE: Name: '{}', Value: '{}'", lPropertyPath, lDateActual );
                    lTarget.put( lMetaDataPropertyName, lDateActual );
                    break;
                case BOOLEAN:
                    boolean lBooleanDefault = lPropertyDefault == null ? false : Boolean.parseBoolean( lPropertyDefault );
                    boolean lBooleanActual = lPreviousMetaDataMap.get( lPropertyPath, Boolean.class );
                    logMessage( pWorkflowContext, "BOOLEAN: Name: '{}', Value: '{}'", lPropertyPath, lBooleanActual );
                    lTarget.put( lMetaDataPropertyName, !lPropertyExists ? lBooleanDefault : lBooleanActual );
                    break;
                case LONG:
                    long lLongDefault = lPropertyDefault == null ? 0L : Long.parseLong( lPropertyDefault );
                    long lLongActual = !lPropertyExists ? lLongDefault : lPreviousMetaDataMap.get( lPropertyPath, Long.class );
                    logMessage( pWorkflowContext, "LONG: Name: '{}', Value: '{}'", lPropertyPath, lLongActual );
                    lTarget.put( lMetaDataPropertyName, lLongActual );
                    break;
                default:
                    throw new WorkflowException( "Given type: " + lType + " isn't supported" );
            }
        }
    }
}
