package org.simple4j.eventdistributor.tasks;

import java.lang.invoke.MethodHandles;
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

public class WSCallerExecutor implements Callable<Boolean>
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
		this.caller = caller;
		this.event = event;
		this.publishAttempt = publishAttempt;
		this.eventDistributorMapper = eventDistributorMapper;
		this.successResponseMatchRegexPattern = successResponseMatchRegexPattern;
	}
	
	@Override
	public Boolean call()
	{
		Boolean ret = null;
		try
		{
			Event eventFromDB = this.eventDistributorMapper.getEvent(this.event.getEventId());
			if(eventFromDB.getStatus().equals(EventStatus.ABORT))
				return ret;
			Map<String, Object> response = this.caller.call(this.event);
			String responseStr = OBJECT_MAPPER.writeValueAsString(response);
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
		}
		finally
		{
			this.publishAttempt.setUpdateTime(ZonedDateTime.now());
		}
		
		try
		{
			this.eventDistributorMapper.updatePublishAttempt(this.publishAttempt);
		}
		catch(Throwable t)
		{
			LOGGER.error("Error while updating publish attempt {}", this.publishAttempt, t);
		}
		return ret;
	}

}
