package org.simple4j.eventdistributor.japi;

import java.util.List;

import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.HealthCheck;
import org.simple4j.eventdistributor.beans.HealthCheck.Status;

/**
 * Business logic interface definition
 */
public interface EventDistributorService
{

	public void setHealthCheck(Status status);
	public AppResponse<HealthCheck> getHealthCheck();

	public AppResponse<Long> postEvent(Event event, String callerId, String userId);
	public AppResponse<Event> getEvent(String callerId, String eventId);
	public AppResponse<List<Event>> getEvents(String callerId, String startPosition, String numberOfRecords,
			Event event);
	public AppResponse<Long> repostEvent(String eventIdStr, String callerId, String userId);
	public AppResponse<Long> republish(String publishIdStr, String callerId, String userId);
	public AppResponse<Long> abortEvent(String eventId, String callerId, String userId);
	public void init();

}
