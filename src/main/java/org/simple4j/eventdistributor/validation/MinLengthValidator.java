package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class MinLengthValidator extends Validator
{
    private Integer minLength = 0;
    
    public MinLengthValidator()
    {
        super();
        this.validationTypeSuffix = "-minlength";
    }

    @Override
    public String validate(String fieldName, Object value)
    {
        if(value == null)
            return null;
        String strValue = null;
        if(value instanceof String)
        {
            strValue = ((String) value).trim();
            
        }
        else
        {
            strValue = "" + value;
        }
        
        if(value == null || strValue.length() < this.getMinLength())
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

    public Integer getMinLength()
    {
        return minLength;
    }

    public void setMinLength(Integer minLength)
    {
        this.minLength = minLength;
    }

    @Override
    public String toString()
    {
        return "MinLengthValidator [minLength=" + minLength + "]";
    }

}
