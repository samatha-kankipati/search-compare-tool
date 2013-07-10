package com.rackspace.search.gateway.core

import groovyx.net.http.RESTClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
@Qualifier("coreAuthGateway")
class CoreAuthGateway {

    private static Logger logger = LoggerFactory.getLogger(CoreAuthGateway.class)

    @Value('${core.api.endpoint}')
    private String coreEndPoint

    @Value('${core.api.username}')
    private String userName

    @Value('${core.api.password}')
    private String password

    private String methodName = "login"
    private String requestData
    private RESTClient client

    private String authToken
    private boolean sessionExpired


    @PostConstruct
    void setup() {
        def coreAuthEndPoint = "${coreEndPoint}/${methodName}/${userName}"
        requestData = "\"${password}\""
        client = new RESTClient(coreAuthEndPoint)
    }


    def getAuthToken() {
        try {
            if (!authToken || sessionExpired) {
                def response = client.post(
                        requestContentType: "application/json",
                        body: requestData
                );
                authToken = response.data.authtoken
                sessionExpired = false
            }
            authToken
        } catch (Exception e) {
            logger.error("Excpetion while getting Authentication from Core ", e)
            throw e
        }
    }

    void setSessionExpired(boolean sessionExpired) {
        this.sessionExpired = sessionExpired
    }
}
