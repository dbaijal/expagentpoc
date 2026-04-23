package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.lifetechnologies.services.util.Constants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * Workflow process that places the given Values as Parameter on the Workflow.
 *
 * @author Andreas Schaefer
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Place the Given Parameters on the Workflow." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Place Parameters" )
    }
)
public class PlaceParametersHandler
    extends AbstractResourceWorkflowProcess
{
    private static final Logger LOGGER = LoggerFactory.getLogger( PlaceParametersHandler.class );

    private static final String WFX_PARAMETER_PAIRS = "wfx.parameter.pairs";

    private enum TYPES { BOOLEAN, LONG, INTEGER, DOUBLE, DATE, STRING };

    @Override
    void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        List<String> lPairs = new ArrayList<String>();
        MetaDataMap lWorkflowDataMap = pWorkflowContext.getModelDataMap();
        // We cannot use the Arguments Class because it only supports single value entries
        String[] lValues = lWorkflowDataMap.get( WFX_PARAMETER_PAIRS, String[].class );
        if( lValues != null ) {
            lPairs.addAll( Arrays.asList( lValues ) );
        }
        String lOutdatedParameters = lWorkflowDataMap.get( Constants.PROCESS_ARGS, String.class );
        if( lOutdatedParameters != null && lOutdatedParameters.trim().length() > 0 ) {
            lPairs.addAll( Arrays.asList( lOutdatedParameters.split( "," ) ) );
        }
        LOGGER.debug( "Parameter Pairs: '{}'", lPairs );
        for( String lPair: lPairs ) {
            int lIndex = lPair.indexOf( "=" );
            LOGGER.debug( "Equal Sign at: '{}'", lIndex );
            // Only use the given value if a key and value is provided
            if( lIndex > 0 && lIndex < lPair.length() - 1 ) {
                String lKey = lPair.substring( 0, lIndex );
                String lValue = "";
                if( lIndex < lPair.length() - 1 ) {
                    lValue = lPair.substring( lIndex + 1 );
                }
                LOGGER.debug( "Key: '{}', Value: '{}'", lKey, lValue );
                Object lParameterValue = lValue;
                if( lValue.length() > 0 && lValue.charAt( 0 ) == '{' ) {
                        lIndex = lValue.indexOf( '}' );
                        if( lIndex > 0 ) {
                            String lTypeValue = lValue.substring( 1, lIndex ).toUpperCase();
                        if( lIndex < lValue.length() - 1 ) {
                            lValue = lValue.substring( lIndex + 1 );
                        } else {
                            lValue = null;
                        }
                        try {
                            TYPES lType = TYPES.valueOf( lTypeValue );
                            switch( lType ) {
                                case BOOLEAN:
                                    if( lValue == null ) {
                                        lParameterValue = Boolean.FALSE;
                                    } else {
                                        lParameterValue = Boolean.parseBoolean( lValue );
                                    }
                                    break;
                                case LONG:
                                    if( lValue == null ) {
                                        lParameterValue = new Long( -1 );
                                    } else {
                                        lParameterValue = Long.parseLong( lValue );
                                    }
                                    break;
                                case INTEGER:
                                    if( lValue == null ) {
                                        lParameterValue = new Integer( -1 );
                                    } else {
                                        lParameterValue = Integer.parseInt( lValue );
                                    }
                                    break;
                                case DOUBLE:
                                    if( lValue == null ) {
                                        lParameterValue = new Double( -1 );
                                    } else {
                                        lParameterValue = Double.parseDouble( lValue );
                                    }
                                    break;
                                case DATE:
                                    if( lValue == null ) {
                                        Calendar lCalendar = Calendar.getInstance();
                                        lCalendar.setTime( new Date() );
                                        lParameterValue = lCalendar;
                                    } else {
                                        Calendar lCalendar = Calendar.getInstance();
                                        lCalendar.setTime( new Date( Integer.parseInt( lValue ) ) );
                                        lParameterValue = lCalendar;
                                    }
                                    break;
                                case STRING:
                                    if( lValue == null ) {
                                        lParameterValue = "";
                                    } else {
                                        lParameterValue = lValue;
                                    }
                                    break;
                            }
                        } catch( IllegalArgumentException e ) {
                            LOGGER.debug( "Given Type: '" + lTypeValue + "' is not supported. Assuming String", e );
                        }
                    }
                }
                logMessage( pWorkflowContext, "Place Parameter '{}' set Value '{}'", lKey, lParameterValue );
                pWorkflowContext.getWorkflowDataMap().put(
                    lKey,
                    lParameterValue
                );
            }
        }
        LOGGER.debug( "WF Data Meta: '{}'", pWorkflowContext.getWorkflowDataMap() );
    }
}
