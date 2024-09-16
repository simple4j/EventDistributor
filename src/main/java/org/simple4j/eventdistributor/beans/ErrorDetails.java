package org.simple4j.eventdistributor.beans;

import java.util.ArrayList;
import java.util.List;

public class ErrorDetails {
	
	public String errorId = null;
	public ErrorType errorType = null;
	public List<String> errorReason = new ArrayList<String>();
	public String errorDescription = null;

	public enum ErrorType
	{
	    PARAMETER_ERROR,
	    EVENT_NOTFOUND,
	    CALLER_NOTAUTHORIZED,
	    EVENT_INPROGRESS,
	    RUNTIME_ERROR,
	    DUPLICATE_REQUEST
	}

    @Override
    public String toString()
    {
        return "ErrorDetails [errorId=" + errorId + ", errorType=" + errorType + ", errorReason=" + errorReason
                + ", errorDescription=" + errorDescription + ", toString()=" + super.toString() + "]";
    }
	
	
}
