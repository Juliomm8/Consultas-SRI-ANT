package ec.edu.udla.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;

@SpringBootApplication(exclude = RedisAutoConfiguration.class)
public class ConsultasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultasApplication.class, args);
    }
}
