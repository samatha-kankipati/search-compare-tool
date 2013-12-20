package com.rackspace.search.comparetool

import com.rackspace.search.comparetool.gateway.ticketsearch.SearchGateway

import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import org.joda.time.DateTime

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import com.rackspace.search.comparetool.gateway.zendesk.ZendeskGateway
import groovy.json.JsonException
import net.sf.json.JSONObject
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.apache.commons.lang.StringUtils
import com.rackspace.search.comparetool.gateway.account.AccountGateway

@Component
class ZendeskTicketMismatchProcessor {

    private static final String TICKET_SEARCH_UPDATE_TIMESTAMP = "TicketSearchUpdateTimestamp"
    private static final String DOCUMENT_LAST_UPDATED_TIMESTAMP = "documentLastUpdatedTimestamp"
    Logger logger = LoggerFactory.getLogger(CoreTicketMismatchProcessor.class)

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
    private ZendeskGateway zendeskGateway

    @Autowired
    private SearchGateway ticketSearchGateway

    @Autowired
    private AccountGateway accountGateway

    private def slurper = new JsonSlurper()

    @Value('${ticket.core.dateformat}')
    def coreTicketDateFormat

    @Value('${ticket.core.date.timezone}')
    def coreTicketDateTimezone

    @Value('${technology.custom.field.id}')
    int technologyCustomFieldId

    @Value('${sct.es.username}')
    String sctUserName

    @Value('${sct.es.password}')
    String sctPassword

    @PostConstruct
    public setup() {
        client = new RESTClient(mismatchTicketSourceURL)
        client.auth.basic(sctUserName, sctPassword)
    }

    public getMismatchTickets() {
        logger.info("reading tickets from : ${mismatchTicketSourceURL}${mismatchTicketSourcePath + searchPath}")
        def response = client.get(
                path: mismatchTicketSourcePath + searchPath,
                requestContentType: "application/json",
                headers: [Accept: "application/json"],
                query: [q: "status:${UNMATCHED} AND sourceSystem:Zendesk_ticket", size: ticketLimit]
        );

        response.data.hits.hits.collect() { hit ->
            hit
        }

    }

    public compareTickets() {
        def mismatches = getMismatchTickets()
        logger.info("Got ${mismatches?.size()} mismatched tickets from \${mismatchTicketSourceURL}\${mismatchTicketSourcePath + searchPath}")
        def ticketNumbersToCompare = getTicketNumbers(mismatches)
        logger.info("Comparing these tickets: ${ticketNumbersToCompare}")

        if (ticketNumbersToCompare) {
            def zdTicketsArray = zendeskGateway.readTickets(ticketNumbersToCompare)
            def tsTicketsArray = ticketSearchGateway.readTickets(ticketNumbersToCompare)

            logger.info("Zendesk response data:\n ${zdTicketsArray}")
            logger.info("TicketSearch response data:\n ${tsTicketsArray}")
            mismatches.each {mismatch ->
                def mismatchFields = new ArrayList<String>()
                def compareResult = compareTicket(zdTicketsArray, tsTicketsArray, mismatch, mismatchFields)
                if (compareResult) {
                    mismatch._source.put("compareResults_mismatchDetails", compareResult.join(","))
                    mismatch._source.put("mismatchFields", mismatchFields.toArray(mismatchFields.size()))
                }
                else {
                    mismatch._source.status = "MATCHED"
                }
            }
            updateMismatchedTickets(mismatches)
        }
    }

    private compareTicket(def zdTicketsArray, def tsTicketsArray, def mismatch, def mismatchFields) {
        String ticketToCompare = mismatch._source.ticketRef
        def mismatchesFound = mismatch._source.mismatchFields

        List<String> mismatchesForThisTicket = new ArrayList<String>()

        def currentZdTicket = getTicketFromZDList(zdTicketsArray, ticketToCompare)
        def currentTSTicket = getTicketFromTSList(tsTicketsArray, ticketToCompare)

        if (currentTSTicket && currentZdTicket) {
            mismatch.put(TICKET_SEARCH_UPDATE_TIMESTAMP, (currentTSTicket.get(DOCUMENT_LAST_UPDATED_TIMESTAMP)))
            mismatchesFound.each { field ->
                switch (field) {

                    case "updated_at":
                    case "idle":
                    case "created_at":
                    case "age":
                        String zdValue = parseDate(readValueFromZDTicketJson(currentZdTicket, field))
                        String tsValue = parseDate(readValueFromTSTicketJson(currentTSTicket, field))
                        if (!(DateTime.parse(zdValue).getMillis() == DateTime.parse(tsValue).getMillis())) {
                            mismatchesForThisTicket.add("${field}[Zendesk:${zdValue}, TicketSearch:${tsValue}]")
                            mismatchFields.add(field)
                        }
                        break;
                    case "account":
                        if (!mismatchesFound.contains("account_number")) {
                            String zdValue = parseDate(readValueFromZDTicketJson(currentZdTicket, field))
                            String tsValue = parseDate(readValueFromTSTicketJson(currentTSTicket, field))
                            if (!zdValue.equals(tsValue)) {
                                mismatchesForThisTicket.add("${field}[Zendesk:${zdValue}, TicketSearch:${tsValue}]")
                                mismatchFields.add(field)
                            }
                            break;
                        }
                    default:
                        String zdValue = readValueFromZDTicketJson(currentZdTicket, field)
                        String tsValue = readValueFromTSTicketJson(currentTSTicket, field)
                        if (!zdValue.equals(tsValue)) {
                            mismatchesForThisTicket.add("${field}[Zendesk:${zdValue}, TicketSearch:${tsValue}]")
                            mismatchFields.add(field)
                        }
                }
            }
        }
        else if (!currentTSTicket && currentZdTicket) {
            mismatchesForThisTicket.add("Ticket Missing from Ticket Search")
        }
        else if (currentTSTicket && !currentZdTicket) {
            mismatchesForThisTicket.add("Ticket may be deleted in Zendesk, But still present in Ticket Search")
        }

        logger.info("Compare Result: ${ticketToCompare}, ${mismatchesForThisTicket}")
        mismatchesForThisTicket
    }

