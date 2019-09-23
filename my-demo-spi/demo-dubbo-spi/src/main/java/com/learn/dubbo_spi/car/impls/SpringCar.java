package com.learn.dubbo_spi.car.impls;

import com.learn.dubbo_spi.car.Car;
import org.apache.dubbo.common.URL;

public class SpringCar implements Car {

    @Override
    public void getColor(URL url) {
        System.out.println("spring");
    }
}
