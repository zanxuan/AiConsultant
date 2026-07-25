package com.zx.consultant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class ConsultantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultantApplication.class, args);
    }

}
