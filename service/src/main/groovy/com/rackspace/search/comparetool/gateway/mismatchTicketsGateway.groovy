package com.rackspace.search.comparetool.gateway

import com.rackspace.search.gateway.core.CoreTicketGateway
import com.rackspace.search.gateway.ticketsearch.SearchGateway
import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.text.SimpleDateFormat

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

    @Autowired
    private CoreTicketGateway coreTicketGateway
    @Autowired
    private SearchGateway ticketSearchGateway

    private def slurper = new JsonSlurper()

    @Value('${ticket.core.dateformat}')
    def coreTicketDateFormat

    @Value('${ticket.core.date.timezone}')
    def coreTicketDateTimezone

    private SimpleDateFormat coreDateFormatter

    @PostConstruct
    public setup(){
        client = new RESTClient(mismatchTicketSourceURL)
        coreDateFormatter = new SimpleDateFormat(coreTicketDateFormat)
        coreDateFormatter.setTimeZone(TimeZone.getTimeZone(coreTicketDateTimezone))
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
        //ticketNumbersToCompare.add("110915-08098")
        //ticketNumbersToCompare.add("130615-06584")
        println ("\n\nTickets to Compare: \n ${ticketNumbersToCompare}")
        def ctkTicketsArray = coreTicketGateway.readCoreTickets(ticketNumbersToCompare)
        def tsTicketsArray = ticketSearchGateway.readTickets(ticketNumbersToCompare)

        println "\n\nCTK:\n ${ctkTicketsArray}"
        println "\n\nTS:\n ${tsTicketsArray}"

        mismatches.each {mismatch ->

            def compareResult = compareTicket(ctkTicketsArray, tsTicketsArray, mismatch._source.ticketRef)
            if(compareResult) {
                mismatch._source.put("mismatchDetails", compareResult.join(","))
            }
            else {
                mismatch._source.status =  "MATCHED"
            }
        }
        //updateMismatchticets(mismatches)
    }

    private compareTicket(def ctkTicketsArray, def tsTicketsArray, String ticketToCompare) {
        def fieldsToCompare = ["queue.id", "status", "hasWindowsServers", "hasLinuxServers", "assignee.sso", "createdAt", "account.highProfile",
                               "priority", "lastPublicCommentDate", "accountServiceLevel", "account.number", "account.team", "difficulty",
                               "category", "statusTypes"]
        List<String> mismatchesForThsiTicket = new ArrayList<String>()

        //TODO: get current ticket from CTK Array & TS Array
        def currentCTKTicket = getTicketFromList(ctkTicketsArray,ticketToCompare)
        def currentTSTicket = getTicketFromList(tsTicketsArray,ticketToCompare)
        if (currentTSTicket) {
            fieldsToCompare.each { field ->
                switch(field){
                    case "account.highProfile":
                        def ctkValue = currentCTKTicket?."${field}"?:[]
                        def tsValue = readValueFromTSTicketJson(currentTSTicket, field)
                        if (!(ctkValue.contains("High Profile") == tsValue)) {
                            mismatchesForThsiTicket.add("${field}[CTK:${ctkValue}, TicketSearch:${tsValue}]")
                        }
                        break;
                    case "createdAt":
                    case "lastPublicCommentDate":
                        String defaultDate = DateTime.parse("2011-09-16T03:06:49.000+0000")
                        String ctkValue = parseCoreDate(currentCTKTicket?."${field}")?:defaultDate
                        String tsValue = parseTSDate(readValueFromTSTicketJson(currentTSTicket, field))?:defaultDate
                        if (!(DateTime.parse(ctkValue).getMillis() == DateTime.parse(tsValue).getMillis())) {
                            mismatchesForThsiTicket.add("${field}[CTK:${ctkValue}, TicketSearch:${tsValue}]")
                        }
                        break;
                    case "statusTypes":
                        break;
                    default:
                        String ctkValue = currentCTKTicket?."${field}"
                        String tsValue = readValueFromTSTicketJson(currentTSTicket, field)
                        if (!ctkValue.equals(tsValue)) {
                            mismatchesForThsiTicket.add("${field}[CTK:${ctkValue}, TicketSearch:${tsValue}]")
                        }
                }
            }
        }
        else {
            mismatchesForThsiTicket.add("Ticket Missing from Ticket Search")
        }
        println ("${ticketToCompare}, ${mismatchesForThsiTicket}")
        mismatchesForThsiTicket
    }

    private parseCoreDate(String coreDateValue){
        if (coreDateValue) {
            new DateTime(coreDateFormatter.parse(coreDateValue).getTime())
        }
        else {
            coreDateValue
        }
    }

    private parseTSDate(String tsDateValue){
        if (tsDateValue) {
            DateTime.parse(tsDateValue)
        }
        else {
            tsDateValue
        }
    }
    private getTicketFromList(def ticketsArray, String ticketToCompare) {
        String ticketjson = (ticketsArray.find() {ticket -> ticket.number.equals(ticketToCompare)} as JSONObject).toString()
        try {
            def result = slurper.parseText(ticketjson)
            result
        }
        catch (JsonException jex) {
            return null
        }
    }

    private readValueFromTSTicketJson(def tsTicketJson, String fieldName) {
        switch(fieldName){
            case "queue.id":
                return tsTicketJson.queue?.id
            case "account.highProfile":
                return tsTicketJson.account?.highProfile
            case "account.number":
                return tsTicketJson.account?.number
            case "account.team":
                return tsTicketJson.account?.team
            case "assignee.sso":
                return tsTicketJson.assignee?.sso
            case "hasWindowsServers":
            case "hasLinuxServers":
                return (tsTicketJson."${fieldName}" ? "true": "false")
            default:
                return tsTicketJson."${fieldName}"
        }
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
