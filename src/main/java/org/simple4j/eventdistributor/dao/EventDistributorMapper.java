package org.simple4j.eventdistributor.dao;

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.PublishAttempt;

public interface EventDistributorMapper
{

	public List<Event> getEvents(@Param("event") Event event, @Param("startPosition") int startPosition, @Param("numberOfRecords") int numberOfRecords);

	public void insertEvent(@Param("event") Event event);

	public Long getEventId();

	public void insertPublishAttempt(@Param("publishAttempt") PublishAttempt publishAttempt);

	public Long getPublishAttemptId();

	public Event getEvent(@Param("eventId") long eventId);

	public EventStatus getEventStatus(@Param("eventId") long eventId);

	public PublishAttempt getPublishAttempt(@Param("publishId") long publishId);

	public void insertEventTarget(@Param("eventId") Long eventId, @Param("targetId") String targetId);

	public void lockEvents(@Param("hostname") String hostname, @Param("batchSize") int batchSize, @Param("statusExpiryTimeZonedDateTime") ZonedDateTime statusExpiryTimeZonedDateTime, @Param("currentTime") ZonedDateTime currentTime);

	public List<Event> fetchLockedEvents(@Param("hostname") String hostname);

	public void updatePublishAttempt(@Param("publishAttempt") PublishAttempt publishAttempt);

	public void updateEvent(@Param("event") Event event);

	public List<Event> getEventsForDuplicateCheck(@Param("event") Event event, @Param("startPosition") int startPosition, @Param("numberOfRecords") int numberOfRecords);

	public int deleteIldRecords(@Param("cleanupAgingInDays") int cleanupAgingInDays, @Param("maxCleanupRecordCountPerDelete") int maxCleanupRecordCountPerDelete);

}
