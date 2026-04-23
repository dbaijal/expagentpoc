package com.lifetechnologies.services.workflow;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.HistoryItem;
import com.adobe.granite.workflow.exec.ParticipantStepChooser;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.Workflow;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.adobe.granite.workflow.model.WorkflowNode;
import com.day.cq.mailer.MailService;
import com.headwire.cloudwords.services.CloudwordsManager;
import com.lifetechnologies.services.email.api.EmailService;
import com.lifetechnologies.services.email.api.Notification;
import com.lifetechnologies.services.email.api.NotificationTemplate;
import com.lifetechnologies.services.util.MetaDataMapWrapper;

/**
 * Workflow process that deletes the given Node.
 *
 * @author Andreas Schaefer
 */
@Component
@Service( WorkflowProcess.class )
@Reference( name = "ParticipantStepChooser", referenceInterface = ParticipantStepChooser.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC )
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Emails Notifications from the Workflow Process." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Email Notifications Workflow" )
    }
)
public class EmailNotificationsHandler
    extends AbstractResourceWorkflowProcess
{

    protected List<ParticipantStepChooser> mParticipantStepChooserList = new CopyOnWriteArrayList();

    @Reference
    protected MailService mMailService;

//    @Reference
//    protected UserManagerFactory mUserManagerFactory;

    @Reference
    protected SlingRepository mSlingRepository;

    //@Reference
    //protected WorkflowService mWorkflowService;
    
    @Reference
	private CloudwordsManager mCloudwordsManager;

//AS NOTE: We might want or need to reactivate this later so keep it around for now
//    @Reference
//    protected PreferencesServiceFactory mPreferencesServiceFactory;
//
//    @Reference
//    protected ResourceBundleProvider mResourceBundleProvider;

    @Reference
    protected EmailService mEmailService;

    protected Session mSession;

    public void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        if( mMailService == null ) {
            mLogger.error( "Mail Service is not Configured. Cannot send an Notification Email" );
		} else {
			
			ResourceResolver rr = null;
			try {
				Map<String, Object> param = new HashMap<String, Object>();
		        param.put(ResourceResolverFactory.SUBSERVICE, "thermofisherservicewfuser");
	            rr = mResourceResolverFactory.getServiceResourceResolver(param);
				mSession = rr.adaptTo(Session.class);
				final UserManager userManager= rr.adaptTo(UserManager.class);
				if (mSession != null && mSession.isLive()) {
					boolean lAlertTriggerSet = pWorkflowContext.getModelArguments()
							.containsKey("alertTriggerPropertyName");
					String lErrorMessagePropertyName = lAlertTriggerSet
							? Util.getArgument(pWorkflowContext.getModelArguments(), "alertTriggerPropertyName") : "";
					logMessage(pWorkflowContext, "Error Message Property Name: {}", lErrorMessagePropertyName);
					if (lErrorMessagePropertyName != null) {
						Arguments lWorkflowArguments = pWorkflowContext.getWorkflowArguments();
						logMessage(pWorkflowContext, "Workflow Arguments: {}", lWorkflowArguments);
						if (lAlertTriggerSet) {
							String lErorMessage = lWorkflowArguments.get(lErrorMessagePropertyName, false);
							logMessage(pWorkflowContext, "Error Message: {}", lErorMessage);
							if (lErorMessage != null && lErorMessage.trim().length() > 0) {
								sendNotification(pWorkflowContext, lErorMessage,userManager);
							}
						} else {
							sendNotification(pWorkflowContext, "",userManager);
						}
					}
				}else{
					mLogger.error( "execute0 :: Failed to send Email :: No session Available");
				}
			} catch (Exception ex) {
				mLogger.error( "execute0 :: Failed to send Email", ex );
			} finally {
				if(mSession!= null && mSession.isLive()){
					mSession.logout();
					mSession = null;
				}
				if(rr!=null && rr.isLive())
				{
					rr.close();
				}
			}
		}
    }

    private void sendNotification( WorkflowContext pWorkflowContext, String pErrorMessage, UserManager lUserManager ) {
        WorkflowSession lWorkflowSession = null;
        Workflow lWorkflow;
        try {
           // UserManager lUserManager = ((JackrabbitSession) mSession).getUserManager(); //mUserManagerFactory.createUserManager( mSession );
            Authorizable lInitiator = getAuthorizable( pWorkflowContext.getWorkItem().getWorkflow().getInitiator(), pWorkflowContext, lUserManager );
            WorkItem lItem = pWorkflowContext.getWorkItem();
            WorkflowSession lSession = pWorkflowContext.getWorkflowSession();
            List<HistoryItem> lHistoryList = lSession.getHistory( lItem.getWorkflow() );
            ListIterator<HistoryItem> i = lHistoryList.listIterator( lHistoryList.size() - 1 );
            Authorizable lParticipant = null;
            while( i.hasPrevious() ) {
                HistoryItem lHistoryItem = i.previous();
                WorkflowNode lWorkflowNode = lHistoryItem.getWorkItem().getNode();
//                mLogger.debug( "Last History, Workflow Node Id: '{}', Type: '{}'", lWorkflowNode.getId(), lWorkflowNode.getType() );
                if(
                    WorkflowNode.TYPE_PARTICIPANT.equalsIgnoreCase( lWorkflowNode.getType() ) ||
                    WorkflowNode.TYPE_DYNAMIC_PARTICIPANT.equalsIgnoreCase( lWorkflowNode.getType() )
                ) {
                    lParticipant = getAuthorizable( lHistoryItem.getUserId(), pWorkflowContext, lUserManager );
//                    mLogger.debug( "Found Participant User: '{}'", lParticipant.getID() );
                    break;
                }
            }
            //lWorkflowSession = getWorkflowSession( pWorkflowContext );
            WorkItem lWorkItem = pWorkflowContext.getWorkItem();
            lWorkflow = lWorkItem != null ? lWorkItem.getWorkflow() : null;
            logMessage( pWorkflowContext, "Workflow: {}", lWorkflow );
            if( lWorkflow != null ) {
                Set lAuthorizables = getParticipants( lWorkItem, pWorkflowContext, lUserManager );
                lAuthorizables.add( lInitiator );
                logMessage( pWorkflowContext, "sendNotification(), authorizables {}", lAuthorizables );
                for( Iterator i$ = lAuthorizables.iterator(); i$.hasNext(); ) {
                    Authorizable lUser = (Authorizable)i$.next();
                    /* CQ Jira 702:Translation Workflow emails - contain the Project Name link rather than the Pages for Activation 
                     * Modified code to deal with the bug where the urls are incorrect for translation workflows
                     */
                    // check if it is a translation workflow
                    String workflowTitle=pWorkflowContext.getWorkItem().getWorkflow().getWorkflowModel().getTitle();
                    String translationWorkflowUrl = "";
                    if (workflowTitle.toLowerCase().indexOf("translation") != -1) {
                    	String lPropertyPrefix = mCloudwordsManager.getWorkflowPropertyPrefix();
        				MetaDataMap mdm = pWorkflowContext.getWorkflowDataMap();
        				//get the base path
        				String basePath = mdm.get( lPropertyPrefix + "basePath", String.class );
        				//get the source Paths
        				String[] sourcePaths = mdm.get( lPropertyPrefix + "sourcePaths", new String[0] );
        				//get the target languages
        				String[] targetLanguagesList = mdm.get( lPropertyPrefix + "targetLanguages", new String[0] );
        				//get the target Paths
        				Map<String,String[]> targetPaths = mCloudwordsManager.getTargetPaths( mdm, lPropertyPrefix );
                    	//check if the emails are for failed: restore inheritance, rollout or cancel inheritance
                    	if (lWorkItem.getNode().getTitle().indexOf("Failed")!= -1) {
                    		NotificationTemplate lNotification = new NotificationTemplate();
    	                    configurationNotification( lNotification, pErrorMessage, lUser, pWorkflowContext, lInitiator, lParticipant,translationWorkflowUrl );
    	                    String lTemplatePath = pWorkflowContext.getModelArguments().get( "alertMessageTemplatePath", false );
    	                    lNotification.addSubstitutionVariable( NotificationTemplate.EMAIL_TEMPLATE_PATH, lTemplatePath );
    	                    String lEmailAddress = getEmailAddress( lUser );
    	                    lNotification.setEmailToAddress( lEmailAddress );
    	                    try {
    	                        logMessage( pWorkflowContext, "sendNotification(), notification: '{}', send email to user: '{}'", lNotification, lEmailAddress );
    	                        mEmailService.sendEmail( lNotification );
    	                        logMessage( pWorkflowContext, "sendNotification(), send email done" );
    	                    } catch( RuntimeException e ) {
    	                        mLogger.warn( "Failed to send Email", e );
    	                    }
                    	} else {
                    		//Loop through each source paths (pages) 
	           				 for(String path : sourcePaths) {
	           					 //replace the source path with the the target path
	           					 for(String targetlLanguage : targetLanguagesList) {
	           						 String[] tList = targetPaths.get(targetlLanguage);
	           						 for(String targetPath : tList) {
	           							 NotificationTemplate lNotification = new NotificationTemplate();
	           							 translationWorkflowUrl = path.replace(basePath,targetPath);
	           							 configurationNotification( lNotification, pErrorMessage, lUser, pWorkflowContext, lInitiator, lParticipant,translationWorkflowUrl );
	           							 String lTemplatePath = pWorkflowContext.getModelArguments().get( "alertMessageTemplatePath", false );
	           			                 lNotification.addSubstitutionVariable( NotificationTemplate.EMAIL_TEMPLATE_PATH, lTemplatePath );
	           			                 String lEmailAddress = getEmailAddress( lUser );
	           			                 lNotification.setEmailToAddress( lEmailAddress );
	           			                 try {
	           			                        logMessage( pWorkflowContext, "sendNotification(), notification: '{}', send email to user: '{}'", lNotification, lEmailAddress );
	           			                        mEmailService.sendEmail( lNotification );
	           			                        logMessage( pWorkflowContext, "sendNotification(), send email done" );
	           			                 } catch( RuntimeException e ) {
	           			                        mLogger.warn( "Failed to send Email", e );
	           			                 }
	           						 }//for(String targetPath : tList)
	           						 
	           					 }//for(String targetlLanguage : targetLanguagesList)
	           				 }//for(String path : sourcePaths)
                    		
                    	}
                    	
                    	
                    	
                    	
                    	
                    	
        				
                    	
                    } else {
	                    NotificationTemplate lNotification = new NotificationTemplate();
	                    configurationNotification( lNotification, pErrorMessage, lUser, pWorkflowContext, lInitiator, lParticipant,translationWorkflowUrl );
	                    String lTemplatePath = pWorkflowContext.getModelArguments().get( "alertMessageTemplatePath", false );
	                    lNotification.addSubstitutionVariable( NotificationTemplate.EMAIL_TEMPLATE_PATH, lTemplatePath );
	                    String lEmailAddress = getEmailAddress( lUser );
	                    lNotification.setEmailToAddress( lEmailAddress );
	                    try {
	                        logMessage( pWorkflowContext, "sendNotification(), notification: '{}', send email to user: '{}'", lNotification, lEmailAddress );
	                        mEmailService.sendEmail( lNotification );
	                        logMessage( pWorkflowContext, "sendNotification(), send email done" );
	                    } catch( RuntimeException e ) {
	                        mLogger.warn( "Failed to send Email", e );
	                    }
                    }
                }
            } else {
                logMessage( pWorkflowContext, "unable to retrieve workflow from item: {}. aborting", lWorkItem );
            }
        } catch( AccessDeniedException e ) {
            logMessage( pWorkflowContext, "Failed to obtain User Manager", e );
        } catch( WorkflowException e ) {
            logMessage( pWorkflowContext, "Failed to obtain Workflow History", e );
        } catch (UnsupportedRepositoryOperationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (RepositoryException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
            if(lWorkflowSession != null) {
                lWorkflowSession.logout();
            }
        }
    }

    private Set getParticipants( WorkItem pItem, WorkflowContext pWorkflowContext, UserManager pUserManager ) throws RepositoryException {
        Set lReturn = new HashSet();
        if( pItem != null ) {
            String lUserId = pItem.getNode().getMetaDataMap().get( "PARTICIPANT", String.class );
            if( lUserId != null ) {
                Authorizable participant = getAuthorizable( lUserId, pWorkflowContext, pUserManager );
                lReturn.addAll( getParticipants( participant, pWorkflowContext ) );
            }
        }
        Arguments lArguments = pWorkflowContext.getModelArguments();
        if( lArguments != null ) {
            String lAuthorizablesValue = lArguments.get( "alertAuthorizables", false );
            if( lAuthorizablesValue != null ) {
                String[] lAuthorizables = lAuthorizablesValue.split( "," );
                for( String lAuthorizable: lAuthorizables ) {
                    Authorizable participant = getAuthorizable( lAuthorizable, pWorkflowContext, pUserManager );
                    lReturn.addAll( getParticipants( participant, pWorkflowContext ) );
                }
            }
            String lParticipantChooserValue = lArguments.get( "participantChooser", false );
            if( lParticipantChooserValue != null ) {
                for( ParticipantStepChooser lParticipantStepChooser: mParticipantStepChooserList ) {
                    if( lParticipantStepChooser.getClass().getName().equals( lParticipantChooserValue ) ) {
                        try {
                            String lParticipantId = lParticipantStepChooser.getParticipant( pItem, pWorkflowContext.getWorkflowSession(), pWorkflowContext.getModelDataMap() );
                            Authorizable lAuthorizable = getAuthorizable( lParticipantId, pWorkflowContext, pUserManager );
                            lReturn.addAll( getParticipants( lAuthorizable, pWorkflowContext ) );
                            break;
                        } catch( WorkflowException e ) {
                            mLogger.warn( "Failed to obtain Participant from Chooser: '{}", lParticipantStepChooser );
                        }
                    }
                }
            }
        }
        return lReturn;
    }

    private Set getParticipants( Authorizable pUserGroup, WorkflowContext pWorkflowContext ) throws RepositoryException {
        Set participants = new HashSet();
        if(pUserGroup != null)
            if(!pUserGroup.isGroup())
            {
                String email = getEmailAddress(pUserGroup);
                if(email != null)
                {
                    logMessage( pWorkflowContext, "extracted user {} with email {} from workflow step.", pUserGroup.getID(), email);
                    participants.add(pUserGroup);
                } else
                {
                    logMessage( pWorkflowContext, "extracted user {} which has no email, not sending notification t" +
                        "o this user."
                        , pUserGroup.getID());
                }
            } else
            {
                Group group = (Group)pUserGroup;
                Authorizable member;
                for( Iterator members = group.getMembers(); members.hasNext(); participants.addAll( getParticipants( member, pWorkflowContext ) ) ) {
                    member = (Authorizable)members.next();
                }

            }
        return participants;
    }

	public String getEmailAddress(Authorizable user)
			throws ValueFormatException, IllegalStateException, RepositoryException {
		String lReturn = null;
		if (user != null && user.hasProperty("./profile/email")) {
			Value[] vals = user.getProperty("./profile/email");
			if (vals != null && vals.length > 0) {
				lReturn = vals[0].getString();
			}
		}
		mLogger.trace("Primary Email Address: '{}'", lReturn);
		if ((lReturn == null || lReturn.length() == 0) && user!=null&&user.hasProperty("rep:e-mail")) {
			Value[] vals = user.getProperty("rep:e-mail");
			mLogger.trace("Rep: Email Address: '{}'", vals);
			if (vals != null) {
				lReturn = vals[0].getString();
			}
		}

		return lReturn;
	}

//AS NOTE: We might want or need to reactivate this later so keep it around for now
//    public ResourceBundle getUserResourceBundle( String pUserId, WorkflowContext pWorkflowContext ) {
//        javax.jcr.Credentials credentials;
//        if( mSession != null ) {
//            credentials = new SimpleCredentials( pUserId, new char[0] );
//            Session userSession = null;
//            try {
//                userSession = mSession.impersonate( credentials );
//                PreferencesService prefs = mPreferencesServiceFactory.getPreferencesService( userSession );
//                ResourceBundle resourcebundle;
//                String language = prefs.get().get( "platform/language" );
//                if( language != null && language.length() > 0 ) {
//                    resourcebundle = mResourceBundleProvider.getResourceBundle( new Locale( language) );
//                    return resourcebundle;
//                }
//            } catch( PathNotFoundException e ) {
//                logMessage( pWorkflowContext, "Could not get resource bundle for " + pUserId + ", path does not exist", e );
//            } catch( RepositoryException e ) {
//                logMessage( pWorkflowContext, "Could not get resource bundle for user " + pUserId + ", impersonation failed", e );
//            } finally {
//                if( userSession != null ) {
//                    userSession.logout();
//                }
//            }
//        } else {
//            logMessage( pWorkflowContext, "Could not get resource bundle for {}, repository unavailable.", pUserId );
//        }
//        return null;
//    }

    public Authorizable getAuthorizable( String pUserId , WorkflowContext pWorkflowContext, UserManager pUserManager ) {
        Authorizable lReturn = null;
        if( pUserId != null ) {
            try {
                if( "system".equals( pUserId ) ) { pUserId = "admin"; }
                lReturn = pUserManager.getAuthorizable( pUserId );
            } catch( RepositoryException e ) {
                logMessage( pWorkflowContext, "user manager did not find user {}", pUserId );
            }
        } else {
            logMessage( pWorkflowContext, "User Id was not provided" );
        }
        return lReturn;
    }

    /*
    private WorkflowSession getWorkflowSession( WorkflowContext pWorkflowContext ) {
        try {
            Session lSession =  mSlingRepository.loginAdministrative(null);
            return mWorkflowService.getWorkflowSession( lSession );
        } catch( RepositoryException e ) {
            logMessage( pWorkflowContext, "could not get workflow session as repository session is unavailable", e );
            return null;
        }
    }
    */

    void activate( ComponentContext context ) {
        mLogger.trace( "ENH Activate Called" );
        super.activate( context );
//        try {
//            mSession = mSlingRepository.loginAdministrative( null );
//        } catch( RepositoryException e ) {
//            mLogger.error( "Could not obtain Session", e );
//        }
        mLogger.trace( "ENH Activate Done" );
    }

    protected void deactivate( ComponentContext context ) {
        mLogger.trace( "ENH Deactivate Called" );
        super.deactivate( context );
//        if( mSession != null ) {
//            mSession.logout();
//            mSession = null;
//        }
    }

    public void configurationNotification(
        Notification pNotification, String pErrorMessage, Authorizable pUser,
        WorkflowContext pWorkflowContext, Authorizable pInitiator, Authorizable pParticipant, String translationWorkflowUrl
    ) {
        WorkItem lWorkItem = pWorkflowContext.getWorkItem();
        // Comment History Handling
        try {
            WorkflowSession lSession = pWorkflowContext.getWorkflowSession();
            List<HistoryItem> lHistoryList = lSession.getHistory( lWorkItem.getWorkflow() );
            if( lHistoryList != null && !lHistoryList.isEmpty() ) {
                ListIterator<HistoryItem> i = lHistoryList.listIterator( lHistoryList.size() );
                StringBuffer lCommentHistory = new StringBuffer( "\n" );
                while( i.hasPrevious() ) {
                    HistoryItem lHistoryItem = i.previous();
                    String lUserId = lHistoryItem.getUserId();
                    String lComment = lHistoryItem.getComment();
                    if( lComment != null && lComment.trim().length() > 0 ) {
                        lCommentHistory.append( "User: " ).append( lUserId ).append( "\t" );
                        lCommentHistory.append( "Comment: " ).append( lComment ).append( "\n\n" );
                    }
                }
                String lStartComment = pWorkflowContext.getWorkflowDataMap().get(  "startComment", String.class );
                if( lStartComment != null && lStartComment.trim().length() > 0 ) {
                    lCommentHistory.append( "Start Comment: " ).append( lStartComment ).append( "\n\n" );
                }
                String lHistory = lCommentHistory.toString();
                logMessage( pWorkflowContext, "Comment History Placed: {}", lHistory );
                pWorkflowContext.getWorkflowDataMap().put( "comment.history", lHistory );
                pWorkflowContext.getWorkflowDataMap().put( "commentHistory", lHistory );
            }
        } catch( WorkflowException e ) {
            mLogger.warn( "Failed to obtain Workflow History", e );
        }

		try {
			// Handle Workflow Configuration Service Values
			pNotification.addSubstitutionVariable("host.prefix",
					mWorkflowConfigurationService.getProperty("host.prefix", "http://author1.lifetechnologies.com"));
			pNotification.addSubstitutionVariable("instance.data.preview.base.url", mWorkflowConfigurationService
					.getProperty("preview.base.url", "http://preview.lifetechnologies.com"));

			pNotification.addSubstitutionVariable("data.errorMessage", pErrorMessage);
			if (pUser != null) {
				configurationUser("participant", pNotification, pUser);
			}
			if (pParticipant != null) {
				configurationUser("previous.participant", pNotification, pParticipant);
			}
			if (pInitiator != null) {
				configurationUser("initiator", pNotification, pInitiator);
			}
			if (lWorkItem != null) {
				pNotification.addSubstitutionVariable("item.id", lWorkItem.getId());
				pNotification.addSubstitutionVariable("item.node.id", lWorkItem.getNode().getId());
				pNotification.addSubstitutionVariable("item.node.title", lWorkItem.getNode().getTitle());
				pNotification.addSubstitutionVariable("item.node.type", lWorkItem.getNode().getType());
				pNotification.addSubstitutionVariables("item.node.data.",
						new MetaDataMapWrapper(lWorkItem.getNode().getMetaDataMap()));
				pNotification.addSubstitutionVariables("item.workflow.data.",
						new MetaDataMapWrapper(lWorkItem.getWorkflowData().getMetaDataMap()));
				pNotification.addSubstitutionVariables("item.data.",
						new MetaDataMapWrapper(lWorkItem.getMetaDataMap()));
			}
			Workflow lWorkflow = lWorkItem.getWorkflow();
			pNotification.addSubstitutionVariable("instance.id", lWorkflow.getId());
			pNotification.addSubstitutionVariable("instance.state", lWorkflow.getState());
			pNotification.addSubstitutionVariables("instance.data.",
					new MetaDataMapWrapper(lWorkflow.getMetaDataMap()));

            pNotification.addSubstitutionVariable("instance.data.dam.path", lWorkflow.getMetaDataMap().get("dam.path", String.class));
			
		    //special case for sdl workflow manipulation, targetPageActionsFailures - property that contains target paths that failed on target page actions
			if (lWorkflow.getMetaDataMap().containsKey("targetPageActionsFailures")) {
				//convert the comma-delimited list to delimited by  carriage return
				String targetPageActionsFailuresList = lWorkflow.getMetaDataMap().get("targetPageActionsFailures", String.class);
				String[] targetList = targetPageActionsFailuresList.split(",");
				
				StringBuffer buffer = new StringBuffer();
				for(String target : targetList){
					buffer.append(target);
					buffer.append("\n");
					
				}
				String newFailureProp = buffer.toString();
				pNotification.addSubstitutionVariable("instance.data.sdlTargetFailures", buffer.toString());
				
				
			}
			
			pNotification.addSubstitutionVariable("model.title", lWorkflow.getWorkflowModel().getTitle());
			pNotification.addSubstitutionVariable("model.id", lWorkflow.getWorkflowModel().getId());
			pNotification.addSubstitutionVariable("model.version", lWorkflow.getWorkflowModel().getVersion());
			pNotification.addSubstitutionVariables("model.data.", lWorkflow.getWorkflowModel().getMetaDataMap());

			WorkflowData lWorkflowData = lWorkflow.getWorkflowData();
			pNotification.addSubstitutionVariables("data.", lWorkflowData.getMetaDataMap());
			pNotification.addSubstitutionVariable("payload.data", lWorkflowData.getPayload() + "");
			pNotification.addSubstitutionVariable("payload.type", lWorkflowData.getPayloadType());
			if (!translationWorkflowUrl.isEmpty()) {
				pNotification.addSubstitutionVariable("payload.path", translationWorkflowUrl + "");
			} else if ("JCR_PATH".equals(lWorkflowData.getPayloadType())) {
				pNotification.addSubstitutionVariable("payload.path", lWorkflowData.getPayload() + "");
			}
		} catch (RepositoryException e) {
			 mLogger.error( "Failed to retreive User's properties", e );
		}
	}

    public void configurationUser( String pPrefix, Notification pNotification, Authorizable pAuthorizable  ) throws RepositoryException {
        if( !pPrefix.endsWith( "." ) ) {
            pPrefix += ".";
        }
        String lKey;
        for( Iterator i = pAuthorizable.getPropertyNames(); i.hasNext(); ) {
            lKey = (String) i.next();
            Value[] vals =  pAuthorizable.getProperty( lKey ) ;
            if(vals!=null && vals.length>0){
            	pNotification.addSubstitutionVariable( pPrefix + lKey,vals[0].getString());
            }
        }

          //Profile profile = pAuthorizable.getProfile();
//        for( Iterator i$ = profile.keySet().iterator(); i$.hasNext(); ) {
//            lKey = (String) i$.next();
//            pNotification.addSubstitutionVariable( pPrefix + lKey, profile.get( lKey, String.class ) );
//        }

        pNotification.addSubstitutionVariable( pPrefix + "id", pAuthorizable.getID() );
        pNotification.addSubstitutionVariable( pPrefix + "name", pAuthorizable.getPrincipal().getName() );
        pNotification.addSubstitutionVariable( pPrefix + "home", pAuthorizable.getPath() );
    }

    public void bindParticipantStepChooser( ParticipantStepChooser chooser ) {
		if (chooser != null && !StringUtils.equals(chooser.getClass().getName(),
				"com.adobe.granite.workflow.core.process.RandomParticipantChooser")){
			mParticipantStepChooserList.add( chooser );
		}
    }

    public void unbindParticipantStepChooser( ParticipantStepChooser chooser ) {
        mParticipantStepChooserList.remove( chooser );
    }
}
