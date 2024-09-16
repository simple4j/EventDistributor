package org.simple4j.eventdistributor.validation;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validator entry point for every field of the java API call
 * 
 * @author sj45615
 *
 */
public class ParameterValidator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private List<Validator> validators = null;
    private String fieldName = null;
    private String argumentPropertyPath = null;

    public List<String> validate(Object parameterValue)
    {

        ArrayList<String> ret = new ArrayList<String>();
        Object fieldValue;
        try
        {
            //TODO: another option is to convert nested object tree to collections tree and use CollectionsPathRetreiver
            fieldValue = PropertyUtils.getNestedProperty(parameterValue, this.getArgumentPropertyPath());
        }
        catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
        {
            LOGGER.warn("Exception while getting nested property targetbean:{}, propertyPath:{}", parameterValue, this.getArgumentPropertyPath(), e);
            throw new RuntimeException("Exception while getting nested property targetbean:"+parameterValue+", propertyPath:"+this.getArgumentPropertyPath(), e);
        }
        
        if(getValidators() != null)
        {
            for (int i = 0 ; i < getValidators().size() ; i++)
            {
                String result = getValidators().get(i).validate(this.getFieldName(), fieldValue);
                if(result != null)
                {
                    ret.add(result);
                }
            }
        }
           
        return ret;
    }

    public List<Validator> getValidators()
    {
        return validators;
    }

    public void setValidators(List<Validator> validators)
    {
        this.validators = validators;
    }

    public String getFieldName()
    {
        if(this.fieldName == null)
            throw new RuntimeException("fieldName not configured in ValidatorCollection");
        return fieldName;
    }

    public void setFieldName(String fieldName)
    {
        this.fieldName = fieldName;
    }

    public String getArgumentPropertyPath()
    {
        return argumentPropertyPath;
    }

    public void setArgumentPropertyPath(String argumentPropertyPath)
    {
        this.argumentPropertyPath = argumentPropertyPath;
    }

    @Override
    public String toString()
    {
        return "ParameterValidator [validators=" + validators + ", fieldName=" + fieldName + ", argumentPropertyPath="
                + argumentPropertyPath + "]";
    }

}
