package org.simple4j.eventdistributor.beans;

import java.time.ZonedDateTime;

public class PublishAttempt
{
    private Long publishId;
    private Long eventId;
    private String targetId;
    private String responseHttpCode;
    private String responseBody;
    private String errorDetails;
    private PublishAttemptStatus publishAttemptStatus;
    private Long createTimeMillisec;
    private ZonedDateTime createTime;
    private Long updateTimeMillisec;
    private ZonedDateTime updateTime;
    private String createBy;
	public Long getPublishId()
	{
		return publishId;
	}
	public void setPublishId(Long publishId)
	{
		this.publishId = publishId;
	}
	public Long getEventId()
	{
		return eventId;
	}
	public void setEventId(Long eventId)
	{
		this.eventId = eventId;
	}
	public String getTargetId()
	{
		return targetId;
	}
	public void setTargetId(String targetId)
	{
		this.targetId = targetId;
	}
	public String getResponseHttpCode()
	{
		return responseHttpCode;
	}
	public void setResponseHttpCode(String responseHttpCode)
	{
		this.responseHttpCode = responseHttpCode;
	}
	public String getResponseBody()
	{
		return responseBody;
	}
	public void setResponseBody(String responseBody)
	{
		this.responseBody = responseBody;
	}
	public String getErrorDetails()
	{
		return errorDetails;
	}
	public void setErrorDetails(String errorDetails)
	{
		this.errorDetails = errorDetails;
	}
	public PublishAttemptStatus getPublishAttemptStatus()
	{
		return publishAttemptStatus;
	}
	public void setPublishAttemptStatus(PublishAttemptStatus publishAttemptStatus)
	{
		this.publishAttemptStatus = publishAttemptStatus;
	}
	public Long getCreateTimeMillisec()
	{
		return createTimeMillisec;
	}
	public void setCreateTimeMillisec(Long createTimeMillisec)
	{
		this.createTimeMillisec = createTimeMillisec;
	}
	public ZonedDateTime getCreateTime()
	{
		return createTime;
	}
	public void setCreateTime(ZonedDateTime createTime)
	{
		this.createTime = createTime;
	}
	public Long getUpdateTimeMillisec()
	{
		return updateTimeMillisec;
	}
	public void setUpdateTimeMillisec(Long updateTimeMillisec)
	{
		this.updateTimeMillisec = updateTimeMillisec;
	}
	public ZonedDateTime getUpdateTime()
	{
		return updateTime;
	}
	public void setUpdateTime(ZonedDateTime updateTime)
	{
		this.updateTime = updateTime;
	}
	public String getCreateBy()
	{
		return createBy;
	}
	public void setCreateBy(String createBy)
	{
		this.createBy = createBy;
	}
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString()).append("[publishId=").append(publishId).append(", eventId=").append(eventId)
				.append(", targetId=").append(targetId).append(", responseHttpCode=").append(responseHttpCode)
				.append(", responseBody=").append(responseBody).append(", errorDetails=").append(errorDetails)
				.append(", publishAttemptStatus=").append(publishAttemptStatus).append(", createTimeMillisec=")
				.append(createTimeMillisec).append(", createTime=").append(createTime).append(", updateTimeMillisec=")
				.append(updateTimeMillisec).append(", updateTime=").append(updateTime).append(", createBy=")
				.append(createBy).append("]");
		return builder.toString();
	}
}
