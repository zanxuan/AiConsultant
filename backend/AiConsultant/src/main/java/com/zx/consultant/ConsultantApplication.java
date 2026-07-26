package com.zx.consultant;

import com.zx.consultant.common.config.LocalDotenvBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class ConsultantApplication {

    public static void main(String[] args) {
        LocalDotenvBootstrap.load();
        SpringApplication.run(ConsultantApplication.class, args);
    }

}
