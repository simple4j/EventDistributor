package org.simple4j.eventdistributor.japi;

import java.util.List;

import org.simple4j.eventdistributor.beans.Event;

/**
 * Abstract class to hold the rules to map event to target systems.
 * 
 */
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

	/**
	 * This method will return true if the rule results in a matches for the event passed.
	 * 
	 * @param event - the rule will be applied on this event instance
	 * @return - return true if the rule matches
	 * 
	 */
	public abstract boolean eval(Event event);
}
