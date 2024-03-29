package sunlabs.titan.node.services.census;

import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Random;


import sunlabs.asdf.util.Time;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.node.services.Census;
import sunlabs.titan.node.services.census.CensusService;
import sunlabs.titan.node.services.census.CensusReportGenerator;
import sunlabs.titan.util.OrderedProperties;

public class BasicReport implements CensusReportGenerator {
    protected OrderedProperties report;
    protected TitanService service;
    
    private Random random;

    public BasicReport(Census census) {
        this.report = new OrderedProperties();
        this.service = census;
        this.random = new Random();

        this.report.setProperty(Census.OperatingSystemArchitecture, ManagementFactory.getOperatingSystemMXBean().getArch());
        this.report.setProperty(Census.OperatingSystemName, ManagementFactory.getOperatingSystemMXBean().getName());
        this.report.setProperty(Census.OperatingSystemVersion, ManagementFactory.getOperatingSystemMXBean().getVersion());
        this.report.setProperty(Census.OperatingSystemAvailableProcessors, ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
    }

    public Properties report() {
        // Fill in this node's dynamic Census properties here...
        this.report.setProperty(Census.SenderTimestamp, Time.millisecondsInSeconds(System.currentTimeMillis()));
        this.report.setProperty(Census.TimeToLiveSeconds, this.service.getNode().getConfiguration().asLong(CensusService.ReportRateSeconds) * 2);
        this.report.setProperty(Census.OperatingSystemLoadAverage, ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        this.report.setProperty(Census.RandomNumber, Math.abs(this.random.nextLong()) % 100);
        return this.report;
    }
}
