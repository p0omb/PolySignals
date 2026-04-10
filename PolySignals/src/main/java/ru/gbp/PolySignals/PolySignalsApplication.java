package ru.gbp.PolySignals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PolySignalsApplication {

	public static void main(String[] args) {
		SpringApplication.run(PolySignalsApplication.class, args);
	}

}
