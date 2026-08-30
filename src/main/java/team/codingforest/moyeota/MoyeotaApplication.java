package team.codingforest.moyeota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoyeotaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoyeotaApplication.class, args);
	}

}
