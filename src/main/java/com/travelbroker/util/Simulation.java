package com.travelbroker.util;

import java.util.concurrent.ThreadLocalRandom;

public class Simulation {
    public static int normalDistributionRandomValue(int mean) {
        return normalDistributionRandomValue(mean, mean * 0.2);
    }
    public static int normalDistributionRandomValue(int mean, double standardDeviation) {
        double gauss = ThreadLocalRandom.current().nextGaussian() * standardDeviation + mean; // shift to mean
        return (int) Math.max(0, gauss);
    }
    public static void artificialLatency(int averageDelayMs) {
        int latency = normalDistributionRandomValue(averageDelayMs);
        try { Thread.sleep(latency); }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    public static boolean artificialFailure(double failureProbability) {
        return ThreadLocalRandom.current().nextDouble() < failureProbability;
    }
}
