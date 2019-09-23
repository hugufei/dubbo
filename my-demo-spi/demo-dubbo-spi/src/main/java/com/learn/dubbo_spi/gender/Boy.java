package com.learn.dubbo_spi.gender;

public class Boy implements Gender {

    @Override
    public void getGender() {
        System.out.println("I am Boy");
    }
}
