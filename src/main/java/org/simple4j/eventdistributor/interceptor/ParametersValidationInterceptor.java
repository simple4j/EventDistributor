package org.simple4j.eventdistributor.interceptor;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.simple4j.eventdistributor.beans.AppResponse;
import org.simple4j.eventdistributor.beans.ErrorDetails;
import org.simple4j.eventdistributor.validation.ParameterValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This interceptor will trigger field validations using AOP before java API is called and 
 * returns any failures to web API layer. The validation rules can be configured using Spring xml
 * without any hardcoding or hardwiring in the java API implementation class.
 * 
 * @author sj45615
 *
 */
public class ParametersValidationInterceptor implements MethodInterceptor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, ParameterValidator[]> method2Validators = null;
    
    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable
    {
        Method method = methodInvocation.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] arguments = methodInvocation.getArguments();
        
        String targetMethodSignature = method.toString();
        
        ParameterValidator[] parameterValidators = this.getMethod2Validators().get(targetMethodSignature);
        
        if(parameterValidators == null)
        {
            LOGGER.info("No validator configured for method {}", targetMethodSignature);
        }
        else
        {
            HashMap<String, Object> parametersMap = new HashMap<>();
            for (int i = 0 ; i < parameters.length ; i++)
            {
                if(parameterValidators[i] != null)
                {
                    String fieldName = parameterValidators[i].getFieldName();
                    LOGGER.trace("fieldName {}", fieldName);
                    Object argument = arguments[i];
                    parametersMap.put(fieldName, argument);
                }
            }
            LOGGER.debug("parametersMap {}", parametersMap);
            List<String> errorReason = new ArrayList<String>();
            
            for(int i = 0 ; i < parameterValidators.length ; i++)
            {
                errorReason.addAll(parameterValidators[i].validate(parametersMap));
            }
            
            if(errorReason.size() > 0)
            {
                AppResponse ret = new AppResponse();
                ErrorDetails errorDetails = new ErrorDetails();
                errorDetails.errorType = ErrorDetails.ErrorType.PARAMETER_ERROR;
                errorDetails.errorId = System.currentTimeMillis() + "@" + InetAddress.getLocalHost().getHostName();
                errorDetails.errorReason = errorReason;
                ret.errorDetails = errorDetails;
                return ret;
            }
        }
        return methodInvocation.proceed();
    }

    public Map<String, ParameterValidator[]> getMethod2Validators()
    {
        if(this.method2Validators == null)
            throw new RuntimeException("method2Validators not configured in ParametersValidationInterceptor");
        return method2Validators;
    }

    public void setMethod2Validators(Map<String, ParameterValidator[]> method2Validators)
    {
        this.method2Validators = method2Validators;
    }

}
