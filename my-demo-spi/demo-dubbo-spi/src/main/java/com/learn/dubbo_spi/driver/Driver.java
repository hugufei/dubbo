package com.learn.dubbo_spi.driver;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Driver {

    void doDrive(URL url);
}
