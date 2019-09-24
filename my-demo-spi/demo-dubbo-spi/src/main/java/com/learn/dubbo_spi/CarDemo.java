package com.learn.dubbo_spi;

import com.learn.dubbo_spi.car.Car;
import org.apache.dubbo.common.extension.ExtensionLoader;

// 本例的作用
//1、理解ExtensionLoader加载类的过程
//2、理解Wrapper的作用-AOP

public class CarDemo {

    public static void main(String[] args) {
        // 每个接口对应一个ExtensionLoader
        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        Car redCar = extensionLoader.getExtension("red");
        redCar.getColor(null);

//        Car blackCar = extensionLoader.getExtension("black");
//        blackCar.getColor(null);
    }
}
