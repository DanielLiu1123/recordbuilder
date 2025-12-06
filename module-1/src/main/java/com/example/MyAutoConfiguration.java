package com.example;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;

@AutoConfiguration
public class MyAutoConfiguration implements SmartInitializingSingleton {
    @Override
    public void afterSingletonsInstantiated() {
        System.out.println("MyAutoConfiguration's afterSingletonsInstantiated() method is called...");
    }
}
