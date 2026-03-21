package com.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * VAN 서비스 애플리케이션
 */
@EnableFeignClients
@SpringBootApplication
public class VANServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VANServiceApplication.class, args);
    }
}
