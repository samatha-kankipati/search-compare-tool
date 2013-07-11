filename=$1

for tkt in `egrep "CompareDetails" $filename | egrep "[0-9]{6}-[0-9]{5}" -o`
do
    url="http://test.elastic.search.api.rackspace.com/queuetest/mismatchedTickets/"
    ticketJson="{\"ticketRef\": \"$tkt\", \"staus\":\"UNMATCHED\", \"mismatchType\": \"CONTENT_MISMATCH\", \"reportDate\": \"`date -u '+%Y-%m-%dT%H:%M:%SZ'`\", \"logfile\":\"$filename\"}"
    curl -X POST $url  -d "$ticketJson"
    echo $ticketJson
    echo " "
done

for tkt in `egrep "missing tickets" $filename | egrep "[0-9]{6}-[0-9]{5}" -o`
do
    url="http://test.elastic.search.api.rackspace.com/queuetest/mismatchedTickets/"
    ticketJson="{\"ticketRef\": \"$tkt\", \"staus\":\"UNMATCHED\", \"mismatchType\": \"MISSING\", \"reportDate\": \"`date -u '+%Y-%m-%dT%H:%M:%SZ'`\", \"logfile\":\"$filename\"}"
    curl -X POST $url  -d "$ticketJson"
    echo $ticketJson
    echo " "
done
