package com.learn.dubbo_spi;

import com.learn.dubbo_spi.car.Car;
import org.apache.dubbo.common.extension.ExtensionLoader;

// 本例的作用
//1、理解ExtensionLoader加载类的过程
//2、理解Wrapper的作用-AOP
//3、理解 @Adaptive注解的功能：
// 1）修饰在方法上时-Car接口，一般是没有人工的代理类实现，需要依赖dubbo自动生成代理类。
// 2）修饰在类上时- AdaptiveExtensionFactory类 ，表示为接口ExtensionFactory的默认代理类实现

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
