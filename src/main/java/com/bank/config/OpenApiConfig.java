package com.bank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 설정 클래스
 * Bank Service의 API 문서화를 위한 설정
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank Service API")
                        .description("카드 결제 시스템의 은행 서비스 API 문서입니다. " +
                                "계좌 잔액 조회, 출금 처리, 이체 처리 기능을 제공합니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Bank Service Team")
                                .email("bank-service@example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버")
                ));
    }
}
