package com.travelbroker.network;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Manual test runner for ZeroMQ tests that provides detailed output
 * and can be used to debug ZeroMQ communication problems.
 */
public class ZeroMQTestRunner {
    public static void main(String[] args) {
        // Create a summary listener to collect test results
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        
        // Create a launcher discovery request for each test class
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        selectClass(ZeroMQClientTest.class),
                        selectClass(ZeroMQFrameHandlingTest.class),
                        selectClass(ZeroMQIntegrationTest.class)
                )
                .build();
        
        // Create and execute the launcher
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        
        // Print the summary
        TestExecutionSummary summary = listener.getSummary();
        
        System.out.println("\n=== ZeroMQ Test Summary ===");
        System.out.println("Tests started: " + summary.getTestsStartedCount());
        System.out.println("Tests succeeded: " + summary.getTestsSucceededCount());
        System.out.println("Tests skipped: " + summary.getTestsSkippedCount());
        System.out.println("Tests failed: " + summary.getTestsFailedCount());
        
        // Print any failures
        if (summary.getTestsFailedCount() > 0) {
            System.out.println("\n=== Failed Tests ===");
            summary.getFailures().forEach(failure -> {
                System.out.println(failure.getTestIdentifier().getDisplayName());
                System.out.println("  " + failure.getException().getMessage());
            });
        }
    }
} 