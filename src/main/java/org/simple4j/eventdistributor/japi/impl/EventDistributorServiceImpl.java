package org.simple4j.eventdistributor.japi.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtils;
import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.apiaopvalidator.beans.ErrorDetails;
import org.simple4j.eventdistributor.beans.ErrorType;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.HealthCheck;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.beans.PublishAttemptStatus;
import org.simple4j.eventdistributor.beans.HealthCheck.Status;
import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.simple4j.eventdistributor.japi.EventDistributorService;
import org.simple4j.eventdistributor.japi.EventTargetRule;
import org.simple4j.eventdistributor.tasks.EventFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDistributorServiceImpl implements EventDistributorService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private Status healthcheckStatus = Status.HEALTHY;

	private String groupId = null;
	private String artifactId = null;
	private String version = null;
	private List<EventTargetRule> eventTargetRules = null;
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

	public List<EventTargetRule> getEventTargetRules()
	{
		return eventTargetRules;
	}

	public void setEventTargetRules(List<EventTargetRule> eventTargetRules)
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
                this.getEventFetcher().getSleepTimeInMillisec(), this.getEventFetcher().getSleepTimeInMillisec(), TimeUnit.MILLISECONDS);
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
		return this.postEvent(event, true);
	}
	
	private AppResponse<Long> postEvent(Event event, boolean enableDuplicateCheck)
	{

		//Not using ZonedDateTime.now() to keep precision at millisec and for easier testing 
		long currentTimeMillis = System.currentTimeMillis();
		ZonedDateTime currentZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());

		List<Event> duplicateEvents = null;
		
		if(enableDuplicateCheck)
		{
			Event inputClone = new Event();
			try
			{
				BeanUtils.copyProperties(inputClone, event);
			} catch (IllegalAccessException | InvocationTargetException e)
			{
				throw new RuntimeException(e);
			}
	
			inputClone.setCreateTime(currentZonedDateTime);
			
			//Fetch first 100 non-abort records with duplicationcheck end date in the future and matching business record id/type/subtype/version
			//duplicates can be from different sources
			duplicateEvents = this.getEventDistributorMapper().getEventsForDuplicateCheck(inputClone, 1, 100);
		}
		if(duplicateEvents != null && duplicateEvents.size() > 0)
		{
			//mark the incoming event as duplicate with the same duplicate check end time as original event and duplicate event id of the first event
			for (Event eventFromDB : duplicateEvents)
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
			//not duplicate case
			event.setStatus(EventStatus.NEW);

			Instant duplicateCheckEndTimeInstant = Instant.ofEpochMilli(currentTimeMillis + this.getDuplicateCheckExpiryMillisec());
			ZonedDateTime duplicateCheckEndTimeZonedDateTime = ZonedDateTime.ofInstant(duplicateCheckEndTimeInstant, ZoneId.systemDefault());
			event.setDuplicateCheckEndTime(duplicateCheckEndTimeZonedDateTime);
			
			//For a given source system, there can be a cooling time to wait for the record to be processed as the system may send duplicate events
			Integer newStatusCoolingTimeMillisec = this.getSource2NewEventCoolingTimeMillisec().get(event.getSource());
			newStatusCoolingTimeMillisec = newStatusCoolingTimeMillisec == null ? 0 : newStatusCoolingTimeMillisec;
			event.setStatusExpiryTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis + newStatusCoolingTimeMillisec), ZoneId.systemDefault()));
		}
		event.setCreateTime(currentZonedDateTime);
		event.setUpdateTime(currentZonedDateTime);

		event.setEventId(this.getEventDistributorMapper().getEventId());
		this.getEventDistributorMapper().insertEvent(event);

		AppResponse<Long> ret = new AppResponse<Long>();
		ret.responseObject = event.getEventId();
		
		if(EventStatus.DUPLICATE.equals(event.getStatus()))
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.DUPLICATE_REQUEST.toString();
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
		for (int i=0 ; i < this.getEventTargetRules().size() ; i++)
		{
			EventTargetRule eventTargetRule = this.getEventTargetRules().get(i);
			if(eventTargetRule .eval(event))
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
			Event event)
	{
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
		return this.postEvent(event, false);
	}
	
	@Override
	public AppResponse<Long> republish(String publishIdStr, String createBy)
	{
		long publishId = Long.parseLong(publishIdStr);

		//Not using ZonedDateTime.now() to keep precision at millisec and for easier testing 
		long currentTimeMillis = System.currentTimeMillis();
		ZonedDateTime currentZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());

		PublishAttempt publishAttempt = this.getEventDistributorMapper().getPublishAttempt(publishId);
        publishAttempt.setPublishId(null);
        publishAttempt.setResponseHttpCode(null);
        publishAttempt.setResponseBody(null);
        publishAttempt.setCreateBy(createBy);
        publishAttempt.setCreateTime(currentZonedDateTime);
        publishAttempt.setErrorDetails(null);
        publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.NEW);
        publishAttempt.setUpdateTime(currentZonedDateTime);
        
		Event event = this.getEventDistributorMapper().getEvent(publishAttempt.getEventId());
		AppResponse<Long> ret = new AppResponse<Long>();
		if(event.getStatus().equals(EventStatus.IN_PROGRESS) && event.getStatusExpiryTime().isAfter(currentZonedDateTime))
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis()+"@"+ this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.EVENT_INPROGRESS.toString();
			ret.errorDetails.errorDescription = "The event is currently being processed and cannot republish";
		}
		else
		{
			Long publishAttemptId = this.getEventDistributorMapper().getPublishAttemptId();
			publishAttempt.setPublishId(publishAttemptId);
			this.getEventDistributorMapper().insertPublishAttempt(publishAttempt);
			ret.responseObject = publishAttemptId;
			
			event.setUpdateTime(currentZonedDateTime);
			event.setStatus(EventStatus.IN_PROGRESS);
			event.setStatusExpiryTime(currentZonedDateTime);
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
			ed.errorType = ErrorType.EVENT_NOTFOUND.toString();
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
