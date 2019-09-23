package com.learn.dubbo_spi.impl;

import com.learn.dubbo_spi.api.Car;
import com.learn.dubbo_spi.api.Driver;
import org.apache.dubbo.common.URL;

public class Trucker implements Driver {

    private Car car;

//    public void setCar(Car car) {
//        this.car = car;
//    }

    //注入
    public void setSpringCar(Car car) {
        this.car = car;
    }

    @Override
    public void driveCar(URL url) {
        car.getColor(url);
    }
}
