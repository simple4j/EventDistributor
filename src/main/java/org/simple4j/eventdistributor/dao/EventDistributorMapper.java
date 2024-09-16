package org.simple4j.eventdistributor.dao;

import java.time.ZonedDateTime;
import java.util.List;

import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.PublishAttempt;

public interface EventDistributorMapper
{

	public List<Event> getEvents(Event event, int startPosition, int numberOfRecords);

	public void insertEvent(Event event);

	public Long getEventId();

	public void insertPublishAttempt(PublishAttempt publishAttempt);

	public Long getPublishAttemptId();

	public Event getEvent(long eventId);

	public PublishAttempt getPublishAttempt(long publishId);

	public void insertEventTarget(Long eventId, String targetId);

	public void lockEvents(String hostname, int batchSize, ZonedDateTime statusExpiryTimeZonedDateTime, ZonedDateTime currentTime);

	public List<Event> fetchLockedEvents(String hostname);

	public void updatePublishAttempt(PublishAttempt publishAttempt);

	public void updateEvent(Event event);

	public List<Event> getEventsForDuplicateCheck(Event event, int startPosition, int numberOfRecords);


}
