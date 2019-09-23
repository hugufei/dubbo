package com.learn.dubbo_spi.impl;

import com.learn.dubbo_spi.api.Car;
import org.apache.dubbo.common.URL;

public class CarWrapper implements Car {

    private Car car;

    public CarWrapper(Car car) {
        this.car = car;
    }

    @Override
    public void getColor(URL url) {
        System.out.println("before...");
        car.getColor(url);
        System.out.println("after...");
    }
}
