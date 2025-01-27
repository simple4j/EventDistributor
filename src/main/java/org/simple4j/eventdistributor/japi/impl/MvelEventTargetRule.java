package org.simple4j.eventdistributor.japi.impl;

import org.mvel2.MVEL;
import org.simple4j.eventdistributor.beans.Event;
import org.simple4j.eventdistributor.japi.EventTargetRule;

/**
 * Mvel based implementation where the rule can be configured with Mvel expression
 */
public class MvelEventTargetRule extends EventTargetRule
{
	private String mvelExpression = null;
	public String getMvelExpression()
	{
		if(this.mvelExpression == null)
			throw new RuntimeException("mvelExpression is not configured in MvelEventTargetRule bean");
		return mvelExpression;
	}
	public void setMvelExpression(String mvelExpression)
	{
		this.mvelExpression = mvelExpression;
	}

	public boolean eval(Event event)
	{
		return MVEL.evalToBoolean(this.getMvelExpression(), event);
	}
	
}
