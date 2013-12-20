package com.rackspace.search.comparetool.gateway.zendesk

import groovy.json.JsonSlurper

import org.apache.commons.lang.StringUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter
import com.sun.jersey.core.util.MultivaluedMapImpl
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.MediaType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import com.rackspace.search.comparetool.CacheObject


@Component
class ZendeskGateway {

    Logger logger = LoggerFactory.getLogger(ZendeskGateway.class)

    @Autowired
    @Qualifier("jerseyClient")
    private Client client

    private WebResource ticketsResource
    private WebResource userIdResource

    private WebResource groupIdResource

    @Value('${zendesk.api.endpoint}')
    private String zendeskEndPoint

    @Value('${zendesk.api.username}')
    private String username

    @Value('${zendesk.api.password}')
    private String password

    @Value('${zendesk.api.tickets.path}')
    private String ticketsPath

    @Value('${http.connection.timeout.in.milliseconds}')
    private int httpConnectionTimeout

    @Value('${http.read.timeout.in.milliseconds}')
    private int httpReadTimeout

    @Value('${zendesk.api.user.endpoint}')
    private String userPath;

    @Value('${zendesk.api.group.endpoint}')
    private String groupPath;


    private Map<String, CacheObject> cacheData = new HashMap<String, CacheObject>()
    private DateTime cleanExecutionTime = new DateTime(DateTimeZone.UTC)
    int cacheTimeInMinutes = 5

    @PostConstruct
    void setup() {
        client.setConnectTimeout(httpConnectionTimeout)
        client.setReadTimeout(httpReadTimeout)

        ticketsResource = client.resource(zendeskEndPoint + ticketsPath)
        ticketsResource.addFilter(new HTTPBasicAuthFilter(username, password))
    }

    def readTickets(List ticketNumbers) {
        logger.info("Getting the tickets: ${ticketNumbers} ")

        String queryTickets = StringUtils.join(ticketNumbers, ",")
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl()
        queryParams.putSingle("ids", queryTickets)

        def response = ticketsResource.queryParams(queryParams).accept(MediaType.APPLICATION_JSON_TYPE).
                post(ClientResponse.class)

        if (response.getStatus() == 200) {
            def strJson = response.getEntity(String.class)
            def slurper = new JsonSlurper()
            def jsonResponse = slurper.parseText(strJson)

            def result = jsonResponse.tickets
            logger.info("Retrieved ticket updates. Number of tickets: ${result?.size()} ")
            return result
        } else {
            logger.error("ZendeskAPIException while getting zendesk tickets: " + response.getStatus())
            throw new Exception("Zendesk returned response:" + response.getStatus())
        }
    }

    def getUser(String userId) {
        cleanCache()
        def cacheUser = cacheData.get("USER" + userId)
        if (!cacheUser) {
            def user = getUserFromZendesk(userId)
            if (user) {
                updateCache("USER" + userId, user.name, user.email)
                cacheUser = cacheData.get("USER" + userId)
            }
        }
        cacheUser
    }

    public void updateCache(key, name, email) {
        CacheObject newCache = new CacheObject(key, name, email, cacheTimeInMinutes)
        cacheData.put(key, newCache);

    }

    public void cleanCache() {
        if (DateTime.now(DateTimeZone.UTC).isAfter(cleanExecutionTime)) {
            cacheData = cacheData.findAll() { cache -> !cache.getValue().isExpired()}
            cleanExecutionTime = cleanExecutionTime.plusMinutes(cacheTimeInMinutes)
        }
    }

    def getUserFromZendesk(String userId) {
        String url = userPath.replace("userId", userId)
        logger.info("Getting the user ${userId}")

        userIdResource = client.resource(zendeskEndPoint + url)
        userIdResource.addFilter(new HTTPBasicAuthFilter(username, password))

        def response = userIdResource.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class)

        if (response.getStatus() == 200) {
            def strJson = response.getEntity(String.class)
            def slurper = new JsonSlurper()
            def jsonResponse = slurper.parseText(strJson)

            def result = jsonResponse.user

            return result
        } else {
            logger.error("ZendeskAPIException while getting zendesk user: " + response.getStatus())
            if (response.getStatus() == 404) {
                return  null
            }
            throw new Exception("Zendesk returned response:" + response.getStatus())
        }
    }

    def getGroup(String groupId) {
        cleanCache()
        def cacheUser = cacheData.get("GROUP" + groupId)
        if (!cacheUser) {
            def group = getGroupFromZendesk(groupId)

            if (group) {
                updateCache("GROUP" + groupId, group.name, "")
                cacheUser = cacheData.get("GROUP" + groupId)
            }
        }
        cacheUser
    }


    def getGroupFromZendesk(String groupId) {
        String url = groupPath.replace("groupId", groupId)
        logger.info("Getting the group ${groupId}")

        groupIdResource = client.resource(zendeskEndPoint + url)
        groupIdResource.addFilter(new HTTPBasicAuthFilter(username, password))

        def response = groupIdResource.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class)

        if (response.getStatus() == 200) {
            def strJson = response.getEntity(String.class)
            def slurper = new JsonSlurper()
            def jsonResponse = slurper.parseText(strJson)

            def result = jsonResponse.group

            return result
        } else {
            logger.error("ZendeskAPIException while getting zendesk group: " + response.getStatus())
            if (response.getStatus() == 404) {
                return null
            }
            throw new Exception("Zendesk returned response:" + response.getStatus())
        }
    }
}