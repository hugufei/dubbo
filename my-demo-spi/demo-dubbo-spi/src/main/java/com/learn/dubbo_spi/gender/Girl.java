package com.learn.dubbo_spi.gender;

public class Girl implements Gender {

    @Override
    public void getGender() {
        System.out.println("I am Girl");
    }


}
