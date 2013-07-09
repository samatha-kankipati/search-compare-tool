package com.rackspace.search.gateway.core

import com.rackspace.search.HttpGatewayClient
import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.json.JSONObject
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.apache.commons.lang.StringUtils

@Component
class CoreTicketGateway {

    Logger logger = LoggerFactory.getLogger(CoreTicketGateway.class)

    @Autowired
    @Qualifier("coreAuthGateway")
    private CoreAuthGateway authGateway

    @Value('${core.api.endpoint}')
    private String coreEndPoint

    @Value('${http.connection.timeout.in.milliseconds}')
    private int httpConnectionTimeout


    private String methodName = "query"
    private JSONObject requestData
    private RESTClient client
    private int maxAttempts = 2

    @PostConstruct
    void setup() {
        def coreTicketEndPoint = "${coreEndPoint}/${methodName}"
        client = HttpGatewayClient.getRESTClient(coreTicketEndPoint, httpConnectionTimeout)

        InputStream ips = getClass().getResourceAsStream("/coreTicketRequest.json");
        requestData = new JsonSlurper().parse(new InputStreamReader(ips))
    }

    def readCoreTickets(List<String> ticketNumbers) {
        int attempt = 1
        def jsonRequestData = requestData as JSONObject
        def response
        while (attempt < maxAttempts) {
            try {
                response = runPostCallOnCoreGateway(jsonRequestData, ticketNumbers)
                logger.info("Reading core tickets: returned from ATK api call.")
                return response
            } catch (Exception e) {
                handleException(e, attempt++)
            }
        }
    }

    def runPostCallOnCoreGateway(jsonRequestData, ticketNumbers) {
        jsonRequestData.load_arg.values = new JsonSlurper().parseText("""["number", "in", [""" + StringUtils.join
                (ticketNumbers, ",")+ "]")
        logger.info(" Reading core tickets: POST call to ${client.getUri()}\n Payload: ${jsonRequestData}")

        client.post(
                requestContentType: "application/json",
                body: jsonRequestData.toString(),
                headers: ['X-Auth': authGateway.getAuthToken()]);
    }

    def handleException(Exception e, int attempt) {
        logger.info("Got Exception while reading tickets from CTK api. Attempt #${attempt}, Exception: ${e.class}, ${e.message}")
        if (e.hasProperty("response") && e.response.status == 403) {
            authGateway.setSessionExpired(true)
            logger.info("Reading data from CTK api. session expired. Will reattempt get new session.")
        }  else {
            logger.error("Attempt #${attempt}. can't recover. escalating exception.")
            throw e
        }
    }
}