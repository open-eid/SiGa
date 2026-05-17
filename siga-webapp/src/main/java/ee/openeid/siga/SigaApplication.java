package ee.openeid.siga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SigaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigaApplication.class, args);
    }

}

