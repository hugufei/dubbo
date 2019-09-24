package com.learn.dubbo_spi.driver;

import com.learn.dubbo_spi.car.Car;
import com.learn.dubbo_spi.gender.Gender;
import org.apache.dubbo.common.URL;

public class Trucker implements Driver {

    private Car car;
    private Gender gender;
    private Gender boy;
    private Gender girl;

    //这里是spi注入，依赖@SPI注解和@Adaptive注解
    public void setCar(Car car) {
        this.car = car;
    }

    //这里使用spring注入，会取beanName为girl的bean，如果没找到，会按类型去找
    public void setGirl(Gender girl) {
        this.girl = girl;
    }

    //这里使用spring注入，会取beanName为boy的bean，如果没找到，会按类型去找
    public void setBoy(Gender boy) {
        this.boy = boy;
    }

    //这里使用spring注入，会取beanName为gender的bean，如果没找到，会按类型去找, 因为这里配了多个，所以会失败
    public void setGender(Gender gender) {
        this.gender = gender;
    }

    @Override
    public void doDrive(URL url) {
        car.getColor(url);
        boy.getGender();
        girl.getGender();
        //gender.getGender();
    }
}
