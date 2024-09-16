package org.simple4j.eventdistributor.beans;

import java.time.ZonedDateTime;
import java.util.List;

public class Event
{
    private Long eventId;
    private String businessRecordId;
    private String businessRecordType;
    private String businessRecordSubType;
    private String businessRecordVersion;
    private String source;
    private EventStatus status;
    private Long duplicateEventId;
    private ZonedDateTime duplicateCheckEndTime;
    private Long repostParentEventId;
    private String processingHost;
    private ZonedDateTime statusExpiryTime;
    private Long createTimeMillisec;
    private ZonedDateTime createTime;
    private Long updateTimeMillisec;
    private ZonedDateTime updateTime;
    private String createBy;
    private String updateBy;
    private List<String> targetIds;
    private List<PublishAttempt> publishAttempts;
    
	public Long getEventId()
	{
		return eventId;
	}
	public void setEventId(Long eventId)
	{
		this.eventId = eventId;
	}
	public String getBusinessRecordId()
	{
		return businessRecordId;
	}
	public void setBusinessRecordId(String businessRecordId)
	{
		this.businessRecordId = businessRecordId;
	}
	public String getBusinessRecordType()
	{
		return businessRecordType;
	}
	public void setBusinessRecordType(String businessRecordType)
	{
		this.businessRecordType = businessRecordType;
	}
	public String getBusinessRecordSubType()
	{
		return businessRecordSubType;
	}
	public void setBusinessRecordSubType(String businessRecordSubType)
	{
		this.businessRecordSubType = businessRecordSubType;
	}
	public String getBusinessRecordVersion()
	{
		return businessRecordVersion;
	}
	public void setBusinessRecordVersion(String businessRecordVersion)
	{
		this.businessRecordVersion = businessRecordVersion;
	}
	public String getSource()
	{
		return source;
	}
	public void setSource(String source)
	{
		this.source = source;
	}
	public EventStatus getStatus()
	{
		return status;
	}
	public void setStatus(EventStatus status)
	{
		this.status = status;
	}
	public Long getDuplicateEventId()
	{
		return duplicateEventId;
	}
	public void setDuplicateEventId(Long duplicateEventId)
	{
		this.duplicateEventId = duplicateEventId;
	}
//	public Long getDuplicateCheckEndTimeNanosec()
//	{
//		if(this.getDuplicateCheckEndTime() == null)
//			return null;
//		Instant instant = this.getDuplicateCheckEndTime().toInstant();
//		return (instant.getEpochSecond() * 1000000000) + instant.getNano(); 
//	}
//	public void setDuplicateCheckEndTimeNanosec(Long duplicateCheckEndTimeNanosec)
//	{
//		long epochSecond = duplicateCheckEndTimeNanosec/1000000000;
//		Instant instant = Instant.ofEpochSecond(epochSecond, duplicateCheckEndTimeNanosec - epochSecond * 1000000000);
//		ZonedDateTime atZone = instant.atZone(ZoneId.systemDefault());
//		this.setDuplicateCheckEndTime(atZone);
//	}
	public ZonedDateTime getDuplicateCheckEndTime()
	{
		return duplicateCheckEndTime;
	}
	public void setDuplicateCheckEndTime(ZonedDateTime duplicateCheckEndTime)
	{
		this.duplicateCheckEndTime = duplicateCheckEndTime;
	}
	public Long getRepostParentEventId()
	{
		return repostParentEventId;
	}
	public void setRepostParentEventId(Long repostParentEventId)
	{
		this.repostParentEventId = repostParentEventId;
	}
	public String getProcessingHost()
	{
		return processingHost;
	}
	public void setProcessingHost(String processingHost)
	{
		this.processingHost = processingHost;
	}
	public ZonedDateTime getStatusExpiryTime()
	{
		return statusExpiryTime;
	}
	public void setStatusExpiryTime(ZonedDateTime statusExpiryTime)
	{
		this.statusExpiryTime = statusExpiryTime;
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
	public String getUpdateBy()
	{
		return updateBy;
	}
	public void setUpdateBy(String updateBy)
	{
		this.updateBy = updateBy;
	}
	public List<String> getTargetIds()
	{
		return targetIds;
	}
	public void setTargetIds(List<String> targetIds)
	{
		this.targetIds = targetIds;
	}
	public List<PublishAttempt> getPublishAttempts()
	{
		return publishAttempts;
	}
	public void setPublishAttempts(List<PublishAttempt> publishAttempts)
	{
		this.publishAttempts = publishAttempts;
	}
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString()).append("[eventId=").append(eventId).append(", businessRecordId=")
				.append(businessRecordId).append(", businessRecordType=").append(businessRecordType)
				.append(", businessRecordSubType=").append(businessRecordSubType).append(", businessRecordVersion=")
				.append(businessRecordVersion).append(", source=").append(source).append(", status=").append(status)
				.append(", duplicateEventId=").append(duplicateEventId).append(", duplicateCheckEndTime=")
				.append(duplicateCheckEndTime).append(", repostParentEventId=").append(repostParentEventId)
				.append(", processingHost=").append(processingHost).append(", statusExpiryTime=")
				.append(statusExpiryTime).append(", createTimeMillisec=").append(createTimeMillisec)
				.append(", createTime=").append(createTime).append(", updateTimeMillisec=").append(updateTimeMillisec)
				.append(", updateTime=").append(updateTime).append(", createBy=").append(createBy).append(", updateBy=")
				.append(updateBy).append(", targetIds=").append(targetIds).append(", publishAttempts=")
				.append(publishAttempts).append("]");
		return builder.toString();
	}
    
}
