while [ $# -gt 0 ]
do
    queueid=$1
    runcount=`egrep "Queue: $queueid" /srv/sync/log/sync.log.* -A4 | egrep "missing tickets: [0-9]*" -o | wc -l`
    details=`egrep "Queue: $queueid" /srv/sync/log/sync.log.* -A4 | egrep "missing tickets: [0-9]*" -o | uniq|  sed -e :a -e 'N;s/\n/, /' -e ta`

        echo " "
    echo "--------------------------"
    echo "Queue : $queueid"

    echo "How many times comparison ran? $runcount"
    echo "DETAILS: $details"
    echo "-------------------------"
    echo " "

    url="http://test.elastic.search.api.rackspace.com/queuetest/queuetest/$queueid"
    jsondata="{\"queueid\": $queueid, \"compareRunCount\": $runcount, \"details\":\"$details\"}"
    curl -X POST $url  -d "$jsondata"

    echo " "

    shift

done
