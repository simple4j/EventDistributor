package org.simple4j.eventdistributor.japi.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtils;
import org.simple4j.eventdistributor.beans.AppResponse;
import org.simple4j.eventdistributor.beans.ErrorDetails;
import org.simple4j.eventdistributor.beans.ErrorDetails.ErrorType;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.HealthCheck;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.beans.HealthCheck.Status;
import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.simple4j.eventdistributor.japi.EventDistributorService;
import org.simple4j.eventdistributor.japi.EventTargetRule;
import org.simple4j.eventdistributor.tasks.EventFetcher;
import org.simple4j.wsclient.caller.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDistributorServiceImpl implements EventDistributorService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private Status healthcheckStatus = Status.HEALTHY;

	private String groupId = null;
	private String artifactId = null;
	private String version = null;
	private LinkedList<EventTargetRule> eventTargetRules = null;
	private EventDistributorMapper eventDistributorMapper = null;
	private int duplicateCheckExpiryMillisec = 300000;
	
	private ScheduledThreadPoolExecutor eventFetcherExecutor = null;
	private int eventFetcherExecutorCoreThreadPoolSize = 1;

	private EventFetcher eventFetcher = null;
	private Map<String, Integer> source2NewEventCoolingTimeMillisec = null;

	private String hostName;
	
	public String getGroupId()
	{
		return groupId;
	}

	public void setGroupId(String groupId)
	{
		this.groupId = groupId;
	}

	public String getArtifactId()
	{
		return artifactId;
	}

	public void setArtifactId(String artifactId)
	{
		this.artifactId = artifactId;
	}

	public String getVersion()
	{
		return version;
	}

	public void setVersion(String version)
	{
		this.version = version;
	}

	public LinkedList<EventTargetRule> getEventTargetRules()
	{
		return eventTargetRules;
	}

	public void setEventTargetRules(LinkedList<EventTargetRule> eventTargetRules)
	{
		this.eventTargetRules = eventTargetRules;
	}

	public EventDistributorMapper getEventDistributorMapper()
	{
		if(this.eventDistributorMapper == null)
			throw new RuntimeException("eventDistributorMapper not configured");
		return eventDistributorMapper;
	}

	public void setEventDistributorMapper(EventDistributorMapper eventDistributorMapper)
	{
		this.eventDistributorMapper = eventDistributorMapper;
	}

	public int getDuplicateCheckExpiryMillisec()
	{
		return duplicateCheckExpiryMillisec;
	}

	public void setDuplicateCheckExpiryMillisec(int duplicateCheckExpiryMillisec)
	{
		this.duplicateCheckExpiryMillisec = duplicateCheckExpiryMillisec;
	}

	public EventFetcher getEventFetcher()
	{
		return eventFetcher;
	}

	public void setEventFetcher(EventFetcher eventFetcher)
	{
		this.eventFetcher = eventFetcher;
	}

	public Map<String, Integer> getSource2NewEventCoolingTimeMillisec()
	{
		if(this.source2NewEventCoolingTimeMillisec == null)
			this.source2NewEventCoolingTimeMillisec = new HashMap<String, Integer>();
		return source2NewEventCoolingTimeMillisec;
	}

	public void setSource2NewEventCoolingTimeMillisec(Map<String, Integer> source2NewEventCoolingPeriod)
	{
		this.source2NewEventCoolingTimeMillisec = source2NewEventCoolingPeriod;
	}

	public int getEventFetcherExecutorCoreThreadPoolSize()
	{
		return eventFetcherExecutorCoreThreadPoolSize;
	}

	public void setEventFetcherExecutorCoreThreadPoolSize(int eventFetcherExecutorCoreThreadPoolSize)
	{
		this.eventFetcherExecutorCoreThreadPoolSize = eventFetcherExecutorCoreThreadPoolSize;
	}

	@Override
	public void init()
	{
    	try
		{
			this.hostName = InetAddress.getLocalHost().getHostName();
		}
    	catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
        this.eventFetcherExecutor = new ScheduledThreadPoolExecutor(this.getEventFetcherExecutorCoreThreadPoolSize());
        this.eventFetcherExecutor.scheduleWithFixedDelay(this.getEventFetcher(),
                this.getEventFetcher().getSleepTimeInMillisec(), this.getEventFetcher().getSleepTimeInMillisec(), TimeUnit.MICROSECONDS);
	}
	
	@Override
	public void setHealthCheck(Status status)
	{
		if(status != null)
			this.healthcheckStatus = status;
		else
			this.healthcheckStatus = Status.HEALTHY;
	}

	@Override
	public AppResponse<HealthCheck> getHealthCheck()
	{
		AppResponse<HealthCheck> ret = new AppResponse<HealthCheck>();
		HealthCheck healthCheck = new HealthCheck();
		healthCheck.configStatus = this.healthcheckStatus;
		//TODO: need to do other healthchecks
		ret.responseObject = healthCheck ;
		return ret;
	}

	@Override
	public AppResponse<Long> postEvent(Event event)
	{

		Event inputClone = new Event();
		try
		{
			BeanUtils.copyProperties(inputClone, event);
		} catch (IllegalAccessException | InvocationTargetException e)
		{
			throw new RuntimeException(e);
		}

		Instant duplicateCheckEndTimeInstant = Instant.ofEpochMilli(System.currentTimeMillis() + this.getDuplicateCheckExpiryMillisec());
		ZonedDateTime duplicateCheckEndTimeZonedDateTime = ZonedDateTime.ofInstant(duplicateCheckEndTimeInstant, ZoneId.systemDefault());
		inputClone.setCreateTime(ZonedDateTime.now());
		
		List<Event> events = this.getEventDistributorMapper().getEventsForDuplicateCheck(inputClone, 1, 100);
		
		if(events != null && events.size() > 0)
		{
			for (Event eventFromDB : events)
			{
				event.setDuplicateCheckEndTime(eventFromDB.getDuplicateCheckEndTime());
				event.setStatus(EventStatus.DUPLICATE);
				
				if(EventStatus.DUPLICATE.equals(eventFromDB.getStatus()))
				{
					event.setDuplicateEventId(eventFromDB.getDuplicateEventId());
				}
				else
				{
					event.setDuplicateEventId(eventFromDB.getEventId());
				}
				break;
			}
		}
		else
		{
			event.setStatus(EventStatus.NEW);
			event.setDuplicateCheckEndTime(duplicateCheckEndTimeZonedDateTime);
			
			//For a given source system, there can be a cooling time to wait for the record to be processed as the system may send duplicate events
			Integer newStatusCoolingTimeMillisec = this.getSource2NewEventCoolingTimeMillisec().get(event.getSource());
			newStatusCoolingTimeMillisec = newStatusCoolingTimeMillisec == null ? 0 : newStatusCoolingTimeMillisec;
			event.setStatusExpiryTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis() + newStatusCoolingTimeMillisec), ZoneId.systemDefault()));
		}
		event.setCreateTime(ZonedDateTime.now());
		event.setUpdateTime(ZonedDateTime.now());

		event.setEventId(this.getEventDistributorMapper().getEventId());
		this.getEventDistributorMapper().insertEvent(event);

		AppResponse<Long> ret = new AppResponse<Long>();
		ret.responseObject = event.getEventId();
		
		if(EventStatus.DUPLICATE.equals(event.getStatus()))
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.DUPLICATE_REQUEST;
			ret.errorDetails.errorDescription = "Another event with the same business record.";
		}
		else
		{
			List<String> targetIds = this.getTargetIds(event);
			event.setTargetIds(targetIds);
			for (String targetId : targetIds)
			{
				this.getEventDistributorMapper().insertEventTarget(event.getEventId(), targetId);
			}
		}
		return ret;
	}


	private List<String> getTargetIds(Event event)
	{
		if(this.getEventTargetRules() == null)
		{
			LOGGER.warn("No eventTargetRules is not configured");
			return null;
		}
		for (EventTargetRule eventTargetRule : this.getEventTargetRules())
		{
			if(eventTargetRule.eval(event))
				return eventTargetRule.getTargetIds();
		}
		
		LOGGER.warn("None of the rule matched for the event {}", event);
		return null;
	}

	@Override
	public AppResponse<Event> getEvent(String callerId, String eventIdStr)
	{
		long eventId = Long.parseLong(eventIdStr);
		AppResponse<Event> ret = new AppResponse<Event>();
		Event event = this.getEventDistributorMapper().getEvent(eventId);
		ret.responseObject = event;
		return ret ;
	}

	@Override
	public AppResponse<List<Event>> getEvents(String callerId, String startPositionStr, String numberOfRecordsStr,
			String eventIdStr, Event event)
	{
		long eventId = Long.parseLong(eventIdStr);
		event.setEventId(eventId);
		int startPosition = Integer.parseInt(startPositionStr);
		int numberOfRecords = Integer.parseInt(numberOfRecordsStr);
		
		AppResponse<List<Event>> ret = new AppResponse<List<Event>>();
		ret.responseObject = this.getEventDistributorMapper().getEvents(event, startPosition, numberOfRecords);
		return ret;
	}

	@Override
	public AppResponse<Long> repostEvent(String eventIdStr, String createBy)
	{
		long eventId = Long.parseLong(eventIdStr);
		Event event = this.getEventDistributorMapper().getEvent(eventId);
        event.setRepostParentEventId(event.getEventId());
        event.setEventId(null);
        event.setStatus(null);
		event.setCreateBy(createBy);
		return this.postEvent(event);
	}
	
	@Override
	public AppResponse<Long> republish(String publishIdStr, String createBy)
	{
		long publishId = Long.parseLong(publishIdStr);
		PublishAttempt publishAttempt = this.getEventDistributorMapper().getPublishAttempt(publishId);
        publishAttempt.setPublishId(null);
        publishAttempt.setResponseHttpCode(null);
        publishAttempt.setResponseBody(null);
        publishAttempt.setCreateBy(createBy);
        
		Event event = this.getEventDistributorMapper().getEvent(publishAttempt.getEventId());
		ZonedDateTime currentTime = ZonedDateTime.now();
		AppResponse<Long> ret = new AppResponse<Long>();
		if(event.getStatus().equals(EventStatus.IN_PROGRESS) && event.getStatusExpiryTime().isAfter(currentTime))
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis()+"@"+ this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.EVENT_INPROGRESS;
			ret.errorDetails.errorDescription = "The event is currently being processed and cannot republish";
		}
		else
		{
			Long publishAttemptId = this.getEventDistributorMapper().getPublishAttemptId();
			publishAttempt.setPublishId(publishAttemptId);
			this.getEventDistributorMapper().insertPublishAttempt(publishAttempt);
			ret.responseObject = publishAttemptId;
			
			event.setUpdateTime(currentTime);
			event.setStatus(EventStatus.IN_PROGRESS);
			event.setStatusExpiryTime(currentTime);
			this.getEventDistributorMapper().updateEvent(event);
		}
		
		return ret;
	}

	@Override
	public AppResponse<Long> abortEvent(String eventIdStr, String updateBy)
	{
		long eventId = Long.parseLong(eventIdStr);
		Event event = this.getEventDistributorMapper().getEvent(eventId);
		AppResponse<Long> ret = new AppResponse<Long>();
		if(event == null)
		{
			ErrorDetails ed = new ErrorDetails();
			ed.errorId = System.currentTimeMillis() +"@@"+this.hostName;
			ed.errorType = ErrorDetails.ErrorType.EVENT_NOTFOUND;
			ed.errorDescription = "Event missing in db. Cant abort";
			ret.errorDetails = ed ;
			LOGGER.error("Returning error response : {}", ret);
			return ret ;
		}
        event.setStatus(EventStatus.ABORT);
		ZonedDateTime currentTime = ZonedDateTime.now();
		event.setUpdateTime(currentTime);
		event.setUpdateBy(updateBy);
		this.getEventDistributorMapper().updateEvent(event);
		ret.responseObject = event.getEventId();
		return ret;
	}
	
}
