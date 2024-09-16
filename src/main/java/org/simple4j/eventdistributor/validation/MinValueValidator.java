package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class MinValueValidator extends Validator
{
    private double minValue = 0;
    
    public MinValueValidator()
    {
        super();
        this.validationTypeSuffix = "-minvalue";
    }

    @Override
    public String validate(String fieldName, Object value)
    {
        if(value == null)
            return null;
        Double dblValue = null;
        if(value instanceof Number)
        {
            dblValue = ((Number) value).doubleValue();
            
        }
        else
        {
            if(value != null)
                dblValue = Double.parseDouble("" + value);
        }
        
        if(value == null || dblValue < this.getMinValue())
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

    public double getMinValue()
    {
        return minValue;
    }

    public void setMinValue(double minValue)
    {
        this.minValue = minValue;
    }

    @Override
    public String toString()
    {
        return "MinValueValidator [minValue=" + minValue + "]";
    }

}
