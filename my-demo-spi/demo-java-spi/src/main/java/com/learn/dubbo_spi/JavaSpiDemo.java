package com.learn.dubbo_spi;

import java.util.Iterator;
import java.util.ServiceLoader;

public class JavaSpiDemo {

    public static void main(String[] args) {
        ServiceLoader<CarInterface> serviceLoader = ServiceLoader.load(CarInterface.class);
        Iterator<CarInterface> iterator = serviceLoader.iterator();
        // java spi不能单独的获取某个指定的实现类
        // java spi没有IOC和AOP机制
        while (iterator.hasNext()) {
            CarInterface carInterface = iterator.next();
            carInterface.getColor();
        }
    }
}
