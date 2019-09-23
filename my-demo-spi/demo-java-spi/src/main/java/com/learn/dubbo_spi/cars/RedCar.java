package com.learn.dubbo_spi.cars;

import com.learn.dubbo_spi.CarInterface;

public class RedCar implements CarInterface {

    public void getColor() {
        System.out.println("red");
    }
}
