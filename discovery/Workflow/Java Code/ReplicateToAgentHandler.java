package com.lifetechnologies.services.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
//import org.apache.sling.api.resource.ResourceResolver;

import com.day.cq.replication.AccessDeniedException;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.collection.ResourceCollection;
import com.adobe.granite.workflow.collection.ResourceCollectionManager;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.headwire.cloudwords.services.CloudwordsManager;
import com.lifetechnologies.web.components.services.replication.ActivateToAgentManager;
import com.lifetechnologies.services.util.Constants;

/**
 * Workflow process reads the name of the Agent the Payload is replicated to and
 * replicates it to this one.
 * 
 * @author Andreas Schaefer
 */

@Component
@Service
@Properties({
		@Property(name = org.osgi.framework.Constants.SERVICE_DESCRIPTION, value = "Replicates to desired Agent."),
		@Property(name = org.osgi.framework.Constants.SERVICE_VENDOR, value = "Lifetech"),
		@Property(name = "process.label", value = "Lifetech: Replication to Agent") })
public class ReplicateToAgentHandler extends AbstractResourceWorkflowProcess {

	@Reference
	private CloudwordsManager mCloudwordsManager;

	@Reference
	private Replicator mReplicator;
	
	@Reference
	private ActivateToAgentManager replicateToAgentHandler;

	@Reference
	private ResourceCollectionManager mResourceCollectionManager;

	void execute0 (Resource pResource, WorkflowContext pWorkflowContext) throws WorkflowException {

		String lErrorMessage = null;
		String lPath = pResource.getPath();
		logMessage(pWorkflowContext, "***lPath: " + lPath);
		try {
			String lNodePath = null;

			if (lPath.startsWith("/content")) {

				String lPagePath = lPath + Constants.JCR_CONTENT_PATH;

				PageManager lPageManager = pWorkflowContext.getResourceResolver().adaptTo(PageManager.class);
				
				logMessage(pWorkflowContext, "Page Manager: " + lPageManager);

				Page lPage = lPageManager.getContainingPage(lPagePath);

				logMessage(pWorkflowContext, "Page: " + lPage);

				if (lPage != null) {
					lNodePath = lPage.getPath();

					logMessage(pWorkflowContext, "Page Path: " + lNodePath);
				} else {
					lNodePath = lPath;

					logMessage(pWorkflowContext, "Content but not a Page, using given path: " + lNodePath);
				}
			} else {
				lNodePath = lPath;

				logMessage(pWorkflowContext, "No Content is just using the given path: " + lNodePath);
			}

			if (lNodePath != null) {

				Session lSession = pWorkflowContext.getResourceResolver().adaptTo(Session.class);
				ResourceResolver resourceResolver = pWorkflowContext.getResourceResolver();
				ResourceCollection lResourceCollection = null;
				try {
					lResourceCollection = mResourceCollectionManager.createCollection((Node) lSession.getItem(lNodePath));
				} catch (PathNotFoundException e1) {
					logMessage(pWorkflowContext, "Failed to Replicate Path: '{}' due to path", lPath, e1);
					throw new WorkflowException("Failed to Replicate due to Path Not Found", e1);
				} catch (RepositoryException e1) {
					logMessage(pWorkflowContext, "Failed to Replicate Path: '{}' due to repository", lPath, e1);
					throw new WorkflowException("Failed to Replicate due to Login", e1);
				}
				List<String> lPaths = getPaths(lNodePath, lResourceCollection, pWorkflowContext);
				
				/* old code pre 5.6.1
				 * 
				 * 
				 ResourceCollection lResourceCollection = ResourceCollectionUtil.getResourceCollection(
						(Node) lSession.getItem(lNodePath), mResourceCollectionManager); 

				 List<String> lPaths = getPaths(lNodePath, lResourceCollection, pWorkflowContext);
				
				*/

				List<String> lAgentList = readAgentNames(pWorkflowContext.getModelDataMap(), lSession,
						pWorkflowContext);

				String lActivationType = pWorkflowContext.getModelArguments().get("activationType");

				ReplicationActionType lReplicationActionType = ReplicationActionType.ACTIVATE;

				if (lActivationType != null) {
					lActivationType = lActivationType.toUpperCase();

					try {
						lReplicationActionType = ReplicationActionType.valueOf(lActivationType);
					} catch (Exception e) {
						mLogger.error("Activation Type: {} is not valid", lActivationType);
					}
				}

				if (!lPaths.isEmpty() && !lAgentList.isEmpty()) {
					logMessage(pWorkflowContext, "agents :{}",lAgentList.size() );
					
					for (String lResourcePath : lPaths) {
					   try {
						replicateToAgentHandler.replicate(resourceResolver, lResourcePath, lReplicationActionType, lAgentList);
						}catch (AccessDeniedException  e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
							logMessage(pWorkflowContext, "AccessDeniedException:  ",  e);
							throw new WorkflowException("AccessDeniedException found", e);
						}
					   catch (WCMException e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
							logMessage(pWorkflowContext, "WCMException:  ",  e);
							throw new WorkflowException("WCM Exception found", e);
						} catch (ReplicationException e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
							logMessage(pWorkflowContext, "ReplicationException: ", e);
							throw new WorkflowException("Replication Exception found", e);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
							logMessage(pWorkflowContext, "Exception:  ",  e);
							throw new WorkflowException(" Exception found", e);
						}
					}   
				}
			} else {
				logMessage(pWorkflowContext, "Didn't found Node Path for: '{}' and so ignored it", lPath);
			}

			logMessage(pWorkflowContext, "After Replication");

			pWorkflowContext.getWorkflowDataMap().put("errorMessage", null); 

		} catch (WorkflowException e) {

			lErrorMessage = Util.createErrorMessage(e);

			pWorkflowContext.getWorkflowDataMap().put("errorMessage", lErrorMessage);

			// If we would throw an exception then the workflow will stop and we
			// do not receive the alert email
		}

		// The confirmation message is either the error message if set otherwise
		// the notification that

		// the publication was successful.

		String lConfirmationMessage = lErrorMessage;

		if (lConfirmationMessage == null) {
			lConfirmationMessage = "Payload: '" + lPath + "' was successfully published";
		}

		pWorkflowContext.getWorkflowDataMap().put("confirmationMessage", lConfirmationMessage);
	}

