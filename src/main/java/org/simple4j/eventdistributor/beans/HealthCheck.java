package org.simple4j.eventdistributor.beans;

public class HealthCheck
{
    public String groupId = null;
    public String artifactId = null;
    public String version = null;
    public Status status = null;
    public Status db = null;
    public Status configStatus = null;

    public enum Status
    {
        HEALTHY, UNHEALTHY
    }
}
