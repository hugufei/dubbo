package com.learn.dubbo_spi.cars;

import com.learn.dubbo_spi.CarInterface;

public class BlackCar implements CarInterface {

    public void getColor() {
        System.out.println("black");
    }
}
