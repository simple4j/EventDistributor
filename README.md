# EventDistributor
This is a web service where it accepts an event, applies some configured rules and send notifications to target systems.

When an event is posted, it first checks if there was another event submitted with same business record id, type, subtype and version
in the last x milliseconds. Where x is the configured with duplicateCheckExpiryMillisec property of EventDistributorServiceImpl.

Once duplicate check passes, the event is accepted for processing.
There can be various sources for posting the event. Based on the source, a cooling period can be configured per source.
This is to allow some time for other systems to synch if necessary.

The target systems to get the notification through web service call are configured with eventTargetRules property of EventDistributorServiceImpl. Each targetId is mapped to WSClient Caller instance through targetId2Caller property of EventFetcher.
The target Caller instance will have the configuration for notification web service call details like host, port, ssl, URL, HTTP method, headers, body. The response from the target system is considered if its success based on targetId2SuccessResponseMatchRegexPattern property of EventFetcher. The regex pattern is matched across, status code, response headers and body.

If there are scenarios to retrigger any past event or a publish attempt to a specific target system, the service offers repostEvent and republish calls.
Other API calls include aborting an event thats posted but not yet processed and get event, get events with various criteria.
