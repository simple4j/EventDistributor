package org.simple4j.eventdistributor.japi;

import java.util.List;

import org.simple4j.eventdistributor.beans.Event;

public abstract class EventTargetRule
{
	protected List<String> targetIds = null;
	public List<String> getTargetIds()
	{
		return targetIds;
	}
	public void setTargetIds(List<String> targetIds)
	{
		this.targetIds = targetIds;
	}

	/*
	 * Rule evaluation should not depend on source of the event as that will cause conflict with duplicate detection logic which does not include source value check.
	 */
	public abstract boolean eval(Event event);
}
