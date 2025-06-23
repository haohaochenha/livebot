package com.example.douyinlive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
// 移除 @MapperScan 注解
public class DouyinliveApplication {

    public static void main(String[] args) {
        SpringApplication.run(DouyinliveApplication.class, args);
    }

    // 定义 RestTemplate Bean，用于 HTTP 请求
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}