package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.Route;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.adobe.granite.workflow.model.WorkflowNode;
import com.adobe.granite.workflow.model.WorkflowTransition;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.List;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * Workflow process that Jumps to a given Node if a given Property is set and to another
 * node if not set. If the met or not met target is empty then if will go to the next node
 * in the workflow.
 *
 * @author Andreas Schaefer
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Jumps to a given Node if a Property is Set or Not." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Jump To Node If Set" )
    }
)
public class JumpToNodeIfSetHandler
    extends JumpToNodeHandler
{

    public void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        WorkItem lItem = pWorkflowContext.getWorkItem();
        WorkflowSession lSession = pWorkflowContext.getWorkflowSession();
        MetaDataMap lMetaDataMap = pWorkflowContext.getModelDataMap();
        Arguments lArguments = Arguments.parse( lMetaDataMap );
        String lPropertyName = Util.getArgument( lArguments, "jumpPropertyName" );
        boolean lIfSet = Util.isOn( lArguments, "jumpIfSet", false );
        boolean lConditionMet = isConditionMet( pWorkflowContext.getWorkflowDataMap(), lPropertyName );
        logMessage( pWorkflowContext, "Property Name: {}", lPropertyName );
        logMessage( pWorkflowContext, "Jump if Set: {}", lIfSet );
        logMessage( pWorkflowContext, "Condition Met: {}", lConditionMet );
        String lTargetNodeMet = lArguments.get( "targetnode", false );
        String lTargetNodeNotMet = lArguments.get( "targetnodenotok", false );
        // This check makes sure that either Flag is set to Meet and the Condition is Met
        // or the Flag is set to NOT to Meet and Condition is NOT Met.
        //
        // If the Condition must be Met EXCLUSIVE OR Condition is NOT Met but NOT both
        if( lIfSet ^ !lConditionMet ) {
            if( lTargetNodeMet != null ) {
                logMessage( pWorkflowContext, "We jump because condition is true" );
                super.execute0( pResource, pWorkflowContext );
            } else {
                jumpToNextStep( pWorkflowContext );
            }
        } else {
            logMessage( pWorkflowContext, "Target for Condition Not Met: {}", lTargetNodeNotMet );
            if( lTargetNodeNotMet != null ) {
                WorkflowNode lCurrentWorkflowNode = lItem.getNode();
                WorkflowModel lModel = lItem.getWorkflow().getWorkflowModel();
                WorkflowNode lTargetWorkflowNode = getWorkflowNodeByName( lTargetNodeNotMet, lModel );
                WorkflowTransition lWorkflowTransition = findBackRouteTransition(
                    lSession, lItem, lCurrentWorkflowNode, lTargetWorkflowNode, false
                );
                if( lWorkflowTransition == null ) {
                    // If there is no Back Route then we need to create a new Transition
                    lWorkflowTransition = lModel.createTransition(
                        lCurrentWorkflowNode, lTargetWorkflowNode, null
                    );
                }
                // Because this route does not exist we need to create our own route
                Route lWorkflowRoute = new SimpleRoute( lWorkflowTransition, false );
                logMessage( pWorkflowContext, "Use the first transition '{}' to jump to", lWorkflowTransition );
                lSession.complete( lItem, lWorkflowRoute );
            } else {
                // Because this one is not auto advancing we need to do this by hand. This is the same
                // way as the Auto Advance Flag
                jumpToNextStep( pWorkflowContext );
            }
        }
    }

    /**
     * This method obtains the Route to the next Workflow Route (default is provided otherwsie the first
     * route found) and then jumps to than Step.
     *
     * @param pWorkflowContext Workflow Context
     *
     * @throws WorkflowException If the Jump fails
     */
    private void jumpToNextStep( WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        List<Route> lRoutes = pWorkflowContext.getWorkflowSession().getRoutes( pWorkflowContext.getWorkItem(),true );
        Route lWorkflowRoute = null;
        for( Route lRoute: lRoutes ) {
            if( lRoute.hasDefault() ) {
                lWorkflowRoute = lRoute;
                break;
            }
        }
        if( lWorkflowRoute == null ) {
            lWorkflowRoute = lRoutes.get( 0 );
        }
        logMessage( pWorkflowContext, "Use the this route '{}' to jump to", lWorkflowRoute );
        pWorkflowContext.getWorkflowSession().complete( pWorkflowContext.getWorkItem(), lWorkflowRoute );
    }

    /**
     * This checks if the condition is met for the Jump. For now we assume that the condition
     * is met if the property is not null and in case of a boolean also true and in the rest does not
     * map to an empty string or the string "null".
     *
     * @param pArguments List of Properties
     * @param pPropertyName Name of the Property to check against
     *
     * @return True if the condition is met otherwise false.
     */
    private boolean isConditionMet( MetaDataMap pMetaDataMap, String pPropertyName ) {
        Object lPropertyValue = pMetaDataMap.get( pPropertyName );
        if( lPropertyValue instanceof Value ) {
            Value lValue = (Value) lPropertyValue;
            try {
                switch( lValue.getType() ) {
                    case PropertyType.BOOLEAN:
                        lPropertyValue = lValue.getBoolean();
                        break;
                    case PropertyType.DOUBLE:
                        lPropertyValue = lValue.getDouble();
                        break;
                    case PropertyType.DATE:
                        lPropertyValue = lValue.getDate();
                        break;
                    default:
                        lPropertyValue = lValue.getString();
                }
            } catch( RepositoryException e ) {
                mLogger.debug( "Failed to obtain Value from Value: '{}'", lValue );
            }
        }
        boolean lReturn = false;
        if( lPropertyValue instanceof Boolean ) {
            lReturn = (Boolean) lPropertyValue;
        } else {
            String lCheck = lPropertyValue + "";
            lReturn = lPropertyValue != null && lCheck.trim().length() > 0 && !"null".equals( lCheck );
        }
        return lReturn;
    }
}
