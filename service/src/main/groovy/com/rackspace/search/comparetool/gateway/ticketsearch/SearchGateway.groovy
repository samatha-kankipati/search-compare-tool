package com.rackspace.search.comparetool.gateway.ticketsearch

import com.rackspace.search.comparetool.HttpGatewayClient
import groovyx.net.http.RESTClient
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class SearchGateway {

    Logger logger = LoggerFactory.getLogger(SearchGateway.class)

    @Value('${search.api.endpoint}')
    private String searchEndpoint

    @Value('${http.connection.timeout.in.milliseconds}')
    private int httpConnectionTimeout

    private RESTClient client

    @PostConstruct
    void setup() {
        client = HttpGatewayClient.getRESTClient(searchEndpoint, httpConnectionTimeout)
    }

    def readTickets(List<String> ticketNumbers) {

        def response
        try {
            response =
                client.get(
                        requestContentType: "application/json",
                        query: [number: StringUtils.join(ticketNumbers, ",")])
            logger.info("Reading tickets: returned from Search api call.")
            return response.data.response.tickets
        } catch (Exception e) {
            logger.info("Exception", e)
        }

    }
}