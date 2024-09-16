package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class RegExpValidator extends Validator
{
    private String regExp = null;
    
    public RegExpValidator()
    {
        super();
        this.validationTypeSuffix = "-invalid";
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
        
        if(!strValue.matches(this.getRegExp()))
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

    public String getRegExp()
    {
        return regExp;
    }

    public void setRegExp(String regExp)
    {
        this.regExp = regExp;
    }

    @Override
    public String toString()
    {
        return "RegExpValidator [regExp=" + regExp + "]";
    }

}
