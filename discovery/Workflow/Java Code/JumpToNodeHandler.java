package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.Route;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.adobe.granite.workflow.model.WorkflowNode;
import com.adobe.granite.workflow.model.WorkflowTransition;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;

import java.util.List;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * Workflow process that Rejects a Workflow and Sends it Back.
 *
 * @author Andreas Schaefer
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Jumps to a given Node." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Jump To Node" )
    }
)
public class JumpToNodeHandler
    extends AbstractResourceWorkflowProcess
{

    public void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        WorkItem lItem = pWorkflowContext.getWorkItem();
        WorkflowSession lSession = pWorkflowContext.getWorkflowSession();
        WorkflowNode lCurrentWorkflowNode = lItem.getNode();
        if( lCurrentWorkflowNode != null ) {
            try {
                Arguments lArguments = pWorkflowContext.getModelArguments();
                // Get the target node
                String lTargetNodeName = Util.getArgument( lArguments, "targetnode" );
                WorkflowModel lModel = lItem.getWorkflow().getWorkflowModel();
                WorkflowNode lTargetWorkflowNode = getWorkflowNodeByName( lTargetNodeName, lModel );

                logMessage( pWorkflowContext, "Current WF Node, ID: {}, Title: {}", lCurrentWorkflowNode.getId(), lCurrentWorkflowNode.getTitle() );
                logMessage( pWorkflowContext, "Target WF Node, ID: {}, Title: {}", lTargetWorkflowNode.getId(), lTargetWorkflowNode.getTitle() );

                // See if there is a Back Route with the given Nodes
                WorkflowTransition lWorkflowTransition = findBackRouteTransition(
                    lSession, lItem, lCurrentWorkflowNode, lTargetWorkflowNode, true
                );
                Route lWorkflowRoute;
                if( lWorkflowTransition == null ) {
                    // No Back Route found so create a new a Transition and then a Route which isn't a Back Route
                    lWorkflowTransition = lModel.createTransition(
                        lCurrentWorkflowNode, lTargetWorkflowNode, null
                    );
                    lWorkflowRoute = new SimpleRoute( lWorkflowTransition, false );
                } else {
                    // Back Route found so mark it as such
                    lWorkflowRoute = new SimpleRoute( lWorkflowTransition, true );
                }
                mLogger.debug( "Workflow Transition Meta Data Map: '{}'", lWorkflowTransition.getMetaDataMap() );
                lSession.complete( lItem, lWorkflowRoute );
            } catch( IllegalArgumentException e ) {
                throw new WorkflowException( "Wrong Arguments", e );
            }
        }
    }

    WorkflowNode getWorkflowNodeByName( String pTargetNodeName, WorkflowModel pModel )
        throws WorkflowException {
        WorkflowNode lReturn = pModel.getNode( pTargetNodeName );
        if( lReturn == null  ) {
            List<WorkflowNode> lNodeList = pModel.getNodes();
            for( WorkflowNode lNode : lNodeList ) {
                if( pTargetNodeName.equalsIgnoreCase( lNode.getTitle() ) ) {
                    lReturn = lNode;
                    break;
                }
            }
            if( lReturn == null ) {
                throw new WorkflowException( "Target Workflow not found for Title: '" + pTargetNodeName + "'" );
            }
        }
        return lReturn;
    }

    WorkflowTransition findBackRouteTransition( WorkflowSession pSession, WorkItem pItem, WorkflowNode pStart, WorkflowNode pEnd, boolean pOnlyBackRoutes )
        throws WorkflowException {
        List<Route> lRoutes = pOnlyBackRoutes ? pSession.getBackRoutes( pItem,true ) : pSession.getRoutes( pItem ,true);
        for( Route lRoute: lRoutes ) {
            List<WorkflowTransition> lWorkflowTransitions = lRoute.getDestinations();
            for( WorkflowTransition lTransition: lWorkflowTransitions ) {
                if(
                    lTransition.getFrom().equals( pStart ) &&
                    lTransition.getTo().equals( pEnd )
                ) {
                    return lTransition;
                }
            }
        }
        return null;
    }
}