    private parseDate(String tsDateValue) {
        if (tsDateValue) {
            DateTime.parse(tsDateValue)
        }
        else {
            tsDateValue
        }
    }

    private getTicketFromTSList(def ticketsArray, String ticketToCompare) {
        String ticketjson = (ticketsArray.find() {ticket -> ticket.number.equals(ticketToCompare)} as JSONObject).toString()
        try {
            def result = slurper.parseText(ticketjson)
            result
        }
        catch (JsonException jex) {
            return null
        }
    }

    private getTicketFromZDList(def ticketsArray, String ticketToCompare) {
        ticketsArray.find() {ticket -> ticket.id.toString().equals(ticketToCompare)}
    }

    private readValueFromTSTicketJson(def tsTicketJson, String fieldName) {
        switch (fieldName) {
            case "account_number":
                return tsTicketJson.account?.number
            case "account":
                return tsTicketJson.account?.name

            case "has_windows_servers":
            case "has_linux_servers":
                return tsTicketJson.technology

            case "updated_at":
            case "idle":
                return tsTicketJson.updatedAt

            case "created_at":
            case "age":
                return tsTicketJson.createdAt

            case "assigned":
                return tsTicketJson.assignee?.name

            case "queue":
                return tsTicketJson.queue?.name

            case "ref_no":
                return tsTicketJson.number

            case "severity":
            case "severity_display":
                return tsTicketJson.priority.toString().toUpperCase()

            default:
                return tsTicketJson."${fieldName}"
        }
    }

    private readValueFromZDTicketJson(def zdTicketJson, String fieldName) {
        switch (fieldName) {
            case "account_number":
                return getAccountNumberFromZendesk(zdTicketJson, fieldName)
            case "account":
                def accountNumber = getAccountNumberFromZendesk(zdTicketJson, fieldName)

                if (accountNumber.equals("0000")) {
                    return null
                }
                else {
                    def account = accountGateway.getAccount("CLOUD", accountNumber)
                    return account.name
                }

            case "subject":
            case "status":
                return zdTicketJson.get(fieldName)

            case "updated_at":
            case "idle":
                return zdTicketJson.get("updated_at")

            case "created_at":
            case "age":
                return zdTicketJson.get("created_at")

            case "assigned":
                def user = zendeskGateway.getUser(zdTicketJson.assignee_id.toString())
                return user?.name

            case "queue":
                def group = zendeskGateway.getGroup(zdTicketJson.group_id.toString())
                return group?.name

            case "ref_no":
                return zdTicketJson.id

            case "severity":
            case "severity_display":
                return zdTicketJson.priority.toString().toUpperCase()

            case "has_windows_servers":
            case "has_linux_servers":
                def customField = zdTicketJson.custom_fields.find() {custom_field -> custom_field.id.toString().equals(technologyCustomFieldId.toString())}
                if (customField) {
                    return customField.value
                } else {
                    return ""
                }
            default:
                throw new Exception("${fieldName} Not implemented")
        }
    }

    private getAccountNumberFromZendesk(def zdTicketJson, def fieldName) {
        //Get requester
        def user = zendeskGateway.getUser(zdTicketJson.requester_id.toString())
        String email = user.email
        def accountNumber = email.substring(0, email.indexOf("@"))
        if (!StringUtils.isNumeric(accountNumber)) {
            return "0000"
        }
        else {
            return accountNumber
        }
    }

    private getTicketNumbers(def mismatches) {
        mismatches.collect() {mismatch ->
            mismatch._source.ticketRef
        }
    }

    public updateMismatchedTickets(def updatedData) {
        updatedData.each() {  mismatch ->
            def jsonData = new org.json.JSONObject(mismatch._source)
            def comparisonTime = mismatch.get(TICKET_SEARCH_UPDATE_TIMESTAMP) ?
                DateTime.parse(mismatch.get(TICKET_SEARCH_UPDATE_TIMESTAMP)) : DateTime.now(DateTimeZone.UTC)
            def reported = DateTime.parse(jsonData.reportDate)

            jsonData.put("matchAttempts", (jsonData.has("matchAttempts") ? jsonData.get("matchAttempts") : 0) + 1)
            jsonData.put("lastComparedTime", DateTime.now(DateTimeZone.UTC).toString())
            jsonData.put("dataMismatchPeriodSeconds", (new Duration(comparisonTime, reported)).toStandardSeconds().getSeconds())
            jsonData.put(TICKET_SEARCH_UPDATE_TIMESTAMP, mismatch.get(TICKET_SEARCH_UPDATE_TIMESTAMP))
            logger.info("Updating mismatchTicket record in elasticSearch ID:${mismatch._id}, Data: ${jsonData}")
            client.put(
                    path: mismatchTicketSourcePath + mismatch._id,
                    requestContentType: "application/json",
                    body: jsonData.toString()
            )
        }
    }

}
