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
import org.simple4j.eventdistributor.tasks.DBCleaner;
import org.simple4j.eventdistributor.tasks.EventFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Business logic bean implementation.
 */
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
	
	private Map<String, String> api2AllowedCallerIdsRegEx = null;
	private Map<String, String> api2AllowedUserIdsRegEx = null;
	
	private DBCleaner dbCleaner = null;

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

	/**
	 * This configuration is list of rules that will be applied and target id is selected based on rule evaluation result.
	 * 
	 * Rule evaluation should not depend on source of the event as that will cause 
	 * conflict with duplicate detection logic which does not include source value check.
	 * 
	 * Why evaluation should not depend on source?
	 * Since duplicate detection is based on business record id, type, subtype and version,
	 * if there are 2 records with the same business record id, type, subtype and version and different source,
	 * the record marked as duplicate may miss delivery to one of the target systems based on the overall configuration of EventTargetRule
	 * 
	 * @return
	 */
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

	/**
	 * An event will be marked as duplicate if another event is submitted with the same business record id/type/subtype/version
	 * with in the configured duplicateCheckExpiryMillisec.
	 * The recent event will be duplicate even if the older event is successfully processed when the recent event is posted.
	 * 
	 * @return
	 */
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
		if(this.eventFetcher == null)
			throw new RuntimeException("eventFetcher not configured for EventDistributorServiceImpl instance");
		return eventFetcher;
	}

	public void setEventFetcher(EventFetcher eventFetcher)
	{
		this.eventFetcher = eventFetcher;
	}

	/**
	 * This configuration will delay the processing of events by the cooling time configured per source or origin of an event.
	 * If a cooling period is not set for a given source, those events will get processed at the earliest by EventFetcher.
	 * 
	 * @return
	 */
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

	public Map<String, String> getApi2AllowedCallerIdsRegEx()
	{
		if(this.api2AllowedCallerIdsRegEx == null)
			this.api2AllowedCallerIdsRegEx = new HashMap<String, String>();
		return api2AllowedCallerIdsRegEx;
	}

	public void setApi2AllowedCallerIdsRegEx(Map<String, String> api2AllowedCallerIdsRegEx)
	{
		this.api2AllowedCallerIdsRegEx = api2AllowedCallerIdsRegEx;
	}

	public Map<String, String> getApi2AllowedUserIdsRegEx()
	{
		if(this.api2AllowedUserIdsRegEx == null)
			this.api2AllowedUserIdsRegEx = new HashMap<String, String>();
		return api2AllowedUserIdsRegEx;
	}

	public void setApi2AllowedUserIdsRegEx(Map<String, String> api2AllowedUserIdsRegEx)
	{
		this.api2AllowedUserIdsRegEx = api2AllowedUserIdsRegEx;
	}

	public int getEventFetcherExecutorCoreThreadPoolSize()
	{
		return eventFetcherExecutorCoreThreadPoolSize;
	}

	public void setEventFetcherExecutorCoreThreadPoolSize(int eventFetcherExecutorCoreThreadPoolSize)
	{
		this.eventFetcherExecutorCoreThreadPoolSize = eventFetcherExecutorCoreThreadPoolSize;
	}

	public DBCleaner getDbCleaner()
	{
		if(this.dbCleaner == null)
			throw new RuntimeException("dbCleaner not configured for EventDistributorServiceImpl instance");
		return dbCleaner;
	}

	public void setDbCleaner(DBCleaner dbCleaner)
	{
		this.dbCleaner = dbCleaner;
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
        
        //Using the eventfetcherExecutor instead of creating a new Executor
        this.eventFetcherExecutor.scheduleWithFixedDelay(this.getDbCleaner(),
                this.getDbCleaner().getSleepTimeInMillisec(), this.getDbCleaner().getSleepTimeInMillisec(), TimeUnit.MILLISECONDS);
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
		healthCheck.artifactId = this.getArtifactId();
		healthCheck.groupId = this.getGroupId();
		healthCheck.version = this.getVersion();
		healthCheck.configStatus = this.healthcheckStatus;
		healthCheck.db = Status.HEALTHY;
		try
		{
			this.getEventDistributorMapper().getEventStatus(0);
		}
		catch(Throwable t)
		{
			LOGGER.warn("Error whule fetching status for health check ", t);
			healthCheck.db = Status.UNHEALTHY;
		}
		ret.responseObject = healthCheck;
		return ret;
	}

	@Override
	public AppResponse<Long> postEvent(Event event, String callerId, String userId)
	{
		String api = "postEvent";
		AppResponse ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;
		ret = checkUser(userId, api);
		if(ret != null)
			return ret;
		return this.postEvent(event, true, callerId, userId);
	}

	private AppResponse checkCaller(String callerId, String api)
	{
		if(this.getApi2AllowedCallerIdsRegEx().containsKey(api))
		{
			if(!callerId.matches(this.getApi2AllowedCallerIdsRegEx().get(api)))
			{
				AppResponse ret = new AppResponse();
				ret.errorDetails = new ErrorDetails();
				ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
				ret.errorDetails.errorType = ErrorType.CALLER_NOTAUTHORIZED.toString();
				ret.errorDetails.errorDescription = "Caller id not configured for operation.";
				return ret;
			}
		}
		else
		{
			AppResponse ret = new AppResponse();
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.CALLER_NOTAUTHORIZED.toString();
			ret.errorDetails.errorDescription = "No callers configured for operation.";
			return ret;
		}
		return null;
	}
	
	private AppResponse checkUser(String userId, String api)
	{
		if(this.getApi2AllowedUserIdsRegEx().containsKey(api))
		{
			if(!userId.matches(this.getApi2AllowedUserIdsRegEx().get(api)))
			{
				AppResponse ret = new AppResponse();
				ret.errorDetails = new ErrorDetails();
				ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
				ret.errorDetails.errorType = ErrorType.USER_NOTAUTHORIZED.toString();
				ret.errorDetails.errorDescription = "User id not configured for operation.";
				return ret;
			}
		}
		else
		{
			AppResponse ret = new AppResponse();
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis() + "@" + this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.USER_NOTAUTHORIZED.toString();
			ret.errorDetails.errorDescription = "No user configured for operation.";
			return ret;
		}
		return null;
	}
	
	private AppResponse<Long> postEvent(Event event, boolean enableDuplicateCheck, String callerId, String userId)
	{

		//Not using ZonedDateTime.now() to keep precision at millisec and for easier testing 
		long currentTimeMillis = System.currentTimeMillis();
		ZonedDateTime currentZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());

		List<Event> duplicateEvents = null;
		
		String createBy = this.getCreateUpdateBy(callerId, userId);
		event.setCreateBy(createBy);
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
			event.setStatus(EventStatus.DRAFT);

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
			event.setStatus(EventStatus.NEW);
			this.getEventDistributorMapper().updateEvent(event);
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
		String api = "getEvent";
		AppResponse<Event> ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;

		long eventId = Long.parseLong(eventIdStr);
		ret = new AppResponse<Event>();
		Event event = this.getEventDistributorMapper().getEvent(eventId);
		if(event == null)
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis()+"@"+ this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.EVENT_NOTFOUND.toString();
			ret.errorDetails.errorDescription = "The event not found";
		}
		ret.responseObject = event;
		return ret ;
	}

	@Override
	public AppResponse<List<Event>> getEvents(String callerId, String startPositionStr, String numberOfRecordsStr,
			Event event)
	{
		String api = "getEvents";
		AppResponse<List<Event>> ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;

		int startPosition = Integer.parseInt(startPositionStr);
		int numberOfRecords = Integer.parseInt(numberOfRecordsStr);
		
		ret = new AppResponse<List<Event>>();
		ret.responseObject = this.getEventDistributorMapper().getEvents(event, startPosition, numberOfRecords);
		return ret;
	}

	@Override
	public AppResponse<Long> repostEvent(String eventIdStr, String callerId, String userId)
	{
		String api = "repostEvent";
		AppResponse ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;
		ret = checkUser(userId, api);
		if(ret != null)
			return ret;

		long eventId = Long.parseLong(eventIdStr);
		Event event = this.getEventDistributorMapper().getEvent(eventId);
		if(event == null)
		{
			ret = new AppResponse<Long>();
			ErrorDetails ed = new ErrorDetails();
			ed.errorId = System.currentTimeMillis() +"@@"+this.hostName;
			ed.errorType = ErrorType.EVENT_NOTFOUND.toString();
			ed.errorDescription = "Event missing in db. Cant abort";
			ret.errorDetails = ed ;
			LOGGER.error("Returning error response : {}", ret);
			return ret ;
		}
        event.setRepostParentEventId(event.getEventId());
        event.setEventId(null);
        event.setStatus(null);
		return this.postEvent(event, false, callerId, userId);
	}
	
	@Override
	public AppResponse<Long> republish(String publishIdStr, String callerId, String userId)
	{
		String api = "republish";
		AppResponse<Long> ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;
		ret = checkUser(userId, api);
		if(ret != null)
			return ret;

		ret = new AppResponse<Long>();
		long publishId = Long.parseLong(publishIdStr);

		//Not using ZonedDateTime.now() to keep precision at millisec and for easier testing 
		long currentTimeMillis = System.currentTimeMillis();
		ZonedDateTime currentZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());

		PublishAttempt publishAttempt = this.getEventDistributorMapper().getPublishAttempt(publishId);
		if(publishAttempt == null)
		{
			ret.errorDetails = new ErrorDetails();
			ret.errorDetails.errorId = System.currentTimeMillis()+"@"+ this.getEventFetcher().getHostName();
			ret.errorDetails.errorType = ErrorType.PUBLISH_ATTEMPT_NOTFOUND.toString();
			ret.errorDetails.errorDescription = "The publish attempt not found and cannot republish";
			return ret;
		}
		String createBy = this.getCreateUpdateBy(callerId, userId);
        publishAttempt.setPublishId(null);
        publishAttempt.setResponseHttpCode(null);
        publishAttempt.setResponseBody(null);
        publishAttempt.setCreateBy(createBy);
        publishAttempt.setCreateTime(currentZonedDateTime);
        publishAttempt.setErrorDetails(null);
        publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.NEW);
        publishAttempt.setUpdateTime(currentZonedDateTime);
        
		Event event = this.getEventDistributorMapper().getEvent(publishAttempt.getEventId());
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
	public AppResponse<Long> abortEvent(String eventIdStr, String callerId, String userId)
	{
		String api = "abortEvent";
		AppResponse<Long> ret = checkCaller(callerId, api);
		if(ret != null)
			return ret;
		ret = checkUser(userId, api);
		if(ret != null)
			return ret;

		long eventId = Long.parseLong(eventIdStr);
		Event event = this.getEventDistributorMapper().getEvent(eventId);
		ret = new AppResponse<Long>();
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
		if(EventStatus.NEW.equals(event.getStatus()) || EventStatus.IN_PROGRESS.equals(event.getStatus()))
		{
	        event.setStatus(EventStatus.ABORT);
			ZonedDateTime currentTime = ZonedDateTime.now();
			event.setUpdateTime(currentTime);
			String createBy = this.getCreateUpdateBy(callerId, userId);
			event.setUpdateBy(createBy);
			this.getEventDistributorMapper().updateEvent(event);
			ret.responseObject = event.getEventId();
			return ret;
		}
		else
		{
			ErrorDetails ed = new ErrorDetails();
			ed.errorId = System.currentTimeMillis() +"@@"+this.hostName;
			ed.errorType = ErrorType.EVENT_ALREADY_PROCESSED.toString();
			ed.errorDescription = "Event already processed. Cant abort";
			ret.errorDetails = ed ;
			LOGGER.error("Returning error response : {}", ret);
			return ret ;
		}
	}
	
	private String getCreateUpdateBy(String callerId, String userId)
	{
		String ret = null;
		ret = getStringWithEmptyCheck(callerId, null);
		ret = getStringWithEmptyCheck(userId, ret);
		return ret;
	}
	

	private String getStringWithEmptyCheck(String in, String defaultValue)
	{
		String ret = defaultValue;
		if(in != null && in.trim().length() > 0)
		{
			ret = in;
		}
		return ret;
	}

}
