package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class NullValidator extends Validator
{

    public NullValidator()
    {
        super();
        this.validationTypeSuffix = "-missing";
    }
    
    @Override
    public String validate(String fieldName, Object value)
    {
        if(value == null)
        {
            return fieldName + this.validationTypeSuffix;
        }
        if(value instanceof String && ((String)value).trim().length() == 0)
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

}
