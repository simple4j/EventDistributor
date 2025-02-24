package org.simple4j.eventdistributor.tasks;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.Callable;

import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.beans.PublishAttemptStatus;
import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.simple4j.wsclient.caller.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * This will call the target systems to distribute the event when its called from EventFetcher.
 */
public class WSCallerExecutor implements Callable<Boolean>, Comparable<WSCallerExecutor>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .serializationInclusion(Include.NON_NULL)
            .build();

	private Caller caller = null;
	private PublishAttempt publishAttempt = null;
	private EventDistributorMapper eventDistributorMapper = null;
	private Event event = null;
	private String successResponseMatchRegexPattern = null;

	public WSCallerExecutor(Caller caller, Event event, PublishAttempt publishAttempt, EventDistributorMapper eventDistributorMapper, String successResponseMatchRegexPattern)
	{
		LOGGER.info("Instantiating {} for publish attempt{}", this, publishAttempt);
    	OBJECT_MAPPER.registerModule(new JavaTimeModule());
		this.caller = caller;
		this.event = event;
		this.publishAttempt = publishAttempt;
		this.eventDistributorMapper = eventDistributorMapper;
		this.successResponseMatchRegexPattern = successResponseMatchRegexPattern;
	}
	
	@Override
	public Boolean call()
	{
		LOGGER.info("Inside run of {} for publish attempt{}", this, this. publishAttempt);
		Boolean ret = null;
		try
		{
			EventStatus eventStatusFromDB = this.eventDistributorMapper.getEventStatus(this.event.getEventId());
			if(EventStatus.ABORT.equals(eventStatusFromDB))
				return ret;
			Map<String, Object> response = this.caller.call(this.event);
			String responseStr = OBJECT_MAPPER.writeValueAsString(response);
			LOGGER.info("Calling caller {} for publish attempt{}", this.caller.getHttpWSClient().getServicePortNumber(), this. publishAttempt);
			this.publishAttempt.setResponseHttpCode((String) response.get(this.caller.getHttpStatusCodeFieldName()));
			this.publishAttempt.setResponseBody(responseStr);

			ret = responseStr.matches(this.successResponseMatchRegexPattern);
			if(ret)
			{
				this.publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.SUCCESS);
			}
			else
			{
				this.publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.FAILURE);
			}
		}
		catch(Throwable t)
		{
			LOGGER.warn("Error while publish attempt {}", this.publishAttempt, t);
			this.publishAttempt.setErrorDetails(t.toString());
			this.publishAttempt.setPublishAttemptStatus(PublishAttemptStatus.FAILURE);
			ret = false;
		}
		finally
		{
			Instant instant = Instant.ofEpochMilli(System.currentTimeMillis());
			ZonedDateTime currentTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
			this.publishAttempt.setUpdateTime(currentTime);
		}
		
		try
		{
			this.eventDistributorMapper.updatePublishAttempt(this.publishAttempt);
		}
		catch(Throwable t)
		{
			LOGGER.error("Error while updating publish attempt {}", this.publishAttempt, t);
			ret = false;
		}
		return ret;
	}

	@Override
	public int compareTo(WSCallerExecutor o)
	{
		if(this.event != null && this.event.getUpdateTime() != null && o.event != null && o.event.getUpdateTime() != null)
			return this.event.getUpdateTime().compareTo(o.event.getUpdateTime());
		else
			if(this.event != null && this.event.getCreateTime() != null && o.event != null && o.event.getCreateTime() != null)
				return this.event.getCreateTime().compareTo(o.event.getCreateTime());
			else
				if(this.event != null && this.event.getEventId() != null && o.event != null && o.event.getEventId() != null)
					return this.event.getEventId().compareTo(o.event.getEventId());
				else
					return 0;
	}

}
