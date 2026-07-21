@org.springframework.modulith.ApplicationModule(
        displayName = "Field Work",
        allowedDependencies = {
                "appointment::api", "audit::api", "authorization::api", "dispatch::api",
                "identity::api", "jooq", "network::api", "reliability::api", "shared", "task::api"
        })
package com.serviceos.fieldwork;
