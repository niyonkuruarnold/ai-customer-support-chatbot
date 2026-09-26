package com.codafriqa.ai_customer_support_chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // enables @Async — used for non-blocking SMTP email notifications
public class AiCustomerSupportChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCustomerSupportChatbotApplication.class, args);
    }
}
