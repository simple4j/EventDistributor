package org.simple4j.eventdistributor.test;


import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.simple4j.eventdistributor.Main;
import org.simple4j.javalinpojoashttp.JavalinHTTPExposer;
import org.simple4j.wsfeeler.model.TestCase;
import org.simple4j.wsfeeler.model.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.collect.Lists;

public class MainTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	static WireMockServer wm1 = null;
	static WireMockServer wm2 = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		wm1 = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2001).withRootDirectory(MainTest.class.getResource("/wiremock1").getPath()));
		wm2 = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2002).withRootDirectory(MainTest.class.getResource("/wiremock2").getPath()));
		
		Main.main(new String[]{"true"});
		exposePOJOAsHTTPService();
	}

	private static void exposePOJOAsHTTPService()
	{
		ApplicationContext ac = Main.getContext();
		Object bean = ac.getBean("eventDistributorMapper");
		Class<? extends Object> class1 = bean.getClass();
		LOGGER.info("getting method instance from object of classes: {}", Lists.asList(Class.class, class1.getClasses()));
		LOGGER.info("declared methods are: {}", Lists.asList(Method.class, class1.getDeclaredMethods()));

		JavalinHTTPExposer javalinHTTPExposer = new JavalinHTTPExposer(ac);
		javalinHTTPExposer.setListenerPortNumber(2411);
		javalinHTTPExposer.expose();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		if(wm1 != null)
			wm1.shutdownServer();
		if(wm2 != null)
			wm2.shutdownServer();
	}

	@Before
	public void setUp() throws Exception
	{
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void test()
	{
		TestSuite ts = new TestSuite();
		boolean success = ts.execute();
		List<String> tcPaths = new ArrayList<String>();
		if(ts.getFailedTestCases() != null)
		{
			for (Iterator<TestCase> iterator = ts.getFailedTestCases().iterator(); iterator.hasNext();)
			{
				TestCase tc = (TestCase) iterator.next();
				tcPaths.add(tc.name);
			}
		}
		Assert.assertTrue("Failed testcases are :" + tcPaths, success);
	}
}
