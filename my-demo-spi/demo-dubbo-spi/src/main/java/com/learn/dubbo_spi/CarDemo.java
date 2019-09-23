package com.learn.dubbo_spi;

import com.learn.dubbo_spi.api.Car;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

public class CarDemo {

    public static void main(String[] args) {
        // 每个接口对应一个ExtensionLoader
        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        Car redCar = extensionLoader.getExtension("red");
        redCar.getColor(null);

        Car blackCar = extensionLoader.getExtension("black");
        blackCar.getColor(null);
    }
}
