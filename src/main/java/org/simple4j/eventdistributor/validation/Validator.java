package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public abstract class Validator
{

    protected String validationTypeSuffix = null;
    
    abstract public String validate(String fieldName, Object value);
}
