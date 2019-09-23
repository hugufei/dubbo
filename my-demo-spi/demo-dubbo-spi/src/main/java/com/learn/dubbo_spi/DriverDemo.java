package com.learn.dubbo_spi;

import com.learn.dubbo_spi.driver.Driver;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Map;

// 1-理解dubbo依赖注入的流程
// 2-理解@Adaptive注解的功能-生成代理类【SPI注入需要，spring不需要】
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

//        try {
//            System.in.read();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }
}
