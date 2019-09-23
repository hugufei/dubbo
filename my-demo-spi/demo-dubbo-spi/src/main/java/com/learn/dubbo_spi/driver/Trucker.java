package com.learn.dubbo_spi.driver;

import com.learn.dubbo_spi.car.Car;
import com.learn.dubbo_spi.gender.Gender;
import org.apache.dubbo.common.URL;

public class Trucker implements Driver {

    private Car car;
   // private Gender gender;
    private Gender boy;

    //这里是spi注入，依赖@SPI注解和@Adaptive注解
    public void setCar(Car car) {
        this.car = car;
    }

//    //这里使用spring注入，会取Gender的类型，如果有多个，会报错
//    public void setGender(Gender gender) {
//        this.gender = gender;
//    }

    //这里使用spring注入，会取id为Boy的bean，如果没找到，会按类型去找
    public void setBoy(Gender boy) {
        this.boy = boy;
    }

    @Override
    public void driveCar(URL url) {
        car.getColor(url);
        //gender.getGender(url);
        boy.getGender();
    }
}
