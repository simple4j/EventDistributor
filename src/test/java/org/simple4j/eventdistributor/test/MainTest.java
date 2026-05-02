package org.simple4j.eventdistributor.test;


import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
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

//import com.google.common.collect.Lists;

public class MainTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	static String wm1Port="2001";
	static String wm1RootDir = "/wiremock1";
	static String wm2Port="2002";
	static String wm2RootDir = "/wiremock2";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{

		startWireMockService(wm1Port, wm1RootDir);

		startWireMockService(wm2Port, wm2RootDir);

		Main.main(new String[]{"true"});
		exposePOJOAsHTTPService();
	}

	private static void startWireMockService(String port, String rootDir)
			throws IOException
	{
		String buildDir = System.getProperty("buildDir");
		String wiremockjar = System.getProperty("wiremockjar");
		String buildTestOutputDirectory = System.getProperty("buildTestOutputDirectory");
		
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", buildDir+"/"+wiremockjar,
				"--disable-gzip", "true", "--bind-address", "localhost", "--port", port,
				"--root-dir", buildTestOutputDirectory+rootDir, "--verbose");
		
		// Merge stderr into stdout, then inherit
		pb.redirectErrorStream(true);   // merges stderr into stdout
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		Process process = pb.start();
	}

	private static void exposePOJOAsHTTPService()
	{
		ApplicationContext ac = Main.getContext();
		Object bean = ac.getBean("eventDistributorMapper");
		Class<? extends Object> class1 = bean.getClass();
		
		LOGGER.info("getting method instance from object of classes: {}", Arrays.asList(class1.getClasses()));
		LOGGER.info("declared methods are: {}", Arrays.asList(class1.getDeclaredMethods()));

		JavalinHTTPExposer javalinHTTPExposer = new JavalinHTTPExposer(ac);
		javalinHTTPExposer.setListenerPortNumber(2411);
		javalinHTTPExposer.expose();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		shutdownWiremock(wm1Port);
		shutdownWiremock(wm2Port);
	}

	private static void shutdownWiremock(String port)
	{

        HttpClient client = HttpClient.newHttpClient();

        String json = "";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+port+"/__admin/shutdown"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
		try
		{
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
			LOGGER.info("Status : " + response.statusCode());
			LOGGER.info("Body   : " + response.body());		
		} catch (IOException e)
		{
			LOGGER.warn("",e);
		} catch (InterruptedException e)
		{
			LOGGER.warn("",e);
		}

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
		ts.setTestApplicationContext(Main.getContext());
		boolean success = ts.execute();
		List<String> tcPaths = new ArrayList<String>();
		if(ts.getFailedTestCases() != null)
		{
			for (Iterator<TestCase> iterator = ts.getFailedTestCases().iterator(); iterator.hasNext();)
			{
				TestCase tc = (TestCase) iterator.next();
				tcPaths.add(tc.getName());
			}
		}
		Assert.assertTrue("Failed testcases are :" + tcPaths, success);
	}
}
