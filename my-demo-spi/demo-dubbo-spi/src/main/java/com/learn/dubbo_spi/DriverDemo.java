package com.learn.dubbo_spi;

import com.learn.dubbo_spi.driver.Driver;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 1-理解dubbo依赖注入的流程-dubbo只有set注入

// 2、理解 @Adaptive注解的功能：
//  1）修饰在方法上时-Car接口，一般是没有人工的代理类实现，需要依赖dubbo自动生成代理类。
//  2）修饰在类上时- AdaptiveExtensionFactory类 ，表示为接口ExtensionFactory的默认代理类实现

// 3-可以使用arthas查看Car的默认代理类： https://github.com/alibaba/arthas/blob/master/README_CN.md

public class DriverDemo {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/spring.xml");
        context.start();
        SpringExtensionFactory.addApplicationContext(context);

        ExtensionLoader<Driver> extensionLoader = ExtensionLoader.getExtensionLoader(Driver.class);
        Driver driver = extensionLoader.getExtension("trucker");

        Map<String, String> map = new HashMap<>();
        map.put("carType", "black");
        URL url = new URL("", "", 0, map);
        driver.doDrive(url);

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
