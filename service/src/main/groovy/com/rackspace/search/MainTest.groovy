package com.rackspace.search

import com.rackspace.search.comparetool.gateway.MismatchTicketsGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext


class MainTest {

    @Autowired
    MismatchTicketsGateway mismatchTicketsGateway

    public MainTest() {
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml" );
        AutowireCapableBeanFactory acbFactory = context.getAutowireCapableBeanFactory();
        acbFactory.autowireBean(this);

    }

    public static void main(String[] args) {
        String configLocation = args[0].split("=")[1]
        System.setProperty("config.location",configLocation)

        MainTest mainTest = new MainTest()

        mainTest.mismatchTicketsGateway.compareTickets()
    }

}
