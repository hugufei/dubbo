package com.learn.dubbo_spi.car.wrapper;

import com.learn.dubbo_spi.car.Car;
import org.apache.dubbo.common.URL;

// Car的包装类，只要存在接口的构造函数，则dubbo就会认为它是一个包装类。

// 比如spi配置文件中的格式如下
// red = com.learn.dubbo_spi.car.impls.RedCar
// black = com.learn.dubbo_spi.car.impls.BlackCar

// 那么在生成实例的过程如下：
// 1) T instance = new RedCar()
// 2) instance = new CarWrapper(instance);

// 可以理解为某个接口的AOP
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
