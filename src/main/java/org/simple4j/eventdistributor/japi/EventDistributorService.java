package org.simple4j.eventdistributor.japi;

import java.util.List;

import org.simple4j.eventdistributor.beans.AppResponse;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.HealthCheck;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.beans.HealthCheck.Status;

public interface EventDistributorService
{

	public void setHealthCheck(Status status);
	public AppResponse<HealthCheck> getHealthCheck();
	public AppResponse<Long> postEvent(Event event);
	public AppResponse<Event> getEvent(String callerId, String eventId);
	public AppResponse<List<Event>> getEvents(String callerId, String startPosition, String numberOfRecords,
			String eventId, Event event);
	public AppResponse<Long> repostEvent(String eventIdStr, String createBy);
	public AppResponse<Long> republish(String publishIdStr, String createBy);
	public AppResponse<Long> abortEvent(String eventId, String updateBy);
	public void init();

}