	private List<String> readAgentNames(MetaDataMap pArguments, Session pSession, WorkflowContext pWorkflowContext) throws WorkflowException {

		List<String> lReturn = new ArrayList<String>();

		logMessage(pWorkflowContext, "readAgentName(), start");

		Arguments lArguments = Arguments.parse(pArguments);

		String lAgentName = Util.getArgument(lArguments, Constants.AGENT);

		logMessage(pWorkflowContext, "readAgentName(), given Agent ID/Name: " + lAgentName);

		// Now check if the Agent can be found
		try {
			lReturn = replicateToAgentHandler.readAgentNames(pSession, lAgentName);
		} catch (RepositoryException e) {
			throw new WorkflowException("Failed to find Agent due to repository", e);
		}

		return lReturn;
	}

	private List<String> getPaths(String pPath, ResourceCollection pResourceCollection, WorkflowContext pWorkflowContext) {
		List<String> lReturn = new ArrayList<String>();

		String workflowTitle = pWorkflowContext.getWorkItem().getWorkflow().getWorkflowModel().getTitle();

		if (pResourceCollection == null) {

			// add logic to determine if it is a translation project or a
			// regular page
			logMessage(pWorkflowContext, "ResourceCollection NOT detected: {}", workflowTitle);
			if (workflowTitle.toLowerCase().indexOf("translation") != -1) {
				logMessage(pWorkflowContext, "translation project detected: {}", workflowTitle);

				// get the pages associated to the translation project
				String lPropertyPrefix = mCloudwordsManager.getWorkflowPropertyPrefix();

				MetaDataMap mdm = pWorkflowContext.getWorkflowDataMap();

				// get the base path
				String basePath = mdm.get(lPropertyPrefix + "basePath", String.class);

				// get the source Paths
				String[] sourcePaths = mdm.get(lPropertyPrefix + "sourcePaths", new String[0]);

				// get the target languages
				String[] targetLanguagesList = mdm.get(lPropertyPrefix + "targetLanguages", new String[0]);

				// get the target Paths
				Map<String, String[]> targetPaths = mCloudwordsManager.getTargetPaths(mdm, lPropertyPrefix);

				// Loop through each source paths (pages)
				for (String path : sourcePaths) {
					// replace the source path with the the target path
					for (String targetlLanguage : targetLanguagesList) {

						String[] tList = targetPaths.get(targetlLanguage);

						for (String targetPath : tList) {
							logMessage(pWorkflowContext, "page for activation: {}", path.replace(basePath, targetPath));
							lReturn.add(path.replace(basePath, targetPath));
						}
					}
				}
			} else {
				lReturn.add(pPath);
			}

		} else {
			logMessage(pWorkflowContext, "ResourceCollection detected: {}", pResourceCollection.getPath());

			try {

				List<Node> lMembers = pResourceCollection.list( new String[] {"cq:Page", "dam:Asset"});

				String lPath;

				for (Node lNode : lMembers) {
					lPath = lNode.getPath();
					lReturn.add(lPath);
				}

			} catch (RepositoryException re) {
				logMessage(pWorkflowContext, "Cannot build path list out of the resource collection: {}",
						pResourceCollection.getPath());
			}
		}

		return lReturn;
	}
}
