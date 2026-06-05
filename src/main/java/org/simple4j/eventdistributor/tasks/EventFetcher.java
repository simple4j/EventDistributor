package org.simple4j.eventdistributor.tasks;

import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.simple4j.eventdistributor.Main;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.beans.PublishAttemptStatus;
import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.simple4j.wsclient.caller.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Periodic job to fetch new events, process for distribution asynchronously, collect statuses and update in DB.
 * This will also fetch events struck in IN_PROGRESS state, reposted events and republishes.
 * 
 */
public class EventFetcher implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private EventDistributorMapper eventDistributorMapper = null;
	private long sleepTimeInMillisec = 1000;
	private int maxFetchRecordCountPerBatch = 10;
	private ThreadPoolExecutor distributionExecutor = null;
	private int distributionExecutorCoreThreadPoolSize = 10;
	private int distributionExecutorMaxThreadPoolSize = 10;
	private long distributionExecutorKeepaliveSeconds = 300;
	private long lockExpiryMillisec = 900000;
	private int distributionExecutorQueueSize = 50;
	private Map<String, Caller> targetId2Caller = null;
	private Map<String, String> targetId2SuccessResponseMatchRegexPattern = null;
	private String hostName;

	public EventDistributorMapper getEventDistributorMapper()
	{
		return eventDistributorMapper;
	}

	public void setEventDistributorMapper(EventDistributorMapper eventDistributorMapper)
	{
		this.eventDistributorMapper = eventDistributorMapper;
	}

	public int getDistributionExecutorCoreThreadPoolSize()
	{
		return distributionExecutorCoreThreadPoolSize;
	}

	public void setDistributionExecutorCoreThreadPoolSize(int distributionExecutorCoreThreadPoolSize)
	{
		this.distributionExecutorCoreThreadPoolSize = distributionExecutorCoreThreadPoolSize;
	}

	public int getDistributionExecutorMaxThreadPoolSize()
	{
		return distributionExecutorMaxThreadPoolSize;
	}

	public void setDistributionExecutorMaxThreadPoolSize(int distributionExecutorMaxThreadPoolSize)
	{
		this.distributionExecutorMaxThreadPoolSize = distributionExecutorMaxThreadPoolSize;
	}

	public long getDistributionExecutorKeepaliveSeconds()
	{
		return distributionExecutorKeepaliveSeconds;
	}

	public void setDistributionExecutorKeepaliveSeconds(long distributionExecutorKeepaliveSeconds)
	{
		this.distributionExecutorKeepaliveSeconds = distributionExecutorKeepaliveSeconds;
	}

	public long getLockExpiryMillisec()
	{
		return lockExpiryMillisec;
	}

	public void setLockExpiryMillisec(long lockExpiryMillisec)
	{
		this.lockExpiryMillisec = lockExpiryMillisec;
	}

	public int getDistributionExecutorQueueSize()
	{
		return distributionExecutorQueueSize;
	}

	public void setDistributionExecutorQueueSize(int distributionExecutorQueueSize)
	{
		this.distributionExecutorQueueSize = distributionExecutorQueueSize;
	}

	public Map<String, Caller> getTargetId2Caller()
	{
		if(targetId2Caller == null)
			throw new RuntimeException("Property targetId2Caller not initialized");
		return targetId2Caller;
	}

	public void setTargetId2Caller(Map<String, Caller> targetId2Caller)
	{
		this.targetId2Caller = targetId2Caller;
	}

	public Map<String, String> getTargetId2SuccessResponseMatchRegexPattern()
	{
		if(targetId2SuccessResponseMatchRegexPattern == null)
			throw new RuntimeException("Property targetId2SuccessResponseMatchRegexPattern not initialized");
		return targetId2SuccessResponseMatchRegexPattern;
	}

	public void setTargetId2SuccessResponseMatchRegexPattern(Map<String, String> targetId2SuccessResponseMatchRegexPattern)
	{
		this.targetId2SuccessResponseMatchRegexPattern = targetId2SuccessResponseMatchRegexPattern;
	}

	public ThreadPoolExecutor getDistributionExecutor()
	{
		if(this.distributionExecutor == null)
			distributionExecutor = new ThreadPoolExecutor(this.distributionExecutorCoreThreadPoolSize, 
					this.distributionExecutorMaxThreadPoolSize, 
					this.distributionExecutorKeepaliveSeconds, 
					TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(this.distributionExecutorQueueSize), 
					new ThreadPoolExecutor.CallerRunsPolicy());
		return distributionExecutor;
	}

	public String getHostName()
	{
		return hostName;
	}

	public EventFetcher()
	{
		super();
		try
		{
			this.hostName =  InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void run()
	{
		try
		{
            MDC.put(Main.REQUEST_ID_KEY, ""+System.currentTimeMillis());
		if(Main.pauseEventFetcher)
		{
			LOGGER.info("pauseEventFetcher is true");
			return;
		}

		long currentLockExpiryMillisec = System.currentTimeMillis() + this.getLockExpiryMillisec();
		ZonedDateTime statusExpiryTimeZonedDateTime = getZonedDateTime(currentLockExpiryMillisec);
		
		ZonedDateTime currentTime = getZonedDateTime(System.currentTimeMillis());
		//update NEW to INPROGRESS with status expiry
		//update any INPROGRESS with status expired to current host and new status expiry
		this.getEventDistributorMapper().lockEvents(this.getHostName(), this.getMaxFetchRecordCountPerBatch(), statusExpiryTimeZonedDateTime, currentTime);
		List<Event> events = this.getEventDistributorMapper().fetchLockedEvents(this.getHostName());
		
		for (Event event : events)
		{

			LOGGER.debug("processing event {}", event);
			Map<String, PublishAttempt> targetId2NEWPublishAttempt = new HashMap<String, PublishAttempt>();
			Map<String, PublishAttempt> targetId2SUCCESSPublishAttempt = new HashMap<String, PublishAttempt>();
			
			//Below loop is to process any stuck publish events
			for (Iterator<PublishAttempt> iterator = event.getPublishAttempts().iterator(); iterator.hasNext();)
			{
				PublishAttempt pa = iterator.next();
				if(PublishAttemptStatus.NEW.equals(pa.getPublishAttemptStatus()))
				{
					targetId2NEWPublishAttempt.put(pa.getTargetId(), pa);
				}
				if(PublishAttemptStatus.SUCCESS.equals(pa.getPublishAttemptStatus()))
				{
					targetId2SUCCESSPublishAttempt.put(pa.getTargetId(), pa);
				}
			}
			
			List<String> targetIds = event.getTargetIds();
			List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
			for (String targetId : targetIds)
			{
				LOGGER.debug("processing targetId {}", targetId);
				EventStatus eventStatusFromDB = this.getEventDistributorMapper().getEventStatus(event.getEventId());
				if(EventStatus.ABORT.equals(eventStatusFromDB))
					break;
				PublishAttempt successPublishAttempt = targetId2SUCCESSPublishAttempt.get(targetId);
				PublishAttempt publishAttempt = targetId2NEWPublishAttempt.get(targetId);
				//successPublishAttempt will be null for new events
				//publishAttempt will not be null for republish case
				if(successPublishAttempt == null || publishAttempt != null)
				{
					if(publishAttempt == null)
					{
						currentTime = getZonedDateTime(System.currentTimeMillis());
	
						//Publish attempt record does not exists, create a new attempt
						publishAttempt = new PublishAttempt();
						publishAttempt.setPublishId(this.getEventDistributorMapper().getPublishAttemptId());
						publishAttempt.setCreateBy(event.getCreateBy());
						publishAttempt.setCreateTime(currentTime);
						publishAttempt.setEventId(event.getEventId());
						publishAttempt.setPublishId(this.getEventDistributorMapper().getPublishAttemptId());
						publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.NEW);
						publishAttempt.setTargetId(targetId);
						publishAttempt.setUpdateTime(currentTime);
						this.getEventDistributorMapper().insertPublishAttempt(publishAttempt);
						LOGGER.debug("inserted new record for targetId {}", targetId);
					}
					
					
					Caller caller = this.getTargetId2Caller().get(targetId);
					String successResponseMatchRegexPattern = this.getTargetId2SuccessResponseMatchRegexPattern().get(targetId);
					LOGGER.debug("submitted for processing for targetId {}", publishAttempt);
	                Future<Boolean> future = this.getDistributionExecutor().submit(new WSCallerExecutor(caller, event,
	                		publishAttempt, this.getEventDistributorMapper(), successResponseMatchRegexPattern));
	                futures.add(future);
				}
			}

			boolean eventSuccess = true;
			for (Iterator iterator = futures.iterator(); iterator.hasNext();)
			{
				Future<Boolean> future = (Future<Boolean>) iterator.next();
				try
				{
					Boolean publishAttemptSuccess = future.get();
					//below condition is possible when the event is aborted after submitting to the executor
					if(publishAttemptSuccess == null)
						break;
					eventSuccess = eventSuccess && publishAttemptSuccess;
				}
				catch (InterruptedException e)
				{
					eventSuccess = false;
					LOGGER.warn("", e);
					
				}
				catch (ExecutionException e)
				{
					eventSuccess = false;
					LOGGER.warn("", e);
				}
			}
			Event eventFromDB = this.getEventDistributorMapper().getEvent(event.getEventId());
			if(!eventFromDB.getStatus().equals(EventStatus.ABORT))
			{
				if(eventSuccess)
					eventFromDB.setStatus(EventStatus.SUCCESS);
				else
					eventFromDB.setStatus(EventStatus.FAILURE);
				currentTime = getZonedDateTime(System.currentTimeMillis());
				eventFromDB.setUpdateTime(currentTime);
				eventFromDB.setUpdateBy("EventFetcher");
				this.getEventDistributorMapper().updateEvent(eventFromDB);
			}
			
		}
		}
		catch(Throwable t)
		{
			LOGGER.warn("", t);
		}
		finally
		{
			MDC.clear();
		}

	}

	private ZonedDateTime getZonedDateTime(long millisec)
	{
		Instant instant = Instant.ofEpochMilli(millisec);
		ZonedDateTime ret = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
		return ret;
	}

	public long getSleepTimeInMillisec()
	{
		return this.sleepTimeInMillisec;
	}

	public void setSleepTimeInMillisec(long sleepTimeInMillisec)
	{
		this.sleepTimeInMillisec = sleepTimeInMillisec;
	}

	public int getMaxFetchRecordCountPerBatch()
	{
		return maxFetchRecordCountPerBatch;
	}

	public void setMaxFetchRecordCountPerBatch(int maxFetchRecordCountPerBatch)
	{
		this.maxFetchRecordCountPerBatch = maxFetchRecordCountPerBatch;
	}

}
