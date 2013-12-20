package com.rackspace.search.comparetool

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class CacheObject {

    String id
    String name
    String email
    DateTime expiryTimestamp

    public CacheObject(id, name, email, int cacheLifetimeInMinutes) {
        this.id = id
        this.name = name
        this.email = email

        DateTime currentTime = DateTime.now(DateTimeZone.UTC)
        expiryTimestamp = currentTime.plusMinutes(cacheLifetimeInMinutes)
    }

    boolean isExpired() {
        DateTime.now(DateTimeZone.UTC).isAfter(expiryTimestamp)
    }
}
