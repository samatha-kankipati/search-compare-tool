package com.rackspace.search.comparetool.gateway
import groovyx.net.http.RESTClient
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class MismatchTicketsGateway {

    Logger logger = LoggerFactory.getLogger(MismatchTicketsGateway.class)

    public final String UNMATCHED = "UNMATCHED"
    private final String searchPath = "_search"

    @Value('${http.connection.timeout.in.milliseconds}')
    private int httpConnectionTimeout

    @Value('${mismatchTickets.source.url}')
    public final String mismatchTicketSourceURL

    @Value('${mismatchTickets.source.path}')
    public final String mismatchTicketSourcePath

    @Value('${mismatchTickets.limit}')
    public final int ticketLimit

    private RESTClient client



    @PostConstruct
    public setup(){
        client = new RESTClient(mismatchTicketSourceURL)
    }

    public getMismatchTickets() {
        //read mismatched tickets that are in UNMATCHED status from elastic search.
        def response = client.get(
                path: mismatchTicketSourcePath+searchPath,
                requestContentType: "application/json",
                headers: [Accept: "application/json"],
                query: [q:"status:${UNMATCHED}", size: ticketLimit]
        );

        response.data.hits.hits.collect() {hit ->
                      hit
        }

    }

    public compareTickets(){
        def mismatches = getMismatchTickets()
        def ticketNumbersToCompare = getTicketNumbers(mismatches)

        //pass these ticket number to CTK gateway : tickets as JSONArray
        //pass these ticket number to ticket search gateway : tickets as JSONArray

        mismatches.each {mismatch ->
            //mismatch._source.status = "UNMATCHED"
            def compareResult = compareTicket()
            /*
              mismatch._source.status =  "UNMATCHED" (if compareResult is not empty) | "MATCHED" (if compareResult is empty)
             */
        }
        updateMismatchticets(mismatches)
    }

    private compareTicket(def ctkTicketsArray, def tsTickesArray, String ticketToCompare) {
        def fieldsToCompare = ["queue.id", "status", "hasWindowsServers", "hasLinuxServers", "assignee.sso", "createdAt", "account.highProfile",
                               "priority", "lastPublicCommentDate", "accountServiceLevel", "account.number", "account.team", "difficulty",
                               "category", "statusTypes"]

        //get current ticket from CTK Array & TS Array
        //compare CTK & TS ticket for given fields in  fieldsToCompare
        //return List of fields that doesn't match as  as string

    }
    private getTicketNumbers(def mismatches){
        mismatches.collect () {mismatch ->
            mismatch._source.ticketRef
        }
    }
    public updateMismatchticets(def updatedData){
        //post these updated json Objects (mismatched tickets) to elastic search.

        println "============"
        updatedData.each() {  mismatch ->
            def jsonData = new org.json.JSONObject(mismatch._source)
            def comparisonTime = DateTime.now(DateTimeZone.UTC)
            def reported = DateTime.parse(jsonData.reportDate)
            def unmatchedTimePeriod = (new Duration(reported, comparisonTime)).toStandardSeconds().getSeconds()
            def matchAttempts = (jsonData.has("matchAttempts"))? jsonData.get("matchAttempts") :  0

            jsonData.put("matchAttempts", matchAttempts+1)
            jsonData.put("lastComparedTime", DateTime.now(DateTimeZone.UTC).toString())
            jsonData.put("dataMismatchPeriodSeconds", unmatchedTimePeriod)
            println "${mismatch._id}, ${jsonData}"
            def response  = client.post(
                    path: mismatchTicketSourcePath+mismatch._id,
                    requestContentType: "application/json",
                    body: jsonData.toString()
            )
        }
    }
}
