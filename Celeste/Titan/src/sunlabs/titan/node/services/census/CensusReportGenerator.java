package sunlabs.titan.node.services.census;

import java.util.Properties;

/**
 * Classes implementing this interface produce information for the Census service.
 * 
 */
public interface CensusReportGenerator {
    
    public Properties report();
}
