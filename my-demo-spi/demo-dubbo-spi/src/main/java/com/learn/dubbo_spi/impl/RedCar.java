package com.learn.dubbo_spi.impl;

import com.learn.dubbo_spi.api.Car;
import org.apache.dubbo.common.Extension;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

public class RedCar implements Car {

    public void getColor(URL url) {
        System.out.println("red");
    }
}
