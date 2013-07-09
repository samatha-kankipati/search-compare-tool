package com.rackspace.search;

import com.rackspace.search.gateway.core.CoreTicketGateway;
import com.rackspace.search.gateway.ticketsearch.SearchGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.Map;

public class SearchCompare {

    @Autowired
    private SearchGateway searchGateway;

    public SearchCompare() {
        final ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        AutowireCapableBeanFactory acbFactory = context.getAutowireCapableBeanFactory();
        acbFactory.autowireBean(this);
    }

    public static void main(String[] args) throws Exception {
        String configLocation = args[0].split("=")[1];
        System.setProperty("config.location",configLocation);

        SearchCompare main = new SearchCompare();

        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("limit", "2");


        main.searchGateway.readTickets(queryParams ) ;

    }
}
