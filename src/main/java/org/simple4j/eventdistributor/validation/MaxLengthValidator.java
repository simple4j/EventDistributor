package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class MaxLengthValidator extends Validator
{
    private Integer maxLength = 0;
    
    public MaxLengthValidator()
    {
        super();
        this.validationTypeSuffix = "-maxlength";
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
        
        if(strValue.length() > this.getMaxLength())
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

    public Integer getMaxLength()
    {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength)
    {
        this.maxLength = maxLength;
    }

    @Override
    public String toString()
    {
        return "MaxLengthValidator [maxLength=" + maxLength + "]";
    }

}
