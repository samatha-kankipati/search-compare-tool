package com.rackspace.search

import com.rackspace.search.comparetool.CompareMismatchTickets
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext


class Main {

    @Autowired
    CompareMismatchTickets mismatchTicketsGateway

    public Main() {
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        AutowireCapableBeanFactory acbFactory = context.getAutowireCapableBeanFactory();
        acbFactory.autowireBean(this);

    }

    public static void main(String[] args) {
        String configLocation = args[0].split("=")[1]
        System.setProperty("config.location", configLocation)

        Main mainTest = new Main()

        mainTest.mismatchTicketsGateway.compareTickets()
    }


}
