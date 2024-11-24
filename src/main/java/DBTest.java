import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.simple4j.eventdistributor.dao.EventDistributorMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DBTest
{

	public static void main(String[] args)
	{
		ApplicationContext context = new ClassPathXmlApplicationContext("databaseContext.xml");

        EventDistributorMapper edm = context.getBean("eventDistributorMapper", EventDistributorMapper.class);
        
		Instant statusExpiryTimeInstant = Instant.ofEpochMilli(System.currentTimeMillis() + 600000);
		ZonedDateTime statusExpiry = ZonedDateTime.ofInstant(statusExpiryTimeInstant, ZoneId.systemDefault());
		ZonedDateTime currentTime = ZonedDateTime.now();
		edm.lockEvents("aa", 5, statusExpiry, currentTime);

	}

}
