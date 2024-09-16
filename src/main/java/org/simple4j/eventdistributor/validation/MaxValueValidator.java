package org.simple4j.eventdistributor.validation;

/**
 * 
 * @author sj45615
 *
 */
public class MaxValueValidator extends Validator
{
    private double maxValue = 0;
    
    public MaxValueValidator()
    {
        super();
        this.validationTypeSuffix = "-maxvalue";
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
            dblValue = Double.parseDouble("" + value);
        }
        
        if(dblValue > this.getMaxValue())
        {
            return fieldName + this.validationTypeSuffix;
        }
        return null;
    }

    public double getMaxValue()
    {
        return maxValue;
    }

    public void setMaxValue(double maxValue)
    {
        this.maxValue = maxValue;
    }

    @Override
    public String toString()
    {
        return "MaxValueValidator [maxValue=" + maxValue + "]";
    }

}
