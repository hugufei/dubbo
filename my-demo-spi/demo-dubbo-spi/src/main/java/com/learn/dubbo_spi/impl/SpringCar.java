package com.learn.dubbo_spi.impl;

import com.learn.dubbo_spi.api.Car;
import org.apache.dubbo.common.URL;

public class SpringCar implements Car {

    @Override
    public void getColor(URL url) {
        System.out.println("spring1111");
    }
}
