package org.simple4j.eventdistributor.test;


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
import org.simple4j.wsfeeler.model.TestCase;
import org.simple4j.wsfeeler.model.TestSuite;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class MainTest
{

	static WireMockServer wm1 = null;
	static WireMockServer wm2 = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		wm1 = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2001).withRootDirectory(MainTest.class.getResource("/wiremock1").getPath()));
		wm2 = new WireMockServer(WireMockConfiguration.options().bindAddress("localhost").port(2002).withRootDirectory(MainTest.class.getResource("/wiremock2").getPath()));
		
		Main.main(null);
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
