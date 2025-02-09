package org.simple4j.eventdistributor;

import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.simple4j.apiaopvalidator.beans.AppResponse;
import org.simple4j.apiaopvalidator.beans.ErrorDetails;
import org.simple4j.eventdistributor.beans.ErrorType;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.beans.EventStatus;
import org.simple4j.eventdistributor.beans.HealthCheck;
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.http.Context;


/**
 * This class holds the main method and the entry point for startup of the application.
 */
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
    
    public static boolean pauseEventFetcher = false;

    private int listenerPortNumber = 2410;
    private int listenerIdleTimeoutMillis = 600000;
    private String urlBase = "/eventdistributorws/eppublic/V1";

    private EventDistributorService eventDistributorService = null;
    private String userIdHeader = "userId";
    private String hostName = null;
    
    private Map<String, Map<String, Integer>> errorType2HTTPStatusMapping = null;
    

    /**
     * This method being the entry point, it initializes all the beans with dependency injection,
     *  initializes web service routes
     * @param args
     */
    public static void main(String[] args)
    {
        LOGGER.info("EventDistributor is starting, please wait...");

        if(args.length>0)
        {
        	try
        	{
	        	pauseEventFetcher = Boolean.parseBoolean(args[0]);
        	}
        	catch(Throwable t)
        	{
        		LOGGER.warn("Error while parsing arguments", t);
        	}
        }
        context = new ClassPathXmlApplicationContext("appContext.xml");

        main = context.getBean("main", Main.class);
        main.init();

		Javalin javalin = Javalin.create();

        javalin.before(ctx ->
        {
            long startTimeMillisec = System.currentTimeMillis();
            ctx.attribute(START_TIME_MILLISEC, startTimeMillisec);
            
			String requestId = startTimeMillisec + "@" + main.hostName;

            String headerRequestId = ctx.header(REQUEST_ID_KEY);
            if (StringUtils.isNotBlank(headerRequestId))
            {
                requestId = headerRequestId;
            }

            MDC.put(REQUEST_ID_KEY, requestId);
            String body = "";
            String query = ctx.queryString();
            ctx.header("content-type", "application/json");
            LOGGER.info("Start request url is {}, method is {}, {}{}", ctx.url(), ctx.method(),
                    StringUtils.isNotBlank(body) ? ("body is " + body + ", ") : "",
                    StringUtils.isNotBlank(query) ? ("query string is " + query) : "");
        });

        javalin.after(ctx ->
        {
            
            Object returnObject = ctx.attribute(JAPI_RETURN_OBJECT);
            if(returnObject instanceof AppResponse)
            {
                AppResponse appResponse = ctx.attribute(JAPI_RETURN_OBJECT);
                ctx.status(main.getStatusCode(appResponse));
            }
            else
            {
                ctx.status(200);
            }
            
            finishCall(ctx);
        });

        javalin.exception(Exception.class, (e, ctx) ->
        {
            LOGGER.error("Unhandled exception", e);
            setHeader(ctx);
            ctx.status(500);
            ErrorDetails errorDetails = new ErrorDetails();
            errorDetails.errorId = MDC.get(REQUEST_ID_KEY);
            errorDetails.errorType = ErrorType.RUNTIME_ERROR.toString();
            errorDetails.errorDescription = e.toString();

            finishCall(ctx);

            try
            {
                ctx.result(OBJECT_MAPPER.writeValueAsString(errorDetails));
            }
            catch (JsonProcessingException e1)
            {
                LOGGER.error("Marshaling error:", e1);
                throw new RuntimeException("Marshaling error:", e1);
            }
        });

        javalin.post(main.getUrlBase()+"/serverhealth.json", ctx -> 
        {
            String bodyJson = ctx.body();
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
            setHeader(ctx);
            ctx.result("{}");
        });

        javalin.get(main.getUrlBase()+"/serverhealth.json", ctx -> 
        {
            AppResponse<HealthCheck> ret = main.getEventDistributorService().getHealthCheck();
            HealthCheck healthCheckRes = ret.responseObject;
            setHeader(ctx);
            ctx.result(OBJECT_MAPPER.writeValueAsString(healthCheckRes));
        });

//        javalin.post(main.getUrlBase()+"/eventFetcherTrigger.json", ctx -> 
//        {
//        	pauseEventFetcher = false;
//            setHeader(ctx);
//            ctx.result("{}");
//        });

        javalin.post(main.getUrlBase()+"/event.json", ctx -> 
        {
            AppResponse<Long> ret = null;
            String callerId = ctx.header("callerId");
            String userId = ctx.header(main.getUserIdHeader());
            
            String body = ctx.body();
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

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
            {
            	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                return;
            }
            
            ctx.result("{\"eventId\":\""+ret.responseObject+"\"}");
        });

        javalin.get(main.getUrlBase()+"/event.json", ctx -> 
        {
            AppResponse<Event> ret = null;
            String callerId = ctx.header("callerId");
            String eventId = ctx.queryParam("eventId");
            
            ret = main.getEventDistributorService().getEvent(callerId, eventId);
            
            LOGGER.info("ret:{}", ret);

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);
            if(ret.errorDetails != null)
            {
            	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                return;
            }
            ctx.result(OBJECT_MAPPER.writeValueAsString(ret.responseObject));
        });

        javalin.get(main.getUrlBase()+"/events.json", ctx -> 
        {
            AppResponse<List<Event>> ret = null;
            String callerId = ctx.header("callerId");

            Event event = new Event();
            String eventIdStr = getStringWithEmptyCheck(ctx.queryParam("eventId"), null);
    		if (eventIdStr != null)
    		{
    			long eventId = Long.parseLong(eventIdStr);
    			event.setEventId(eventId);
    		}

            event.setBusinessRecordId(getStringWithEmptyCheck(ctx.queryParam("businessRecordId"), null));
            event.setBusinessRecordType(getStringWithEmptyCheck(ctx.queryParam("businessRecordType"), null));
            event.setBusinessRecordSubType(getStringWithEmptyCheck(ctx.queryParam("businessRecordSubType"), null));
            event.setBusinessRecordVersion(getStringWithEmptyCheck(ctx.queryParam("businessRecordVersion"), null));
            event.setSource(getStringWithEmptyCheck(ctx.queryParam("source"), null));
            String statusStr = getStringWithEmptyCheck(ctx.queryParam("status"), null);
            if(statusStr != null)
				event.setStatus(EventStatus.valueOf(statusStr));
            event.setProcessingHost(getStringWithEmptyCheck(ctx.queryParam("processingHost"), null));
            event.setCreateBy(getStringWithEmptyCheck(ctx.queryParam("createBy"), null));
            String startPosition = getStringWithEmptyCheck(ctx.queryParam("startPosition"), null);
            String numberOfRecords = getStringWithEmptyCheck(ctx.queryParam("numberOfRecords"), null);
            
            ret = main.getEventDistributorService().getEvents(callerId, startPosition, numberOfRecords, event);
            
            LOGGER.info("ret:{}", ret);

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);
            if(ret.errorDetails != null)
                if(ret.errorDetails != null)
                {
                	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                    return;
                }
            
            HashMap<String, List<Event>> retMap = new HashMap<String, List<Event>>();
            retMap.put("events", ret.responseObject);
            ctx.result(OBJECT_MAPPER.writeValueAsString(retMap));
        });

        javalin.post(main.getUrlBase()+"/repost/event.json", ctx -> 
        {
            AppResponse<Long> ret = null;
            String callerId = ctx.header("callerId");
            String userId = ctx.header(main.getUserIdHeader());
            String eventId = ctx.queryParam("eventId");
            
    		String createBy = null;
        	createBy = getStringWithEmptyCheck(callerId, null);
        	createBy = getStringWithEmptyCheck(userId, createBy);

        	ret = main.getEventDistributorService().repostEvent(eventId, createBy);

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
            {
            	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                return;
            }
            
            ctx.result("{\"eventId\":\""+ret.responseObject+"\"}");
        });

        javalin.post(main.getUrlBase()+"/repost/publish.json", ctx -> 
        {
            AppResponse<Long> ret = null;
            String callerId = ctx.header("callerId");
            String userId = ctx.header(main.getUserIdHeader());
            String publishId = ctx.queryParam("publishId");
            
    		String createBy = null;
        	createBy = getStringWithEmptyCheck(callerId, null);
        	createBy = getStringWithEmptyCheck(userId, createBy);
            
            ret = main.getEventDistributorService().republish(publishId, createBy);

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
            {
            	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                return;
            }
            
            ctx.result("{\"publishId\":\""+ret.responseObject+"\"}");
        });

        javalin.post(main.getUrlBase()+"/abort/event.json", ctx -> 
        {
            AppResponse<Long> ret = null;
            String callerId = ctx.header("callerId");
            String userId = ctx.header(main.getUserIdHeader());
            String eventId = ctx.queryParam("eventId");
            
    		String updateBy = null;
        	updateBy = getStringWithEmptyCheck(callerId, null);
        	updateBy = getStringWithEmptyCheck(userId, updateBy);

        	ret = main.getEventDistributorService().abortEvent(eventId, updateBy);

            //setting AppResponse object for usage in javalin.after handler
            ctx.attribute(JAPI_RETURN_OBJECT, ret);

            if(ret.errorDetails != null)
            {
            	ctx.result(OBJECT_MAPPER.writeValueAsString(ret.errorDetails));
                return;
            }
            
            ctx.result("{}");
        });

        javalin.start(main.getListenerPortNumber());
        
    	LOGGER.info("Start up completed");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        	LOGGER.info("Shutdown hook called");
        	javalin.stop();
        }));

        javalin.events(event -> {
            event.serverStopping(() -> {
            	LOGGER.info("Stopping event");
            });
            event.serverStopped(() -> {
            	LOGGER.info("Stopped event");
            });
        });
    	LOGGER.info("End of main method");
    }

	private static String getStringWithEmptyCheck(String in, String defaultValue)
	{
		String ret = defaultValue;
		if(in != null && in.trim().length() > 0)
		{
			ret = in;
		}
		return ret;
	}

    private void init()
    {
    	OBJECT_MAPPER.registerModule(new JavaTimeModule());
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

    private static void setHeader(Context ctx)
    {
        ctx.contentType("application/json;charset=utf-8");
        ctx.header("Content-Language", "en");
        ctx.header("requestid", MDC.get(REQUEST_ID_KEY));
    }

    private static void finishCall(Context ctx)
    {
        long startTimeMillisec = ctx.attribute(START_TIME_MILLISEC);
        LOGGER.info("End request url is {}, method is {}, time {}s, query string is {}", ctx.url(),
        		ctx.method(), (System.currentTimeMillis() - startTimeMillisec) / 1000.0,
        		ctx.queryString());
        MDC.clear();
    }
    
    public static ApplicationContext getContext()
	{
		return context;
	}

	public int getListenerPortNumber()
    {
        return listenerPortNumber;
    }

    public void setListenerPortNumber(int listenerPortNumber)
    {
        this.listenerPortNumber = listenerPortNumber;
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

    /**
     * This setter will set the mapping for AppResponse to HTTP codes.
     * The mapping is set via dependency injection
     * {
     *      "SUCCESS" : 
     *      {
     *          "" : "200",
     *          "&lt;API name&gt;" : "201"
     *      },
     *      "PARAMETER_ERROR" : 
     *      {
     *          "" : "412",
     *      }
     * }
     */
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

	/**
	 * This method can be used to set the header name for sending userid by the authentication systems.
	 * 
	 * @param userIdHeader
	 */
	public void setUserIdHeader(String userIdHeader)
	{
		this.userIdHeader = userIdHeader;
	}

}
