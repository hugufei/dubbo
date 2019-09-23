package com.learn.dubbo_spi.car;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Car {

    @Adaptive(value = "carType")
    public void getColor(URL url);
}
