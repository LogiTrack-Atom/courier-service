package com.logitrackatom.courierservice;

import org.springframework.boot.SpringApplication;

public class TestCourierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CourierServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
