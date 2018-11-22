package be.uantwerpen.sc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;

// SimCity application run
@SpringBootApplication
public class SimCityApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(SimCityApplication.class, args);
	}
}
