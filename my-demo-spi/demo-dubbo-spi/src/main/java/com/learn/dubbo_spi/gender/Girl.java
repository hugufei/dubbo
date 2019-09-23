package com.learn.dubbo_spi.gender;

import org.apache.dubbo.common.URL;

public class Girl implements Gender {

    @Override
    public void getGender() {
        System.out.println("I am Girl");
    }


}
