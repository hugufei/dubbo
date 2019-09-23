package com.learn.dubbo_spi;

import com.learn.dubbo_spi.api.Driver;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Map;

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
        driver.driveCar(url);


//        try {
//            System.in.read();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }
}
