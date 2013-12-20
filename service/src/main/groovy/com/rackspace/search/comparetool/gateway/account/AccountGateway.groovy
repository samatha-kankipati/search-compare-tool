package com.rackspace.search.comparetool.gateway.account

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.WebResource
import org.springframework.beans.factory.annotation.Value

import javax.annotation.PostConstruct
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter

import javax.ws.rs.core.MediaType
import com.sun.jersey.api.client.ClientResponse
import groovy.json.JsonSlurper
import org.springframework.stereotype.Component

@Component
class AccountGateway {

    Logger logger = LoggerFactory.getLogger(AccountGateway.class)

    @Autowired
    @Qualifier("jerseyClient")
    private Client client

    private WebResource accountResource

    @Value('${account.api.endpoint}')
    private String accountEndPoint

    @Value('${account.api.username}')
    private String username

    @Value('${account.api.password}')
    private String password

    @Value('${http.connection.timeout.in.milliseconds}')
    private int httpConnectionTimeout

    @Value('${http.read.timeout.in.milliseconds}')
    private int httpReadTimeout

    @PostConstruct
    void setup() {
        client.setConnectTimeout(httpConnectionTimeout)
        client.setReadTimeout(httpReadTimeout)
    }

    def getAccount(String accountType, String accountNumber) {

        accountResource = client.resource(accountEndPoint + "/" + accountType + "/" + accountNumber)
        accountResource.addFilter(new HTTPBasicAuthFilter(username, password))

        def response = accountResource.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class)

        if (response.getStatus() == 200) {
            def strJson = response.getEntity(String.class)
            def slurper = new JsonSlurper()
            def jsonResponse = slurper.parseText(strJson)

            def result = jsonResponse.account

            return result
        } else {
            logger.error("Exception while getting account : " + response.getStatus())
            throw new Exception("Account Service returned response:" + response.getStatus())
        }
    }
}
