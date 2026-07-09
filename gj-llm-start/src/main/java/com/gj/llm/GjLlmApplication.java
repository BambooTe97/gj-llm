package com.gj.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * gj-llm 应用入口。
 *
 * @author gj-llm
 */
@EnableAsync
@SpringBootApplication
public class GjLlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GjLlmApplication.class, args);
    }

}
