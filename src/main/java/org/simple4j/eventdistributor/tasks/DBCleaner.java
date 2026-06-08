package org.simple4j.eventdistributor.tasks;

import java.lang.invoke.MethodHandles;

import org.simple4j.eventdistributor.Main;
import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class DBCleaner implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private EventDistributorMapper eventDistributorMapper = null;
	private long sleepTimeInMillisec = 1800000;
	private int maxCleanupRecordCountPerBatch = 10000;
	private int maxNumberOfDeletesPerBatch = 200;
	private int cleanupAgingInDays = 30;
	private int maxCleanupRecordCountPerDelete = 100;

	public EventDistributorMapper getEventDistributorMapper()
	{
		if(this.eventDistributorMapper == null)
			throw new RuntimeException("eventDistributorMapper not configured in DBCleaner instance");
		return eventDistributorMapper;
	}

	public void setEventDistributorMapper(EventDistributorMapper eventDistributorMapper)
	{
		this.eventDistributorMapper = eventDistributorMapper;
	}

	public long getSleepTimeInMillisec()
	{
		return sleepTimeInMillisec;
	}

	public void setSleepTimeInMillisec(long sleepTimeInMillisec)
	{
		this.sleepTimeInMillisec = sleepTimeInMillisec;
	}

	public int getMaxCleanupRecordCountPerBatch()
	{
		return maxCleanupRecordCountPerBatch;
	}

	public void setMaxCleanupRecordCountPerBatch(int maxCleanupRecordCountPerBatch)
	{
		this.maxCleanupRecordCountPerBatch = maxCleanupRecordCountPerBatch;
	}

	public int getMaxNumberOfDeletesPerBatch()
	{
		return maxNumberOfDeletesPerBatch;
	}

	public void setMaxNumberOfDeletesPerBatch(int maxNumberOfDeletesPerBatch)
	{
		this.maxNumberOfDeletesPerBatch = maxNumberOfDeletesPerBatch;
	}

	public int getCleanupAgingInDays()
	{
		return cleanupAgingInDays;
	}

	public void setCleanupAgingInDays(int cleanupAgingInDays)
	{
		this.cleanupAgingInDays = cleanupAgingInDays;
	}

	public int getMaxCleanupRecordCountPerDelete()
	{
		return maxCleanupRecordCountPerDelete;
	}

	public void setMaxCleanupRecordCountPerDelete(int maxCleanupRecordCountPerDelete)
	{
		this.maxCleanupRecordCountPerDelete = maxCleanupRecordCountPerDelete;
	}

	@Override
	public void run()
	{
		try
		{
            MDC.put(Main.REQUEST_ID_KEY, ""+System.currentTimeMillis());
		
			int deleteRecordCount = 0;
			int deleteExecutionCount = 0;
			int lastDeleteCount = 1;
			while (deleteRecordCount < this.getMaxCleanupRecordCountPerBatch() &&
					deleteExecutionCount < this.getMaxNumberOfDeletesPerBatch() &&
					lastDeleteCount > 0)
			{
				lastDeleteCount = this.getEventDistributorMapper().deleteIldRecords(this.getCleanupAgingInDays(), this.getMaxCleanupRecordCountPerDelete());
				deleteRecordCount = deleteRecordCount + lastDeleteCount;
				deleteExecutionCount = deleteExecutionCount+1;
				
				LOGGER.debug("deleteRecordCount:{}", deleteRecordCount);
				LOGGER.debug("deleteExecutionCount:{}", deleteExecutionCount);
			}
		}
		catch(Throwable t)
		{
			LOGGER.error("", t);
		}
		finally
		{
			MDC.clear();
		}
	}
    

}
