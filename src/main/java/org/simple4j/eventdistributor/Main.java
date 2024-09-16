package org.simple4j.eventdistributor;

import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.simple4j.eventdistributor.beans.AppResponse;
import org.simple4j.eventdistributor.beans.ErrorDetails;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.HealthCheck;
import org.simple4j.eventdistributor.beans.PublishAttempt;
import org.simple4j.eventdistributor.japi.EventDistributorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import spark.Response;
import spark.Spark;

public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static ApplicationContext context;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .serializationInclusion(Include.NON_NULL)
            .build();
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String JAPI_RETURN_OBJECT = "returnObject";
    private static final String START_TIME_MILLISEC = "startTimeMillisec";

    private static Main main = null;

    private int listenerPortNumber = 9260;
    private int listenerThreadMax = 100;
    private int listenerThreadMin = 10;
    private int listenerIdleTimeoutMillis = 600000;
    private String urlBase = "/foundation/eventdistributorws/eppublic/V1";

    private EventDistributorService eventDistributorService = null;
    private String userIdHeader = "userId";
    private String hostName = null;
    
    /**
     * {
     *      "SUCCESS" : 
     *      {
     *          "" : "200",
     *          "<API name>" : "201"
     *      },
     *      "PARAMETER_ERROR" : 
     *      {
     *          "" : "412",
     *      }
     * }
     */
    private Map<String, Map<String, Integer>> errorType2HTTPStatusMapping = null;
    

    public static void main(String[] args)
    {
        LOGGER.info("EventDistributor is starting, please wait...");

        context = new ClassPathXmlApplicationContext("appContext.xml");

        main = context.getBean("main", Main.class);
        main.init();

        Spark.port(main.getListenerPortNumber());

        Spark.threadPool(main.getListenerThreadMax(), main.getListenerThreadMin(), main.getListenerIdleTimeoutMillis());

        //Spark request lifecycle:
        // Spark.before
        // Spark.get / post / delete / put / list
        // ResponseTransformer to marshall the body
        // If no exception thrown in above steps, Spark.after
        // If any exception thrown in above steps including Spark.after, Spark.exception
        // Spark.afterAfter
        
        Spark.before((request, response) ->
        {
            long startTimeMillisec = System.currentTimeMillis();
            request.attribute(START_TIME_MILLISEC, startTimeMillisec);
            
			String requestId = startTimeMillisec + "@" + main.hostName;

            String headerRequestId = request.headers(REQUEST_ID_KEY);
            if (StringUtils.isNotBlank(headerRequestId))
            {
                requestId = headerRequestId;
            }

            MDC.put(REQUEST_ID_KEY, requestId);
            String body = "";
            String query = request.queryString();
            response.header("content-type", "application/json");
            LOGGER.info("Start request url is {}, method is {}, {}{}", request.uri(), request.requestMethod(),
                    StringUtils.isNotBlank(body) ? ("body is " + body + ", ") : "",
                    StringUtils.isNotBlank(query) ? ("query string is " + query) : "");
        });

        Spark.after((request, response) ->
        {
            
            Object returnObject = request.attribute(JAPI_RETURN_OBJECT);
            if(returnObject instanceof AppResponse)
            {
                AppResponse appResponse = request.attribute(JAPI_RETURN_OBJECT);
                response.status(main.getStatusCode(appResponse));
            }
            else
            {
                response.status(200);
            }
        });

        Spark.exception(Exception.class, (e, req, resp) ->
        {
            LOGGER.error("Unhandled exception", e);
            setHeader(resp);
            resp.status(500);
            ErrorDetails errorDetails = new ErrorDetails();
            errorDetails.errorId = MDC.get(REQUEST_ID_KEY);
            errorDetails.errorType = ErrorDetails.ErrorType.RUNTIME_ERROR;
            errorDetails.errorDescription = e.toString();

            try
            {
                resp.body(OBJECT_MAPPER.writeValueAsString(errorDetails));
            }
            catch (JsonProcessingException e1)
            {
                LOGGER.error("Marshaling error:", e1);
                throw new RuntimeException("Marshaling error:", e1);
            }
        });

        Spark.afterAfter((request, response) ->
        {
            long startTimeMillisec = request.attribute(START_TIME_MILLISEC);
            LOGGER.info("End request url is {}, method is {}, time {}s, query string is {}", request.uri(),
                    request.requestMethod(), (System.currentTimeMillis() - startTimeMillisec) / 1000.0,
                    request.queryString());
            MDC.clear();
        });
        
        Spark.post(main.getUrlBase()+"/serverhealth.json", (request, response) -> 
        {
            String bodyJson = request.body();
            HealthCheck healthCheckReq = null;
            try
            {
                healthCheckReq = OBJECT_MAPPER.readValue(bodyJson, HealthCheck.class);
            }
            catch(Exception e)
            {
                throw new RuntimeException("Error parsing request body <" + bodyJson +">",  e);
            }
            main.getEventDistributorService().setHealthCheck(healthCheckReq.status);
            setHeader(response);
            return "{}";
        });

        Spark.get(main.getUrlBase()+"/serverhealth.json", (request, response) -> 
        {
            AppResponse<HealthCheck> ret = main.getEventDistributorService().getHealthCheck();
            HealthCheck healthCheckRes = ret.responseObject;
            setHeader(response);
            
            return OBJECT_MAPPER.writeValueAsString(healthCheckRes);
        });

        Spark.post(main.getUrlBase()+"/event.json", (request, response) -> 
        {
            AppResponse<Long> ret = null;
            String callerId = request.headers("callerId");
            String userId = request.headers(main.getUserIdHeader());
            
            String body = request.body();
            Event event = null;
            try
            {
            	event = OBJECT_MAPPER.readValue(body, Event.class);
            	if(callerId != null && callerId.trim().length() > 0)
            	{
            		event.setCreateBy(callerId);
            	}
            	if(userId != null && userId.trim().length() > 0)
            	{
            		event.setCreateBy(userId);
            	}
            }
            catch(Exception e)
            {
                throw new RuntimeException("Error parsing request body <" + body +">",  e);
            }
            
            ret = main.getEventDistributorService().postEvent(event);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            
            return "{}";
        });

        Spark.get(main.getUrlBase()+"/event.json", (request, response) -> 
        {
            AppResponse<Event> ret = null;
            String callerId = request.headers("callerId");
            String eventId = request.queryParams("eventId");
            
            ret = main.getEventDistributorService().getEvent(callerId, eventId);
            
            LOGGER.info("ret:{}", ret);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);
            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            return OBJECT_MAPPER.writeValueAsString(ret.responseObject);
        });

        Spark.get(main.getUrlBase()+"/events.json", (request, response) -> 
        {
            AppResponse<List<Event>> ret = null;
            String callerId = request.headers("callerId");

            Event event = new Event();
            String eventId = request.queryParams("eventId");
            event.setBusinessRecordId(request.queryParams("businessRecordId"));
            event.setBusinessRecordType(request.queryParams("businessRecordType"));
            event.setBusinessRecordSubType(request.queryParams("businessRecordSubType"));
            event.setBusinessRecordVersion(request.queryParams("businessRecordVersion"));
            event.setSource(request.queryParams("source"));
            event.setStatus(EventStatus.valueOf(request.queryParams("status")));
            event.setProcessingHost(request.queryParams("processingHost"));
            event.setCreateBy(request.queryParams("createBy"));
            String startPosition = request.queryParams("startPosition");
            String numberOfRecords = request.queryParams("numberOfRecords");
            
            ret = main.getEventDistributorService().getEvents(callerId, startPosition, numberOfRecords, eventId, event);
            
            LOGGER.info("ret:{}", ret);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);
            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            return OBJECT_MAPPER.writeValueAsString(ret.responseObject);
        });

        Spark.post(main.getUrlBase()+"/repost/event.json", (request, response) -> 
        {
            AppResponse<Long> ret = null;
            String callerId = request.headers("callerId");
            String userId = request.headers(main.getUserIdHeader());
            String eventId = request.queryParams("eventId");
            
    		String createBy = null;
        	if(callerId != null && callerId.trim().length() > 0)
        	{
        		createBy = callerId;
        	}
        	if(userId != null && userId.trim().length() > 0)
        	{
        		createBy = userId;
        	}

        	ret = main.getEventDistributorService().repostEvent(eventId, createBy);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            
            return "{}";
        });

        Spark.post(main.getUrlBase()+"/repost/publish.json", (request, response) -> 
        {
            AppResponse<Long> ret = null;
            String callerId = request.headers("callerId");
            String userId = request.headers(main.getUserIdHeader());
            String publishId = request.queryParams("publishId");
            
    		String createBy = null;
        	if(callerId != null && callerId.trim().length() > 0)
        	{
        		createBy = callerId;
        	}
        	if(userId != null && userId.trim().length() > 0)
        	{
        		createBy = userId;
        	}
            
            ret = main.getEventDistributorService().republish(publishId, createBy);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            
            return "{}";
        });

        Spark.post(main.getUrlBase()+"/abort/event.json", (request, response) -> 
        {
            AppResponse<Long> ret = null;
            String callerId = request.headers("callerId");
            String userId = request.headers(main.getUserIdHeader());
            String eventId = request.queryParams("eventId");
            
    		String updateBy = null;
        	if(callerId != null && callerId.trim().length() > 0)
        	{
        		updateBy = callerId;
        	}
        	if(userId != null && userId.trim().length() > 0)
        	{
        		updateBy = userId;
        	}

        	ret = main.getEventDistributorService().abortEvent(eventId, updateBy);

            //setting AppResponse object for usage in Spark.after handler
            request.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
                return OBJECT_MAPPER.writeValueAsString(ret.errorDetails);
            
            return "{}";
        });

    }

    private void init()
    {
    	try
		{
			this.hostName = InetAddress.getLocalHost().getHostName();
		}
    	catch (UnknownHostException e)
		{
			throw new RuntimeException(e);
		}
    	this.getEventDistributorService().init();
    }

    private int getStatusCode(AppResponse appResponse)
    {
        if(appResponse.errorDetails == null)
        {
            return 200;
        }
        Map<String, Integer> apiCallName2HTTPStatusMapping = this.getErrorType2HTTPStatusMapping().get(appResponse.errorDetails.errorType.toString());
        if(apiCallName2HTTPStatusMapping != null)
        {
            if(apiCallName2HTTPStatusMapping.get(appResponse.apiCallName) != null)
            {
               return apiCallName2HTTPStatusMapping.get(appResponse.apiCallName);
            }
            else
                if(apiCallName2HTTPStatusMapping.get("") != null)
                {
                    return apiCallName2HTTPStatusMapping.get("");
                }
                else
                {
                    LOGGER.error("Missing mapping for apiCallName {} or for fallback", appResponse.apiCallName);
                }
        }
        else
        {
            LOGGER.error("Missing mapping for error type {}", appResponse.errorDetails.errorType);
        }
        throw new RuntimeException("main.getErrorType2HTTPStatusMapping() not configured for :"+appResponse);
    }

    private static void setHeader(Response response)
    {
        response.type("application/json;charset=utf-8");
        response.header("Content-Language", "en");
        response.header("requestid", MDC.get(REQUEST_ID_KEY));
    }

    public int getListenerPortNumber()
    {
        return listenerPortNumber;
    }

    public void setListenerPortNumber(int listenerPortNumber)
    {
        this.listenerPortNumber = listenerPortNumber;
    }

    public int getListenerThreadMax()
    {
        return listenerThreadMax;
    }

    public void setListenerThreadMax(int listenerThreadMax)
    {
        this.listenerThreadMax = listenerThreadMax;
    }

    public int getListenerThreadMin()
    {
        return listenerThreadMin;
    }

    public void setListenerThreadMin(int listenerThreadMin)
    {
        this.listenerThreadMin = listenerThreadMin;
    }

    public int getListenerIdleTimeoutMillis()
    {
        return listenerIdleTimeoutMillis;
    }

    public void setListenerIdleTimeoutMillis(int listenerIdleTimeoutMillis)
    {
        this.listenerIdleTimeoutMillis = listenerIdleTimeoutMillis;
    }

    public String getUrlBase()
    {
        return urlBase;
    }

    public void setUrlBase(String urlBase)
    {
        this.urlBase = urlBase;
    }

    public Map<String, Map<String, Integer>> getErrorType2HTTPStatusMapping()
    {
        return errorType2HTTPStatusMapping;
    }

    public void setErrorType2HTTPStatusMapping(
            Map<String, Map<String, Integer>> errorType2HTTPStatusMapping)
    {
        this.errorType2HTTPStatusMapping = errorType2HTTPStatusMapping;
    }

	public EventDistributorService getEventDistributorService()
	{
		if(this.eventDistributorService == null)
			throw new RuntimeException("eventDistributorService not configured");
		return eventDistributorService;
	}

	public void setEventDistributorService(EventDistributorService eventDistributorService)
	{
		this.eventDistributorService = eventDistributorService;
	}

	public String getUserIdHeader()
	{
		return userIdHeader;
	}

	public void setUserIdHeader(String userIdHeader)
	{
		this.userIdHeader = userIdHeader;
	}

}
