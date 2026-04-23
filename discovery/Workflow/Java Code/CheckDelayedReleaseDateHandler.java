package com.lifetechnologies.services.workflow;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.lifetechnologies.services.util.Constants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;

import java.text.SimpleDateFormat;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * This Workflow Process is checking if there is a given Delayed Release Date and if
 * not it will add to now a given time interval
 */
@Component
@Service
@Properties(
    {
        @Property( name = SERVICE_DESCRIPTION, value = "Checks the Delayed Release Date." ),
        @Property( name = SERVICE_VENDOR, value = "Lifetech" ),
        @Property( name = "process.label", value = "Lifetech: Delayed Release Date Checker" )
    }
)
public class CheckDelayedReleaseDateHandler
    extends AbstractResourceWorkflowProcess
{
    public static final long SECOND = 1000;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;

    public static final String OFFSET_VALUE_NAME = "delayOffset";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat( "MM/dd/yyyy 'at' HH:mm:ss" );

    void execute0( Resource pResource, WorkflowContext pWorkflowContext )
        throws WorkflowException
    {
        WorkItem lWorkItem = pWorkflowContext.getWorkItem();
        MetaDataMap lTarget = lWorkItem.getWorkflowData().getMetaDataMap();
        Long lAbsoluteTime = lTarget.get( Constants.ABSOLUTE_TIME, Long.class );
        logMessage( pWorkflowContext, "Raw Absolute Time: {}", lAbsoluteTime );
        if( lAbsoluteTime != null && lAbsoluteTime > 5 ) {
            logMessage( pWorkflowContext, "Contained Absolute Time => we are fine and can proceed" );
        } else {
            long lOffset = 1 * HOUR;
            if( pWorkflowContext.getModelArguments().containsKey( OFFSET_VALUE_NAME ) ) {
                String lOffsetValue = pWorkflowContext.getModelArguments().get( OFFSET_VALUE_NAME );
                long lValue = parseTime( lOffsetValue );
                lOffset = lValue > 0 ? lValue : lOffset;
            }
            lAbsoluteTime = System.currentTimeMillis() + lOffset;
            logMessage( pWorkflowContext, "Set a new Absolute Time: '{}'", lAbsoluteTime );
            lTarget.put( Constants.ABSOLUTE_TIME, lAbsoluteTime );
        }
    }

    /**
     * Parses a given time string looking for suffixes that indicates time multipliers
     * like seconds (s), minutes (min), hours (h) or days (d). The suffixes are not case
     * sensitive.
     *
     * This method supports multiple different time units and will accept that pattern:
     *
     * (<number>)[ ](ms|s\min\h\d))*(<number>)[ ][(ms|s\min\h\d)]
     *
     * If the last unit is omitted then milliseconds are assumed. Any other omitted units
     * are making the parsing fail. So these are valid settings:
     * 1d 5 h 7min 55s 100ms
     * 1d 5h 7 min 55s 100
     * 100
     * 1 d 5h
     *
     * But these are not valid
     * 1 5 d
     * 100 d d
     *
     * @param pTimePeriod Period of time string in the format of "number"<suffix> where suffix can be
     *                    'ms' milliseconds, 's' seconds, 'h' hours and 'd' days. If none is given
     *                    milliseconds are assumed.
     *
     * @return Time in milliseconds.
     *
     * @throws WorkflowException If the parsing failed of the given time period is empty or cannot be parsed.
     */
    public static long parseTime( String pTimePeriod )
        throws WorkflowException
    {
        long lReturn = 0;
        String lOriginal = pTimePeriod;
        if( pTimePeriod == null || pTimePeriod.trim().length() == 0 ) {
            throw new WorkflowException( "Given Time period is not set" );
        }
        pTimePeriod = pTimePeriod.trim().toLowerCase();
        String[] lParts = pTimePeriod.split( " " );
        int i = 0;
        if( lParts.length > 0 ) {
            do {
                String lPart = lParts[ i++ ].trim();
                long lFactor = -1;
                if( lPart.endsWith( "ms" ) ) {
                    lFactor = 1;
                    lPart = lPart.substring( 0, lPart.length() - 2 );
                } else if( lPart.endsWith( "s" ) ) {
                    lFactor = SECOND;
                    lPart = lPart.substring( 0, lPart.length() - 1 );
                } else if( lPart.endsWith( "min" ) ) {
                    lFactor = MINUTE;
                    lPart = lPart.substring( 0, lPart.length() - 3 );
                } else if( lPart.endsWith( "h" ) ) {
                    lFactor = HOUR;
                    lPart = lPart.substring( 0, lPart.length() - 1 );
                } else if( lPart.endsWith( "d" ) ) {
                    lFactor = DAY;
                    lPart = lPart.substring( 0, lPart.length() - 1 );
                }
                if( lFactor < 0 ) {
                    if( i < lParts.length ) {
                        String lUnits = lParts[ i++ ].trim();
                        if( lUnits.equals( "ms" ) ) {
                            lFactor = 1;
                        } else if( lUnits.equals( "s" ) ) {
                            lFactor = SECOND;
                        } else if( lUnits.equals( "min" ) ) {
                            lFactor = MINUTE;
                        } else if( lUnits.equals( "h" ) ) {
                            lFactor = HOUR;
                        } else if( lUnits.equals( "d" ) ) {
                            lFactor = DAY;
                        }
                    }
                    if( lFactor < 0 ) {
                        // Last Entry can omit the Unit
                        if( i >= lParts.length - 1 ) {
                            lFactor = 1;
                        }
                    }
                }
                if( lFactor < 0 ) {
                    throw new WorkflowException( "Given Time could not been parsed: '" + lOriginal + "' because it does not follow the pattern" );
                } else {
                    try {
                        Long lTime = Long.parseLong( lPart );
                        lReturn += lTime * lFactor;
                    } catch( NumberFormatException e ) {
                        throw new WorkflowException( "Given Time could not been parsed: '" + lOriginal + "'", e );
                    }
                }
            } while( i < lParts.length );
        } else {
            throw new WorkflowException( "Given Time is empty: '" + lOriginal + "'" );
        }
        return lReturn;
    }
}
