package com.learn.dubbo_spi.impl;

import com.learn.dubbo_spi.api.Car;
import org.apache.dubbo.common.URL;

public class BlackCar implements Car {

    public void getColor(URL url) {
        System.out.println("black");
    }
}
