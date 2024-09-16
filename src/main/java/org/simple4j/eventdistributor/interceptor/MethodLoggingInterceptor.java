package org.simple4j.eventdistributor.interceptor;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interceptor will log method entry and exits using AOP.
 * The logger names used are
 * <fully qualified class name> - to control logging of method entry/exits at class level
 * <fully qualified class name>.parameter.<method name> - to control logging of parameters at class level or method level
 * <fully qualified class name>.returnvalue.<method name> - to control logging of return values at class level or method level
 * @author sj45615
 *
 */
public class MethodLoggingInterceptor implements MethodInterceptor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    
    private ConcurrentHashMap<Class, Logger> clsLoggers = new ConcurrentHashMap<Class, Logger>();
    private ConcurrentHashMap<String, Logger> strLoggers = new ConcurrentHashMap<String, Logger>();
    private boolean printStacktrace = true;
    private ThreadLocal<Indentor> threadLocalIndentor = new ThreadLocal<Indentor>();
    private char indentationChar = '~';
    private String parameterLoggerName = "parameter";
    private String returnValueLoggerName = "returnvalue";

    public boolean isPrintStacktrace() {
        return printStacktrace;
    }

    public void setPrintStacktrace(boolean printStacktrace) {
        this.printStacktrace = printStacktrace;
    }

    public char getIndentationChar() {
        return indentationChar;
    }

    public void setIndentationChar(char indentationChar) {
        this.indentationChar = indentationChar;
    }

    public String getParameterLoggerName() {
        return parameterLoggerName;
    }

    public void setParameterLoggerName(String parameterLoggerName) {
        this.parameterLoggerName = parameterLoggerName;
    }

    public String getReturnValueLoggerName() {
        return returnValueLoggerName;
    }

    public void setReturnValueLoggerName(String returnValueLoggerName) {
        this.returnValueLoggerName = returnValueLoggerName;
    }

    public Object invoke(final MethodInvocation methodInvocation) throws Throwable {
        LOGGER.debug("Entering MethodLoggingInterceptor.invoke");
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("parameter methodInvocation=" + methodInvocation);
        }
        Class declaringClass = methodInvocation.getMethod().getDeclaringClass();
        Logger logger = this.clsLoggers.get(declaringClass);
        if (logger == null) {
            logger = LoggerFactory.getLogger(declaringClass);
            this.clsLoggers.putIfAbsent(declaringClass, logger);
        }
        String parameterLoggerFullName = declaringClass.getName() + "." + this.getParameterLoggerName() + "." + methodInvocation.getMethod().getName();
        Logger parameterLogger = this.strLoggers.get(parameterLoggerFullName);
        if (parameterLogger == null) {
            parameterLogger = LoggerFactory.getLogger(parameterLoggerFullName);
            this.strLoggers.putIfAbsent(parameterLoggerFullName, parameterLogger);
        }
        String returnValueLoggerFullName = declaringClass.getName() + "." + this.getReturnValueLoggerName() + "." + methodInvocation.getMethod().getName();
        Logger returnValueLogger = this.strLoggers.get(returnValueLoggerFullName);
        if (returnValueLogger == null) {
            returnValueLogger = LoggerFactory.getLogger(returnValueLoggerFullName);
            this.strLoggers.putIfAbsent(returnValueLoggerFullName, returnValueLogger);
        }
        String targetMethodSignature = methodInvocation.getMethod().toString();
        String indentString = "";
        Indentor indentor = threadLocalIndentor.get();
        try {
            if (indentor == null) {
                indentor = new Indentor(this.getIndentationChar());
                threadLocalIndentor.set(indentor);
            } else {
                indentor.add(1);
            }
            indentString = indentor.getIndentString();
            logger.info(indentString + "Entering " + targetMethodSignature);
            if (parameterLogger.isDebugEnabled()) {
                parameterLogger.debug(indentString + "parameters are:" + Arrays.asList(methodInvocation.getArguments()));
            }
            long startTimeMilliSec = System.currentTimeMillis();
            Object retVal = methodInvocation.proceed();
            long endTimeMilliSec = System.currentTimeMillis();
            logger.info(indentString + "Exiting " + targetMethodSignature + ":" + (endTimeMilliSec - startTimeMilliSec));
            if (returnValueLogger.isDebugEnabled()) {
                returnValueLogger.debug(indentString + "return value=" + retVal);
            }
            return retVal;
        } catch (Throwable e) {
            logger.warn(indentString + "Error while calling " + targetMethodSignature);
            if (this.isPrintStacktrace()) {
                logger.warn(indentString + "", e);
            }
            throw e;
        } finally {
            LOGGER.debug("Exiting MethodLoggingInterceptor.invoke");
            indentor.sub(1);
        }
    }
}

class Indentor {
    private int indentSize = 1;
    private char indentChar = '~';

    public Indentor(char indentChar) {
        this.indentChar = indentChar;
    }

    public int getIndentSize() {
        return indentSize;
    }

    public void add(int increment) {
        this.indentSize = this.indentSize + increment;
    }

    public void sub(int decrement) {
        this.indentSize = this.indentSize - decrement;
        if (this.indentSize < 1)
            this.indentSize = 1;
    }

    public String getIndentString() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < this.getIndentSize(); i++)
            ret.append(indentChar);
        return ret.toString();
    }
}