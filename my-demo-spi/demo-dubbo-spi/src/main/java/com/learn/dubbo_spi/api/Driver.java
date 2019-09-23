package com.learn.dubbo_spi.api;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Driver {

    void driveCar(URL url);
}
